package com.zm.skill.storage;

import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.Status;
import org.eclipse.jgit.api.errors.GitAPIException;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Git auto-commit service using JGit.
 * Initializes a git repository if needed and provides commit operations
 * for automatic version control of skill files.
 */
public class GitService {

    private final Path repoPath;

    public GitService(Path repoPath) {
        this.repoPath = repoPath;
    }

    /**
     * Initialize a git repository at the configured path if one does not already exist.
     */
    public void init() {
        Path gitDir = repoPath.resolve(".git");
        if (Files.exists(gitDir)) {
            return;
        }
        try {
            Git.init().setDirectory(repoPath.toFile()).call().close();
        } catch (GitAPIException e) {
            throw new RuntimeException("Failed to initialize git repository at: " + repoPath, e);
        }
    }

    /**
     * Stage all changes and commit with the given message.
     *
     * @param message the commit message
     * @return true if a commit was made, false if there was nothing to commit
     */
    public boolean commitAll(String message) {
        try (Git git = Git.open(repoPath.toFile())) {
            // Stage all changes (new, modified, deleted)
            git.add().addFilepattern(".").call();
            git.add().addFilepattern(".").setUpdate(true).call();

            Status status = git.status().call();
            if (status.isClean()) {
                return false;
            }

            git.commit()
                .setMessage(message)
                .setAuthor("skill-collector", "skill-collector@zm.com")
                .call();
            return true;
        } catch (IOException | GitAPIException e) {
            throw new RuntimeException("Failed to commit changes: " + message, e);
        }
    }

    /**
     * Stage a specific file and commit with the given message.
     *
     * @param relativePath the file path relative to the repository root
     * @param message the commit message
     * @return true if a commit was made, false if there was nothing to commit
     */
    public boolean commitFile(String relativePath, String message) {
        try (Git git = Git.open(repoPath.toFile())) {
            git.add().addFilepattern(relativePath).call();

            Status status = git.status().call();
            if (status.getAdded().isEmpty() && status.getChanged().isEmpty()) {
                return false;
            }

            git.commit()
                .setMessage(message)
                .setAuthor("skill-collector", "skill-collector@zm.com")
                .call();
            return true;
        } catch (IOException | GitAPIException e) {
            throw new RuntimeException("Failed to commit file: " + relativePath, e);
        }
    }
}
