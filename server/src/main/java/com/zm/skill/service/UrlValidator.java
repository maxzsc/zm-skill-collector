package com.zm.skill.service;

import org.springframework.stereotype.Component;

import java.net.InetAddress;
import java.net.URI;
import java.net.UnknownHostException;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Validates URLs against an allowlist and rejects private/internal IPs (SSRF protection).
 */
@Component
public class UrlValidator {

    private static final List<String> ALLOWED_DOMAINS = List.of(
        "yuque.com",
        "github.com",
        "gitlab.com"
    );

    private static final List<Pattern> PRIVATE_IP_PATTERNS = List.of(
        Pattern.compile("^10\\..*"),
        Pattern.compile("^172\\.(1[6-9]|2\\d|3[01])\\..*"),
        Pattern.compile("^192\\.168\\..*"),
        Pattern.compile("^127\\..*"),
        Pattern.compile("^0\\..*"),
        Pattern.compile("^localhost$", Pattern.CASE_INSENSITIVE)
    );

    /**
     * Validate that a URL is allowed (matches domain allowlist and is not a private IP).
     *
     * @param url the URL to validate
     * @throws IllegalArgumentException if the URL is not allowed
     */
    public void validate(String url) {
        if (url == null || url.isBlank()) {
            throw new IllegalArgumentException("URL cannot be null or blank");
        }

        URI uri;
        try {
            uri = URI.create(url);
        } catch (Exception e) {
            throw new IllegalArgumentException("Invalid URL format: " + url);
        }

        String scheme = uri.getScheme();
        if (scheme == null || (!scheme.equals("http") && !scheme.equals("https"))) {
            throw new IllegalArgumentException("URL must use http or https scheme: " + url);
        }

        String host = uri.getHost();
        if (host == null || host.isBlank()) {
            throw new IllegalArgumentException("URL must have a valid host: " + url);
        }

        // Check for private IPs
        if (isPrivateHost(host)) {
            throw new IllegalArgumentException("URL points to a private/internal address: " + url);
        }

        // Check against allowed domains
        if (!isAllowedDomain(host)) {
            throw new IllegalArgumentException(
                "URL domain not in allowlist. Allowed: " + ALLOWED_DOMAINS + ". Got: " + host);
        }
    }

    private boolean isAllowedDomain(String host) {
        String lowerHost = host.toLowerCase();
        for (String allowed : ALLOWED_DOMAINS) {
            if (lowerHost.equals(allowed) || lowerHost.endsWith("." + allowed)) {
                return true;
            }
        }
        return false;
    }

    private boolean isPrivateHost(String host) {
        for (Pattern pattern : PRIVATE_IP_PATTERNS) {
            if (pattern.matcher(host).matches()) {
                return true;
            }
        }

        // Try to resolve hostname and check if it resolves to a private IP
        try {
            InetAddress addr = InetAddress.getByName(host);
            return addr.isLoopbackAddress() || addr.isSiteLocalAddress() || addr.isLinkLocalAddress();
        } catch (UnknownHostException e) {
            // If we can't resolve, let it through (will fail at connection time)
            return false;
        }
    }
}
