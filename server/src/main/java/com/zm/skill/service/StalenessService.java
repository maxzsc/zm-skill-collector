package com.zm.skill.service;

import com.zm.skill.domain.SkillMeta;
import com.zm.skill.storage.GitService;
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
    private final GitService gitService;
    private final AuditService auditService;
    private final int thresholdMonths;

    public StalenessService(
        SkillRepository skillRepository,
        GitService gitService,
        AuditService auditService,
        @Value("${skill-collector.staleness.threshold-months:6}") int thresholdMonths
    ) {
        this.skillRepository = skillRepository;
        this.gitService = gitService;
        this.auditService = auditService;
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
        int updatedCount = 0;

        for (SkillMeta meta : allSkills) {
            var docOpt = skillRepository.findByName(meta.getName());
            if (docOpt.isPresent()) {
                SkillDocument doc = docOpt.get();
                boolean isStale = isStale(doc.getMeta(), threshold);
                if (isStale && !Boolean.TRUE.equals(doc.getMeta().getStale())) {
                    doc.getMeta().setStale(true);
                    skillRepository.save(doc.getMeta(), doc.getBody());
                    updatedCount++;
                } else if (!isStale && Boolean.TRUE.equals(doc.getMeta().getStale())) {
                    doc.getMeta().setStale(null);
                    skillRepository.save(doc.getMeta(), doc.getBody());
                    updatedCount++;
                }
            }
        }

        // P1-20: Audit logging
        auditService.log("staleness_scan", "system", updatedCount + " skills updated");

        gitService.commitAll("lifecycle: update staleness markers");
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
            "\u26a0 \u6b64 skill \u6700\u540e\u66f4\u65b0\u4e8e %d \u4e2a\u6708\u524d\uff0c\u5185\u5bb9\u53ef\u80fd\u5df2\u8fc7\u65f6\uff0c\u8bf7\u6ce8\u610f\u9a8c\u8bc1\n\n",
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
