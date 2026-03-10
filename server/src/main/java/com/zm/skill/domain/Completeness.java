package com.zm.skill.domain;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

public enum Completeness {
    L1("L1"),
    L2("L2"),
    L3("L3");

    private final String value;

    Completeness(String value) {
        this.value = value;
    }

    @JsonValue
    public String getValue() {
        return value;
    }

    @JsonCreator
    public static Completeness fromValue(String value) {
        for (Completeness c : values()) {
            if (c.value.equals(value)) {
                return c;
            }
        }
        throw new IllegalArgumentException("Unknown Completeness: " + value);
    }
}
