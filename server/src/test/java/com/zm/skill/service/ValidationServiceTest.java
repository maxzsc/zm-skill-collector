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
        SkillDocument doc = new SkillDocument(meta, "# Content\n\nSome body text");

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
            .summary("清算核心规则")
            .trigger("用户提到清算、对账时")
            .aliases(List.of("清算", "结算"))
            .visibility(Visibility.parse("public"))
            .sources(List.of("arch.md"))
            .build();
        String body = "# 清算知识\n\n" + "详细内容包含清算规则说明。".repeat(20);
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
            .summary("清算核心规则")
            .trigger("用户提到清算、对账时")
            .aliases(List.of("清算", "结算", "清分"))
            .completeness(Completeness.L3)
            .visibility(Visibility.parse("public"))
            .sources(List.of("arch.md", "settlement.md"))
            .relatedSkills(List.of("risk-rules"))
            .build();
        String longBody = "# 清算知识\n\n" + "详细内容。".repeat(100);
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
        SkillDocument doc = new SkillDocument(meta, "body");

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
        SkillDocument doc = new SkillDocument(meta, "body");

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
        SkillDocument doc = new SkillDocument(meta, "body");

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
        SkillDocument doc = new SkillDocument(meta, "body");

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
            .summary("清算核心规则")
            .trigger("用户提到清算时")
            .aliases(List.of("清算"))
            .visibility(Visibility.parse("public"))
            .build();
        SkillDocument doc = new SkillDocument(meta, "# 清算\n\n清算采用T+1模式");

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
            .summary("退款流程")
            .trigger("执行退款时")
            .visibility(Visibility.parse("public"))
            .build();
        SkillDocument doc = new SkillDocument(meta, "# 退款\n\n## 步骤\n1. 开工单\n2. 验证金额");

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
}
