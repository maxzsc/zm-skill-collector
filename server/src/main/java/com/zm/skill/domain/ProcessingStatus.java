package com.zm.skill.domain;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

import java.util.Set;
import java.util.Map;

public enum ProcessingStatus {
    SUBMITTED("submitted"),
    PARSING("parsing"),
    CLASSIFYING("classifying"),
    CLUSTERING("clustering"),
    AWAITING_CONFIRMATION("awaiting_confirmation"),
    GENERATING("generating"),
    VALIDATING("validating"),
    DEDUP_CHECK("dedup_check"),
    COMPLETED("completed"),
    FAILED("failed");

    private final String value;

    private static final Map<ProcessingStatus, Set<ProcessingStatus>> VALID_TRANSITIONS = Map.of(
        SUBMITTED, Set.of(PARSING, FAILED),
        PARSING, Set.of(CLASSIFYING, FAILED),
        CLASSIFYING, Set.of(CLUSTERING, FAILED),
        CLUSTERING, Set.of(AWAITING_CONFIRMATION, FAILED),
        AWAITING_CONFIRMATION, Set.of(GENERATING, FAILED),
        GENERATING, Set.of(VALIDATING, FAILED),
        VALIDATING, Set.of(DEDUP_CHECK, FAILED),
        DEDUP_CHECK, Set.of(COMPLETED, FAILED),
        COMPLETED, Set.of(),
        FAILED, Set.of(SUBMITTED)
    );

    ProcessingStatus(String value) {
        this.value = value;
    }

    @JsonValue
    public String getValue() {
        return value;
    }

    @JsonCreator
    public static ProcessingStatus fromValue(String value) {
        for (ProcessingStatus s : values()) {
            if (s.value.equals(value)) {
                return s;
            }
        }
        throw new IllegalArgumentException("Unknown ProcessingStatus: " + value);
    }

    public boolean canTransitionTo(ProcessingStatus target) {
        return VALID_TRANSITIONS.getOrDefault(this, Set.of()).contains(target);
    }
}
