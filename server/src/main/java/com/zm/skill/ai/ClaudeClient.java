package com.zm.skill.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

/**
 * Client for the Anthropic Claude Messages API.
 * Provides raw call() and convenience methods for different AI tasks.
 */
@Component
public class ClaudeClient {

    private static final String MESSAGES_PATH = "/v1/messages";
    private static final String ANTHROPIC_VERSION = "2023-06-01";
    private static final int DEFAULT_MAX_TOKENS = 4096;

    private final AiModelConfig config;
    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    public ClaudeClient(AiModelConfig config, RestTemplate restTemplate) {
        this.config = config;
        this.restTemplate = restTemplate;
        this.objectMapper = new ObjectMapper();
    }

    /**
     * Call the Anthropic Messages API with the given model, system prompt, and user message.
     *
     * @param model        the model ID (e.g. "claude-sonnet-4-6")
     * @param systemPrompt the system prompt
     * @param userMessage  the user message
     * @return the assistant's text response
     */
    public String call(String model, String systemPrompt, String userMessage) {
        String url = config.getBaseUrl() + MESSAGES_PATH;

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("x-api-key", config.getApiKey());
        headers.set("anthropic-version", ANTHROPIC_VERSION);

        ObjectNode requestBody = objectMapper.createObjectNode();
        requestBody.put("model", model);
        requestBody.put("max_tokens", DEFAULT_MAX_TOKENS);
        requestBody.put("system", systemPrompt);

        ArrayNode messages = requestBody.putArray("messages");
        ObjectNode userMsg = messages.addObject();
        userMsg.put("role", "user");
        userMsg.put("content", userMessage);

        try {
            String requestJson = objectMapper.writeValueAsString(requestBody);
            HttpEntity<String> entity = new HttpEntity<>(requestJson, headers);

            String responseStr = restTemplate.postForObject(url, entity, String.class);
            JsonNode response = objectMapper.readTree(responseStr);

            return response.get("content").get(0).get("text").asText();
        } catch (Exception e) {
            throw new RuntimeException("Claude API call failed: " + e.getMessage(), e);
        }
    }

    /**
     * Summarize content using the fast (haiku) model.
     */
    public String summarize(String content) {
        return call(
            config.getModels().getSummarize(),
            "You are a document summarizer. Provide concise summaries.",
            content
        );
    }

    /**
     * Cluster documents using the cluster model (sonnet).
     */
    public String cluster(String content) {
        return call(
            config.getModels().getCluster(),
            "You are a document clustering assistant. Group related documents into domains.",
            content
        );
    }

    /**
     * Generate skill content using the generate model (sonnet).
     */
    public String generate(String prompt) {
        return call(
            config.getModels().getGenerate(),
            "You are a skill generation assistant. Generate structured skill content.",
            prompt
        );
    }

    /**
     * Validate skill content using the validate model (sonnet).
     */
    public String validate(String prompt) {
        return call(
            config.getModels().getValidate(),
            "You are a skill quality validator. Assess skill completeness and accuracy.",
            prompt
        );
    }
}
