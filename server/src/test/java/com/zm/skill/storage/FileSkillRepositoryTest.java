package com.zm.skill.storage;

import com.zm.skill.domain.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class FileSkillRepositoryTest {

    @TempDir
    Path tempDir;

    @Test
    void shouldSaveAndLoadSkill() {
        var repo = new FileSkillRepository(tempDir);
        SkillMeta meta = SkillMeta.builder()
            .name("payment-clearing").type(SkillType.KNOWLEDGE)
            .domain("payment").summary("清算规则")
            .visibility(Visibility.parse("public")).build();
        repo.save(meta, "# 清算知识\n\n正文内容");

        assertThat(tempDir.resolve("skills/knowledge/payment.md")).exists();
        var loaded = repo.findByName("payment-clearing");
        assertThat(loaded).isPresent();
        assertThat(loaded.get().getBody()).contains("正文内容");
    }

    @Test
    void shouldSaveProcedureSkill() {
        var repo = new FileSkillRepository(tempDir);
        SkillMeta meta = SkillMeta.builder()
            .name("refund-flow").type(SkillType.PROCEDURE)
            .domain("payment").summary("退款流程")
            .visibility(Visibility.parse("team:payment")).build();
        repo.save(meta, "# 退款流程\n\n步骤1...");

        assertThat(tempDir.resolve("skills/procedure/refund-flow.md")).exists();
        var loaded = repo.findByName("refund-flow");
        assertThat(loaded).isPresent();
        assertThat(loaded.get().getMeta().getType()).isEqualTo(SkillType.PROCEDURE);
    }

    @Test
    void shouldSaveRawDocument() {
        var repo = new FileSkillRepository(tempDir);
        repo.saveRaw("payment", SkillType.KNOWLEDGE, "doc1.md", "raw content");
        assertThat(tempDir.resolve("raw/knowledge/payment/doc1.md")).exists();
    }

    @Test
    void shouldLoadIndex() {
        var repo = new FileSkillRepository(tempDir);
        repo.save(SkillMeta.builder().name("s1").type(SkillType.KNOWLEDGE)
            .domain("d1").summary("s1").visibility(Visibility.parse("public")).build(), "b1");
        repo.save(SkillMeta.builder().name("s2").type(SkillType.PROCEDURE)
            .domain("d2").summary("s2").visibility(Visibility.parse("team:t")).build(), "b2");

        List<SkillMeta> index = repo.loadIndex();
        assertThat(index).hasSize(2);
    }

    @Test
    void shouldSaveGlossary() {
        var repo = new FileSkillRepository(tempDir);
        repo.saveGlossary("payment", Map.of("清算", List.of("结算", "清分")));
        assertThat(tempDir.resolve("glossary/payment.yaml")).exists();
    }

    @Test
    void shouldParseYamlFrontMatterFormat() throws Exception {
        var repo = new FileSkillRepository(tempDir);
        SkillMeta meta = SkillMeta.builder()
            .name("test-skill").type(SkillType.KNOWLEDGE)
            .domain("test").summary("test summary")
            .visibility(Visibility.parse("public")).build();
        repo.save(meta, "# Test Body\n\nContent here.");

        // Read the raw file and verify format
        Path filePath = tempDir.resolve("skills/knowledge/test.md");
        String raw = Files.readString(filePath);
        assertThat(raw).contains("---");
        assertThat(raw).contains("name:");
        assertThat(raw).contains("# Test Body");
    }

    @Test
    void shouldReturnEmptyForNonExistentSkill() {
        var repo = new FileSkillRepository(tempDir);
        assertThat(repo.findByName("nonexistent")).isEmpty();
    }

    @Test
    void shouldOverwriteExistingSkill() {
        var repo = new FileSkillRepository(tempDir);
        SkillMeta meta = SkillMeta.builder()
            .name("updatable").type(SkillType.KNOWLEDGE)
            .domain("test").summary("v1")
            .visibility(Visibility.parse("public")).build();
        repo.save(meta, "Version 1 body");

        SkillMeta updated = SkillMeta.builder()
            .name("updatable").type(SkillType.KNOWLEDGE)
            .domain("test").summary("v2")
            .visibility(Visibility.parse("public")).build();
        repo.save(updated, "Version 2 body");

        var loaded = repo.findByName("updatable");
        assertThat(loaded).isPresent();
        assertThat(loaded.get().getBody()).contains("Version 2 body");
        assertThat(loaded.get().getMeta().getSummary()).isEqualTo("v2");
    }

    @Test
    void shouldLoadGlossary() {
        var repo = new FileSkillRepository(tempDir);
        Map<String, List<String>> glossary = Map.of(
            "清算", List.of("结算", "清分"),
            "退款", List.of("退单", "逆向交易")
        );
        repo.saveGlossary("payment", glossary);

        var loaded = repo.loadGlossary("payment");
        assertThat(loaded).isPresent();
        assertThat(loaded.get()).containsKey("清算");
        assertThat(loaded.get().get("清算")).contains("结算");
    }
}
