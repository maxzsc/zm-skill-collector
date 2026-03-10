package com.zm.skill.service;

import org.eclipse.jgit.api.Git;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class ReleaseServiceTest {

    @TempDir
    Path tempDir;

    private ReleaseService releaseService;

    @BeforeEach
    void setUp() throws Exception {
        // Initialize a git repo so resolveHeadHash works
        Git.init().setDirectory(tempDir.toFile()).call().close();
        // Create an initial commit so HEAD exists
        Files.writeString(tempDir.resolve("init.txt"), "init");
        try (Git git = Git.open(tempDir.toFile())) {
            git.add().addFilepattern(".").call();
            git.commit().setMessage("initial").setAuthor("test", "test@test.com").call();
        }

        releaseService = new ReleaseService(tempDir.toString());
    }

    @Test
    void shouldPublishAndRetrieveSkill() {
        releaseService.publish("payment-clearing");

        ReleaseService.ReleaseFile released = releaseService.getPublished();
        assertThat(released.getSchemaVersion()).isEqualTo(1);
        assertThat(released.getSkills()).containsKey("payment-clearing");

        ReleaseService.ReleaseEntry entry = released.getSkills().get("payment-clearing");
        assertThat(entry.getRevision()).isNotNull();
        assertThat(entry.getRevision()).isNotEqualTo("unknown");
        assertThat(entry.getPublishedAt()).isNotNull();
    }

    @Test
    void shouldRollbackSkill() {
        releaseService.publish("refund-flow");
        releaseService.rollback("refund-flow", "abc123");

        ReleaseService.ReleaseFile released = releaseService.getPublished();
        assertThat(released.getSkills().get("refund-flow").getRevision()).isEqualTo("abc123");
    }

    @Test
    void shouldReturnEmptyReleaseWhenNoPublish() {
        ReleaseService.ReleaseFile released = releaseService.getPublished();
        assertThat(released.getSchemaVersion()).isEqualTo(1);
        assertThat(released.getSkills()).isEmpty();
    }

    @Test
    void shouldWriteReleaseJsonFile() {
        releaseService.publish("test-skill");
        assertThat(Files.exists(tempDir.resolve("release.json"))).isTrue();
    }
}
