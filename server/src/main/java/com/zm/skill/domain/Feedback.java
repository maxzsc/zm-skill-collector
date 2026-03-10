package com.zm.skill.domain;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Feedback {

    private String skillName;
    private Rating rating;
    private String comment;
    @Builder.Default
    private Instant timestamp = Instant.now();

    public enum Rating {
        USEFUL("useful"),
        MISLEADING("misleading"),
        OUTDATED("outdated");

        private final String value;

        Rating(String value) {
            this.value = value;
        }

        @JsonValue
        public String getValue() {
            return value;
        }

        @JsonCreator
        public static Rating fromValue(String value) {
            for (Rating r : values()) {
                if (r.value.equals(value)) {
                    return r;
                }
            }
            throw new IllegalArgumentException("Unknown Rating: " + value);
        }

        public double getScore() {
            return switch (this) {
                case USEFUL -> 1.0;
                case MISLEADING -> -1.0;
                case OUTDATED -> -0.5;
            };
        }
    }
}
