package com.zm.skill.controller;

import com.zm.skill.controller.dto.ApiResponse;
import com.zm.skill.storage.SkillRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.Optional;

@RestController
@RequestMapping("/api/glossary")
public class GlossaryController {

    private final SkillRepository skillRepository;

    public GlossaryController(SkillRepository skillRepository) {
        this.skillRepository = skillRepository;
    }

    @GetMapping("/{domain}")
    public ResponseEntity<ApiResponse<Map<String, List<String>>>> getGlossary(
            @PathVariable String domain
    ) {
        Optional<Map<String, List<String>>> glossary = skillRepository.loadGlossary(domain);
        if (glossary.isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(ApiResponse.ok(glossary.get()));
    }
}
