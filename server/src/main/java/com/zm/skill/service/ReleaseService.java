package com.zm.skill.service;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.zm.skill.storage.GitService;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Repository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Manages release.json — a publish pointer that tracks which git revision
 * each skill was last published at.
 */
@Service
public class ReleaseService {

    private final Path releaseJsonPath;
    private final Path repoPath;
    private final ObjectMapper objectMapper;

    public ReleaseService(
        @Value("${skill-collector.storage.base-path:./skill-repo}") String basePath
    ) {
        this.repoPath = Path.of(basePath).toAbsolutePath();
        this.releaseJsonPath = repoPath.resolve("release.json");
        this.objectMapper = new ObjectMapper();
        this.objectMapper.setSerializationInclusion(JsonInclude.Include.NON_NULL);
    }

    /**
     * Publish a skill: record current git HEAD hash + timestamp in release.json.
     */
    public void publish(String skillName) {
        ReleaseFile release = loadOrCreate();
        String headHash = resolveHeadHash();
        ReleaseEntry entry = new ReleaseEntry();
        entry.setRevision(headHash);
        entry.setPublishedAt(Instant.now().toString());
        release.getSkills().put(skillName, entry);
        writeRelease(release);
    }

    /**
     * Rollback a skill to a target revision.
     */
    public void rollback(String skillName, String targetRevision) {
        ReleaseFile release = loadOrCreate();
        ReleaseEntry entry = release.getSkills().get(skillName);
        if (entry == null) {
            entry = new ReleaseEntry();
        }
        entry.setRevision(targetRevision);
        entry.setPublishedAt(Instant.now().toString());
        release.getSkills().put(skillName, entry);
        writeRelease(release);
    }

    /**
     * Get the current release map.
     */
    public ReleaseFile getPublished() {
        return loadOrCreate();
    }

    private ReleaseFile loadOrCreate() {
        if (!Files.exists(releaseJsonPath)) {
            ReleaseFile empty = new ReleaseFile();
            empty.setSchemaVersion(1);
            empty.setSkills(new LinkedHashMap<>());
            return empty;
        }
        try {
            return objectMapper.readValue(releaseJsonPath.toFile(), ReleaseFile.class);
        } catch (IOException e) {
            throw new RuntimeException("Failed to read release.json", e);
        }
    }

    private void writeRelease(ReleaseFile release) {
        try {
            Files.createDirectories(releaseJsonPath.getParent());
            objectMapper.writerWithDefaultPrettyPrinter()
                .writeValue(releaseJsonPath.toFile(), release);
        } catch (IOException e) {
            throw new RuntimeException("Failed to write release.json", e);
        }
    }

    private String resolveHeadHash() {
        try (Git git = Git.open(repoPath.toFile())) {
            Repository repo = git.getRepository();
            ObjectId head = repo.resolve("HEAD");
            return head != null ? head.getName() : "unknown";
        } catch (IOException e) {
            return "unknown";
        }
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class ReleaseFile {
        private int schemaVersion;
        private Map<String, ReleaseEntry> skills = new LinkedHashMap<>();

        // Alias for JSON serialization as "schema_version"
        @com.fasterxml.jackson.annotation.JsonProperty("schema_version")
        public int getSchemaVersion() { return schemaVersion; }

        @com.fasterxml.jackson.annotation.JsonProperty("schema_version")
        public void setSchemaVersion(int schemaVersion) { this.schemaVersion = schemaVersion; }
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class ReleaseEntry {
        private String revision;
        @com.fasterxml.jackson.annotation.JsonProperty("published_at")
        private String publishedAt;
    }
}
