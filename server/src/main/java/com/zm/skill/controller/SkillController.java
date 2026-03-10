package com.zm.skill.controller;

import com.zm.skill.controller.dto.ApiResponse;
import com.zm.skill.controller.dto.ErrorCode;
import com.zm.skill.domain.SkillMeta;
import com.zm.skill.service.ReleaseService;
import com.zm.skill.service.StalenessService;
import com.zm.skill.service.VisibilityFilter;
import com.zm.skill.storage.SkillDocument;
import com.zm.skill.storage.SkillRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api")
public class SkillController {

    private final SkillRepository skillRepository;
    private final StalenessService stalenessService;
    private final ReleaseService releaseService;

    public SkillController(SkillRepository skillRepository, StalenessService stalenessService,
                           ReleaseService releaseService) {
        this.skillRepository = skillRepository;
        this.stalenessService = stalenessService;
        this.releaseService = releaseService;
    }

    @GetMapping("/skills")
    public ResponseEntity<ApiResponse<List<SkillMeta>>> listSkills(
            @RequestParam(required = false) String domain,
            @RequestParam(required = false) String team,
            @RequestParam(required = false) List<String> teams,
            @RequestParam(required = false) String type
    ) {
        List<SkillMeta> allSkills = skillRepository.loadIndex();

        // Merge single 'team' param into 'teams' list for backward compatibility
        List<String> effectiveTeams = resolveTeams(team, teams);

        List<SkillMeta> filtered = allSkills.stream()
                .filter(meta -> domain == null || domain.equals(meta.getDomain()))
                .filter(meta -> type == null || type.equals(meta.getType().getValue()))
                .collect(Collectors.toList());

        // Apply visibility filtering
        filtered = VisibilityFilter.filter(filtered, effectiveTeams);

        return ResponseEntity.ok(ApiResponse.ok(filtered));
    }

    @GetMapping("/skills/{name}")
    public ResponseEntity<ApiResponse<SkillDocument>> getSkill(
            @PathVariable String name,
            @RequestParam(required = false) List<String> teams
    ) {
        Optional<SkillDocument> skill = skillRepository.findByName(name);
        if (skill.isEmpty()) {
            return ResponseEntity.status(404).body(ApiResponse.error(ErrorCode.NOT_FOUND, "Resource not found: " + name));
        }

        SkillDocument doc = skill.get();

        // QA-002: Visibility check — same logic as list/index
        List<SkillMeta> singleList = List.of(doc.getMeta());
        List<SkillMeta> visible = VisibilityFilter.filter(singleList, teams);
        if (visible.isEmpty()) {
            return ResponseEntity.status(404).body(ApiResponse.error(ErrorCode.NOT_FOUND, "Resource not found: " + name));
        }

        // QA-011: Decorate body with staleness warning if stale
        if (Boolean.TRUE.equals(doc.getMeta().getStale())) {
            String decoratedBody = stalenessService.decorateBody(doc);
            doc = new SkillDocument(doc.getMeta(), decoratedBody);
        }

        return ResponseEntity.ok(ApiResponse.ok(doc));
    }

    @GetMapping("/skills/index")
    public ResponseEntity<ApiResponse<List<SkillMeta>>> getIndex(
            @RequestParam(required = false) List<String> teams
    ) {
        List<SkillMeta> index = skillRepository.loadIndex();
        index = VisibilityFilter.filter(index, teams);
        return ResponseEntity.ok(ApiResponse.ok(index));
    }

    // QA-001: Release endpoint
    @GetMapping("/releases")
    public ResponseEntity<ApiResponse<ReleaseService.ReleaseFile>> getReleases() {
        return ResponseEntity.ok(ApiResponse.ok(releaseService.getPublished()));
    }

    private List<String> resolveTeams(String team, List<String> teams) {
        if (teams != null && !teams.isEmpty()) {
            return teams;
        }
        if (team != null && !team.isBlank()) {
            return List.of(team);
        }
        return null;
    }
}
