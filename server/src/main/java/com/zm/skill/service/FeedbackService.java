package com.zm.skill.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.zm.skill.domain.Feedback;
import com.zm.skill.storage.GitService;
import com.zm.skill.storage.SkillDocument;
import com.zm.skill.storage.SkillRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Manages feedback collection, aggregation and auto-degradation for skills.
 * Stores feedback as JSON files per skill in the feedback directory.
 */
@Service
public class FeedbackService {

    private final Path feedbackDir;
    private final SkillRepository skillRepository;
    private final GitService gitService;
    private final ObjectMapper objectMapper;

    public FeedbackService(
        @Value("${skill-collector.storage.base-path:./skill-repo}/feedback") Path feedbackDir,
        SkillRepository skillRepository,
        GitService gitService
    ) {
        this.feedbackDir = feedbackDir;
        this.skillRepository = skillRepository;
        this.gitService = gitService;
        this.objectMapper = new ObjectMapper();
        this.objectMapper.registerModule(new JavaTimeModule());
    }

    /**
     * Submit a feedback entry. Persists to the JSON file and checks auto-degradation.
     */
    public void submit(Feedback feedback) {
        if (feedback.getTimestamp() == null) {
            feedback.setTimestamp(java.time.Instant.now());
        }

        List<Feedback> existing = getFeedbackForSkill(feedback.getSkillName());
        existing.add(feedback);
        saveFeedback(feedback.getSkillName(), existing);

        // Check auto-degradation
        checkAutoDegradation(feedback.getSkillName(), existing);
    }

    /**
     * Load all feedback entries for a given skill.
     */
    public List<Feedback> getFeedbackForSkill(String skillName) {
        Path file = feedbackDir.resolve(skillName + ".json");
        if (!Files.exists(file)) {
            return new ArrayList<>();
        }
        try {
            return new ArrayList<>(objectMapper.readValue(
                file.toFile(),
                new TypeReference<List<Feedback>>() {}
            ));
        } catch (IOException e) {
            throw new RuntimeException("Failed to read feedback for: " + skillName, e);
        }
    }

    /**
     * Compute an aggregate score string: "X.X/5 (N)" where N is total count.
     * Score mapping: average of individual scores (-1 to +1) mapped to 0-5 scale.
     */
    public String getAggregateScore(String skillName) {
        List<Feedback> feedbacks = getFeedbackForSkill(skillName);
        if (feedbacks.isEmpty()) {
            return "N/A (0)";
        }

        double totalScore = feedbacks.stream()
            .mapToDouble(f -> f.getRating().getScore())
            .sum();
        double avgScore = totalScore / feedbacks.size();

        // Map from [-1, 1] to [0, 5]
        double fivePointScore = (avgScore + 1.0) / 2.0 * 5.0;
        fivePointScore = Math.max(0.0, Math.min(5.0, fivePointScore));

        return String.format("%.1f/5 (%d)", fivePointScore, feedbacks.size());
    }

    private void saveFeedback(String skillName, List<Feedback> feedbacks) {
        try {
            Files.createDirectories(feedbackDir);
            Path file = feedbackDir.resolve(skillName + ".json");
            objectMapper.writerWithDefaultPrettyPrinter().writeValue(file.toFile(), feedbacks);
        } catch (IOException e) {
            throw new RuntimeException("Failed to save feedback for: " + skillName, e);
        }
    }

    /**
     * If >= 3 "misleading" feedbacks AND misleading count > useful count,
     * mark the skill as needing review.
     */
    private void checkAutoDegradation(String skillName, List<Feedback> feedbacks) {
        long misleadingCount = feedbacks.stream()
            .filter(f -> f.getRating() == Feedback.Rating.MISLEADING)
            .count();
        long usefulCount = feedbacks.stream()
            .filter(f -> f.getRating() == Feedback.Rating.USEFUL)
            .count();

        if (misleadingCount >= 3 && misleadingCount > usefulCount) {
            skillRepository.findByName(skillName).ifPresent(doc -> {
                if (!Boolean.TRUE.equals(doc.getMeta().getNeedsReview())) {
                    doc.getMeta().setNeedsReview(true);
                    skillRepository.save(doc.getMeta(), doc.getBody());
                    gitService.commitAll("feedback: mark " + skillName + " as needs-review");
                }
            });
        }
    }
}
