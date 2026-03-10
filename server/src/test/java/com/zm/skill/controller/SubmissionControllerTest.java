package com.zm.skill.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.zm.skill.controller.dto.ConfirmRequest;
import com.zm.skill.controller.dto.YuqueSubmitRequest;
import com.zm.skill.domain.DomainCluster;
import com.zm.skill.domain.ProcessingStatus;
import com.zm.skill.domain.SkillType;
import com.zm.skill.domain.Submission;
import com.zm.skill.parser.ParserFactory;
import com.zm.skill.service.PipelineResult;
import com.zm.skill.service.PipelineService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.Map;

import static org.hamcrest.Matchers.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
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

        List<DomainCluster> clusters = List.of(
                DomainCluster.builder()
                        .domain("payment")
                        .confidence(0.9)
                        .documents(List.of("test.md"))
                        .suggestedType(SkillType.KNOWLEDGE)
                        .summaryPreview("Payment clearing")
                        .build()
        );
        when(pipelineService.submitAndScan(any(Submission.class), anyMap())).thenReturn(clusters);

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
