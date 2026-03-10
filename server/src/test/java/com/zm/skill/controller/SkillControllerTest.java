package com.zm.skill.controller;

import com.zm.skill.domain.Completeness;
import com.zm.skill.domain.SkillMeta;
import com.zm.skill.domain.SkillType;
import com.zm.skill.domain.Visibility;
import com.zm.skill.storage.SkillDocument;
import com.zm.skill.storage.SkillRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.Optional;

import static org.hamcrest.Matchers.*;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(SkillController.class)
class SkillControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private SkillRepository skillRepository;

    @Test
    void listSkills_shouldReturnAllSkills() throws Exception {
        List<SkillMeta> skills = List.of(
                SkillMeta.builder()
                        .name("payment-clearing")
                        .type(SkillType.KNOWLEDGE)
                        .domain("payment")
                        .summary("Payment clearing rules")
                        .build(),
                SkillMeta.builder()
                        .name("refund-flow")
                        .type(SkillType.PROCEDURE)
                        .domain("payment")
                        .summary("Refund processing")
                        .build()
        );
        when(skillRepository.loadIndex()).thenReturn(skills);

        mockMvc.perform(get("/api/skills"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data", hasSize(2)))
                .andExpect(jsonPath("$.data[0].name").value("payment-clearing"))
                .andExpect(jsonPath("$.data[1].name").value("refund-flow"));
    }

    @Test
    void listSkills_filterByDomain_shouldFilterResults() throws Exception {
        List<SkillMeta> skills = List.of(
                SkillMeta.builder()
                        .name("payment-clearing")
                        .type(SkillType.KNOWLEDGE)
                        .domain("payment")
                        .build(),
                SkillMeta.builder()
                        .name("deploy-guide")
                        .type(SkillType.PROCEDURE)
                        .domain("ops")
                        .build()
        );
        when(skillRepository.loadIndex()).thenReturn(skills);

        mockMvc.perform(get("/api/skills").param("domain", "payment"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(1)))
                .andExpect(jsonPath("$.data[0].name").value("payment-clearing"));
    }

    @Test
    void listSkills_filterByType_shouldFilterResults() throws Exception {
        List<SkillMeta> skills = List.of(
                SkillMeta.builder()
                        .name("payment-clearing")
                        .type(SkillType.KNOWLEDGE)
                        .domain("payment")
                        .build(),
                SkillMeta.builder()
                        .name("refund-flow")
                        .type(SkillType.PROCEDURE)
                        .domain("payment")
                        .build()
        );
        when(skillRepository.loadIndex()).thenReturn(skills);

        mockMvc.perform(get("/api/skills").param("type", "procedure"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(1)))
                .andExpect(jsonPath("$.data[0].name").value("refund-flow"));
    }

    @Test
    void getSkill_existingSkill_shouldReturnMetaAndBody() throws Exception {
        SkillMeta meta = SkillMeta.builder()
                .name("payment-clearing")
                .type(SkillType.KNOWLEDGE)
                .domain("payment")
                .summary("Payment clearing rules")
                .build();
        SkillDocument doc = new SkillDocument(meta, "# Payment Clearing\n\nClearing rules T+1");
        when(skillRepository.findByName("payment-clearing")).thenReturn(Optional.of(doc));

        mockMvc.perform(get("/api/skills/payment-clearing"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.meta.name").value("payment-clearing"))
                .andExpect(jsonPath("$.data.body").value("# Payment Clearing\n\nClearing rules T+1"));
    }

    @Test
    void getSkill_unknownSkill_shouldReturn404() throws Exception {
        when(skillRepository.findByName("nonexistent")).thenReturn(Optional.empty());

        mockMvc.perform(get("/api/skills/nonexistent"))
                .andExpect(status().isNotFound());
    }

    @Test
    void getIndex_shouldReturnAllMeta() throws Exception {
        List<SkillMeta> index = List.of(
                SkillMeta.builder()
                        .name("payment-clearing")
                        .type(SkillType.KNOWLEDGE)
                        .domain("payment")
                        .summary("Payment clearing rules")
                        .build()
        );
        when(skillRepository.loadIndex()).thenReturn(index);

        mockMvc.perform(get("/api/skills/index"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data", hasSize(1)))
                .andExpect(jsonPath("$.data[0].name").value("payment-clearing"));
    }
}
