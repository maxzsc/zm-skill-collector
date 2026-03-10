package com.zm.skill.domain;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

public enum SkillType {
    KNOWLEDGE("knowledge"),
    PROCEDURE("procedure");

    private final String value;

    SkillType(String value) {
        this.value = value;
    }

    @JsonValue
    public String getValue() {
        return value;
    }

    @JsonCreator
    public static SkillType fromValue(String value) {
        for (SkillType type : values()) {
            if (type.value.equals(value)) {
                return type;
            }
        }
        throw new IllegalArgumentException("Unknown SkillType: " + value);
    }
}
