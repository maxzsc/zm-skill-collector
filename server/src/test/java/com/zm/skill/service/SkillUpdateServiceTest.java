package com.zm.skill.service;

import com.zm.skill.domain.SkillMeta;
import com.zm.skill.domain.SkillType;
import com.zm.skill.domain.Visibility;
import com.zm.skill.storage.FileSkillRepository;
import com.zm.skill.storage.SkillDocument;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SkillUpdateServiceTest {

    @TempDir
    Path tempDir;

    @Mock
    private SkillGenerationService generationService;

    private FileSkillRepository repository;
    private SkillUpdateService skillUpdateService;

    @BeforeEach
    void setUp() {
        repository = new FileSkillRepository(tempDir);
        skillUpdateService = new SkillUpdateService(repository, generationService);
    }

    @Test
    void shouldAggregateKnowledgeFromAllRawDocs() {
        // Save existing knowledge skill
        SkillMeta existing = SkillMeta.builder()
            .name("payment-knowledge")
            .type(SkillType.KNOWLEDGE)
            .domain("payment")
            .visibility(Visibility.parse("public"))
            .build();
        repository.save(existing, "# Old knowledge");

        // Save two raw docs for the domain
        repository.saveRaw("payment", SkillType.KNOWLEDGE, "doc1.md", "Payment clearing rules");
        repository.saveRaw("payment", SkillType.KNOWLEDGE, "doc2.md", "Payment settlement process");

        // Mock the generation service to return a new aggregated skill
        SkillDocument regenerated = new SkillDocument(
            SkillMeta.builder()
                .name("payment-knowledge")
                .type(SkillType.KNOWLEDGE)
                .domain("payment")
                .visibility(Visibility.parse("public"))
                .build(),
            "# Aggregated Knowledge\n\nClearing + Settlement"
        );
        when(generationService.generateKnowledge(eq("payment"), anyList(), any(Visibility.class)))
            .thenReturn(regenerated);

        // Submit new doc to existing domain
        SkillDocument result = skillUpdateService.updateKnowledge(
            "payment", "doc3.md", "Payment reconciliation", Visibility.parse("public"));

        // Verify generation was called with ALL raw docs (including new one)
        verify(generationService).generateKnowledge(eq("payment"), argThat(docs -> docs.size() == 3), any());

        // Verify result
        assertThat(result.getBody()).contains("Aggregated Knowledge");
    }

    @Test
    void shouldOverwriteProcedureOnUpdate() {
        // Save existing procedure skill
        SkillMeta existing = SkillMeta.builder()
            .name("refund-flow")
            .type(SkillType.PROCEDURE)
            .domain("payment")
            .visibility(Visibility.parse("public"))
            .build();
        repository.save(existing, "# Old procedure");
        repository.saveRaw("payment", SkillType.PROCEDURE, "refund.md", "Old refund steps");

        // Mock generation
        SkillDocument regenerated = new SkillDocument(
            SkillMeta.builder()
                .name("refund-flow")
                .type(SkillType.PROCEDURE)
                .domain("payment")
                .visibility(Visibility.parse("public"))
                .build(),
            "# Updated Refund Flow\n\n1. New step"
        );
        when(generationService.generateProcedure(eq("payment"), anyString(), any(Visibility.class)))
            .thenReturn(regenerated);

        // Update the procedure
        SkillDocument result = skillUpdateService.updateProcedure(
            "payment", "refund.md", "New refund steps", Visibility.parse("public"));

        // Verify the raw doc was overwritten (saveRaw called with new content)
        // and generation used the new document text
        verify(generationService).generateProcedure(eq("payment"), eq("New refund steps"), any());
        assertThat(result.getBody()).contains("Updated Refund Flow");
    }

    @Test
    void shouldSaveRegeneratedKnowledgeSkill() {
        // Save raw docs
        repository.saveRaw("infra", SkillType.KNOWLEDGE, "doc1.md", "Server setup");

        SkillDocument regenerated = new SkillDocument(
            SkillMeta.builder()
                .name("infra-knowledge")
                .type(SkillType.KNOWLEDGE)
                .domain("infra")
                .visibility(Visibility.parse("public"))
                .build(),
            "# Infra Knowledge"
        );
        when(generationService.generateKnowledge(eq("infra"), anyList(), any(Visibility.class)))
            .thenReturn(regenerated);

        skillUpdateService.updateKnowledge("infra", "doc2.md", "Network config", Visibility.parse("public"));

        // Verify the skill was saved
        var saved = repository.findByName("infra-knowledge");
        assertThat(saved).isPresent();
        assertThat(saved.get().getBody()).isEqualTo("# Infra Knowledge");
    }

    @Test
    void shouldSaveRegeneratedProcedureSkill() {
        SkillDocument regenerated = new SkillDocument(
            SkillMeta.builder()
                .name("deploy-process")
                .type(SkillType.PROCEDURE)
                .domain("infra")
                .visibility(Visibility.parse("public"))
                .build(),
            "# Deploy Process"
        );
        when(generationService.generateProcedure(eq("infra"), anyString(), any(Visibility.class)))
            .thenReturn(regenerated);

        skillUpdateService.updateProcedure("infra", "deploy.md", "Deploy steps", Visibility.parse("public"));

        var saved = repository.findByName("deploy-process");
        assertThat(saved).isPresent();
        assertThat(saved.get().getBody()).isEqualTo("# Deploy Process");
    }

    @Test
    void shouldSetLastUpdatedOnRegeneration() {
        repository.saveRaw("payment", SkillType.KNOWLEDGE, "doc1.md", "Existing doc");

        SkillDocument regenerated = new SkillDocument(
            SkillMeta.builder()
                .name("payment-knowledge")
                .type(SkillType.KNOWLEDGE)
                .domain("payment")
                .visibility(Visibility.parse("public"))
                .build(),
            "# Payment"
        );
        when(generationService.generateKnowledge(eq("payment"), anyList(), any(Visibility.class)))
            .thenReturn(regenerated);

        skillUpdateService.updateKnowledge("payment", "doc2.md", "New doc", Visibility.parse("public"));

        var saved = repository.findByName("payment-knowledge").orElseThrow();
        assertThat(saved.getMeta().getLastUpdated()).isNotNull();
    }
}
