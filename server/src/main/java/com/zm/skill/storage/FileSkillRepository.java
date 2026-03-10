package com.zm.skill.storage;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.fasterxml.jackson.dataformat.yaml.YAMLGenerator;
import com.zm.skill.domain.SkillMeta;
import com.zm.skill.domain.SkillType;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * File-system based skill repository.
 * Stores skills as YAML front matter + markdown body files.
 *
 * Directory structure:
 *   {basePath}/skills/knowledge/{domain}.md
 *   {basePath}/skills/procedure/{skill-name}.md
 *   {basePath}/raw/{type}/{domain}/{filename}
 *   {basePath}/glossary/{domain}.yaml
 */
public class FileSkillRepository implements SkillRepository {

    private final Path basePath;
    private final ObjectMapper yamlMapper;

    public FileSkillRepository(Path basePath) {
        this.basePath = basePath;
        YAMLFactory factory = new YAMLFactory()
            .disable(YAMLGenerator.Feature.WRITE_DOC_START_MARKER);
        this.yamlMapper = new ObjectMapper(factory);
    }

    @Override
    public void save(SkillMeta meta, String body) {
        Path filePath = resolveSkillPath(meta);
        try {
            Files.createDirectories(filePath.getParent());
            String content = SkillFileFormat.serialize(meta, body);
            Files.writeString(filePath, content);
        } catch (IOException e) {
            throw new RuntimeException("Failed to save skill: " + meta.getName(), e);
        }
    }

    @Override
    public Optional<SkillDocument> findByName(String name) {
        // Search through all skill files
        Path skillsDir = basePath.resolve("skills");
        if (!Files.exists(skillsDir)) {
            return Optional.empty();
        }

        try (Stream<Path> files = Files.walk(skillsDir)) {
            return files
                .filter(p -> p.toString().endsWith(".md"))
                .map(this::tryLoadSkillDocument)
                .filter(Optional::isPresent)
                .map(Optional::get)
                .filter(doc -> name.equals(doc.getMeta().getName()))
                .findFirst();
        } catch (IOException e) {
            throw new RuntimeException("Failed to search for skill: " + name, e);
        }
    }

    @Override
    public List<SkillMeta> loadIndex() {
        Path skillsDir = basePath.resolve("skills");
        if (!Files.exists(skillsDir)) {
            return List.of();
        }

        List<SkillMeta> index = new ArrayList<>();
        try (Stream<Path> files = Files.walk(skillsDir)) {
            files.filter(p -> p.toString().endsWith(".md"))
                .forEach(path -> {
                    tryLoadSkillDocument(path).ifPresent(doc -> index.add(doc.getMeta()));
                });
        } catch (IOException e) {
            throw new RuntimeException("Failed to load skill index", e);
        }
        return index;
    }

    @Override
    public void saveRaw(String domain, SkillType type, String fileName, String content) {
        Path rawPath = basePath.resolve("raw")
            .resolve(type.getValue())
            .resolve(domain)
            .resolve(fileName);
        try {
            Files.createDirectories(rawPath.getParent());
            Files.writeString(rawPath, content);
        } catch (IOException e) {
            throw new RuntimeException("Failed to save raw document: " + fileName, e);
        }
    }

    @Override
    public void saveGlossary(String domain, Map<String, List<String>> glossary) {
        Path glossaryPath = basePath.resolve("glossary").resolve(domain + ".yaml");
        try {
            Files.createDirectories(glossaryPath.getParent());
            yamlMapper.writeValue(glossaryPath.toFile(), glossary);
        } catch (IOException e) {
            throw new RuntimeException("Failed to save glossary for domain: " + domain, e);
        }
    }

    @Override
    public Optional<Map<String, List<String>>> loadGlossary(String domain) {
        Path glossaryPath = basePath.resolve("glossary").resolve(domain + ".yaml");
        if (!Files.exists(glossaryPath)) {
            return Optional.empty();
        }
        try {
            Map<String, List<String>> glossary = yamlMapper.readValue(
                glossaryPath.toFile(),
                new TypeReference<Map<String, List<String>>>() {}
            );
            return Optional.of(glossary);
        } catch (IOException e) {
            throw new RuntimeException("Failed to load glossary for domain: " + domain, e);
        }
    }

    @Override
    public List<String> loadRawDocuments(String domain, SkillType type) {
        Path rawDir = basePath.resolve("raw").resolve(type.getValue()).resolve(domain);
        if (!Files.exists(rawDir)) {
            return List.of();
        }
        try (Stream<Path> files = Files.list(rawDir)) {
            return files
                .filter(Files::isRegularFile)
                .sorted()
                .map(path -> {
                    try {
                        return Files.readString(path);
                    } catch (IOException e) {
                        throw new RuntimeException("Failed to read raw document: " + path, e);
                    }
                })
                .collect(Collectors.toList());
        } catch (IOException e) {
            throw new RuntimeException("Failed to list raw documents for: " + domain, e);
        }
    }

    private Path resolveSkillPath(SkillMeta meta) {
        String typeDir = meta.getType().getValue();
        String fileName;
        if (meta.getType() == SkillType.KNOWLEDGE) {
            fileName = meta.getDomain() + ".md";
        } else {
            fileName = meta.getName() + ".md";
        }
        return basePath.resolve("skills").resolve(typeDir).resolve(fileName);
    }

    private Optional<SkillDocument> tryLoadSkillDocument(Path path) {
        try {
            String content = Files.readString(path);
            return Optional.of(SkillFileFormat.deserialize(content));
        } catch (Exception e) {
            return Optional.empty();
        }
    }
}
