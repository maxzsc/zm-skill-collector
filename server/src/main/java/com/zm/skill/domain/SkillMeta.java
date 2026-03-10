package com.zm.skill.domain;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class SkillMeta {

    private String name;
    private SkillType type;
    private String domain;
    private String trigger;
    private List<String> aliases;
    private String summary;
    private Completeness completeness;
    private Visibility visibility;
    private List<String> sources;

    @JsonProperty("related_skills")
    private List<String> relatedSkills;

    // Procedure-specific fields
    private ProcedureCategory category;

    @JsonProperty("agent_readiness")
    private AgentReadiness agentReadiness;

    @JsonProperty("related_knowledge")
    private List<String> relatedKnowledge;

    @JsonProperty("last_updated")
    private Instant lastUpdated;

    private Boolean stale;

    @JsonProperty("needs_review")
    private Boolean needsReview;

    // Validation methods
    public static boolean isValidSummary(String summary) {
        return summary != null && summary.length() <= 50;
    }

    public static boolean isValidTrigger(String trigger) {
        return trigger != null && trigger.length() <= 100;
    }

    public static boolean isValidAliases(List<String> aliases) {
        return aliases != null && aliases.size() <= 10;
    }
}
