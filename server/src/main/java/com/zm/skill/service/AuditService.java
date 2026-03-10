package com.zm.skill.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Instant;

/**
 * Audit logging service that writes JSON-lines to an audit.log file.
 */
@Service
public class AuditService {

    private final Path auditLogPath;
    private final ObjectMapper objectMapper;

    public AuditService(@Value("${skill-collector.storage.base-path:./skill-repo}") String basePath) {
        this.auditLogPath = Path.of(basePath).resolve("audit.log");
        this.objectMapper = new ObjectMapper();
    }

    /**
     * Log an audit event.
     *
     * @param action the action performed (e.g. "submit", "confirm", "feedback")
     * @param target the target of the action (e.g. submission ID, skill name)
     * @param detail additional detail about the action
     */
    public void log(String action, String target, String detail) {
        try {
            ObjectNode node = objectMapper.createObjectNode();
            node.put("timestamp", Instant.now().toString());
            node.put("action", action);
            node.put("target", target);
            node.put("detail", detail);
            node.put("ip", resolveClientIp());

            String line = objectMapper.writeValueAsString(node) + "\n";

            Files.createDirectories(auditLogPath.getParent());
            Files.writeString(auditLogPath, line,
                StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (IOException e) {
            // Audit logging should not break the main flow
            // In production, this would go to a secondary logger
        }
    }

    private String resolveClientIp() {
        try {
            ServletRequestAttributes attrs =
                (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
            if (attrs != null) {
                HttpServletRequest request = attrs.getRequest();
                String forwarded = request.getHeader("X-Forwarded-For");
                if (forwarded != null && !forwarded.isBlank()) {
                    return forwarded.split(",")[0].trim();
                }
                return request.getRemoteAddr();
            }
        } catch (Exception ignored) {
            // Not in a request context
        }
        return "system";
    }
}
