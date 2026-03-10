package com.zm.skill.ai;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Configuration properties for AI model routing.
 * Reads from skill-collector.ai.* in application.yml.
 */
@Data
@Component
@ConfigurationProperties(prefix = "skill-collector.ai")
public class AiModelConfig {

    private String apiKey;
    private String baseUrl = "https://api.anthropic.com";
    private Models models = new Models();
    private String preset = "balanced";

    @Data
    public static class Models {
        private String summarize = "claude-haiku-4-5-20251001";
        private String cluster = "claude-sonnet-4-6";
        private String generate = "claude-sonnet-4-6";
        private String validate = "claude-sonnet-4-6";
        private String dedup = "claude-haiku-4-5-20251001";
    }
}
