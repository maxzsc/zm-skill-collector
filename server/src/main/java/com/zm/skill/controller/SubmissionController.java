package com.zm.skill.controller;

import com.zm.skill.controller.dto.*;
import com.zm.skill.domain.DomainCluster;
import com.zm.skill.domain.ProcessingStatus;
import com.zm.skill.domain.Submission;
import com.zm.skill.parser.DocumentParser;
import com.zm.skill.parser.ParserFactory;
import com.zm.skill.service.AuditService;
import com.zm.skill.service.PipelineResult;
import com.zm.skill.service.PipelineService;
import com.zm.skill.service.UrlValidator;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.*;

@RestController
@RequestMapping("/api/submissions")
public class SubmissionController {

    private final PipelineService pipelineService;
    private final ParserFactory parserFactory;
    private final UrlValidator urlValidator;
    private final AuditService auditService;

    // P1-24: File size and batch limits
    private static final long MAX_FILE_SIZE_BYTES = 20L * 1024 * 1024; // 20MB
    private static final int MAX_BATCH_FILES = 50;
    private static final long MAX_BATCH_SIZE_BYTES = 100L * 1024 * 1024; // 100MB

    // Store domain maps for retrieval between submit and confirm
    private final Map<String, List<DomainCluster>> domainMaps = new java.util.concurrent.ConcurrentHashMap<>();

    public SubmissionController(PipelineService pipelineService, ParserFactory parserFactory,
                                UrlValidator urlValidator, AuditService auditService) {
        this.pipelineService = pipelineService;
        this.parserFactory = parserFactory;
        this.urlValidator = urlValidator;
        this.auditService = auditService;
    }

    @PostMapping
    public ResponseEntity<ApiResponse<SubmitResponse>> submit(
            @RequestParam("files") List<MultipartFile> files,
            @RequestParam(value = "description", required = false) String description,
            @RequestParam(value = "seedDomain", required = false) String seedDomain
    ) {
        if (files == null || files.isEmpty()) {
            return ResponseEntity.badRequest()
                    .body(ApiResponse.error(ErrorCode.VALIDATION_FAILED, "At least one file is required"));
        }

        // P1-24: Validate batch limits
        if (files.size() > MAX_BATCH_FILES) {
            return ResponseEntity.badRequest()
                    .body(ApiResponse.error(ErrorCode.BATCH_LIMIT_EXCEEDED,
                        "Too many files: " + files.size() + ". Maximum is " + MAX_BATCH_FILES));
        }

        long totalSize = 0;
        for (MultipartFile file : files) {
            long fileSize = file.getSize();
            if (fileSize > MAX_FILE_SIZE_BYTES) {
                return ResponseEntity.badRequest()
                        .body(ApiResponse.error(ErrorCode.FILE_TOO_LARGE,
                            "File '" + file.getOriginalFilename() + "' exceeds maximum size of 20MB"));
            }
            totalSize += fileSize;
        }
        if (totalSize > MAX_BATCH_SIZE_BYTES) {
            return ResponseEntity.badRequest()
                    .body(ApiResponse.error(ErrorCode.BATCH_LIMIT_EXCEEDED,
                        "Total batch size exceeds maximum of 100MB"));
        }

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

            // P0-10: Compute idempotency key from file contents
            String idempotencyKey = computeIdempotencyKey(documents.values());

            // QA-003: Use atomic getOrCreateSubmission to eliminate race condition
            final Map<String, String> finalDocuments = documents;
            final List<MultipartFile> finalFiles = files;
            final String finalDescription = description;
            final String finalSeedDomain = seedDomain;

            // Track whether we created a new submission or found an existing one
            boolean[] isNew = {false};
            Submission submission = pipelineService.getOrCreateSubmission(idempotencyKey, () -> {
                isNew[0] = true;
                String submissionId = UUID.randomUUID().toString();
                Submission sub = Submission.builder()
                        .id(submissionId)
                        .description(finalDescription)
                        .seedDomain(finalSeedDomain)
                        .status(ProcessingStatus.SUBMITTED)
                        .idempotencyKey(idempotencyKey)
                        .build();
                sub.setFileName(finalFiles.size() == 1 ? finalFiles.get(0).getOriginalFilename() : finalFiles.size() + " files");
                sub.setDocumentPaths(new ArrayList<>(finalDocuments.keySet()));
                return sub;
            });

            if (!isNew[0]) {
                // Existing submission found
                SubmitResponse response = SubmitResponse.builder()
                        .submissionId(submission.getId())
                        .status(submission.getStatus().getValue())
                        .build();
                return ResponseEntity.ok(ApiResponse.ok(response));
            }

            // P1-20: Audit logging
            auditService.log("submit", submission.getId(), submission.getFileName());

            // P0-3: Single file quick path vs multi-file clustering path
            if (files.size() == 1) {
                String fileName = documents.keySet().iterator().next();
                String text = documents.get(fileName);
                PipelineResult result = pipelineService.submitSingle(submission, fileName, text);

                SubmitResponse response = SubmitResponse.builder()
                        .submissionId(submission.getId())
                        .status(submission.getStatus().getValue())
                        .build();

                return ResponseEntity.ok(ApiResponse.ok(response));
            } else {
                List<DomainCluster> clusters = pipelineService.submitAndScan(submission, documents);
                domainMaps.put(submission.getId(), clusters);

                SubmitResponse response = SubmitResponse.builder()
                        .submissionId(submission.getId())
                        .status(submission.getStatus().getValue())
                        .build();

                return ResponseEntity.ok(ApiResponse.ok(response));
            }
        } catch (IOException e) {
            return ResponseEntity.internalServerError()
                    .body(ApiResponse.error(ErrorCode.INTERNAL_ERROR, "Failed to read uploaded file: " + e.getMessage()));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest()
                    .body(ApiResponse.error(ErrorCode.VALIDATION_FAILED, e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.internalServerError()
                    .body(ApiResponse.error(ErrorCode.INTERNAL_ERROR, "Processing failed: " + e.getMessage()));
        }
    }

    @PostMapping("/yuque")
    public ResponseEntity<ApiResponse<SubmitResponse>> submitYuque(
            @Valid @RequestBody YuqueSubmitRequest request
    ) {
        // P0-13: Validate URL against allowlist and reject private IPs
        try {
            urlValidator.validate(request.getUrl());
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest()
                    .body(ApiResponse.error(ErrorCode.INVALID_URL, e.getMessage()));
        }

        return ResponseEntity.status(org.springframework.http.HttpStatus.NOT_IMPLEMENTED)
                .body(ApiResponse.error(ErrorCode.INTERNAL_ERROR, "\u8bed\u96c0\u5bfc\u5165\u529f\u80fd\u5c1a\u672a\u5b9e\u73b0"));
    }

    @GetMapping("/{id}/status")
    public ResponseEntity<ApiResponse<Submission>> getStatus(@PathVariable String id) {
        Submission submission = pipelineService.getSubmission(id);
        if (submission == null) {
            return ResponseEntity.status(404).body(ApiResponse.error(ErrorCode.NOT_FOUND, "Resource not found: " + id));
        }
        return ResponseEntity.ok(ApiResponse.ok(submission));
    }

    @GetMapping("/{id}/domain-map")
    public ResponseEntity<ApiResponse<DomainMapResponse>> getDomainMap(@PathVariable String id) {
        Submission submission = pipelineService.getSubmission(id);
        if (submission == null) {
            return ResponseEntity.status(404).body(ApiResponse.error(ErrorCode.NOT_FOUND, "Resource not found: " + id));
        }

        List<DomainCluster> clusters = domainMaps.get(id);
        if (clusters == null) {
            return ResponseEntity.status(404).body(ApiResponse.error(ErrorCode.NOT_FOUND, "Resource not found: " + id));
        }

        DomainMapResponse response = DomainMapResponse.builder()
                .submissionId(id)
                .clusters(clusters)
                .build();

        return ResponseEntity.ok(ApiResponse.ok(response));
    }

    @PostMapping("/{id}/confirm")
    public ResponseEntity<ApiResponse<SubmitResponse>> confirm(
            @PathVariable String id,
            @Valid @RequestBody ConfirmRequest request
    ) {
        Submission submission = pipelineService.getSubmission(id);
        if (submission == null) {
            return ResponseEntity.status(404).body(ApiResponse.error(ErrorCode.NOT_FOUND, "Resource not found: " + id));
        }

        try {
            // P1-20: Audit logging
            auditService.log("confirm", id, "clusters confirmed");

            // Trigger async generation; client should poll status endpoint
            pipelineService.confirmAndGenerate(id, request.getClusters());
            SubmitResponse response = SubmitResponse.builder()
                    .submissionId(id)
                    .status("generating")
                    .build();
            return ResponseEntity.accepted().body(ApiResponse.ok(response));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest()
                    .body(ApiResponse.error(ErrorCode.VALIDATION_FAILED, e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.internalServerError()
                    .body(ApiResponse.error(ErrorCode.INTERNAL_ERROR, "Generation failed: " + e.getMessage()));
        }
    }

    /**
     * P0-10: Compute SHA-256 hash of concatenated file contents as idempotency key.
     */
    private String computeIdempotencyKey(Collection<String> contents) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            for (String content : contents) {
                digest.update(content.getBytes(StandardCharsets.UTF_8));
            }
            byte[] hash = digest.digest();
            StringBuilder sb = new StringBuilder();
            for (byte b : hash) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 not available", e);
        }
    }
}
