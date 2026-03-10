package com.zm.skill.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zm.skill.ai.ClaudeClient;
import com.zm.skill.domain.Completeness;
import com.zm.skill.domain.SkillMeta;
import com.zm.skill.domain.SkillType;
import com.zm.skill.storage.SkillDocument;
import lombok.Data;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * Validates skill quality: format, completeness level, required fields, and optional AI checks.
 */
@Service
public class ValidationService {

    private static final int MIN_BODY_LENGTH_L2 = 100;
    private static final int MIN_BODY_LENGTH_L3 = 300;

    private final ClaudeClient claudeClient;
    private final ObjectMapper objectMapper;

    public ValidationService(ClaudeClient claudeClient) {
        this.claudeClient = claudeClient;
        this.objectMapper = new ObjectMapper();
    }

    /**
     * Validate skill format and determine completeness level (no AI).
     */
    public ValidationResult validate(SkillDocument doc) {
        ValidationResult result = new ValidationResult();
        List<String> errors = new ArrayList<>();

        SkillMeta meta = doc.getMeta();

        // Required fields for all levels
        if (meta.getName() == null || meta.getName().isBlank()) {
            errors.add("Missing required field: name");
        }
        if (meta.getType() == null) {
            errors.add("Missing required field: type");
        }
        if (meta.getDomain() == null || meta.getDomain().isBlank()) {
            errors.add("Missing required field: domain");
        }
        if (meta.getSummary() == null || meta.getSummary().isBlank()) {
            errors.add("Missing required field: summary");
        }
        if (meta.getVisibility() == null) {
            errors.add("Missing required field: visibility");
        }
        if (doc.getBody() == null || doc.getBody().isBlank()) {
            errors.add("Missing required field: body must not be empty");
        }

        // Length constraints
        if (meta.getSummary() != null && !SkillMeta.isValidSummary(meta.getSummary())) {
            errors.add("Length constraint: summary must be <= 50 characters");
        }
        if (meta.getTrigger() != null && !SkillMeta.isValidTrigger(meta.getTrigger())) {
            errors.add("Length constraint: trigger must be <= 100 characters");
        }
        if (meta.getAliases() != null && !SkillMeta.isValidAliases(meta.getAliases())) {
            errors.add("Length constraint: aliases must be <= 10 items");
        }

        result.setErrors(errors);
        result.setValid(errors.isEmpty());

        // Auto-determine completeness level
        if (errors.isEmpty()) {
            result.setAutoCompleteness(determineCompleteness(doc));
        }

        return result;
    }

    /**
     * Validate with AI assistance (Q&A test for knowledge, step simulation for procedure).
     */
    public ValidationResult validateWithAi(SkillDocument doc) {
        ValidationResult result = validate(doc);

        String prompt;
        if (doc.getMeta().getType() == SkillType.KNOWLEDGE) {
            prompt = buildKnowledgeValidationPrompt(doc);
        } else {
            prompt = buildProcedureValidationPrompt(doc);
        }

        try {
            String aiResponse = claudeClient.validate(prompt);
            String json = aiResponse.strip();
            if (json.startsWith("```")) {
                json = json.replaceAll("^```(?:json)?\\s*", "").replaceAll("\\s*```$", "");
            }
            JsonNode node = objectMapper.readTree(json);

            result.setAiScore(node.get("score").asDouble());
            result.setAiPassed(node.get("passed").asBoolean());

            List<String> aiIssues = new ArrayList<>();
            JsonNode issuesNode = node.get("issues");
            if (issuesNode != null && issuesNode.isArray()) {
                for (JsonNode issue : issuesNode) {
                    aiIssues.add(issue.asText());
                }
            }
            result.setAiIssues(aiIssues);
        } catch (Exception e) {
            result.setAiScore(0.0);
            result.setAiPassed(false);
            result.setAiIssues(List.of("AI validation failed: " + e.getMessage()));
        }

        return result;
    }

    private Completeness determineCompleteness(SkillDocument doc) {
        SkillMeta meta = doc.getMeta();
        String body = doc.getBody() != null ? doc.getBody() : "";

        boolean hasTrigger = meta.getTrigger() != null && !meta.getTrigger().isBlank();
        boolean hasAliases = meta.getAliases() != null && !meta.getAliases().isEmpty();
        boolean hasSources = meta.getSources() != null && !meta.getSources().isEmpty();
        boolean hasRelated = (meta.getRelatedSkills() != null && !meta.getRelatedSkills().isEmpty())
            || (meta.getRelatedKnowledge() != null && !meta.getRelatedKnowledge().isEmpty());
        boolean hasLongBody = body.length() >= MIN_BODY_LENGTH_L3;
        boolean hasMediumBody = body.length() >= MIN_BODY_LENGTH_L2;

        // L3: all fields populated, long body, has sources and related
        if (hasTrigger && hasAliases && hasSources && hasRelated && hasLongBody) {
            return Completeness.L3;
        }

        // L2: trigger + aliases + medium body
        if (hasTrigger && hasAliases && hasMediumBody) {
            return Completeness.L2;
        }

        // L1: minimal required fields
        return Completeness.L1;
    }

    private String buildKnowledgeValidationPrompt(SkillDocument doc) {
        return """
            Validate this knowledge skill by asking 3 questions about its content
            and checking if the answers can be found in the skill body.

            Skill name: %s
            Skill body:
            ---
            %s
            ---

            Return ONLY a JSON object:
            {
                "score": <0.0-1.0>,
                "issues": ["<issue1>", ...],
                "passed": true/false
            }

            Score > 0.7 means passed.
            """.formatted(doc.getMeta().getName(), doc.getBody());
    }

    private String buildProcedureValidationPrompt(SkillDocument doc) {
        return """
            Validate this procedure skill by simulating the steps:
            1. Check for dead ends (steps with no next action)
            2. Check for termination (procedure has a clear end)
            3. Check for completeness (all steps are actionable)

            Skill name: %s
            Skill body:
            ---
            %s
            ---

            Return ONLY a JSON object:
            {
                "score": <0.0-1.0>,
                "issues": ["<issue1>", ...],
                "passed": true/false
            }

            Score > 0.7 means passed.
            """.formatted(doc.getMeta().getName(), doc.getBody());
    }

    @Data
    public static class ValidationResult {
        private boolean valid;
        private List<String> errors = new ArrayList<>();
        private Completeness autoCompleteness;

        // AI validation fields
        private double aiScore;
        private boolean aiPassed;
        private List<String> aiIssues = new ArrayList<>();
    }
}
