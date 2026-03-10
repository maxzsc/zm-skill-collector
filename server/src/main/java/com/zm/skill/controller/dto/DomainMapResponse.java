package com.zm.skill.controller.dto;

import com.zm.skill.domain.DomainCluster;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DomainMapResponse {
    private String submissionId;
    private List<DomainCluster> clusters;
}
