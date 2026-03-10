package com.zm.skill.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.zm.skill.controller.dto.ConfirmRequest;
import com.zm.skill.controller.dto.YuqueSubmitRequest;
import com.zm.skill.domain.DomainCluster;
import com.zm.skill.domain.ProcessingStatus;
import com.zm.skill.domain.SkillType;
import com.zm.skill.domain.Submission;
import com.zm.skill.parser.ParserFactory;
import com.zm.skill.service.AuditService;
import com.zm.skill.service.PipelineResult;
import com.zm.skill.service.PipelineService;
import com.zm.skill.service.UrlValidator;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.hamcrest.Matchers.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(SubmissionController.class)
class SubmissionControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private PipelineService pipelineService;

    @MockBean
    private ParserFactory parserFactory;

    @MockBean
    private UrlValidator urlValidator;

    @MockBean
    private AuditService auditService;

    @Test
    void submitFiles_shouldReturnSubmissionId() throws Exception {
        // ParserFactory returns a parser that just returns the text as-is
        com.zm.skill.parser.DocumentParser mockParser = new com.zm.skill.parser.DocumentParser() {
            @Override
            public String parse(String content) { return content; }
            @Override
            public String parseFile(java.io.InputStream is) {
                try { return new String(is.readAllBytes()); } catch (Exception e) { return ""; }
            }
        };
        when(parserFactory.getParser(any(String.class))).thenReturn(mockParser);

        // P0-10: findByIdempotencyKey returns empty (no duplicate)
        when(pipelineService.findByIdempotencyKey(anyString())).thenReturn(Optional.empty());

        // P0-3: Single file goes through submitSingle
        PipelineResult singleResult = PipelineResult.builder()
                .skillName("payment-clearing")
                .domain("payment")
                .success(true)
                .build();
        when(pipelineService.submitSingle(any(Submission.class), anyString(), anyString()))
                .thenAnswer(invocation -> {
                    Submission sub = invocation.getArgument(0);
                    sub.setStatus(ProcessingStatus.COMPLETED);
                    return singleResult;
                });

        MockMultipartFile file = new MockMultipartFile(
                "files", "test.md", "text/markdown", "# Payment\n\nClearing rules".getBytes());

        mockMvc.perform(multipart("/api/submissions")
                        .file(file)
                        .param("description", "test upload")
                        .param("seedDomain", "payment"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.submissionId").isNotEmpty())
                .andExpect(jsonPath("$.data.status").isNotEmpty());
    }

    @Test
    void submitMultipleFiles_shouldReturnAwaitingConfirmation() throws Exception {
        com.zm.skill.parser.DocumentParser mockParser = new com.zm.skill.parser.DocumentParser() {
            @Override
            public String parse(String content) { return content; }
            @Override
            public String parseFile(java.io.InputStream is) {
                try { return new String(is.readAllBytes()); } catch (Exception e) { return ""; }
            }
        };
        when(parserFactory.getParser(any(String.class))).thenReturn(mockParser);
        when(pipelineService.findByIdempotencyKey(anyString())).thenReturn(Optional.empty());

        List<DomainCluster> clusters = List.of(
                DomainCluster.builder()
                        .domain("payment")
                        .confidence(0.9)
                        .documents(List.of("test1.md", "test2.md"))
                        .suggestedType(SkillType.KNOWLEDGE)
                        .summaryPreview("Payment clearing")
                        .build()
        );
        when(pipelineService.submitAndScan(any(Submission.class), anyMap())).thenAnswer(invocation -> {
            Submission sub = invocation.getArgument(0);
            sub.setStatus(ProcessingStatus.AWAITING_CONFIRMATION);
            return clusters;
        });

        MockMultipartFile file1 = new MockMultipartFile(
                "files", "test1.md", "text/markdown", "# Payment 1\n\nContent".getBytes());
        MockMultipartFile file2 = new MockMultipartFile(
                "files", "test2.md", "text/markdown", "# Payment 2\n\nContent".getBytes());

        mockMvc.perform(multipart("/api/submissions")
                        .file(file1)
                        .file(file2)
                        .param("description", "test upload"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.submissionId").isNotEmpty())
                .andExpect(jsonPath("$.data.status").value("awaiting_confirmation"));
    }

    @Test
    void submitFiles_noFiles_shouldReturnBadRequest() throws Exception {
        mockMvc.perform(multipart("/api/submissions"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void submitYuque_shouldReturn501NotImplemented() throws Exception {
        YuqueSubmitRequest request = YuqueSubmitRequest.builder()
                .url("https://yuque.com/team/doc")
                .description("yuque doc")
                .seedDomain("ops")
                .build();

        mockMvc.perform(post("/api/submissions/yuque")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isNotImplemented())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error").isNotEmpty());
    }

    @Test
    void submitYuque_noUrl_shouldReturnBadRequest() throws Exception {
        YuqueSubmitRequest request = YuqueSubmitRequest.builder().build();

        mockMvc.perform(post("/api/submissions/yuque")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void getStatus_existingSubmission_shouldReturnStatus() throws Exception {
        Submission submission = Submission.builder()
                .id("sub-1")
                .status(ProcessingStatus.AWAITING_CONFIRMATION)
                .build();
        when(pipelineService.getSubmission("sub-1")).thenReturn(submission);

        mockMvc.perform(get("/api/submissions/sub-1/status"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.id").value("sub-1"))
                .andExpect(jsonPath("$.data.status").value("awaiting_confirmation"));
    }

    @Test
    void getStatus_unknownSubmission_shouldReturn404() throws Exception {
        when(pipelineService.getSubmission("unknown")).thenReturn(null);

        mockMvc.perform(get("/api/submissions/unknown/status"))
                .andExpect(status().isNotFound());
    }

    @Test
    void confirm_shouldReturnAcceptedWithSubmissionId() throws Exception {
        Submission submission = Submission.builder()
                .id("sub-1")
                .status(ProcessingStatus.AWAITING_CONFIRMATION)
                .build();
        when(pipelineService.getSubmission("sub-1")).thenReturn(submission);

        when(pipelineService.confirmAndGenerate(any(String.class), any()))
                .thenReturn(java.util.concurrent.CompletableFuture.completedFuture(List.of(
                        PipelineResult.builder()
                                .skillName("payment-clearing")
                                .domain("payment")
                                .success(true)
                                .build()
                )));

        ConfirmRequest request = ConfirmRequest.builder()
                .clusters(List.of(
                        DomainCluster.builder()
                                .domain("payment")
                                .confidence(0.9)
                                .documents(List.of("test.md"))
                                .suggestedType(SkillType.KNOWLEDGE)
                                .build()
                ))
                .build();

        mockMvc.perform(post("/api/submissions/sub-1/confirm")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.submissionId").value("sub-1"))
                .andExpect(jsonPath("$.data.status").value("generating"));
    }
}
