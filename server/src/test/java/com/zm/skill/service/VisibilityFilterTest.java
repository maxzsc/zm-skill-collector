package com.zm.skill.service;

import com.zm.skill.domain.SkillMeta;
import com.zm.skill.domain.SkillType;
import com.zm.skill.domain.Visibility;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class VisibilityFilterTest {

    @Test
    void shouldReturnPublicSkillsAlways() {
        List<SkillMeta> skills = List.of(
            SkillMeta.builder().name("public-skill").type(SkillType.KNOWLEDGE).domain("test")
                .visibility(Visibility.parse("public")).build()
        );

        List<SkillMeta> result = VisibilityFilter.filter(skills, List.of("teamA"));
        assertThat(result).hasSize(1);
        assertThat(result.get(0).getName()).isEqualTo("public-skill");
    }

    @Test
    void shouldReturnTeamSkillsWhenTeamMatches() {
        List<SkillMeta> skills = List.of(
            SkillMeta.builder().name("team-skill").type(SkillType.KNOWLEDGE).domain("test")
                .visibility(Visibility.parse("team:backend")).build()
        );

        List<SkillMeta> result = VisibilityFilter.filter(skills, List.of("backend"));
        assertThat(result).hasSize(1);
    }

    @Test
    void shouldExcludeTeamSkillsWhenTeamDoesNotMatch() {
        List<SkillMeta> skills = List.of(
            SkillMeta.builder().name("team-skill").type(SkillType.KNOWLEDGE).domain("test")
                .visibility(Visibility.parse("team:backend")).build()
        );

        List<SkillMeta> result = VisibilityFilter.filter(skills, List.of("frontend"));
        assertThat(result).isEmpty();
    }

    @Test
    void shouldHandleMultipleTeams() {
        List<SkillMeta> skills = List.of(
            SkillMeta.builder().name("backend-only").type(SkillType.KNOWLEDGE).domain("test")
                .visibility(Visibility.parse("team:backend")).build(),
            SkillMeta.builder().name("frontend-only").type(SkillType.KNOWLEDGE).domain("test")
                .visibility(Visibility.parse("team:frontend")).build(),
            SkillMeta.builder().name("ops-only").type(SkillType.KNOWLEDGE).domain("test")
                .visibility(Visibility.parse("team:ops")).build()
        );

        List<SkillMeta> result = VisibilityFilter.filter(skills, List.of("backend", "frontend"));
        assertThat(result).hasSize(2);
        assertThat(result).extracting(SkillMeta::getName)
            .containsExactlyInAnyOrder("backend-only", "frontend-only");
    }

    @Test
    void shouldReturnOnlyPublicWhenNoTeamsProvided() {
        List<SkillMeta> skills = List.of(
            SkillMeta.builder().name("public-skill").type(SkillType.KNOWLEDGE).domain("test")
                .visibility(Visibility.parse("public")).build(),
            SkillMeta.builder().name("team-skill").type(SkillType.KNOWLEDGE).domain("test")
                .visibility(Visibility.parse("team:backend")).build()
        );

        // Null teams -> only public skills returned (P0-4 fix)
        List<SkillMeta> result = VisibilityFilter.filter(skills, null);
        assertThat(result).hasSize(1);
        assertThat(result.get(0).getName()).isEqualTo("public-skill");
    }

    @Test
    void shouldReturnOnlyPublicWhenEmptyTeamsList() {
        List<SkillMeta> skills = List.of(
            SkillMeta.builder().name("public-skill").type(SkillType.KNOWLEDGE).domain("test")
                .visibility(Visibility.parse("public")).build(),
            SkillMeta.builder().name("team-skill").type(SkillType.KNOWLEDGE).domain("test")
                .visibility(Visibility.parse("team:backend")).build()
        );

        // Empty teams list -> only public skills returned (P0-4 fix)
        List<SkillMeta> result = VisibilityFilter.filter(skills, List.of());
        assertThat(result).hasSize(1);
        assertThat(result.get(0).getName()).isEqualTo("public-skill");
    }

    @Test
    void shouldHandleMixedVisibilities() {
        List<SkillMeta> skills = List.of(
            SkillMeta.builder().name("pub1").type(SkillType.KNOWLEDGE).domain("test")
                .visibility(Visibility.parse("public")).build(),
            SkillMeta.builder().name("team-backend").type(SkillType.PROCEDURE).domain("test")
                .visibility(Visibility.parse("team:backend")).build(),
            SkillMeta.builder().name("team-frontend").type(SkillType.PROCEDURE).domain("test")
                .visibility(Visibility.parse("team:frontend")).build(),
            SkillMeta.builder().name("pub2").type(SkillType.KNOWLEDGE).domain("test")
                .visibility(Visibility.parse("public")).build()
        );

        List<SkillMeta> result = VisibilityFilter.filter(skills, List.of("backend"));
        assertThat(result).hasSize(3);
        assertThat(result).extracting(SkillMeta::getName)
            .containsExactlyInAnyOrder("pub1", "pub2", "team-backend");
    }

    @Test
    void shouldHandleNullVisibilityAsPublic() {
        List<SkillMeta> skills = List.of(
            SkillMeta.builder().name("no-vis").type(SkillType.KNOWLEDGE).domain("test").build()
        );

        List<SkillMeta> result = VisibilityFilter.filter(skills, List.of("backend"));
        // Null visibility treated as public -> included
        assertThat(result).hasSize(1);
    }

    @Test
    void shouldReturnNullVisibilityWhenNoTeams() {
        List<SkillMeta> skills = List.of(
            SkillMeta.builder().name("no-vis").type(SkillType.KNOWLEDGE).domain("test").build()
        );

        // Null visibility treated as public, and null teams -> only public returned
        List<SkillMeta> result = VisibilityFilter.filter(skills, null);
        assertThat(result).hasSize(1);
    }
}
