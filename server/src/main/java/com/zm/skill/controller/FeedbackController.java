package com.zm.skill.controller;

import com.zm.skill.controller.dto.ApiResponse;
import com.zm.skill.controller.dto.FeedbackRequest;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@RestController
@RequestMapping("/api/feedback")
public class FeedbackController {

    // In-memory feedback store
    private final List<FeedbackRequest> feedbackList =
            Collections.synchronizedList(new ArrayList<>());
    private final Map<String, Map<String, Integer>> stats = new ConcurrentHashMap<>();

    @PostMapping
    public ResponseEntity<ApiResponse<String>> submitFeedback(
            @Valid @RequestBody FeedbackRequest request
    ) {
        feedbackList.add(request);

        stats.computeIfAbsent(request.getSkillName(), k -> new ConcurrentHashMap<>())
                .merge(request.getRating(), 1, Integer::sum);

        return ResponseEntity.ok(ApiResponse.ok("Feedback recorded"));
    }

    @GetMapping("/stats")
    public ResponseEntity<ApiResponse<Map<String, Map<String, Integer>>>> getStats() {
        return ResponseEntity.ok(ApiResponse.ok(stats));
    }
}
