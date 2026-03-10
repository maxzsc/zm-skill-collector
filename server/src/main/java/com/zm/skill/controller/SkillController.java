package com.zm.skill.controller;

import com.zm.skill.controller.dto.ApiResponse;
import com.zm.skill.domain.SkillMeta;
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
            @RequestParam(required = false) String type
    ) {
        List<SkillMeta> allSkills = skillRepository.loadIndex();

        List<SkillMeta> filtered = allSkills.stream()
                .filter(meta -> domain == null || domain.equals(meta.getDomain()))
                .filter(meta -> type == null || type.equals(meta.getType().getValue()))
                .collect(Collectors.toList());

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
    public ResponseEntity<ApiResponse<List<SkillMeta>>> getIndex() {
        List<SkillMeta> index = skillRepository.loadIndex();
        return ResponseEntity.ok(ApiResponse.ok(index));
    }
}
