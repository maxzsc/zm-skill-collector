package com.zm.skill.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zm.skill.ai.ClaudeClient;
import com.zm.skill.ai.prompts.KnowledgePrompt;
import com.zm.skill.ai.prompts.ProcedurePrompt;
import com.zm.skill.domain.SkillMeta;
import com.zm.skill.domain.SkillType;
import com.zm.skill.domain.Visibility;
import com.zm.skill.storage.SkillDocument;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * Generates structured skills from documents using AI.
 * Knowledge: aggregates multiple docs into one skill.
 * Procedure: converts single doc into one skill.
 * Enforces length constraints and applies sensitive info filtering.
 */
@Service
public class SkillGenerationService {

    private static final int MAX_SUMMARY_LENGTH = 50;
    private static final int MAX_TRIGGER_LENGTH = 100;
    private static final int MAX_ALIASES_COUNT = 10;

    private final ClaudeClient claudeClient;
    private final SensitiveInfoFilter sensitiveInfoFilter;
    private final ObjectMapper objectMapper;

    public SkillGenerationService(ClaudeClient claudeClient, SensitiveInfoFilter sensitiveInfoFilter) {
        this.claudeClient = claudeClient;
        this.sensitiveInfoFilter = sensitiveInfoFilter;
        this.objectMapper = new ObjectMapper();
    }

    /**
     * Generate a knowledge skill by aggregating multiple documents.
     */
    public SkillDocument generateKnowledge(String domain, List<String> documentTexts, Visibility visibility) {
        String prompt = KnowledgePrompt.build(domain, documentTexts);
        String aiResponse = claudeClient.generate(prompt);
        return parseAndBuildSkill(aiResponse, domain, SkillType.KNOWLEDGE, visibility);
    }

    /**
     * Generate a procedure skill from a single document.
     */
    public SkillDocument generateProcedure(String domain, String documentText, Visibility visibility) {
        String prompt = ProcedurePrompt.build(domain, documentText);
        String aiResponse = claudeClient.generate(prompt);
        return parseAndBuildSkill(aiResponse, domain, SkillType.PROCEDURE, visibility);
    }

    private SkillDocument parseAndBuildSkill(String aiResponse, String domain, SkillType type, Visibility visibility) {
        try {
            String json = aiResponse.strip();
            if (json.startsWith("```")) {
                json = json.replaceAll("^```(?:json)?\\s*", "").replaceAll("\\s*```$", "");
            }

            JsonNode node = objectMapper.readTree(json);

            String name = node.get("name").asText();
            String summary = truncate(node.get("summary").asText(), MAX_SUMMARY_LENGTH);
            String trigger = truncate(node.get("trigger").asText(), MAX_TRIGGER_LENGTH);
            List<String> aliases = parseAliases(node.get("aliases"));
            String body = node.get("body").asText();

            // Apply sensitive info filter
            body = sensitiveInfoFilter.filter(body);
            summary = sensitiveInfoFilter.filter(summary);

            SkillMeta meta = SkillMeta.builder()
                .name(name)
                .type(type)
                .domain(domain)
                .summary(summary)
                .trigger(trigger)
                .aliases(aliases)
                .visibility(visibility)
                .build();

            return new SkillDocument(meta, body);
        } catch (Exception e) {
            throw new RuntimeException("Failed to parse skill generation response: " + aiResponse, e);
        }
    }

    private List<String> parseAliases(JsonNode aliasesNode) {
        List<String> aliases = new ArrayList<>();
        if (aliasesNode != null && aliasesNode.isArray()) {
            for (JsonNode alias : aliasesNode) {
                if (aliases.size() >= MAX_ALIASES_COUNT) {
                    break;
                }
                aliases.add(alias.asText());
            }
        }
        return aliases;
    }

    private String truncate(String text, int maxLen) {
        if (text == null) return "";
        return text.length() <= maxLen ? text : text.substring(0, maxLen);
    }
}
