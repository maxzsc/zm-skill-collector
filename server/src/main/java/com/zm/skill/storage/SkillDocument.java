package com.zm.skill.storage;

import com.zm.skill.domain.SkillMeta;
import lombok.AllArgsConstructor;
import lombok.Data;

/**
 * Represents a complete skill document: metadata + body content.
 */
@Data
@AllArgsConstructor
public class SkillDocument {
    private SkillMeta meta;
    private String body;
}
