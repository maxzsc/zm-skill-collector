package com.zm.skill.service;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zm.skill.ai.ClaudeClient;
import com.zm.skill.ai.prompts.ClassificationPrompt;
import com.zm.skill.domain.SkillType;
import lombok.Data;
import org.springframework.stereotype.Service;

/**
 * Classifies documents using AI to determine type, domain, category and confidence.
 */
@Service
public class ClassificationService {

    private final ClaudeClient claudeClient;
    private final ObjectMapper objectMapper;

    public ClassificationService(ClaudeClient claudeClient) {
        this.claudeClient = claudeClient;
        this.objectMapper = new ObjectMapper();
    }

    /**
     * Classify a document's text content.
     *
     * @param documentText the full text of the document
     * @return classification result with type, domain, confidence, etc.
     */
    public ClassificationResult classify(String documentText) {
        String prompt = ClassificationPrompt.build(documentText);
        String aiResponse = claudeClient.summarize(prompt);

        try {
            // Strip any markdown code fence wrappers if present
            String json = aiResponse.strip();
            if (json.startsWith("```")) {
                json = json.replaceAll("^```(?:json)?\\s*", "").replaceAll("\\s*```$", "");
            }
            return objectMapper.readValue(json, ClassificationResult.class);
        } catch (Exception e) {
            throw new RuntimeException("Failed to parse classification response: " + aiResponse, e);
        }
    }

    @Data
    public static class ClassificationResult {
        private SkillType type;
        private String domain;
        private String category;

        @JsonProperty("doc_type")
        private String docType;

        private double confidence;

        @JsonProperty("summary_preview")
        private String summaryPreview;
    }
}
