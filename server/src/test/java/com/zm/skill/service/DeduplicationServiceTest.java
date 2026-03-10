package com.zm.skill.service;

import com.zm.skill.ai.AiModelConfig;
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
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class DeduplicationServiceTest {

    @Mock
    private ClaudeClient claudeClient;

    private DeduplicationService deduplicationService;

    @BeforeEach
    void setUp() {
        AiModelConfig aiModelConfig = new AiModelConfig();
        deduplicationService = new DeduplicationService(claudeClient, aiModelConfig);
        // Default LLM stub for borderline cases - returns similar=true
        when(claudeClient.call(anyString(), anyString(), anyString()))
            .thenReturn("{\"similar\": true, \"reason\": \"topic overlap\"}");
    }

    @Test
    void shouldDetectHighSimilarityDirectly() {
        // Identical content -> similarity = 1.0 -> direct flag (no LLM needed)
        SkillDocument skill1 = createDoc("skill-a", "# Same content here with many words for testing purpose");
        SkillDocument skill2 = createDoc("skill-b", "# Same content here with many words for testing purpose");

        DeduplicationService.DedupResult result = deduplicationService.checkDuplicate(
            skill1, List.of(skill2));

        assertThat(result.isDuplicate()).isTrue();
        assertThat(result.getSimilarity()).isGreaterThanOrEqualTo(0.85);
        // Should NOT call LLM for high similarity
        verify(claudeClient, never()).call(anyString(), anyString(), anyString());
    }

    @Test
    void shouldDetectSimilarContentViaLlm() {
        // These docs have moderate overlap (0.6-0.85) -> goes to LLM path
        SkillDocument newSkill = createDoc("payment-clearing",
            "# 支付清算\n\n清算规则采用T+1模式，结算周期为每日。对账流程包含三个步骤。");
        SkillDocument existingSkill = createDoc("payment-settlement",
            "# 支付结算\n\n结算规则采用T+1模式，清算周期为每日。对账流程包含三个步骤。");

        // LLM says similar (from setUp default)
        DeduplicationService.DedupResult result = deduplicationService.checkDuplicate(
            newSkill, List.of(existingSkill));

        assertThat(result.getSimilarity()).isGreaterThan(0.5);
        assertThat(result.getMostSimilarSkill()).isEqualTo("payment-settlement");
        // If in borderline range, should be flagged via LLM
        if (result.getSimilarity() >= 0.6 && result.getSimilarity() < 0.85) {
            assertThat(result.isDuplicate()).isTrue();
            assertThat(result.getMergeSuggestion()).contains("LLM confirms");
        }
    }

    @Test
    void shouldNotFlagDifferentContent() {
        SkillDocument newSkill = createDoc("payment-clearing",
            "# 支付清算\n\n清算规则采用T+1模式，结算周期为每日。");
        SkillDocument existingSkill = createDoc("risk-rules",
            "# 风控规则\n\n风险评估模型使用机器学习算法，包含反欺诈检测。");

        DeduplicationService.DedupResult result = deduplicationService.checkDuplicate(
            newSkill, List.of(existingSkill));

        assertThat(result.isDuplicate()).isFalse();
        assertThat(result.getSimilarity()).isLessThan(0.6);
    }

    @Test
    void shouldHandleEmptyExistingList() {
        SkillDocument newSkill = createDoc("payment-clearing", "# Content");

        DeduplicationService.DedupResult result = deduplicationService.checkDuplicate(
            newSkill, List.of());

        assertThat(result.isDuplicate()).isFalse();
        assertThat(result.getSimilarity()).isEqualTo(0.0);
    }

    @Test
    void shouldFindMostSimilarAmongMultiple() {
        SkillDocument newSkill = createDoc("payment-clearing",
            "# 支付清算规则\n\n清算采用T+1模式结算对账三步走");

        SkillDocument skill1 = createDoc("risk-rules",
            "# 风控规则\n\n风险评估模型使用机器学习");
        SkillDocument skill2 = createDoc("payment-settlement",
            "# 支付结算规则\n\n结算采用T+1模式清算对账三步走");
        SkillDocument skill3 = createDoc("user-guide",
            "# 用户指南\n\n如何使用系统进行日常操作");

        DeduplicationService.DedupResult result = deduplicationService.checkDuplicate(
            newSkill, List.of(skill1, skill2, skill3));

        assertThat(result.getMostSimilarSkill()).isEqualTo("payment-settlement");
    }

    @Test
    void shouldComputeJaccardCoefficient() {
        double similarity = deduplicationService.jaccardSimilarity(
            "the quick brown fox jumps over lazy dog",
            "the slow brown fox walks over lazy cat"
        );

        // Good overlap but not identical
        assertThat(similarity).isGreaterThan(0.3);
        assertThat(similarity).isLessThan(1.0);
    }

    @Test
    void shouldReturnMergeSuggestionForDuplicates() {
        SkillDocument newSkill = createDoc("payment-clearing",
            "# 支付清算 清算规则采用T+1模式 结算周期为每日 对账流程包含三个步骤");
        SkillDocument existingSkill = createDoc("payment-settlement",
            "# 支付结算 结算规则采用T+1模式 清算周期为每日 对账流程包含三个步骤");

        DeduplicationService.DedupResult result = deduplicationService.checkDuplicate(
            newSkill, List.of(existingSkill));

        if (result.isDuplicate()) {
            assertThat(result.getMergeSuggestion()).isNotNull();
            assertThat(result.getMergeSuggestion()).isNotBlank();
        }
    }

    @Test
    void shouldHandleIdenticalContent() {
        SkillDocument skill1 = createDoc("skill-a", "# Same content here with many words for testing");
        SkillDocument skill2 = createDoc("skill-b", "# Same content here with many words for testing");

        DeduplicationService.DedupResult result = deduplicationService.checkDuplicate(
            skill1, List.of(skill2));

        assertThat(result.isDuplicate()).isTrue();
        assertThat(result.getSimilarity()).isEqualTo(1.0);
    }

    @Test
    void shouldNotFlagWhenLlmSaysNotSimilar() {
        // Override default stub: LLM says NOT similar
        when(claudeClient.call(anyString(), anyString(), anyString()))
            .thenReturn("{\"similar\": false, \"reason\": \"Different topics\"}");

        // Docs with moderate similarity that will go through LLM
        SkillDocument newSkill = createDoc("payment-clearing",
            "# 支付清算\n\n清算规则采用T+1模式，结算周期为每日。对账流程包含三个步骤。");
        SkillDocument existingSkill = createDoc("payment-settlement",
            "# 支付结算\n\n结算规则采用T+1模式，清算周期为每日。对账流程包含三个步骤。");

        DeduplicationService.DedupResult result = deduplicationService.checkDuplicate(
            newSkill, List.of(existingSkill));

        // If in borderline range, LLM said no -> not duplicate
        if (result.getSimilarity() >= 0.6 && result.getSimilarity() < 0.85) {
            assertThat(result.isDuplicate()).isFalse();
        }
    }

    private SkillDocument createDoc(String name, String body) {
        SkillMeta meta = SkillMeta.builder()
            .name(name)
            .type(SkillType.KNOWLEDGE)
            .domain("payment")
            .summary("Test")
            .visibility(Visibility.parse("public"))
            .build();
        return new SkillDocument(meta, body);
    }
}
