package com.zm.skill.service;

import org.springframework.stereotype.Component;

import java.util.List;
import java.util.regex.Pattern;

/**
 * Filters sensitive information from text content.
 * Replaces matches with {FILTERED} placeholder.
 *
 * Detects:
 * - IP addresses (with optional ports)
 * - Database connection strings (JDBC, MongoDB)
 * - Credentials and tokens (api_key, password, secret, access_token)
 * - Internal URLs (private IPs, .internal hostnames)
 * - Cloud access keys (AWS keys)
 */
@Component
public class SensitiveInfoFilter {

    private static final String FILTERED = "{FILTERED}";

    private static final List<Pattern> PATTERNS = List.of(
        // Database connection strings (must be before IP pattern)
        Pattern.compile("(?:jdbc:[a-z]+://|mongodb(?:\\+srv)?://)[^\\s]+", Pattern.CASE_INSENSITIVE),

        // Internal URLs with private IPs
        Pattern.compile("https?://(?:10\\.|172\\.(?:1[6-9]|2\\d|3[01])\\.|192\\.168\\.)[^\\s]+", Pattern.CASE_INSENSITIVE),

        // Internal URLs with .internal hostnames
        Pattern.compile("https?://[a-zA-Z0-9.-]*\\.internal[a-zA-Z0-9./-]*[^\\s]*", Pattern.CASE_INSENSITIVE),

        // AWS access key IDs
        Pattern.compile("(?:AWS_ACCESS_KEY_ID|aws_access_key_id)\\s*[=:]\\s*[A-Z0-9]{16,}", Pattern.CASE_INSENSITIVE),

        // AWS secret keys
        Pattern.compile("(?:AWS_SECRET_ACCESS_KEY|aws_secret_access_key)\\s*[=:]\\s*[A-Za-z0-9/+=]{20,}", Pattern.CASE_INSENSITIVE),

        // Generic credentials (api_key, password, secret, token, etc.)
        Pattern.compile("(?:api[_-]?key|apikey|access[_-]?token|password|secret[_-]?key|auth[_-]?token)\\s*[=:]\\s*\\S+", Pattern.CASE_INSENSITIVE),

        // Private IP addresses with optional port (standalone, not in URLs)
        Pattern.compile("(?:10\\.|172\\.(?:1[6-9]|2\\d|3[01])\\.|192\\.168\\.)\\d{1,3}\\.?\\d{0,3}(?::\\d+)?")
    );

    /**
     * Filter sensitive information from the given text.
     *
     * @param text the input text
     * @return the text with sensitive info replaced by {FILTERED}
     */
    public String filter(String text) {
        if (text == null) {
            return null;
        }

        String result = text;
        for (Pattern pattern : PATTERNS) {
            result = pattern.matcher(result).replaceAll(FILTERED);
        }
        return result;
    }
}
