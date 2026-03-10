package com.zm.skill.domain;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

public class Visibility {

    private static final String PUBLIC = "public";
    private static final String TEAM_PREFIX = "team:";

    private final String value;

    private Visibility(String value) {
        this.value = value;
    }

    @JsonCreator
    public static Visibility parse(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Visibility cannot be null or blank");
        }
        if (value.equals(PUBLIC) || value.startsWith(TEAM_PREFIX)) {
            return new Visibility(value);
        }
        throw new IllegalArgumentException("Invalid visibility: " + value
            + ". Must be 'public' or 'team:{name}'");
    }

    @JsonValue
    public String toValue() {
        return value;
    }

    public boolean isPublic() {
        return PUBLIC.equals(value);
    }

    public boolean isTeam() {
        return value.startsWith(TEAM_PREFIX);
    }

    public String getTeamName() {
        if (!isTeam()) {
            throw new IllegalStateException("Not a team visibility");
        }
        return value.substring(TEAM_PREFIX.length());
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Visibility that = (Visibility) o;
        return value.equals(that.value);
    }

    @Override
    public int hashCode() {
        return value.hashCode();
    }

    @Override
    public String toString() {
        return value;
    }
}
