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
}
