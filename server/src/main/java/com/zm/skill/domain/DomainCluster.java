package com.zm.skill.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DomainCluster {
    private String domain;
    private double confidence;
    private List<String> documents;
    private SkillType suggestedType;
    private String summaryPreview;
}
