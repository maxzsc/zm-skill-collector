package com.zm.skill.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Submission {
    private String id;
    private String fileName;
    private String description;
    private String seedDomain;
    private ProcessingStatus status;
    private List<String> documentPaths;
    @Builder.Default
    private Instant createdAt = Instant.now();
    private Instant updatedAt;
    private String errorMessage;

    // P0-7: Failure recovery
    @Builder.Default
    private int retryCount = 0;
    public static final int MAX_RETRIES = 3;

    // P0-10: Idempotency
    private String idempotencyKey;
}
