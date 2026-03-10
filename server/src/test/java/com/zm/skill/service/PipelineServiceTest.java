package com.zm.skill.service;

import com.zm.skill.ai.ClaudeClient;
import com.zm.skill.domain.*;
import com.zm.skill.parser.ParserFactory;
import com.zm.skill.storage.FileSkillRepository;
import com.zm.skill.storage.GitService;
import com.zm.skill.storage.SkillDocument;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PipelineServiceTest {

    @TempDir
    Path tempDir;

    @Mock
    private ClaudeClient claudeClient;

    @Mock
    private GitService gitService;

    private PipelineService pipelineService;

    @BeforeEach
    void setUp() {
        SensitiveInfoFilter sensitiveFilter = new SensitiveInfoFilter();
        ClassificationService classificationService = new ClassificationService(claudeClient);
        // Create a mock AiModelConfig for ClusteringService
        com.zm.skill.ai.AiModelConfig aiModelConfig = new com.zm.skill.ai.AiModelConfig();
        ClusteringService clusteringService = new ClusteringService(claudeClient, aiModelConfig);
        SkillGenerationService generationService = new SkillGenerationService(claudeClient, sensitiveFilter);
        ValidationService validationService = new ValidationService(claudeClient);
        DeduplicationService deduplicationService = new DeduplicationService(claudeClient, aiModelConfig);
        FileSkillRepository repository = new FileSkillRepository(tempDir);
        ParserFactory parserFactory = new ParserFactory();
        SkillUpdateService skillUpdateService = new SkillUpdateService(repository, generationService);

        pipelineService = new PipelineService(
            classificationService, clusteringService, generationService,
            validationService, deduplicationService, repository,
            gitService, parserFactory, skillUpdateService
        );
    }

    @Test
    void shouldSubmitAndScanReturnDomainMap() {
        // Classification response
        String classificationResponse = """
            {
                "type": "knowledge",
                "domain": "payment",
                "category": null,
                "doc_type": "architecture",
                "confidence": 0.90,
                "summary_preview": "Payment clearing"
            }
            """;
        when(claudeClient.summarize(anyString())).thenReturn(classificationResponse);

        // Clustering response
        String clusterResponse = """
            [
                {
                    "domain": "payment",
                    "confidence": 0.90,
                    "documents": ["doc1.md"],
                    "suggestedType": "knowledge",
                    "summaryPreview": "Payment clearing"
                }
            ]
            """;
        when(claudeClient.call(anyString(), anyString(), anyString())).thenReturn(clusterResponse);

        Submission submission = Submission.builder()
            .id("sub-1")
            .status(ProcessingStatus.SUBMITTED)
            .build();

        Map<String, String> documents = Map.of("doc1.md", "# Payment Clearing\n\nClearing rules...");

        List<DomainCluster> result = pipelineService.submitAndScan(submission, documents);

        assertThat(result).isNotEmpty();
        assertThat(submission.getStatus()).isEqualTo(ProcessingStatus.AWAITING_CONFIRMATION);
    }

    @Test
    void shouldTrackSubmissionStatus() {
        String classificationResponse = """
            {
                "type": "knowledge",
                "domain": "payment",
                "category": null,
                "doc_type": "architecture",
                "confidence": 0.90,
                "summary_preview": "Payment clearing"
            }
            """;
        when(claudeClient.summarize(anyString())).thenReturn(classificationResponse);

        String clusterResponse = """
            [
                {
                    "domain": "payment",
                    "confidence": 0.90,
                    "documents": ["doc1.md"],
                    "suggestedType": "knowledge",
                    "summaryPreview": "Payment clearing"
                }
            ]
            """;
        when(claudeClient.call(anyString(), anyString(), anyString())).thenReturn(clusterResponse);

        Submission submission = Submission.builder()
            .id("sub-track")
            .status(ProcessingStatus.SUBMITTED)
            .build();

        pipelineService.submitAndScan(submission, Map.of("doc1.md", "Content about payment..."));

        Submission tracked = pipelineService.getSubmission("sub-track");
        assertThat(tracked).isNotNull();
        assertThat(tracked.getStatus()).isEqualTo(ProcessingStatus.AWAITING_CONFIRMATION);
    }

    @Test
    void shouldConfirmAndGenerateSkills() {
        // Setup: first do submitAndScan
        String classificationResponse = """
            {
                "type": "knowledge",
                "domain": "payment",
                "category": null,
                "doc_type": "architecture",
                "confidence": 0.90,
                "summary_preview": "Payment clearing"
            }
            """;
        when(claudeClient.summarize(anyString())).thenReturn(classificationResponse);

        String clusterResponse = """
            [
                {
                    "domain": "payment",
                    "confidence": 0.90,
                    "documents": ["doc1.md"],
                    "suggestedType": "knowledge",
                    "summaryPreview": "Payment clearing"
                }
            ]
            """;
        when(claudeClient.call(anyString(), anyString(), anyString())).thenReturn(clusterResponse);

        Submission submission = Submission.builder()
            .id("sub-gen")
            .status(ProcessingStatus.SUBMITTED)
            .build();

        pipelineService.submitAndScan(submission, Map.of("doc1.md", "# Payment\n\nClearing rules T+1"));

        // Generate skill
        String generateResponse = """
            {
                "name": "payment-clearing",
                "summary": "Payment clearing rules",
                "trigger": "When user asks about clearing",
                "aliases": ["clearing"],
                "body": "# Payment Clearing\\n\\nClearing rules T+1"
            }
            """;
        when(claudeClient.generate(anyString())).thenReturn(generateResponse);

        List<DomainCluster> confirmedClusters = List.of(
            DomainCluster.builder()
                .domain("payment")
                .confidence(0.90)
                .documents(List.of("doc1.md"))
                .suggestedType(SkillType.KNOWLEDGE)
                .build()
        );

        List<PipelineResult> results = pipelineService.confirmAndGenerate("sub-gen", confirmedClusters).join();

        assertThat(results).hasSize(1);
        assertThat(results.get(0).isSuccess()).isTrue();
        assertThat(results.get(0).getSkillName()).isEqualTo("payment-clearing");
    }

    @Test
    void shouldHandleSingleDocumentQuickPath() {
        // Classification
        String classificationResponse = """
            {
                "type": "procedure",
                "domain": "payment",
                "category": "biz-operation",
                "doc_type": "runbook",
                "confidence": 0.88,
                "summary_preview": "Refund flow"
            }
            """;
        when(claudeClient.summarize(anyString())).thenReturn(classificationResponse);

        // Generation
        String generateResponse = """
            {
                "name": "refund-flow",
                "summary": "Refund processing steps",
                "trigger": "When executing refund",
                "aliases": ["refund"],
                "body": "# Refund Flow\\n\\n## Steps\\n1. Open ticket"
            }
            """;
        when(claudeClient.generate(anyString())).thenReturn(generateResponse);

        Submission submission = Submission.builder()
            .id("sub-single")
            .status(ProcessingStatus.SUBMITTED)
            .build();

        PipelineResult result = pipelineService.submitSingle(
            submission, "refund.md", "# Refund\n\n1. Open ticket\n2. Verify amount"
        );

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getSkillName()).isEqualTo("refund-flow");
    }

    @Test
    void shouldHandleGenerationError() {
        String classificationResponse = """
            {
                "type": "knowledge",
                "domain": "test",
                "category": null,
                "doc_type": "misc",
                "confidence": 0.80,
                "summary_preview": "Test doc"
            }
            """;
        when(claudeClient.summarize(anyString())).thenReturn(classificationResponse);
        when(claudeClient.generate(anyString())).thenThrow(new RuntimeException("AI service unavailable"));

        Submission submission = Submission.builder()
            .id("sub-err")
            .status(ProcessingStatus.SUBMITTED)
            .build();

        PipelineResult result = pipelineService.submitSingle(
            submission, "test.md", "# Test Content"
        );

        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getErrorMessage()).contains("AI service unavailable");
    }
}
