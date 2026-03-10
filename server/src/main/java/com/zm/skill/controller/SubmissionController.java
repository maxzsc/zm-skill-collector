package com.zm.skill.controller;

import com.zm.skill.controller.dto.*;
import com.zm.skill.domain.DomainCluster;
import com.zm.skill.domain.ProcessingStatus;
import com.zm.skill.domain.Submission;
import com.zm.skill.parser.DocumentParser;
import com.zm.skill.parser.ParserFactory;
import com.zm.skill.service.PipelineResult;
import com.zm.skill.service.PipelineService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.*;

@RestController
@RequestMapping("/api/submissions")
public class SubmissionController {

    private final PipelineService pipelineService;
    private final ParserFactory parserFactory;

    // Store domain maps for retrieval between submit and confirm
    private final Map<String, List<DomainCluster>> domainMaps = new LinkedHashMap<>();

    public SubmissionController(PipelineService pipelineService, ParserFactory parserFactory) {
        this.pipelineService = pipelineService;
        this.parserFactory = parserFactory;
    }

    @PostMapping
    public ResponseEntity<ApiResponse<SubmitResponse>> submit(
            @RequestParam("files") List<MultipartFile> files,
            @RequestParam(value = "description", required = false) String description,
            @RequestParam(value = "seedDomain", required = false) String seedDomain
    ) {
        if (files == null || files.isEmpty()) {
            return ResponseEntity.badRequest()
                    .body(ApiResponse.error("At least one file is required"));
        }

        String submissionId = UUID.randomUUID().toString();
        Submission submission = Submission.builder()
                .id(submissionId)
                .description(description)
                .seedDomain(seedDomain)
                .status(ProcessingStatus.SUBMITTED)
                .build();

        try {
            Map<String, String> documents = new LinkedHashMap<>();
            for (MultipartFile file : files) {
                String fileName = file.getOriginalFilename();
                if (fileName == null || fileName.isBlank()) {
                    fileName = "unnamed";
                }
                DocumentParser parser = parserFactory.getParser(fileName);
                String text = parser.parseFile(file.getInputStream());
                documents.put(fileName, text);
            }

            submission.setFileName(files.size() == 1 ? files.get(0).getOriginalFilename() : files.size() + " files");
            submission.setDocumentPaths(new ArrayList<>(documents.keySet()));

            List<DomainCluster> clusters = pipelineService.submitAndScan(submission, documents);
            domainMaps.put(submissionId, clusters);

            SubmitResponse response = SubmitResponse.builder()
                    .submissionId(submissionId)
                    .status(submission.getStatus().getValue())
                    .build();

            return ResponseEntity.ok(ApiResponse.ok(response));
        } catch (IOException e) {
            return ResponseEntity.internalServerError()
                    .body(ApiResponse.error("Failed to read uploaded file: " + e.getMessage()));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest()
                    .body(ApiResponse.error(e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.internalServerError()
                    .body(ApiResponse.error("Processing failed: " + e.getMessage()));
        }
    }

    @PostMapping("/yuque")
    public ResponseEntity<ApiResponse<SubmitResponse>> submitYuque(
            @Valid @RequestBody YuqueSubmitRequest request
    ) {
        String submissionId = UUID.randomUUID().toString();
        Submission submission = Submission.builder()
                .id(submissionId)
                .fileName(request.getUrl())
                .description(request.getDescription())
                .seedDomain(request.getSeedDomain())
                .status(ProcessingStatus.SUBMITTED)
                .build();

        // TODO: Fetch content from Yuque URL. For now return a placeholder.
        SubmitResponse response = SubmitResponse.builder()
                .submissionId(submissionId)
                .status(submission.getStatus().getValue())
                .build();

        return ResponseEntity.ok(ApiResponse.ok(response));
    }

    @GetMapping("/{id}/status")
    public ResponseEntity<ApiResponse<Submission>> getStatus(@PathVariable String id) {
        Submission submission = pipelineService.getSubmission(id);
        if (submission == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(ApiResponse.ok(submission));
    }

    @GetMapping("/{id}/domain-map")
    public ResponseEntity<ApiResponse<DomainMapResponse>> getDomainMap(@PathVariable String id) {
        Submission submission = pipelineService.getSubmission(id);
        if (submission == null) {
            return ResponseEntity.notFound().build();
        }

        List<DomainCluster> clusters = domainMaps.get(id);
        if (clusters == null) {
            return ResponseEntity.notFound().build();
        }

        DomainMapResponse response = DomainMapResponse.builder()
                .submissionId(id)
                .clusters(clusters)
                .build();

        return ResponseEntity.ok(ApiResponse.ok(response));
    }

    @PostMapping("/{id}/confirm")
    public ResponseEntity<ApiResponse<List<PipelineResult>>> confirm(
            @PathVariable String id,
            @Valid @RequestBody ConfirmRequest request
    ) {
        Submission submission = pipelineService.getSubmission(id);
        if (submission == null) {
            return ResponseEntity.notFound().build();
        }

        try {
            List<PipelineResult> results = pipelineService.confirmAndGenerate(id, request.getClusters());
            return ResponseEntity.ok(ApiResponse.ok(results));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest()
                    .body(ApiResponse.error(e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.internalServerError()
                    .body(ApiResponse.error("Generation failed: " + e.getMessage()));
        }
    }
}
