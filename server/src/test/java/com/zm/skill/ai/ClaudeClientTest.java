package com.zm.skill.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;

class ClaudeClientTest {

    private ClaudeClient claudeClient;
    private MockRestServiceServer mockServer;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        AiModelConfig config = new AiModelConfig();
        config.setApiKey("test-api-key");
        config.setBaseUrl("https://api.anthropic.com");

        AiModelConfig.Models models = new AiModelConfig.Models();
        models.setSummarize("claude-haiku-4-5-20251001");
        models.setCluster("claude-sonnet-4-6");
        models.setGenerate("claude-sonnet-4-6");
        models.setValidate("claude-sonnet-4-6");
        models.setDedup("claude-haiku-4-5-20251001");
        config.setModels(models);

        RestTemplate restTemplate = new RestTemplate();
        mockServer = MockRestServiceServer.createServer(restTemplate);
        claudeClient = new ClaudeClient(config, restTemplate);
    }

    @Test
    void shouldCallAnthropicMessagesApi() throws Exception {
        String responseBody = """
            {
                "id": "msg_123",
                "type": "message",
                "role": "assistant",
                "content": [{"type": "text", "text": "Hello from Claude"}],
                "model": "claude-haiku-4-5-20251001",
                "stop_reason": "end_turn"
            }
            """;

        mockServer.expect(requestTo("https://api.anthropic.com/v1/messages"))
            .andExpect(method(HttpMethod.POST))
            .andExpect(header("x-api-key", "test-api-key"))
            .andExpect(header("anthropic-version", "2023-06-01"))
            .andExpect(content().contentType(MediaType.APPLICATION_JSON))
            .andRespond(withSuccess(responseBody, MediaType.APPLICATION_JSON));

        String result = claudeClient.call("claude-haiku-4-5-20251001", "You are helpful.", "Say hello");

        assertThat(result).isEqualTo("Hello from Claude");
        mockServer.verify();
    }

    @Test
    void shouldSendCorrectRequestBody() throws Exception {
        String responseBody = """
            {
                "id": "msg_456",
                "type": "message",
                "role": "assistant",
                "content": [{"type": "text", "text": "response"}],
                "model": "claude-sonnet-4-6",
                "stop_reason": "end_turn"
            }
            """;

        mockServer.expect(requestTo("https://api.anthropic.com/v1/messages"))
            .andExpect(method(HttpMethod.POST))
            .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
            .andExpect(request -> {
                String body = request.getBody().toString();
                JsonNode json = objectMapper.readTree(body);
                assertThat(json.get("model").asText()).isEqualTo("claude-sonnet-4-6");
                assertThat(json.get("system").asText()).isEqualTo("System prompt");
                assertThat(json.get("messages").get(0).get("role").asText()).isEqualTo("user");
                assertThat(json.get("messages").get(0).get("content").asText()).isEqualTo("User message");
                assertThat(json.get("max_tokens").asInt()).isGreaterThan(0);
            })
            .andRespond(withSuccess(responseBody, MediaType.APPLICATION_JSON));

        claudeClient.call("claude-sonnet-4-6", "System prompt", "User message");
        mockServer.verify();
    }

    @Test
    void shouldUseSummarizeConvenienceMethod() throws Exception {
        String responseBody = """
            {
                "id": "msg_789",
                "type": "message",
                "role": "assistant",
                "content": [{"type": "text", "text": "Summary result"}],
                "model": "claude-haiku-4-5-20251001",
                "stop_reason": "end_turn"
            }
            """;

        mockServer.expect(requestTo("https://api.anthropic.com/v1/messages"))
            .andRespond(withSuccess(responseBody, MediaType.APPLICATION_JSON));

        String result = claudeClient.summarize("Some document text to summarize");
        assertThat(result).isEqualTo("Summary result");
        mockServer.verify();
    }

    @Test
    void shouldUseClusterConvenienceMethod() throws Exception {
        String responseBody = """
            {
                "id": "msg_abc",
                "type": "message",
                "role": "assistant",
                "content": [{"type": "text", "text": "Cluster result"}],
                "model": "claude-sonnet-4-6",
                "stop_reason": "end_turn"
            }
            """;

        mockServer.expect(requestTo("https://api.anthropic.com/v1/messages"))
            .andRespond(withSuccess(responseBody, MediaType.APPLICATION_JSON));

        String result = claudeClient.cluster("Documents to cluster");
        assertThat(result).isEqualTo("Cluster result");
        mockServer.verify();
    }

    @Test
    void shouldUseGenerateConvenienceMethod() throws Exception {
        String responseBody = """
            {
                "id": "msg_def",
                "type": "message",
                "role": "assistant",
                "content": [{"type": "text", "text": "Generated content"}],
                "model": "claude-sonnet-4-6",
                "stop_reason": "end_turn"
            }
            """;

        mockServer.expect(requestTo("https://api.anthropic.com/v1/messages"))
            .andRespond(withSuccess(responseBody, MediaType.APPLICATION_JSON));

        String result = claudeClient.generate("Generate prompt");
        assertThat(result).isEqualTo("Generated content");
        mockServer.verify();
    }

    @Test
    void shouldUseValidateConvenienceMethod() throws Exception {
        String responseBody = """
            {
                "id": "msg_ghi",
                "type": "message",
                "role": "assistant",
                "content": [{"type": "text", "text": "Validation result"}],
                "model": "claude-sonnet-4-6",
                "stop_reason": "end_turn"
            }
            """;

        mockServer.expect(requestTo("https://api.anthropic.com/v1/messages"))
            .andRespond(withSuccess(responseBody, MediaType.APPLICATION_JSON));

        String result = claudeClient.validate("Validate prompt");
        assertThat(result).isEqualTo("Validation result");
        mockServer.verify();
    }

    @Test
    void shouldHandleApiError() {
        mockServer.expect(requestTo("https://api.anthropic.com/v1/messages"))
            .andRespond(withServerError());

        assertThatThrownBy(() -> claudeClient.call("claude-haiku-4-5-20251001", "sys", "msg"))
            .isInstanceOf(RuntimeException.class);
        mockServer.verify();
    }
}
