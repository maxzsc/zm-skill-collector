package com.zm.skill.controller.dto;

import com.zm.skill.domain.DomainCluster;
import jakarta.validation.constraints.NotEmpty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ConfirmRequest {
    @NotEmpty(message = "clusters must not be empty")
    private List<DomainCluster> clusters;
}
