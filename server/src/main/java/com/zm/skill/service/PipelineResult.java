package com.zm.skill.service;

import com.zm.skill.domain.Completeness;
import com.zm.skill.storage.SkillDocument;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Result DTO for the processing pipeline.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PipelineResult {
    private String skillName;
    private String domain;
    private boolean success;
    private String errorMessage;
    private SkillDocument skillDocument;
    private Completeness completeness;
    private ValidationService.ValidationResult validationResult;
    private DeduplicationService.DedupResult dedupResult;
    private List<String> warnings;
}
