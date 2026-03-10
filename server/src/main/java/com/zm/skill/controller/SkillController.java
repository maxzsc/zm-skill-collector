package com.zm.skill.controller;

import com.zm.skill.controller.dto.ApiResponse;
import com.zm.skill.domain.SkillMeta;
import com.zm.skill.service.VisibilityFilter;
import com.zm.skill.storage.SkillDocument;
import com.zm.skill.storage.SkillRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/skills")
public class SkillController {

    private final SkillRepository skillRepository;

    public SkillController(SkillRepository skillRepository) {
        this.skillRepository = skillRepository;
    }

    @GetMapping
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

    @GetMapping("/{name}")
    public ResponseEntity<ApiResponse<SkillDocument>> getSkill(@PathVariable String name) {
        Optional<SkillDocument> skill = skillRepository.findByName(name);
        if (skill.isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(ApiResponse.ok(skill.get()));
    }

    @GetMapping("/index")
    public ResponseEntity<ApiResponse<List<SkillMeta>>> getIndex(
            @RequestParam(required = false) List<String> teams
    ) {
        List<SkillMeta> index = skillRepository.loadIndex();
        index = VisibilityFilter.filter(index, teams);
        return ResponseEntity.ok(ApiResponse.ok(index));
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
