package com.zm.skill.service;

import com.zm.skill.ai.ClaudeClient;
import com.zm.skill.domain.*;
import com.zm.skill.storage.SkillDocument;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ValidationServiceTest {

    @Mock
    private ClaudeClient claudeClient;

    private ValidationService validationService;

    @BeforeEach
    void setUp() {
        validationService = new ValidationService(claudeClient);
    }

    @Test
    void shouldPassL1ValidationWithMinimalFields() {
        SkillMeta meta = SkillMeta.builder()
            .name("test-skill")
            .type(SkillType.KNOWLEDGE)
            .domain("test")
            .summary("Test summary")
            .visibility(Visibility.parse("public"))
            .build();
        // Need at least 50 chars of substantive content for knowledge
        SkillDocument doc = new SkillDocument(meta, "# Content\n\nSome body text that is long enough to pass the fifty character minimum requirement for knowledge skills.");

        ValidationService.ValidationResult result = validationService.validate(doc);

        assertThat(result.isValid()).isTrue();
        assertThat(result.getAutoCompleteness()).isEqualTo(Completeness.L1);
    }

    @Test
    void shouldPassL2ValidationWithTriggerAndAliases() {
        SkillMeta meta = SkillMeta.builder()
            .name("payment-clearing")
            .type(SkillType.KNOWLEDGE)
            .domain("payment")
            .summary("\u6e05\u7b97\u6838\u5fc3\u89c4\u5219")
            .trigger("\u7528\u6237\u63d0\u5230\u6e05\u7b97\u3001\u5bf9\u8d26\u65f6")
            .aliases(List.of("\u6e05\u7b97", "\u7ed3\u7b97"))
            .visibility(Visibility.parse("public"))
            .sources(List.of("arch.md"))
            .build();
        String body = "# \u6e05\u7b97\u77e5\u8bc6\n\n" + "\u8be6\u7ec6\u5185\u5bb9\u5305\u542b\u6e05\u7b97\u89c4\u5219\u8bf4\u660e\u3002".repeat(20);
        SkillDocument doc = new SkillDocument(meta, body);

        ValidationService.ValidationResult result = validationService.validate(doc);

        assertThat(result.isValid()).isTrue();
        assertThat(result.getAutoCompleteness()).isEqualTo(Completeness.L2);
    }

    @Test
    void shouldPassL3ValidationWithAllFields() {
        SkillMeta meta = SkillMeta.builder()
            .name("payment-clearing")
            .type(SkillType.KNOWLEDGE)
            .domain("payment")
            .summary("\u6e05\u7b97\u6838\u5fc3\u89c4\u5219")
            .trigger("\u7528\u6237\u63d0\u5230\u6e05\u7b97\u3001\u5bf9\u8d26\u65f6")
            .aliases(List.of("\u6e05\u7b97", "\u7ed3\u7b97", "\u6e05\u5206"))
            .completeness(Completeness.L3)
            .visibility(Visibility.parse("public"))
            .sources(List.of("arch.md", "settlement.md"))
            .relatedSkills(List.of("risk-rules"))
            .build();
        String longBody = "# \u6e05\u7b97\u77e5\u8bc6\n\n" + "\u8be6\u7ec6\u5185\u5bb9\u3002".repeat(100);
        SkillDocument doc = new SkillDocument(meta, longBody);

        ValidationService.ValidationResult result = validationService.validate(doc);

        assertThat(result.isValid()).isTrue();
        assertThat(result.getAutoCompleteness()).isEqualTo(Completeness.L3);
    }

    @Test
    void shouldFailWhenMissingRequiredFields() {
        SkillMeta meta = SkillMeta.builder()
            .name("test-skill")
            .type(SkillType.KNOWLEDGE)
            // missing domain
            // missing summary
            .build();
        SkillDocument doc = new SkillDocument(meta, "body with enough content to pass the fifty character substantive content check for knowledge");

        ValidationService.ValidationResult result = validationService.validate(doc);

        assertThat(result.isValid()).isFalse();
        assertThat(result.getErrors()).isNotEmpty();
    }

    @Test
    void shouldFailWhenSummaryTooLong() {
        SkillMeta meta = SkillMeta.builder()
            .name("test-skill")
            .type(SkillType.KNOWLEDGE)
            .domain("test")
            .summary("x".repeat(51))
            .visibility(Visibility.parse("public"))
            .build();
        SkillDocument doc = new SkillDocument(meta, "body with enough content to pass the fifty character substantive content check for knowledge");

        ValidationService.ValidationResult result = validationService.validate(doc);

        assertThat(result.isValid()).isFalse();
        assertThat(result.getErrors()).anyMatch(e -> e.contains("summary"));
    }

    @Test
    void shouldFailWhenTriggerTooLong() {
        SkillMeta meta = SkillMeta.builder()
            .name("test-skill")
            .type(SkillType.KNOWLEDGE)
            .domain("test")
            .summary("Valid summary")
            .trigger("x".repeat(101))
            .visibility(Visibility.parse("public"))
            .build();
        SkillDocument doc = new SkillDocument(meta, "body with enough content to pass the fifty character substantive content check for knowledge");

        ValidationService.ValidationResult result = validationService.validate(doc);

        assertThat(result.isValid()).isFalse();
        assertThat(result.getErrors()).anyMatch(e -> e.contains("trigger"));
    }

    @Test
    void shouldFailWhenTooManyAliases() {
        SkillMeta meta = SkillMeta.builder()
            .name("test-skill")
            .type(SkillType.KNOWLEDGE)
            .domain("test")
            .summary("Valid summary")
            .aliases(List.of("1","2","3","4","5","6","7","8","9","10","11"))
            .visibility(Visibility.parse("public"))
            .build();
        SkillDocument doc = new SkillDocument(meta, "body with enough content to pass the fifty character substantive content check for knowledge");

        ValidationService.ValidationResult result = validationService.validate(doc);

        assertThat(result.isValid()).isFalse();
        assertThat(result.getErrors()).anyMatch(e -> e.contains("aliases"));
    }

    @Test
    void shouldFailWhenBodyEmpty() {
        SkillMeta meta = SkillMeta.builder()
            .name("test-skill")
            .type(SkillType.KNOWLEDGE)
            .domain("test")
            .summary("Valid summary")
            .visibility(Visibility.parse("public"))
            .build();
        SkillDocument doc = new SkillDocument(meta, "");

        ValidationService.ValidationResult result = validationService.validate(doc);

        assertThat(result.isValid()).isFalse();
        assertThat(result.getErrors()).anyMatch(e -> e.contains("body"));
    }

    @Test
    void shouldPerformAiKnowledgeValidation() {
        SkillMeta meta = SkillMeta.builder()
            .name("payment-clearing")
            .type(SkillType.KNOWLEDGE)
            .domain("payment")
            .summary("\u6e05\u7b97\u6838\u5fc3\u89c4\u5219")
            .trigger("\u7528\u6237\u63d0\u5230\u6e05\u7b97\u65f6")
            .aliases(List.of("\u6e05\u7b97"))
            .visibility(Visibility.parse("public"))
            .build();
        SkillDocument doc = new SkillDocument(meta, "# \u6e05\u7b97\n\n\u6e05\u7b97\u91c7\u7528T+1\u6a21\u5f0f\uff0c\u8fd9\u662f\u4e00\u4e2a\u975e\u5e38\u91cd\u8981\u7684\u89c4\u5219\uff0c\u9700\u8981\u8be6\u7ec6\u4e86\u89e3\u5176\u4e2d\u7684\u5404\u4e2a\u73af\u8282\u548c\u6d41\u7a0b");

        String aiResponse = """
            {
                "score": 0.85,
                "issues": [],
                "passed": true
            }
            """;
        when(claudeClient.validate(anyString())).thenReturn(aiResponse);

        ValidationService.ValidationResult result = validationService.validateWithAi(doc);

        assertThat(result.getAiScore()).isEqualTo(0.85);
        assertThat(result.isAiPassed()).isTrue();
    }

    @Test
    void shouldPerformAiProcedureValidation() {
        SkillMeta meta = SkillMeta.builder()
            .name("refund-flow")
            .type(SkillType.PROCEDURE)
            .domain("payment")
            .summary("\u9000\u6b3e\u6d41\u7a0b")
            .trigger("\u6267\u884c\u9000\u6b3e\u65f6")
            .visibility(Visibility.parse("public"))
            .build();
        SkillDocument doc = new SkillDocument(meta, "# \u9000\u6b3e\n\n## \u6b65\u9aa4\n1. \u5f00\u5de5\u5355\n2. \u9a8c\u8bc1\u91d1\u989d");

        String aiResponse = """
            {
                "score": 0.78,
                "issues": ["Missing rollback section"],
                "passed": true
            }
            """;
        when(claudeClient.validate(anyString())).thenReturn(aiResponse);

        ValidationService.ValidationResult result = validationService.validateWithAi(doc);

        assertThat(result.getAiScore()).isEqualTo(0.78);
        assertThat(result.isAiPassed()).isTrue();
        assertThat(result.getAiIssues()).contains("Missing rollback section");
    }

    // P0-14: Deterministic validation tests

    @Test
    void shouldFailKnowledgeWithInsufficientSubstantiveContent() {
        SkillMeta meta = SkillMeta.builder()
            .name("test-skill")
            .type(SkillType.KNOWLEDGE)
            .domain("test")
            .summary("Test summary")
            .visibility(Visibility.parse("public"))
            .build();
        // Body has headers/formatting but < 50 chars of actual content
        SkillDocument doc = new SkillDocument(meta, "# Title\n\n## Section\n\n- Short");

        ValidationService.ValidationResult result = validationService.validate(doc);

        assertThat(result.isValid()).isFalse();
        assertThat(result.getErrors()).anyMatch(e -> e.contains("substantive content"));
    }

    @Test
    void shouldFailProcedureWithoutSectionMarkers() {
        SkillMeta meta = SkillMeta.builder()
            .name("test-procedure")
            .type(SkillType.PROCEDURE)
            .domain("test")
            .summary("Test procedure")
            .visibility(Visibility.parse("public"))
            .build();
        // Body without any required section markers
        SkillDocument doc = new SkillDocument(meta, "# Some Procedure\n\nDo something then do something else and keep going until done");

        ValidationService.ValidationResult result = validationService.validate(doc);

        assertThat(result.isValid()).isFalse();
        assertThat(result.getErrors()).anyMatch(e -> e.contains("section marker"));
    }

    @Test
    void shouldPassProcedureWithChineseSectionMarkers() {
        SkillMeta meta = SkillMeta.builder()
            .name("test-procedure")
            .type(SkillType.PROCEDURE)
            .domain("test")
            .summary("Test procedure")
            .visibility(Visibility.parse("public"))
            .build();
        SkillDocument doc = new SkillDocument(meta, "# \u64cd\u4f5c\u6d41\u7a0b\n\n## \u524d\u7f6e\u6761\u4ef6\n- \u6743\u9650\n\n## \u6b65\u9aa4\n1. \u6267\u884c");

        ValidationService.ValidationResult result = validationService.validate(doc);

        assertThat(result.isValid()).isTrue();
    }

    @Test
    void shouldPassProcedureWithEnglishSectionMarkers() {
        SkillMeta meta = SkillMeta.builder()
            .name("test-procedure")
            .type(SkillType.PROCEDURE)
            .domain("test")
            .summary("Test procedure")
            .visibility(Visibility.parse("public"))
            .build();
        SkillDocument doc = new SkillDocument(meta, "# Deploy Process\n\n## Precondition\n- Access\n\n## Steps\n1. Do it");

        ValidationService.ValidationResult result = validationService.validate(doc);

        assertThat(result.isValid()).isTrue();
    }
}
