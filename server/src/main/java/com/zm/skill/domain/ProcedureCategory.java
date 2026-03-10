package com.zm.skill.domain;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

public enum ProcedureCategory {
    DEV("dev"),
    OPS("ops"),
    TEST("test"),
    BIZ_OPERATION("biz-operation"),
    ANALYSIS("analysis"),
    COLLABORATION("collaboration");

    private final String value;

    ProcedureCategory(String value) {
        this.value = value;
    }

    @JsonValue
    public String getValue() {
        return value;
    }

    @JsonCreator
    public static ProcedureCategory fromValue(String value) {
        for (ProcedureCategory c : values()) {
            if (c.value.equals(value)) {
                return c;
            }
        }
        throw new IllegalArgumentException("Unknown ProcedureCategory: " + value);
    }
}
