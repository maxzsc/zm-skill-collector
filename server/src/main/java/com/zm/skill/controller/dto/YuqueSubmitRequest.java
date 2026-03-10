package com.zm.skill.controller.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class YuqueSubmitRequest {
    @NotBlank(message = "url is required")
    private String url;
    private String description;
    private String seedDomain;
}
