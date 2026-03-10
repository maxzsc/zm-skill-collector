package com.zm.skill.service;

import com.zm.skill.domain.SkillMeta;
import com.zm.skill.storage.SkillDocument;
import com.zm.skill.storage.SkillRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

/**
 * Detects stale skills that haven't been updated within a configurable threshold.
 * Marks them as stale and provides a warning message for display.
 */
@Service
public class StalenessService {

    private final SkillRepository skillRepository;
    private final int thresholdMonths;

    public StalenessService(
        SkillRepository skillRepository,
        @Value("${skill-collector.staleness.threshold-months:6}") int thresholdMonths
    ) {
        this.skillRepository = skillRepository;
        this.thresholdMonths = thresholdMonths;
    }

    /**
     * Scheduled scan that checks all skills for staleness.
     * Runs daily at 2 AM.
     */
    @Scheduled(cron = "0 0 2 * * *")
    public void scanForStaleSkills() {
        List<SkillMeta> allSkills = skillRepository.loadIndex();
        Instant threshold = Instant.now().minus(thresholdMonths * 30L, ChronoUnit.DAYS);

        for (SkillMeta meta : allSkills) {
            skillRepository.findByName(meta.getName()).ifPresent(doc -> {
                boolean isStale = isStale(doc.getMeta(), threshold);
                if (isStale && !Boolean.TRUE.equals(doc.getMeta().getStale())) {
                    doc.getMeta().setStale(true);
                    skillRepository.save(doc.getMeta(), doc.getBody());
                } else if (!isStale && Boolean.TRUE.equals(doc.getMeta().getStale())) {
                    doc.getMeta().setStale(null);
                    skillRepository.save(doc.getMeta(), doc.getBody());
                }
            });
        }
    }

    /**
     * Decorate a skill body with a staleness warning if the skill is stale.
     */
    public String decorateBody(SkillDocument doc) {
        if (!Boolean.TRUE.equals(doc.getMeta().getStale())) {
            return doc.getBody();
        }

        long monthsAgo = computeMonthsAgo(doc.getMeta().getLastUpdated());
        String warning = String.format(
            "⚠ 此 skill 最后更新于 %d 个月前，内容可能已过时，请注意验证\n\n",
            monthsAgo
        );
        return warning + doc.getBody();
    }

    private boolean isStale(SkillMeta meta, Instant threshold) {
        Instant lastUpdated = meta.getLastUpdated();
        if (lastUpdated == null) {
            return true; // No update date means we treat it as stale
        }
        return lastUpdated.isBefore(threshold);
    }

    private long computeMonthsAgo(Instant lastUpdated) {
        if (lastUpdated == null) {
            return thresholdMonths;
        }
        long days = ChronoUnit.DAYS.between(lastUpdated, Instant.now());
        return days / 30;
    }
}
