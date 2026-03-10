package com.zm.skill.service;

import com.zm.skill.domain.SkillType;
import com.zm.skill.domain.Visibility;
import com.zm.skill.storage.SkillDocument;
import com.zm.skill.storage.SkillRepository;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Handles skill update strategies:
 * - Knowledge: aggregates ALL raw docs in a domain to regenerate the skill
 * - Procedure: overwrites the raw doc and regenerates from the new source
 */
@Service
public class SkillUpdateService {

    private final SkillRepository skillRepository;
    private final SkillGenerationService generationService;

    public SkillUpdateService(SkillRepository skillRepository, SkillGenerationService generationService) {
        this.skillRepository = skillRepository;
        this.generationService = generationService;
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
        // Save the new raw document first
        skillRepository.saveRaw(domain, SkillType.KNOWLEDGE, fileName, docContent);

        // Load ALL raw docs for this domain (including the one just saved)
        List<String> allDocs = new ArrayList<>(skillRepository.loadRawDocuments(domain, SkillType.KNOWLEDGE));

        // Regenerate the knowledge skill from all documents
        SkillDocument regenerated = generationService.generateKnowledge(domain, allDocs, visibility);

        // Set last_updated timestamp
        regenerated.getMeta().setLastUpdated(Instant.now());

        // Save the regenerated skill
        skillRepository.save(regenerated.getMeta(), regenerated.getBody());

        return regenerated;
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
        // Overwrite the raw document
        skillRepository.saveRaw(domain, SkillType.PROCEDURE, fileName, docContent);

        // Regenerate the procedure skill from the new document
        SkillDocument regenerated = generationService.generateProcedure(domain, docContent, visibility);

        // Set last_updated timestamp
        regenerated.getMeta().setLastUpdated(Instant.now());

        // Save the regenerated skill
        skillRepository.save(regenerated.getMeta(), regenerated.getBody());

        return regenerated;
    }
}
