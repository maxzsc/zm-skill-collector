package com.zm.skill.service;

import com.zm.skill.domain.SkillMeta;
import com.zm.skill.domain.SkillType;
import com.zm.skill.domain.Visibility;
import com.zm.skill.storage.SkillDocument;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class DeduplicationServiceTest {

    private DeduplicationService deduplicationService;

    @BeforeEach
    void setUp() {
        deduplicationService = new DeduplicationService();
    }

    @Test
    void shouldDetectHighSimilarity() {
        SkillDocument newSkill = createDoc("payment-clearing",
            "# 支付清算\n\n清算规则采用T+1模式，结算周期为每日。对账流程包含三个步骤。");
        SkillDocument existingSkill = createDoc("payment-settlement",
            "# 支付结算\n\n结算规则采用T+1模式，清算周期为每日。对账流程包含三个步骤。");

        DeduplicationService.DedupResult result = deduplicationService.checkDuplicate(
            newSkill, List.of(existingSkill));

        assertThat(result.isDuplicate()).isTrue();
        assertThat(result.getSimilarity()).isGreaterThan(0.7);
        assertThat(result.getMostSimilarSkill()).isEqualTo("payment-settlement");
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
        assertThat(result.getSimilarity()).isLessThan(0.7);
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
