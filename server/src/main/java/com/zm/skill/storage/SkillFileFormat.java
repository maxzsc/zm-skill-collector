package com.zm.skill.storage;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.fasterxml.jackson.dataformat.yaml.YAMLGenerator;
import com.zm.skill.domain.SkillMeta;

/**
 * Handles serialization/deserialization of skill files in YAML front matter + body format.
 *
 * Format:
 * ---
 * name: skill-name
 * type: knowledge
 * ...
 * ---
 * # Markdown body content
 */
public class SkillFileFormat {

    private static final String SEPARATOR = "---";
    private static final ObjectMapper YAML_MAPPER;

    static {
        YAMLFactory factory = new YAMLFactory()
            .disable(YAMLGenerator.Feature.WRITE_DOC_START_MARKER);
        YAML_MAPPER = new ObjectMapper(factory);
        YAML_MAPPER.findAndRegisterModules();
    }

    /**
     * Serialize a skill to the front matter + body format.
     */
    public static String serialize(SkillMeta meta, String body) {
        try {
            String yamlMeta = YAML_MAPPER.writeValueAsString(meta);
            return SEPARATOR + "\n" + yamlMeta + SEPARATOR + "\n" + body + "\n";
        } catch (Exception e) {
            throw new RuntimeException("Failed to serialize skill: " + meta.getName(), e);
        }
    }

    /**
     * Deserialize a skill file into meta + body.
     */
    public static SkillDocument deserialize(String content) {
        if (content == null || !content.startsWith(SEPARATOR)) {
            throw new IllegalArgumentException("Invalid skill file format: missing front matter");
        }

        // Find the closing separator: look for \n---\n after the opening ---
        int afterFirstSep = SEPARATOR.length();
        // Skip optional newline after opening ---
        if (afterFirstSep < content.length() && content.charAt(afterFirstSep) == '\n') {
            afterFirstSep++;
        }
        int secondSep = content.indexOf("\n" + SEPARATOR + "\n", afterFirstSep);
        if (secondSep < 0) {
            // Try \n---EOF (file may not have trailing newline after closing ---)
            if (content.endsWith("\n" + SEPARATOR)) {
                secondSep = content.length() - SEPARATOR.length() - 1;
            } else {
                throw new IllegalArgumentException("Invalid skill file format: missing closing separator");
            }
        }

        String yamlPart = content.substring(afterFirstSep, secondSep).trim();
        String bodyPart = content.substring(secondSep + 1 + SEPARATOR.length()).trim();

        try {
            SkillMeta meta = YAML_MAPPER.readValue(yamlPart, SkillMeta.class);
            return new SkillDocument(meta, bodyPart);
        } catch (Exception e) {
            throw new RuntimeException("Failed to deserialize skill file", e);
        }
    }
}
