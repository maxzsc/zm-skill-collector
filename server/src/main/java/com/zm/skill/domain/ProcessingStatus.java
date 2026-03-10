package com.zm.skill.domain;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

import java.util.Set;
import java.util.Map;
import java.util.HashMap;

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
    PARTIALLY_COMPLETED("partially_completed"),
    REVIEW_REQUIRED("review_required"),
    FAILED("failed");

    private final String value;

    private static final Map<ProcessingStatus, Set<ProcessingStatus>> VALID_TRANSITIONS;

    static {
        VALID_TRANSITIONS = new HashMap<>();
        VALID_TRANSITIONS.put(SUBMITTED, Set.of(PARSING, FAILED));
        VALID_TRANSITIONS.put(PARSING, Set.of(CLASSIFYING, FAILED));
        VALID_TRANSITIONS.put(CLASSIFYING, Set.of(CLUSTERING, GENERATING, FAILED));
        VALID_TRANSITIONS.put(CLUSTERING, Set.of(AWAITING_CONFIRMATION, FAILED));
        VALID_TRANSITIONS.put(AWAITING_CONFIRMATION, Set.of(GENERATING, FAILED));
        VALID_TRANSITIONS.put(GENERATING, Set.of(VALIDATING, FAILED, REVIEW_REQUIRED));
        VALID_TRANSITIONS.put(VALIDATING, Set.of(DEDUP_CHECK, FAILED, REVIEW_REQUIRED));
        VALID_TRANSITIONS.put(DEDUP_CHECK, Set.of(COMPLETED, PARTIALLY_COMPLETED, FAILED));
        VALID_TRANSITIONS.put(COMPLETED, Set.of());
        VALID_TRANSITIONS.put(PARTIALLY_COMPLETED, Set.of());
        VALID_TRANSITIONS.put(REVIEW_REQUIRED, Set.of(GENERATING, FAILED));
        VALID_TRANSITIONS.put(FAILED, Set.of(SUBMITTED));
    }

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
