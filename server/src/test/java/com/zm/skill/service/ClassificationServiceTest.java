package com.zm.skill.service;

import com.zm.skill.ai.ClaudeClient;
import com.zm.skill.domain.SkillType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ClassificationServiceTest {

    @Mock
    private ClaudeClient claudeClient;

    private ClassificationService classificationService;

    @BeforeEach
    void setUp() {
        classificationService = new ClassificationService(claudeClient);
    }

    @Test
    void shouldClassifyKnowledgeDocument() {
        String aiResponse = """
            {
                "type": "knowledge",
                "domain": "payment",
                "category": null,
                "doc_type": "architecture",
                "confidence": 0.92,
                "summary_preview": "Payment clearing architecture overview"
            }
            """;
        when(claudeClient.summarize(anyString())).thenReturn(aiResponse);

        ClassificationService.ClassificationResult result =
            classificationService.classify("# Payment Clearing\n\nThe clearing system processes...");

        assertThat(result.getType()).isEqualTo(SkillType.KNOWLEDGE);
        assertThat(result.getDomain()).isEqualTo("payment");
        assertThat(result.getConfidence()).isEqualTo(0.92);
        assertThat(result.getSummaryPreview()).isEqualTo("Payment clearing architecture overview");
        verify(claudeClient).summarize(anyString());
    }

    @Test
    void shouldClassifyProcedureDocument() {
        String aiResponse = """
            {
                "type": "procedure",
                "domain": "payment",
                "category": "biz-operation",
                "doc_type": "runbook",
                "confidence": 0.88,
                "summary_preview": "Refund processing steps"
            }
            """;
        when(claudeClient.summarize(anyString())).thenReturn(aiResponse);

        ClassificationService.ClassificationResult result =
            classificationService.classify("# Refund Flow\n\n1. Open refund ticket\n2. Verify...");

        assertThat(result.getType()).isEqualTo(SkillType.PROCEDURE);
        assertThat(result.getDomain()).isEqualTo("payment");
        assertThat(result.getCategory()).isEqualTo("biz-operation");
        assertThat(result.getConfidence()).isEqualTo(0.88);
    }

    @Test
    void shouldHandleLowConfidence() {
        String aiResponse = """
            {
                "type": "knowledge",
                "domain": "unknown",
                "category": null,
                "doc_type": "misc",
                "confidence": 0.35,
                "summary_preview": "Unclear document"
            }
            """;
        when(claudeClient.summarize(anyString())).thenReturn(aiResponse);

        ClassificationService.ClassificationResult result =
            classificationService.classify("Some vague content about various things...");

        assertThat(result.getConfidence()).isLessThan(0.5);
        assertThat(result.getDomain()).isEqualTo("unknown");
    }

    @Test
    void shouldIncludeDocumentTextInPrompt() {
        String aiResponse = """
            {
                "type": "knowledge",
                "domain": "risk",
                "category": null,
                "doc_type": "guide",
                "confidence": 0.85,
                "summary_preview": "Risk rules"
            }
            """;
        when(claudeClient.summarize(anyString())).thenReturn(aiResponse);

        classificationService.classify("Risk management documentation");

        verify(claudeClient).summarize(org.mockito.ArgumentMatchers.contains("Risk management documentation"));
    }
}
