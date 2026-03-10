package com.zm.skill.service;

import com.zm.skill.storage.SkillDocument;
import lombok.Data;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Detects duplicate skills using keyword-based Jaccard similarity.
 * Flags skills with > 70% similarity and returns merge suggestions.
 */
@Service
public class DeduplicationService {

    private static final double DUPLICATE_THRESHOLD = 0.7;

    /**
     * Check if a new skill is a duplicate of any existing skills.
     *
     * @param newSkill      the new skill to check
     * @param existingSkills list of existing skills in the same domain
     * @return dedup result with similarity score and merge suggestion
     */
    public DedupResult checkDuplicate(SkillDocument newSkill, List<SkillDocument> existingSkills) {
        if (existingSkills == null || existingSkills.isEmpty()) {
            return DedupResult.noDuplicate();
        }

        String newBody = newSkill.getBody();
        double maxSimilarity = 0.0;
        String mostSimilarName = null;

        for (SkillDocument existing : existingSkills) {
            double similarity = jaccardSimilarity(newBody, existing.getBody());
            if (similarity > maxSimilarity) {
                maxSimilarity = similarity;
                mostSimilarName = existing.getMeta().getName();
            }
        }

        DedupResult result = new DedupResult();
        result.setSimilarity(maxSimilarity);
        result.setMostSimilarSkill(mostSimilarName);
        result.setDuplicate(maxSimilarity >= DUPLICATE_THRESHOLD);

        if (result.isDuplicate()) {
            result.setMergeSuggestion(
                "Skill '%s' is %.0f%% similar to existing skill '%s'. Consider merging content."
                    .formatted(newSkill.getMeta().getName(), maxSimilarity * 100, mostSimilarName)
            );
        }

        return result;
    }

    /**
     * Compute Jaccard similarity between two text strings based on word tokens.
     */
    public double jaccardSimilarity(String text1, String text2) {
        Set<String> tokens1 = tokenize(text1);
        Set<String> tokens2 = tokenize(text2);

        if (tokens1.isEmpty() && tokens2.isEmpty()) {
            return 1.0;
        }
        if (tokens1.isEmpty() || tokens2.isEmpty()) {
            return 0.0;
        }

        Set<String> intersection = new HashSet<>(tokens1);
        intersection.retainAll(tokens2);

        Set<String> union = new HashSet<>(tokens1);
        union.addAll(tokens2);

        return (double) intersection.size() / union.size();
    }

    private Set<String> tokenize(String text) {
        if (text == null || text.isBlank()) {
            return Set.of();
        }
        Set<String> tokens = new HashSet<>();

        // Split on whitespace/punctuation for word-level tokens
        String[] words = text.toLowerCase().split("[\\s\\p{Punct}]+");
        for (String word : words) {
            if (!word.isBlank()) {
                tokens.add(word);
            }
        }

        // Generate character bigrams for CJK text to handle unsegmented Chinese
        String cleaned = text.replaceAll("[\\s\\p{Punct}]", "");
        for (int i = 0; i < cleaned.length() - 1; i++) {
            tokens.add(cleaned.substring(i, i + 2));
        }

        return tokens;
    }

    @Data
    public static class DedupResult {
        private boolean duplicate;
        private double similarity;
        private String mostSimilarSkill;
        private String mergeSuggestion;

        public static DedupResult noDuplicate() {
            DedupResult result = new DedupResult();
            result.setDuplicate(false);
            result.setSimilarity(0.0);
            return result;
        }
    }
}
