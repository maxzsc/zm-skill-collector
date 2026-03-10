package com.zm.skill.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zm.skill.ai.AiModelConfig;
import com.zm.skill.ai.ClaudeClient;
import com.zm.skill.storage.SkillDocument;
import lombok.Data;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Detects duplicate skills using a two-stage approach:
 * Stage 1: Keyword-based Jaccard similarity for fast screening.
 * Stage 2: LLM review for borderline cases (0.6-0.85 similarity).
 * Direct flag for high similarity (> 0.85).
 */
@Service
public class DeduplicationService {

    private static final double LOW_THRESHOLD = 0.6;
    private static final double HIGH_THRESHOLD = 0.85;

    private final ClaudeClient claudeClient;
    private final AiModelConfig aiModelConfig;
    private final ObjectMapper objectMapper;

    public DeduplicationService(ClaudeClient claudeClient, AiModelConfig aiModelConfig) {
        this.claudeClient = claudeClient;
        this.aiModelConfig = aiModelConfig;
        this.objectMapper = new ObjectMapper();
    }

    /**
     * Check if a new skill is a duplicate of any existing skills.
     * Uses two-stage dedup: Jaccard similarity + LLM review for borderline cases.
     *
     * @param newSkill      the new skill to check
     * @param existingSkills list of existing skills in the same domain
     * @return dedup result with similarity score and merge suggestion
     */
    public DedupResult checkDuplicate(SkillDocument newSkill, List<SkillDocument> existingSkills) {
        if (existingSkills == null || existingSkills.isEmpty()) {
            return DedupResult.noDuplicate();
        }

        String newBody = newSkill.getBody();
        double maxSimilarity = 0.0;
        String mostSimilarName = null;
        SkillDocument mostSimilarDoc = null;

        for (SkillDocument existing : existingSkills) {
            double similarity = jaccardSimilarity(newBody, existing.getBody());
            if (similarity > maxSimilarity) {
                maxSimilarity = similarity;
                mostSimilarName = existing.getMeta().getName();
                mostSimilarDoc = existing;
            }
        }

        DedupResult result = new DedupResult();
        result.setSimilarity(maxSimilarity);
        result.setMostSimilarSkill(mostSimilarName);

        // P1-23: Two-stage dedup
        if (maxSimilarity >= HIGH_THRESHOLD) {
            // Stage 1: High similarity -> directly flag as duplicate
            result.setDuplicate(true);
            result.setMergeSuggestion(
                "Skill '%s' is %.0f%% similar to existing skill '%s'. Highly likely duplicate - consider merging."
                    .formatted(newSkill.getMeta().getName(), maxSimilarity * 100, mostSimilarName)
            );
        } else if (maxSimilarity >= LOW_THRESHOLD && mostSimilarDoc != null) {
            // Stage 2: Borderline similarity -> LLM review
            boolean llmSaysSimilar = checkWithLlm(newSkill, mostSimilarDoc);
            result.setDuplicate(llmSaysSimilar);
            if (llmSaysSimilar) {
                result.setMergeSuggestion(
                    "Skill '%s' is %.0f%% similar to '%s' and LLM confirms topic overlap. Consider merging."
                        .formatted(newSkill.getMeta().getName(), maxSimilarity * 100, mostSimilarName)
                );
            }
        } else {
            // Below threshold -> not duplicate
            result.setDuplicate(false);
        }

        return result;
    }

    /**
     * Use LLM to determine if two borderline-similar skills are about the same topic.
     */
    private boolean checkWithLlm(SkillDocument newSkill, SkillDocument existingSkill) {
        try {
            String model = aiModelConfig.getModels().getDedup();
            String systemPrompt = "You are a deduplication assistant. Compare two skills and determine if they cover the same topic.";
            String userMessage = """
                Skill A name: %s
                Skill A summary: %s
                Skill A body (first 500 chars): %s

                Skill B name: %s
                Skill B summary: %s
                Skill B body (first 500 chars): %s

                Are these two skills about the same topic? Reply ONLY with JSON: {"similar": true/false, "reason": "..."}
                """.formatted(
                newSkill.getMeta().getName(),
                newSkill.getMeta().getSummary(),
                truncate(newSkill.getBody(), 500),
                existingSkill.getMeta().getName(),
                existingSkill.getMeta().getSummary(),
                truncate(existingSkill.getBody(), 500)
            );

            String response = claudeClient.call(model, systemPrompt, userMessage);
            String json = response.strip();
            if (json.startsWith("```")) {
                json = json.replaceAll("^```(?:json)?\\s*", "").replaceAll("\\s*```$", "");
            }
            JsonNode node = objectMapper.readTree(json);
            return node.has("similar") && node.get("similar").asBoolean();
        } catch (Exception e) {
            // If LLM call fails, fall back to not flagging as duplicate
            return false;
        }
    }

    private String truncate(String text, int maxLen) {
        if (text == null) return "";
        return text.length() <= maxLen ? text : text.substring(0, maxLen);
    }

    /**
     * Compute Jaccard similarity between two text strings based on word tokens.
     */
    public double jaccardSimilarity(String text1, String text2) {
        Set<String> tokens1 = tokenize(text1);
        Set<String> tokens2 = tokenize(text2);

        if (tokens1.isEmpty() && tokens2.isEmpty()) {
            return 1.0;
        }
        if (tokens1.isEmpty() || tokens2.isEmpty()) {
            return 0.0;
        }

        Set<String> intersection = new HashSet<>(tokens1);
        intersection.retainAll(tokens2);

        Set<String> union = new HashSet<>(tokens1);
        union.addAll(tokens2);

        return (double) intersection.size() / union.size();
    }

    private Set<String> tokenize(String text) {
        if (text == null || text.isBlank()) {
            return Set.of();
        }
        Set<String> tokens = new HashSet<>();

        // Split on whitespace/punctuation for word-level tokens
        String[] words = text.toLowerCase().split("[\\s\\p{Punct}]+");
        for (String word : words) {
            if (!word.isBlank()) {
                tokens.add(word);
            }
        }

        // Generate character bigrams for CJK text to handle unsegmented Chinese
        String cleaned = text.replaceAll("[\\s\\p{Punct}]", "");
        for (int i = 0; i < cleaned.length() - 1; i++) {
            tokens.add(cleaned.substring(i, i + 2));
        }

        return tokens;
    }

    @Data
    public static class DedupResult {
        private boolean duplicate;
        private double similarity;
        private String mostSimilarSkill;
        private String mergeSuggestion;

        public static DedupResult noDuplicate() {
            DedupResult result = new DedupResult();
            result.setDuplicate(false);
            result.setSimilarity(0.0);
            return result;
        }
    }
}
