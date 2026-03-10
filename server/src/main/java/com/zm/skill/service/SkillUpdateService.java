package com.zm.skill.service;

import com.zm.skill.domain.SkillType;
import com.zm.skill.domain.Visibility;
import com.zm.skill.storage.SkillDocument;
import com.zm.skill.storage.SkillRepository;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Handles skill update strategies:
 * - Knowledge: aggregates ALL raw docs in a domain to regenerate the skill
 * - Procedure: overwrites the raw doc and regenerates from the new source
 */
@Service
public class SkillUpdateService {

    private final SkillRepository skillRepository;
    private final SkillGenerationService generationService;
    private final ValidationService validationService;

    // P0-9: Domain-level concurrency locks
    private final ConcurrentHashMap<String, ReentrantLock> domainLocks = new ConcurrentHashMap<>();

    public SkillUpdateService(SkillRepository skillRepository, SkillGenerationService generationService,
                              ValidationService validationService) {
        this.skillRepository = skillRepository;
        this.generationService = generationService;
        this.validationService = validationService;
    }

    /**
     * Update a knowledge skill by adding a new document and re-aggregating all raw docs.
     *
     * @param domain     the domain name
     * @param fileName   the new document file name
     * @param docContent the new document content
     * @param visibility the visibility setting
     * @return the regenerated skill document
     */
    public SkillDocument updateKnowledge(String domain, String fileName, String docContent, Visibility visibility) {
        ReentrantLock lock = getDomainLock(domain);
        lock.lock();
        try {
            // Save the new raw document first
            skillRepository.saveRaw(domain, SkillType.KNOWLEDGE, fileName, docContent);

            // Load ALL raw docs for this domain (including the one just saved)
            List<String> allDocs = new ArrayList<>(skillRepository.loadRawDocuments(domain, SkillType.KNOWLEDGE));

            // Regenerate the knowledge skill from all documents
            SkillDocument regenerated = generationService.generateKnowledge(domain, allDocs, visibility);

            // QA-004b: Validate before saving
            ValidationService.ValidationResult validationResult = validationService.validate(regenerated);
            if (!validationResult.isValid()) {
                throw new IllegalStateException("Validation failed: " + String.join("; ", validationResult.getErrors()));
            }

            // Set last_updated timestamp
            regenerated.getMeta().setLastUpdated(Instant.now());

            // Save the regenerated skill
            skillRepository.save(regenerated.getMeta(), regenerated.getBody());

            // QA-006b: Save glossary from aliases
            if (regenerated.getMeta().getAliases() != null && !regenerated.getMeta().getAliases().isEmpty()) {
                Map<String, List<String>> glossary = Map.of(regenerated.getMeta().getName(), regenerated.getMeta().getAliases());
                skillRepository.saveGlossary(domain, glossary);
            }

            return regenerated;
        } finally {
            lock.unlock();
        }
    }

    /**
     * Update a procedure skill by overwriting the raw doc and regenerating.
     *
     * @param domain     the domain name
     * @param fileName   the source document file name
     * @param docContent the new document content (overwrites old)
     * @param visibility the visibility setting
     * @return the regenerated skill document
     */
    public SkillDocument updateProcedure(String domain, String fileName, String docContent, Visibility visibility) {
        ReentrantLock lock = getDomainLock(domain);
        lock.lock();
        try {
            // QA-008: Clear all existing raw files before saving new one
            skillRepository.clearRawDirectory(domain, SkillType.PROCEDURE, fileName);

            // Save the new raw document
            skillRepository.saveRaw(domain, SkillType.PROCEDURE, fileName, docContent);

            // Regenerate the procedure skill from the new document
            SkillDocument regenerated = generationService.generateProcedure(domain, docContent, visibility);

            // QA-004b: Validate before saving
            ValidationService.ValidationResult validationResult = validationService.validate(regenerated);
            if (!validationResult.isValid()) {
                throw new IllegalStateException("Validation failed: " + String.join("; ", validationResult.getErrors()));
            }

            // Set last_updated timestamp
            regenerated.getMeta().setLastUpdated(Instant.now());

            // Save the regenerated skill
            skillRepository.save(regenerated.getMeta(), regenerated.getBody());

            // QA-006b: Save glossary from aliases
            if (regenerated.getMeta().getAliases() != null && !regenerated.getMeta().getAliases().isEmpty()) {
                Map<String, List<String>> glossary = Map.of(regenerated.getMeta().getName(), regenerated.getMeta().getAliases());
                skillRepository.saveGlossary(domain, glossary);
            }

            return regenerated;
        } finally {
            lock.unlock();
        }
    }

    private ReentrantLock getDomainLock(String domain) {
        return domainLocks.computeIfAbsent(domain, k -> new ReentrantLock());
    }
}
