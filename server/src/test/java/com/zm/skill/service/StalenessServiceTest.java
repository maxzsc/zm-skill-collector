package com.zm.skill.service;

import com.zm.skill.domain.SkillMeta;
import com.zm.skill.domain.SkillType;
import com.zm.skill.domain.Visibility;
import com.zm.skill.storage.FileSkillRepository;
import com.zm.skill.storage.SkillDocument;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

import static org.assertj.core.api.Assertions.assertThat;

class StalenessServiceTest {

    @TempDir
    Path tempDir;

    private FileSkillRepository repository;
    private StalenessService stalenessService;

    @BeforeEach
    void setUp() {
        repository = new FileSkillRepository(tempDir);
        stalenessService = new StalenessService(repository, 6);
    }

    @Test
    void shouldMarkSkillAsStaleWhenOlderThanThreshold() {
        Instant sevenMonthsAgo = Instant.now().minus(210, ChronoUnit.DAYS);
        SkillMeta meta = SkillMeta.builder()
            .name("old-skill")
            .type(SkillType.KNOWLEDGE)
            .domain("test")
            .visibility(Visibility.parse("public"))
            .lastUpdated(sevenMonthsAgo)
            .build();
        repository.save(meta, "# Old Content");

        stalenessService.scanForStaleSkills();

        SkillDocument updated = repository.findByName("old-skill").orElseThrow();
        assertThat(updated.getMeta().getStale()).isTrue();
    }

    @Test
    void shouldNotMarkRecentSkillAsStale() {
        Instant oneMonthAgo = Instant.now().minus(30, ChronoUnit.DAYS);
        SkillMeta meta = SkillMeta.builder()
            .name("recent-skill")
            .type(SkillType.KNOWLEDGE)
            .domain("test")
            .visibility(Visibility.parse("public"))
            .lastUpdated(oneMonthAgo)
            .build();
        repository.save(meta, "# Recent Content");

        stalenessService.scanForStaleSkills();

        SkillDocument updated = repository.findByName("recent-skill").orElseThrow();
        assertThat(updated.getMeta().getStale()).isNull();
    }

    @Test
    void shouldTreatMissingLastUpdatedAsStale() {
        SkillMeta meta = SkillMeta.builder()
            .name("no-date-skill")
            .type(SkillType.KNOWLEDGE)
            .domain("test")
            .visibility(Visibility.parse("public"))
            .build();
        repository.save(meta, "# No Date");

        stalenessService.scanForStaleSkills();

        SkillDocument updated = repository.findByName("no-date-skill").orElseThrow();
        assertThat(updated.getMeta().getStale()).isTrue();
    }

    @Test
    void shouldInjectWarningIntoStaleSkillBody() {
        Instant sevenMonthsAgo = Instant.now().minus(210, ChronoUnit.DAYS);
        SkillMeta meta = SkillMeta.builder()
            .name("stale-warning-skill")
            .type(SkillType.KNOWLEDGE)
            .domain("test")
            .visibility(Visibility.parse("public"))
            .lastUpdated(sevenMonthsAgo)
            .stale(true)
            .build();
        repository.save(meta, "# Content Body");

        SkillDocument doc = repository.findByName("stale-warning-skill").orElseThrow();
        String decorated = stalenessService.decorateBody(doc);

        assertThat(decorated).startsWith("⚠ 此 skill 最后更新于");
        assertThat(decorated).contains("个月前，内容可能已过时，请注意验证");
        assertThat(decorated).contains("# Content Body");
    }

    @Test
    void shouldNotInjectWarningForNonStaleSkill() {
        Instant oneMonthAgo = Instant.now().minus(30, ChronoUnit.DAYS);
        SkillMeta meta = SkillMeta.builder()
            .name("fresh-skill")
            .type(SkillType.KNOWLEDGE)
            .domain("test")
            .visibility(Visibility.parse("public"))
            .lastUpdated(oneMonthAgo)
            .build();
        repository.save(meta, "# Fresh Content");

        SkillDocument doc = repository.findByName("fresh-skill").orElseThrow();
        String decorated = stalenessService.decorateBody(doc);

        assertThat(decorated).isEqualTo("# Fresh Content");
    }

    @Test
    void shouldUseConfiguredThreshold() {
        StalenessService customService = new StalenessService(repository, 3);
        Instant fourMonthsAgo = Instant.now().minus(120, ChronoUnit.DAYS);
        SkillMeta meta = SkillMeta.builder()
            .name("threshold-skill")
            .type(SkillType.KNOWLEDGE)
            .domain("test")
            .visibility(Visibility.parse("public"))
            .lastUpdated(fourMonthsAgo)
            .build();
        repository.save(meta, "# Threshold Test");

        customService.scanForStaleSkills();

        SkillDocument updated = repository.findByName("threshold-skill").orElseThrow();
        assertThat(updated.getMeta().getStale()).isTrue();
    }

    @Test
    void shouldClearStaleWhenSkillIsUpdated() {
        Instant sevenMonthsAgo = Instant.now().minus(210, ChronoUnit.DAYS);
        SkillMeta meta = SkillMeta.builder()
            .name("cleared-stale")
            .type(SkillType.KNOWLEDGE)
            .domain("test")
            .visibility(Visibility.parse("public"))
            .lastUpdated(sevenMonthsAgo)
            .stale(true)
            .build();
        repository.save(meta, "# Old");

        // Simulate update: set recent lastUpdated and clear stale
        meta.setLastUpdated(Instant.now());
        meta.setStale(null);
        repository.save(meta, "# Updated");

        stalenessService.scanForStaleSkills();

        SkillDocument updated = repository.findByName("cleared-stale").orElseThrow();
        assertThat(updated.getMeta().getStale()).isNull();
    }
}
