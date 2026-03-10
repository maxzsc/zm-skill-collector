package com.zm.skill.domain;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class SkillMetaTest {

    private final ObjectMapper yaml = new ObjectMapper(new YAMLFactory());

    @Test
    void shouldRoundTripKnowledgeMeta() throws Exception {
        SkillMeta meta = SkillMeta.builder()
            .name("payment-clearing").type(SkillType.KNOWLEDGE)
            .domain("payment").trigger("用户提到清算、对账")
            .aliases(List.of("清算", "结算", "清分"))
            .summary("支付清算核心规则").completeness(Completeness.L2)
            .visibility(Visibility.parse("public"))
            .sources(List.of("arch.md")).relatedSkills(List.of("risk-rules"))
            .build();

        String s = yaml.writeValueAsString(meta);
        SkillMeta d = yaml.readValue(s, SkillMeta.class);
        assertThat(d.getName()).isEqualTo("payment-clearing");
        assertThat(d.getType()).isEqualTo(SkillType.KNOWLEDGE);
        assertThat(d.getAliases()).containsExactly("清算", "结算", "清分");
    }

    @Test
    void shouldRoundTripProcedureMeta() throws Exception {
        SkillMeta meta = SkillMeta.builder()
            .name("refund-flow").type(SkillType.PROCEDURE)
            .category(ProcedureCategory.BIZ_OPERATION).domain("payment")
            .visibility(Visibility.parse("team:payment"))
            .agentReadiness(AgentReadiness.FUTURE).summary("退款流程")
            .relatedKnowledge(List.of("payment-clearing"))
            .build();

        String s = yaml.writeValueAsString(meta);
        SkillMeta d = yaml.readValue(s, SkillMeta.class);
        assertThat(d.getVisibility().isTeam()).isTrue();
        assertThat(d.getVisibility().getTeamName()).isEqualTo("payment");
    }

    @Test
    void shouldValidateLengthConstraints() {
        assertThat(SkillMeta.isValidSummary("短摘要")).isTrue();
        assertThat(SkillMeta.isValidSummary("x".repeat(51))).isFalse();
        assertThat(SkillMeta.isValidTrigger("x".repeat(100))).isTrue();
        assertThat(SkillMeta.isValidTrigger("x".repeat(101))).isFalse();
        assertThat(SkillMeta.isValidAliases(List.of("a", "b", "c"))).isTrue();
        assertThat(SkillMeta.isValidAliases(
            List.of("1", "2", "3", "4", "5", "6", "7", "8", "9", "10", "11")
        )).isFalse();
    }

    @Test
    void shouldSerializeEnumsWithJsonValue() throws Exception {
        SkillMeta meta = SkillMeta.builder()
            .name("test").type(SkillType.KNOWLEDGE)
            .domain("test").summary("test")
            .completeness(Completeness.L3)
            .visibility(Visibility.parse("public"))
            .build();

        String s = yaml.writeValueAsString(meta);
        assertThat(s).contains("knowledge");
        assertThat(s).contains("L3");
    }

    @Test
    void shouldSerializeVisibilityCorrectly() throws Exception {
        Visibility pub = Visibility.parse("public");
        Visibility team = Visibility.parse("team:risk");

        assertThat(pub.isPublic()).isTrue();
        assertThat(pub.isTeam()).isFalse();
        assertThat(team.isPublic()).isFalse();
        assertThat(team.isTeam()).isTrue();
        assertThat(team.getTeamName()).isEqualTo("risk");

        // Round-trip through YAML
        SkillMeta meta = SkillMeta.builder()
            .name("t").type(SkillType.KNOWLEDGE).domain("d").summary("s")
            .visibility(team).build();
        String s = yaml.writeValueAsString(meta);
        SkillMeta d = yaml.readValue(s, SkillMeta.class);
        assertThat(d.getVisibility().isTeam()).isTrue();
        assertThat(d.getVisibility().getTeamName()).isEqualTo("risk");
    }

    @Test
    void shouldHandleProcessingStatus() {
        assertThat(ProcessingStatus.SUBMITTED.canTransitionTo(ProcessingStatus.PARSING)).isTrue();
        assertThat(ProcessingStatus.SUBMITTED.canTransitionTo(ProcessingStatus.COMPLETED)).isFalse();
        assertThat(ProcessingStatus.GENERATING.canTransitionTo(ProcessingStatus.VALIDATING)).isTrue();
    }

    @Test
    void shouldCreateSubmission() {
        Submission sub = Submission.builder()
            .id("sub-1")
            .fileName("doc.md")
            .status(ProcessingStatus.SUBMITTED)
            .build();
        assertThat(sub.getId()).isEqualTo("sub-1");
        assertThat(sub.getStatus()).isEqualTo(ProcessingStatus.SUBMITTED);
    }

    @Test
    void shouldCreateDomainCluster() {
        DomainCluster cluster = DomainCluster.builder()
            .domain("payment")
            .confidence(0.9)
            .documents(List.of("doc1.md", "doc2.md"))
            .suggestedType(SkillType.KNOWLEDGE)
            .summaryPreview("支付相关文档")
            .build();
        assertThat(cluster.getDomain()).isEqualTo("payment");
        assertThat(cluster.getDocuments()).hasSize(2);
    }
}
