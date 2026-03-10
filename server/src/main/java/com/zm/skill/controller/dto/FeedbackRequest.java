package com.zm.skill.controller.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FeedbackRequest {
    @NotBlank(message = "skillName is required")
    private String skillName;

    @NotBlank(message = "rating is required")
    @Pattern(regexp = "useful|misleading|outdated", message = "rating must be one of: useful, misleading, outdated")
    private String rating;

    private String comment;
}
