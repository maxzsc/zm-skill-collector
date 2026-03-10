package com.zm.skill.storage;

import com.zm.skill.domain.SkillMeta;
import com.zm.skill.domain.SkillType;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Repository interface for skill storage operations.
 */
public interface SkillRepository {

    /**
     * Save a skill (meta + body) to storage.
     */
    void save(SkillMeta meta, String body);

    /**
     * Find a skill by its unique name.
     */
    Optional<SkillDocument> findByName(String name);

    /**
     * Load the index of all skill metadata.
     */
    List<SkillMeta> loadIndex();

    /**
     * Save a raw source document.
     */
    void saveRaw(String domain, SkillType type, String fileName, String content);

    /**
     * Save a glossary (term -> aliases mapping) for a domain.
     */
    void saveGlossary(String domain, Map<String, List<String>> glossary);

    /**
     * Load a glossary for a domain.
     */
    Optional<Map<String, List<String>>> loadGlossary(String domain);

    /**
     * Load all raw documents for a given domain and type.
     */
    List<String> loadRawDocuments(String domain, SkillType type);
}
