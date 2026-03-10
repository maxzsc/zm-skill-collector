package com.zm.skill.storage;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

@Configuration
public class StorageConfig {

    @Bean
    public FileSkillRepository fileSkillRepository(
            @Value("${skill-collector.storage.base-path:./skill-repo}") String basePath
    ) throws IOException {
        Path path = Path.of(basePath).toAbsolutePath();
        Files.createDirectories(path);
        return new FileSkillRepository(path);
    }

    @Bean
    public GitService gitService(
            @Value("${skill-collector.storage.base-path:./skill-repo}") String basePath
    ) throws IOException {
        Path path = Path.of(basePath).toAbsolutePath();
        Files.createDirectories(path);
        GitService gitService = new GitService(path);
        gitService.init();
        return gitService;
    }
}
