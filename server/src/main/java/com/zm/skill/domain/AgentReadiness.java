package com.zm.skill.domain;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

public enum AgentReadiness {
    READY("ready"),
    PARTIAL("partial"),
    FUTURE("future");

    private final String value;

    AgentReadiness(String value) {
        this.value = value;
    }

    @JsonValue
    public String getValue() {
        return value;
    }

    @JsonCreator
    public static AgentReadiness fromValue(String value) {
        for (AgentReadiness a : values()) {
            if (a.value.equals(value)) {
                return a;
            }
        }
        throw new IllegalArgumentException("Unknown AgentReadiness: " + value);
    }
}
