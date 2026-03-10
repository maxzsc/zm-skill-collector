package com.zm.skill.service;

import com.zm.skill.domain.SkillMeta;
import com.zm.skill.domain.Visibility;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Utility for filtering skills by team visibility.
 * Rules:
 * - "public" skills are always visible
 * - "team:{name}" skills are visible only if {name} is in the provided teams list
 * - If teams is null or empty, no visibility filtering is applied (all returned)
 * - Null visibility is treated as public
 */
public final class VisibilityFilter {

    private VisibilityFilter() {}

    /**
     * Filter a list of skills by team visibility.
     *
     * @param skills the full skill list
     * @param teams  the teams the caller belongs to (null or empty = no filter)
     * @return filtered list of skills visible to the given teams
     */
    public static List<SkillMeta> filter(List<SkillMeta> skills, List<String> teams) {
        if (teams == null || teams.isEmpty()) {
            return skills;
        }

        Set<String> teamSet = new HashSet<>(teams);

        return skills.stream()
            .filter(meta -> isVisible(meta.getVisibility(), teamSet))
            .collect(Collectors.toList());
    }

    private static boolean isVisible(Visibility visibility, Set<String> teams) {
        if (visibility == null || visibility.isPublic()) {
            return true;
        }
        if (visibility.isTeam()) {
            return teams.contains(visibility.getTeamName());
        }
        return false;
    }
}
