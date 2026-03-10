package com.zm.skill.controller;

import com.zm.skill.controller.dto.ApiResponse;
import com.zm.skill.controller.dto.FeedbackRequest;
import com.zm.skill.domain.Feedback;
import com.zm.skill.service.AuditService;
import com.zm.skill.service.FeedbackService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/feedback")
public class FeedbackController {

    private final FeedbackService feedbackService;
    private final AuditService auditService;

    public FeedbackController(FeedbackService feedbackService, AuditService auditService) {
        this.feedbackService = feedbackService;
        this.auditService = auditService;
    }

    @PostMapping
    public ResponseEntity<ApiResponse<String>> submitFeedback(
            @Valid @RequestBody FeedbackRequest request
    ) {
        Feedback feedback = Feedback.builder()
            .skillName(request.getSkillName())
            .rating(Feedback.Rating.fromValue(request.getRating()))
            .comment(request.getComment())
            .build();

        feedbackService.submit(feedback);

        // P1-20: Audit logging
        auditService.log("feedback", request.getSkillName(), String.valueOf(request.getRating()));

        return ResponseEntity.ok(ApiResponse.ok("Feedback recorded"));
    }

    @GetMapping("/stats")
    public ResponseEntity<ApiResponse<Map<String, String>>> getStats(
            @RequestParam(required = false) String skillName
    ) {
        if (skillName != null) {
            String score = feedbackService.getAggregateScore(skillName);
            return ResponseEntity.ok(ApiResponse.ok(Map.of(skillName, score)));
        }
        return ResponseEntity.ok(ApiResponse.ok(Map.of()));
    }

    @GetMapping("/{skillName}")
    public ResponseEntity<ApiResponse<List<Feedback>>> getFeedback(@PathVariable String skillName) {
        List<Feedback> feedbacks = feedbackService.getFeedbackForSkill(skillName);
        return ResponseEntity.ok(ApiResponse.ok(feedbacks));
    }
}
