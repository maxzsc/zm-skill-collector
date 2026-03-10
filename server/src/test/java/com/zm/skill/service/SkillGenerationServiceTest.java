package com.zm.skill.service;

import com.zm.skill.ai.ClaudeClient;
import com.zm.skill.domain.SkillMeta;
import com.zm.skill.domain.SkillType;
import com.zm.skill.domain.Visibility;
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
class SkillGenerationServiceTest {

    @Mock
    private ClaudeClient claudeClient;

    private SkillGenerationService generationService;

    @BeforeEach
    void setUp() {
        SensitiveInfoFilter filter = new SensitiveInfoFilter();
        generationService = new SkillGenerationService(claudeClient, filter);
    }

    @Test
    void shouldGenerateKnowledgeSkillFromMultipleDocs() {
        String aiResponse = """
            {
                "name": "payment-clearing",
                "summary": "支付清算核心规则",
                "trigger": "用户提到清算、对账、结算相关问题时",
                "aliases": ["清算", "结算", "清分"],
                "body": "# 支付清算知识\\n\\n## 清算规则\\n\\n清算采用T+1模式..."
            }
            """;
        when(claudeClient.generate(anyString())).thenReturn(aiResponse);

        SkillDocument result = generationService.generateKnowledge(
            "payment",
            List.of("清算系统架构文档...", "结算规则说明..."),
            Visibility.parse("public")
        );

        assertThat(result.getMeta().getName()).isEqualTo("payment-clearing");
        assertThat(result.getMeta().getType()).isEqualTo(SkillType.KNOWLEDGE);
        assertThat(result.getMeta().getDomain()).isEqualTo("payment");
        assertThat(result.getMeta().getSummary()).isEqualTo("支付清算核心规则");
        assertThat(result.getMeta().getSummary().length()).isLessThanOrEqualTo(50);
        assertThat(result.getBody()).contains("清算规则");
    }

    @Test
    void shouldGenerateProcedureSkillFromSingleDoc() {
        String aiResponse = """
            {
                "name": "refund-flow",
                "summary": "退款处理流程",
                "trigger": "需要执行退款操作时",
                "aliases": ["退款", "退钱"],
                "body": "# 退款流程\\n\\n## 前置条件\\n- 有退款权限\\n\\n## 步骤\\n1. 打开退款工单\\n2. 验证金额"
            }
            """;
        when(claudeClient.generate(anyString())).thenReturn(aiResponse);

        SkillDocument result = generationService.generateProcedure(
            "payment",
            "退款处理步骤文档...",
            Visibility.parse("team:payment")
        );

        assertThat(result.getMeta().getName()).isEqualTo("refund-flow");
        assertThat(result.getMeta().getType()).isEqualTo(SkillType.PROCEDURE);
        assertThat(result.getMeta().getDomain()).isEqualTo("payment");
        assertThat(result.getBody()).contains("退款流程");
    }

    @Test
    void shouldEnforceSummaryLengthConstraint() {
        String aiResponse = """
            {
                "name": "test-skill",
                "summary": "这是一个非常非常非常非常非常非常非常非常非常非常非常非常非常非常长的摘要超过五十个字符",
                "trigger": "测试触发条件",
                "aliases": ["test"],
                "body": "# Test"
            }
            """;
        when(claudeClient.generate(anyString())).thenReturn(aiResponse);

        SkillDocument result = generationService.generateKnowledge(
            "test", List.of("test doc"), Visibility.parse("public")
        );

        assertThat(result.getMeta().getSummary().length()).isLessThanOrEqualTo(50);
    }

    @Test
    void shouldEnforceTriggerLengthConstraint() {
        String longTrigger = "a".repeat(150);
        String aiResponse = """
            {
                "name": "test-skill",
                "summary": "Test",
                "trigger": "%s",
                "aliases": ["test"],
                "body": "# Test"
            }
            """.formatted(longTrigger);
        when(claudeClient.generate(anyString())).thenReturn(aiResponse);

        SkillDocument result = generationService.generateKnowledge(
            "test", List.of("test doc"), Visibility.parse("public")
        );

        assertThat(result.getMeta().getTrigger().length()).isLessThanOrEqualTo(100);
    }

    @Test
    void shouldEnforceAliasesCountConstraint() {
        String aiResponse = """
            {
                "name": "test-skill",
                "summary": "Test",
                "trigger": "test trigger",
                "aliases": ["a1","a2","a3","a4","a5","a6","a7","a8","a9","a10","a11","a12"],
                "body": "# Test"
            }
            """;
        when(claudeClient.generate(anyString())).thenReturn(aiResponse);

        SkillDocument result = generationService.generateKnowledge(
            "test", List.of("test doc"), Visibility.parse("public")
        );

        assertThat(result.getMeta().getAliases().size()).isLessThanOrEqualTo(10);
    }

    @Test
    void shouldFilterSensitiveInfoFromOutput() {
        String aiResponse = """
            {
                "name": "infra-skill",
                "summary": "基础设施说明",
                "trigger": "询问基础设施时",
                "aliases": ["infra"],
                "body": "# 基础设施\\n\\n数据库连接 jdbc:mysql://10.0.1.5/db 密码 password=secret123"
            }
            """;
        when(claudeClient.generate(anyString())).thenReturn(aiResponse);

        SkillDocument result = generationService.generateKnowledge(
            "infra", List.of("infra doc"), Visibility.parse("public")
        );

        assertThat(result.getBody()).doesNotContain("jdbc:mysql://10.0.1.5/db");
        assertThat(result.getBody()).doesNotContain("password=secret123");
        assertThat(result.getBody()).contains("{FILTERED}");
    }
}
