package com.zm.skill.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zm.skill.ai.ClaudeClient;
import com.zm.skill.ai.AiModelConfig;
import com.zm.skill.domain.DomainCluster;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Two-phase domain clustering service.
 * Phase 1: Quick summarize with haiku (first 500 chars)
 * Phase 2: Full clustering with sonnet
 */
@Service
public class ClusteringService {

    private static final int PHASE1_MAX_CHARS = 500;

    private final ClaudeClient claudeClient;
    private final AiModelConfig aiModelConfig;
    private final ObjectMapper objectMapper;

    public ClusteringService(ClaudeClient claudeClient, AiModelConfig aiModelConfig) {
        this.claudeClient = claudeClient;
        this.aiModelConfig = aiModelConfig;
        this.objectMapper = new ObjectMapper();
    }

    /**
     * Cluster documents into domains.
     *
     * @param docSummaries map of filename -> document text
     * @param seedDomain   optional seed domain hint
     * @return list of domain clusters
     */
    public List<DomainCluster> cluster(Map<String, String> docSummaries, String seedDomain) {
        // Phase 1: Quick summarize each document (haiku, first 500 chars)
        Map<String, String> quickSummaries = phase1Summarize(docSummaries);

        // Phase 2: Cluster using full context (sonnet)
        return phase2Cluster(quickSummaries, seedDomain);
    }

    private Map<String, String> phase1Summarize(Map<String, String> docSummaries) {
        return docSummaries.entrySet().stream()
            .collect(Collectors.toMap(
                Map.Entry::getKey,
                entry -> {
                    String truncated = truncate(entry.getValue(), PHASE1_MAX_CHARS);
                    return claudeClient.call(
                        aiModelConfig.getModels().getSummarize(),
                        "Summarize this document excerpt in one sentence.",
                        truncated
                    );
                }
            ));
    }

    private List<DomainCluster> phase2Cluster(Map<String, String> summaries, String seedDomain) {
        StringBuilder prompt = new StringBuilder();
        prompt.append("Cluster the following documents into business domains. ");
        prompt.append("Return a JSON array of domain clusters.\n\n");

        if (seedDomain != null && !seedDomain.isBlank()) {
            prompt.append("Seed domain hint: ").append(seedDomain).append("\n\n");
        }

        prompt.append("Documents:\n");
        for (Map.Entry<String, String> entry : summaries.entrySet()) {
            prompt.append("- ").append(entry.getKey()).append(": ").append(entry.getValue()).append("\n");
        }

        prompt.append("""

            Return ONLY a JSON array where each element has:
            {
                "domain": "<domain name>",
                "confidence": <0.0-1.0>,
                "documents": ["<filename>", ...],
                "suggestedType": "knowledge" or "procedure",
                "summaryPreview": "<brief summary>"
            }
            """);

        String response = claudeClient.call(
            aiModelConfig.getModels().getCluster(),
            "You are a document clustering assistant. Group documents into coherent business domains.",
            prompt.toString()
        );

        try {
            String json = response.strip();
            if (json.startsWith("```")) {
                json = json.replaceAll("^```(?:json)?\\s*", "").replaceAll("\\s*```$", "");
            }
            return objectMapper.readValue(json, new TypeReference<List<DomainCluster>>() {});
        } catch (Exception e) {
            throw new RuntimeException("Failed to parse clustering response: " + response, e);
        }
    }

    private String truncate(String text, int maxLen) {
        if (text == null) return "";
        return text.length() <= maxLen ? text : text.substring(0, maxLen);
    }
}
