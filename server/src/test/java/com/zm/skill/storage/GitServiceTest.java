package com.zm.skill.storage;

import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.Status;
import org.eclipse.jgit.revwalk.RevCommit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class GitServiceTest {

    @TempDir
    Path tempDir;

    @Test
    void shouldInitRepoIfNotExists() {
        GitService gitService = new GitService(tempDir);
        gitService.init();

        assertThat(tempDir.resolve(".git")).exists();
    }

    @Test
    void shouldNotFailIfAlreadyInitialized() throws Exception {
        Git.init().setDirectory(tempDir.toFile()).call().close();

        GitService gitService = new GitService(tempDir);
        gitService.init();

        assertThat(tempDir.resolve(".git")).exists();
    }

    @Test
    void shouldCommitNewFile() throws Exception {
        GitService gitService = new GitService(tempDir);
        gitService.init();

        // Create a file
        Path testFile = tempDir.resolve("test.md");
        Files.writeString(testFile, "Hello World");

        gitService.commitAll("Add test file");

        // Verify the commit happened
        try (Git git = Git.open(tempDir.toFile())) {
            Iterable<RevCommit> log = git.log().call();
            RevCommit latest = log.iterator().next();
            assertThat(latest.getFullMessage()).isEqualTo("Add test file");

            Status status = git.status().call();
            assertThat(status.isClean()).isTrue();
        }
    }

    @Test
    void shouldCommitMultipleFiles() throws Exception {
        GitService gitService = new GitService(tempDir);
        gitService.init();

        Files.createDirectories(tempDir.resolve("skills/knowledge"));
        Files.writeString(tempDir.resolve("skills/knowledge/payment.md"), "content 1");
        Files.writeString(tempDir.resolve("skills/knowledge/risk.md"), "content 2");

        gitService.commitAll("Add multiple skills");

        try (Git git = Git.open(tempDir.toFile())) {
            Iterable<RevCommit> log = git.log().call();
            RevCommit latest = log.iterator().next();
            assertThat(latest.getFullMessage()).isEqualTo("Add multiple skills");

            Status status = git.status().call();
            assertThat(status.isClean()).isTrue();
        }
    }

    @Test
    void shouldHandleModifiedFiles() throws Exception {
        GitService gitService = new GitService(tempDir);
        gitService.init();

        Path testFile = tempDir.resolve("test.md");
        Files.writeString(testFile, "Version 1");
        gitService.commitAll("Initial commit");

        Files.writeString(testFile, "Version 2");
        gitService.commitAll("Update file");

        try (Git git = Git.open(tempDir.toFile())) {
            int commitCount = 0;
            for (RevCommit ignored : git.log().call()) {
                commitCount++;
            }
            assertThat(commitCount).isEqualTo(2);
        }
    }

    @Test
    void shouldSkipCommitWhenNothingToCommit() throws Exception {
        GitService gitService = new GitService(tempDir);
        gitService.init();

        Path testFile = tempDir.resolve("test.md");
        Files.writeString(testFile, "content");
        gitService.commitAll("First commit");

        // Commit again without changes - should not throw
        boolean committed = gitService.commitAll("No changes");
        assertThat(committed).isFalse();

        try (Git git = Git.open(tempDir.toFile())) {
            int commitCount = 0;
            for (RevCommit ignored : git.log().call()) {
                commitCount++;
            }
            assertThat(commitCount).isEqualTo(1);
        }
    }

    @Test
    void shouldCommitSpecificFile() throws Exception {
        GitService gitService = new GitService(tempDir);
        gitService.init();

        Files.writeString(tempDir.resolve("file1.md"), "content 1");
        Files.writeString(tempDir.resolve("file2.md"), "content 2");

        gitService.commitFile("file1.md", "Add file1 only");

        try (Git git = Git.open(tempDir.toFile())) {
            Status status = git.status().call();
            // file2.md should still be untracked
            assertThat(status.getUntracked()).contains("file2.md");
        }
    }
}
