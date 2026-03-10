package com.zm.skill.service;

import com.zm.skill.ai.ClaudeClient;
import com.zm.skill.ai.AiModelConfig;
import com.zm.skill.domain.DomainCluster;
import com.zm.skill.domain.SkillType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ClusteringServiceTest {

    @Mock
    private ClaudeClient claudeClient;

    @Mock
    private AiModelConfig aiModelConfig;

    @Mock
    private AiModelConfig.Models models;

    private ClusteringService clusteringService;

    @BeforeEach
    void setUp() {
        when(aiModelConfig.getModels()).thenReturn(models);
        when(models.getSummarize()).thenReturn("claude-haiku-4-5-20251001");
        when(models.getCluster()).thenReturn("claude-sonnet-4-6");
        clusteringService = new ClusteringService(claudeClient, aiModelConfig);
    }

    @Test
    void shouldClusterDocumentsIntoDomains() {
        // Phase 1: summarize (haiku)
        when(claudeClient.call(eq("claude-haiku-4-5-20251001"), anyString(), anyString()))
            .thenReturn("Payment clearing document about settlement rules")
            .thenReturn("Risk management rules for fraud detection");

        // Phase 2: cluster (sonnet)
        String clusterResponse = """
            [
                {
                    "domain": "payment",
                    "confidence": 0.90,
                    "documents": ["doc1.md"],
                    "suggestedType": "knowledge",
                    "summaryPreview": "Payment clearing rules"
                },
                {
                    "domain": "risk",
                    "confidence": 0.85,
                    "documents": ["doc2.md"],
                    "suggestedType": "knowledge",
                    "summaryPreview": "Risk management rules"
                }
            ]
            """;
        when(claudeClient.call(eq("claude-sonnet-4-6"), anyString(), anyString()))
            .thenReturn(clusterResponse);

        Map<String, String> docSummaries = Map.of(
            "doc1.md", "Payment clearing architecture with settlement rules and reconciliation...",
            "doc2.md", "Risk management framework with fraud detection algorithms..."
        );

        List<DomainCluster> clusters = clusteringService.cluster(docSummaries, null);

        assertThat(clusters).hasSize(2);
        assertThat(clusters.get(0).getDomain()).isEqualTo("payment");
        assertThat(clusters.get(0).getConfidence()).isEqualTo(0.90);
        assertThat(clusters.get(1).getDomain()).isEqualTo("risk");
    }

    @Test
    void shouldUseSeedDomain() {
        when(claudeClient.call(eq("claude-haiku-4-5-20251001"), anyString(), anyString()))
            .thenReturn("Payment related content");

        String clusterResponse = """
            [
                {
                    "domain": "payment",
                    "confidence": 0.95,
                    "documents": ["doc1.md"],
                    "suggestedType": "knowledge",
                    "summaryPreview": "Payment clearing"
                }
            ]
            """;
        when(claudeClient.call(eq("claude-sonnet-4-6"), anyString(), anyString()))
            .thenReturn(clusterResponse);

        Map<String, String> docSummaries = Map.of(
            "doc1.md", "Content about payment processing and clearing..."
        );

        List<DomainCluster> clusters = clusteringService.cluster(docSummaries, "payment");

        assertThat(clusters).hasSize(1);
        assertThat(clusters.get(0).getDomain()).isEqualTo("payment");
        // Verify seed domain was passed in the clustering prompt
        verify(claudeClient).call(eq("claude-sonnet-4-6"), anyString(),
            org.mockito.ArgumentMatchers.contains("payment"));
    }

    @Test
    void shouldHandleSingleDocument() {
        when(claudeClient.call(eq("claude-haiku-4-5-20251001"), anyString(), anyString()))
            .thenReturn("Refund processing steps");

        String clusterResponse = """
            [
                {
                    "domain": "payment",
                    "confidence": 0.88,
                    "documents": ["refund.md"],
                    "suggestedType": "procedure",
                    "summaryPreview": "Refund flow"
                }
            ]
            """;
        when(claudeClient.call(eq("claude-sonnet-4-6"), anyString(), anyString()))
            .thenReturn(clusterResponse);

        Map<String, String> docSummaries = Map.of(
            "refund.md", "Step 1: Open refund ticket. Step 2: Verify amount..."
        );

        List<DomainCluster> clusters = clusteringService.cluster(docSummaries, null);

        assertThat(clusters).hasSize(1);
        assertThat(clusters.get(0).getSuggestedType()).isEqualTo(SkillType.PROCEDURE);
    }

    @Test
    void shouldTruncatePhase1InputTo500Chars() {
        when(claudeClient.call(eq("claude-haiku-4-5-20251001"), anyString(), anyString()))
            .thenReturn("Summary of long doc");

        String clusterResponse = """
            [
                {
                    "domain": "misc",
                    "confidence": 0.70,
                    "documents": ["long.md"],
                    "suggestedType": "knowledge",
                    "summaryPreview": "Long document"
                }
            ]
            """;
        when(claudeClient.call(eq("claude-sonnet-4-6"), anyString(), anyString()))
            .thenReturn(clusterResponse);

        String longText = "A".repeat(1000);
        Map<String, String> docSummaries = Map.of("long.md", longText);

        clusteringService.cluster(docSummaries, null);

        // Phase 1 should truncate to 500 chars
        verify(claudeClient).call(eq("claude-haiku-4-5-20251001"), anyString(),
            org.mockito.ArgumentMatchers.argThat(arg -> arg.length() <= 600)); // prompt + 500 char content
    }
}
