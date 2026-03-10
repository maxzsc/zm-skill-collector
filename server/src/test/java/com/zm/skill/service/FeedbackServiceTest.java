package com.zm.skill.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.zm.skill.domain.Feedback;
import com.zm.skill.domain.SkillMeta;
import com.zm.skill.domain.SkillType;
import com.zm.skill.domain.Visibility;
import com.zm.skill.storage.FileSkillRepository;
import com.zm.skill.storage.GitService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.nio.file.Path;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class FeedbackServiceTest {

    @TempDir
    Path tempDir;

    @Mock
    private GitService gitService;

    private FeedbackService feedbackService;
    private FileSkillRepository skillRepository;

    @BeforeEach
    void setUp() {
        Path feedbackDir = tempDir.resolve("feedback");
        skillRepository = new FileSkillRepository(tempDir);
        feedbackService = new FeedbackService(feedbackDir, skillRepository, gitService);
    }

    @Test
    void shouldStoreFeedbackInJsonFile() {
        Feedback feedback = Feedback.builder()
            .skillName("test-skill")
            .rating(Feedback.Rating.USEFUL)
            .comment("Very helpful")
            .build();

        feedbackService.submit(feedback);

        List<Feedback> stored = feedbackService.getFeedbackForSkill("test-skill");
        assertThat(stored).hasSize(1);
        assertThat(stored.get(0).getRating()).isEqualTo(Feedback.Rating.USEFUL);
        assertThat(stored.get(0).getComment()).isEqualTo("Very helpful");
    }

    @Test
    void shouldAppendMultipleFeedbacks() {
        feedbackService.submit(Feedback.builder()
            .skillName("multi-skill")
            .rating(Feedback.Rating.USEFUL)
            .build());
        feedbackService.submit(Feedback.builder()
            .skillName("multi-skill")
            .rating(Feedback.Rating.MISLEADING)
            .comment("Incorrect info")
            .build());
        feedbackService.submit(Feedback.builder()
            .skillName("multi-skill")
            .rating(Feedback.Rating.OUTDATED)
            .build());

        List<Feedback> stored = feedbackService.getFeedbackForSkill("multi-skill");
        assertThat(stored).hasSize(3);
    }

    @Test
    void shouldComputeAggregateScore() {
        // useful=+1, misleading=-1, outdated=-0.5
        // 2 useful (+2), 1 misleading (-1) = net +1.0
        // Score formula: normalize to X.X/5 (N)
        feedbackService.submit(Feedback.builder()
            .skillName("scored-skill")
            .rating(Feedback.Rating.USEFUL)
            .build());
        feedbackService.submit(Feedback.builder()
            .skillName("scored-skill")
            .rating(Feedback.Rating.USEFUL)
            .build());
        feedbackService.submit(Feedback.builder()
            .skillName("scored-skill")
            .rating(Feedback.Rating.MISLEADING)
            .build());

        String aggregate = feedbackService.getAggregateScore("scored-skill");
        // With 2 useful (+2) and 1 misleading (-1), avg score = 1/3 = 0.333
        // Mapped to 5-point scale: (0.333 + 1) / 2 * 5 = 3.3
        assertThat(aggregate).matches("\\d\\.\\d/5 \\(3\\)");
    }

    @Test
    void shouldReturnDefaultScoreWhenNoFeedback() {
        String aggregate = feedbackService.getAggregateScore("no-feedback-skill");
        assertThat(aggregate).isEqualTo("N/A (0)");
    }

    @Test
    void shouldAutoDegradeWhenTooManyMisleading() {
        // Create the skill first
        SkillMeta meta = SkillMeta.builder()
            .name("bad-skill")
            .type(SkillType.KNOWLEDGE)
            .domain("test")
            .visibility(Visibility.parse("public"))
            .lastUpdated(Instant.now())
            .build();
        skillRepository.save(meta, "# Bad content");

        // Submit 3 misleading feedbacks (>= 3 misleading AND misleading > useful)
        feedbackService.submit(Feedback.builder()
            .skillName("bad-skill")
            .rating(Feedback.Rating.MISLEADING)
            .build());
        feedbackService.submit(Feedback.builder()
            .skillName("bad-skill")
            .rating(Feedback.Rating.MISLEADING)
            .build());
        feedbackService.submit(Feedback.builder()
            .skillName("bad-skill")
            .rating(Feedback.Rating.MISLEADING)
            .build());

        // Check the skill is marked for review
        var doc = skillRepository.findByName("bad-skill").orElseThrow();
        assertThat(doc.getMeta().getNeedsReview()).isTrue();

        // Verify git commit was made
        verify(gitService).commitAll("feedback: mark bad-skill as needs-review");
    }

    @Test
    void shouldNotDegradeWhenUsefulOutnumbersMisleading() {
        SkillMeta meta = SkillMeta.builder()
            .name("ok-skill")
            .type(SkillType.KNOWLEDGE)
            .domain("test")
            .visibility(Visibility.parse("public"))
            .lastUpdated(Instant.now())
            .build();
        skillRepository.save(meta, "# OK content");

        // 3 misleading but 4 useful -> misleading NOT > useful
        feedbackService.submit(Feedback.builder().skillName("ok-skill").rating(Feedback.Rating.USEFUL).build());
        feedbackService.submit(Feedback.builder().skillName("ok-skill").rating(Feedback.Rating.USEFUL).build());
        feedbackService.submit(Feedback.builder().skillName("ok-skill").rating(Feedback.Rating.USEFUL).build());
        feedbackService.submit(Feedback.builder().skillName("ok-skill").rating(Feedback.Rating.USEFUL).build());
        feedbackService.submit(Feedback.builder().skillName("ok-skill").rating(Feedback.Rating.MISLEADING).build());
        feedbackService.submit(Feedback.builder().skillName("ok-skill").rating(Feedback.Rating.MISLEADING).build());
        feedbackService.submit(Feedback.builder().skillName("ok-skill").rating(Feedback.Rating.MISLEADING).build());

        var doc = skillRepository.findByName("ok-skill").orElseThrow();
        assertThat(doc.getMeta().getNeedsReview()).isNull();
    }

    @Test
    void shouldReturnEmptyListForUnknownSkill() {
        List<Feedback> result = feedbackService.getFeedbackForSkill("nonexistent");
        assertThat(result).isEmpty();
    }

    @Test
    void shouldSeparateFeedbackBySkillName() {
        feedbackService.submit(Feedback.builder()
            .skillName("skill-a")
            .rating(Feedback.Rating.USEFUL)
            .build());
        feedbackService.submit(Feedback.builder()
            .skillName("skill-b")
            .rating(Feedback.Rating.MISLEADING)
            .build());

        assertThat(feedbackService.getFeedbackForSkill("skill-a")).hasSize(1);
        assertThat(feedbackService.getFeedbackForSkill("skill-b")).hasSize(1);
    }
}
