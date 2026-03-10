package com.zm.skill.service;

import com.zm.skill.domain.*;
import com.zm.skill.parser.ParserFactory;
import com.zm.skill.storage.SkillDocument;
import com.zm.skill.storage.SkillRepository;
import com.zm.skill.storage.GitService;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;
import java.util.stream.Collectors;

/**
 * Orchestrates the full skill processing pipeline.
 *
 * Phase 1: submitAndScan - parse, classify, cluster documents -> DomainMap for confirmation
 * Phase 2: confirmAndGenerate - generate, validate, dedup, save skills
 * Quick path: submitSingle - single document end-to-end
 */
@Service
public class PipelineService {

    private final ClassificationService classificationService;
    private final ClusteringService clusteringService;
    private final SkillGenerationService generationService;
    private final ValidationService validationService;
    private final DeduplicationService deduplicationService;
    private final SkillRepository skillRepository;
    private final GitService gitService;
    private final ParserFactory parserFactory;
    private final SkillUpdateService skillUpdateService;

    // In-memory submission store for tracking status
    private final Map<String, Submission> submissions = new ConcurrentHashMap<>();
    // Store documents associated with submissions
    private final Map<String, Map<String, String>> submissionDocuments = new ConcurrentHashMap<>();

    // P0-9: Domain-level concurrency locks
    private final ConcurrentHashMap<String, ReentrantLock> domainLocks = new ConcurrentHashMap<>();

    public PipelineService(
        ClassificationService classificationService,
        ClusteringService clusteringService,
        SkillGenerationService generationService,
        ValidationService validationService,
        DeduplicationService deduplicationService,
        SkillRepository skillRepository,
        GitService gitService,
        ParserFactory parserFactory,
        SkillUpdateService skillUpdateService
    ) {
        this.classificationService = classificationService;
        this.clusteringService = clusteringService;
        this.generationService = generationService;
        this.validationService = validationService;
        this.deduplicationService = deduplicationService;
        this.skillRepository = skillRepository;
        this.gitService = gitService;
        this.parserFactory = parserFactory;
        this.skillUpdateService = skillUpdateService;
    }

    /**
     * Phase 1: Submit documents and scan/classify/cluster them.
     *
     * @param submission the submission metadata
     * @param documents  map of filename -> document text
     * @return list of domain clusters for user confirmation
     */
    public List<DomainCluster> submitAndScan(Submission submission, Map<String, String> documents) {
        submissions.put(submission.getId(), submission);
        submissionDocuments.put(submission.getId(), new HashMap<>(documents));

        try {
            // Parse documents
            updateStatus(submission, ProcessingStatus.PARSING);

            // Classify each document
            updateStatus(submission, ProcessingStatus.CLASSIFYING);
            Map<String, ClassificationService.ClassificationResult> classifications = new HashMap<>();
            for (Map.Entry<String, String> entry : documents.entrySet()) {
                ClassificationService.ClassificationResult classification =
                    classificationService.classify(entry.getValue());
                classifications.put(entry.getKey(), classification);
            }

            // Build summaries for clustering
            updateStatus(submission, ProcessingStatus.CLUSTERING);
            Map<String, String> docSummaries = new HashMap<>();
            for (Map.Entry<String, ClassificationService.ClassificationResult> entry : classifications.entrySet()) {
                docSummaries.put(entry.getKey(), entry.getValue().getSummaryPreview());
            }

            // Cluster documents into domains
            List<DomainCluster> clusters = clusteringService.cluster(
                docSummaries, submission.getSeedDomain());

            // Update status to awaiting confirmation
            updateStatus(submission, ProcessingStatus.AWAITING_CONFIRMATION);

            return clusters;
        } catch (Exception e) {
            submission.setStatus(ProcessingStatus.FAILED);
            submission.setErrorMessage(e.getMessage());
            throw new RuntimeException("Pipeline scan failed: " + e.getMessage(), e);
        }
    }

    /**
     * Phase 2: User confirms domain map, generate and save skills.
     *
     * @param submissionId     the submission ID
     * @param confirmedClusters the confirmed domain clusters
     * @return list of pipeline results
     */
    @Async
    public CompletableFuture<List<PipelineResult>> confirmAndGenerate(String submissionId, List<DomainCluster> confirmedClusters) {
        Submission submission = submissions.get(submissionId);
        if (submission == null) {
            throw new IllegalArgumentException("Unknown submission: " + submissionId);
        }

        Map<String, String> documents = submissionDocuments.get(submissionId);
        if (documents == null) {
            throw new IllegalStateException("No documents found for submission: " + submissionId);
        }

        // P1-19: Snapshot documents at confirm time to prevent inconsistency
        // if new docs are submitted between confirm and generate
        Map<String, String> snapshotDocuments = new HashMap<>(documents);

        updateStatus(submission, ProcessingStatus.GENERATING);
        List<PipelineResult> results = new ArrayList<>();
        List<String> failedClusterNames = new ArrayList<>();

        for (DomainCluster cluster : confirmedClusters) {
            try {
                // P0-2: Check if skill already exists for this domain; if so, use update service
                List<SkillDocument> existingSkills = loadExistingSkillsForDomain(cluster.getDomain());
                PipelineResult result;
                if (!existingSkills.isEmpty()) {
                    result = updateExistingSkill(cluster, snapshotDocuments, existingSkills);
                } else {
                    result = generateSkillForCluster(cluster, snapshotDocuments, submission);
                }
                results.add(result);
            } catch (Exception e) {
                // P0-7: Check if error is retryable
                if (isRetryableError(e) && submission.getRetryCount() < Submission.MAX_RETRIES) {
                    submission.setRetryCount(submission.getRetryCount() + 1);
                    // Retry: re-attempt this cluster
                    try {
                        PipelineResult retryResult = generateSkillForCluster(cluster, snapshotDocuments, submission);
                        results.add(retryResult);
                        continue;
                    } catch (Exception retryEx) {
                        // Fall through to failure handling
                    }
                }
                failedClusterNames.add(cluster.getDomain());
                results.add(PipelineResult.builder()
                    .domain(cluster.getDomain())
                    .success(false)
                    .errorMessage(e.getMessage())
                    .build());
            }
        }

        // Progress through validation and dedup states
        updateStatus(submission, ProcessingStatus.VALIDATING);
        updateStatus(submission, ProcessingStatus.DEDUP_CHECK);

        // Update final status
        boolean allSuccess = results.stream().allMatch(PipelineResult::isSuccess);
        boolean anySuccess = results.stream().anyMatch(PipelineResult::isSuccess);
        if (allSuccess) {
            updateStatus(submission, ProcessingStatus.COMPLETED);
            gitService.commitAll("skill: generate/update skills");
        } else if (anySuccess) {
            // P0-8 / P1-21: Partial success - store failed cluster names
            updateStatus(submission, ProcessingStatus.PARTIALLY_COMPLETED);
            submission.setErrorMessage("Failed clusters: " + String.join(", ", failedClusterNames));
            gitService.commitAll("skill: generate/update skills (partial)");
        } else {
            submission.setStatus(ProcessingStatus.FAILED);
            submission.setErrorMessage("All clusters failed to generate: " + String.join(", ", failedClusterNames));
        }

        return CompletableFuture.completedFuture(results);
    }

    /**
     * Quick path: single document end-to-end processing.
     */
    public PipelineResult submitSingle(Submission submission, String fileName, String documentText) {
        submissions.put(submission.getId(), submission);

        try {
            // Parse
            updateStatus(submission, ProcessingStatus.PARSING);

            // Classify
            updateStatus(submission, ProcessingStatus.CLASSIFYING);
            ClassificationService.ClassificationResult classification =
                classificationService.classify(documentText);

            // Generate (with domain lock)
            updateStatus(submission, ProcessingStatus.GENERATING);
            String domain = classification.getDomain();
            ReentrantLock lock = getDomainLock(domain);
            lock.lock();
            try {
                SkillDocument skillDoc;
                Visibility visibility = Visibility.parse("public");

                if (classification.getType() == SkillType.PROCEDURE) {
                    skillDoc = generationService.generateProcedure(
                        domain, documentText, visibility);
                } else {
                    skillDoc = generationService.generateKnowledge(
                        domain, List.of(documentText), visibility);
                }

                // Validate
                updateStatus(submission, ProcessingStatus.VALIDATING);
                ValidationService.ValidationResult validationResult = validationService.validate(skillDoc);

                // P0-8: If validation fails, retry once with adjusted prompt; if still fails -> REVIEW_REQUIRED
                if (!validationResult.isValid()) {
                    // Retry generation once
                    if (classification.getType() == SkillType.PROCEDURE) {
                        skillDoc = generationService.generateProcedure(domain, documentText, visibility);
                    } else {
                        skillDoc = generationService.generateKnowledge(domain, List.of(documentText), visibility);
                    }
                    validationResult = validationService.validate(skillDoc);

                    if (!validationResult.isValid()) {
                        submission.setStatus(ProcessingStatus.REVIEW_REQUIRED);
                        submission.setErrorMessage("Validation failed after retry: " + String.join("; ", validationResult.getErrors()));
                        return PipelineResult.builder()
                            .domain(domain)
                            .success(false)
                            .errorMessage(submission.getErrorMessage())
                            .build();
                    }
                }

                // Dedup check
                updateStatus(submission, ProcessingStatus.DEDUP_CHECK);
                List<SkillDocument> existingSkills = loadExistingSkillsForDomain(domain);
                DeduplicationService.DedupResult dedupResult =
                    deduplicationService.checkDuplicate(skillDoc, existingSkills);

                // Save
                skillRepository.save(skillDoc.getMeta(), skillDoc.getBody());
                skillRepository.saveRaw(
                    domain, classification.getType(), fileName, documentText);

                updateStatus(submission, ProcessingStatus.COMPLETED);
                gitService.commitAll("skill: generate/update skills");

                List<String> warnings = new ArrayList<>();
                if (dedupResult.isDuplicate()) {
                    warnings.add(dedupResult.getMergeSuggestion());
                }
                if (!validationResult.isValid()) {
                    warnings.addAll(validationResult.getErrors());
                }

                return PipelineResult.builder()
                    .skillName(skillDoc.getMeta().getName())
                    .domain(domain)
                    .success(true)
                    .skillDocument(skillDoc)
                    .completeness(validationResult.getAutoCompleteness())
                    .validationResult(validationResult)
                    .dedupResult(dedupResult)
                    .warnings(warnings)
                    .build();
            } finally {
                lock.unlock();
            }

        } catch (Exception e) {
            // P0-7: Check if retryable
            if (isRetryableError(e) && submission.getRetryCount() < Submission.MAX_RETRIES) {
                submission.setRetryCount(submission.getRetryCount() + 1);
                submission.setStatus(ProcessingStatus.REVIEW_REQUIRED);
                submission.setErrorMessage("Retryable error: " + e.getMessage());
            } else {
                submission.setStatus(ProcessingStatus.FAILED);
                submission.setErrorMessage(e.getMessage());
            }

            return PipelineResult.builder()
                .success(false)
                .errorMessage(e.getMessage())
                .build();
        }
    }

    /**
     * Get a tracked submission by ID.
     */
    public Submission getSubmission(String submissionId) {
        return submissions.get(submissionId);
    }

    /**
     * P0-10: Find submission by idempotency key.
     */
    public Optional<Submission> findByIdempotencyKey(String key) {
        if (key == null) {
            return Optional.empty();
        }
        return submissions.values().stream()
            .filter(s -> key.equals(s.getIdempotencyKey()))
            .findFirst();
    }

    /**
     * P0-2: Update existing skill using SkillUpdateService instead of generating fresh.
     */
    private PipelineResult updateExistingSkill(
        DomainCluster cluster, Map<String, String> allDocuments, List<SkillDocument> existingSkills
    ) {
        String domain = cluster.getDomain();
        Visibility visibility = Visibility.parse("public");
        ReentrantLock lock = getDomainLock(domain);
        lock.lock();
        try {
            List<String> clusterDocs = cluster.getDocuments().stream()
                .map(allDocuments::get)
                .filter(Objects::nonNull)
                .collect(Collectors.toList());

            if (clusterDocs.isEmpty()) {
                return PipelineResult.builder()
                    .domain(domain)
                    .success(false)
                    .errorMessage("No documents found for cluster: " + domain)
                    .build();
            }

            SkillDocument updatedDoc;
            if (cluster.getSuggestedType() == SkillType.PROCEDURE && clusterDocs.size() == 1) {
                String docName = cluster.getDocuments().get(0);
                updatedDoc = skillUpdateService.updateProcedure(domain, docName, clusterDocs.get(0), visibility);
            } else {
                // For knowledge, add each document via updateKnowledge
                SkillDocument lastDoc = null;
                for (int i = 0; i < clusterDocs.size(); i++) {
                    String docName = cluster.getDocuments().get(i);
                    lastDoc = skillUpdateService.updateKnowledge(domain, docName, clusterDocs.get(i), visibility);
                }
                updatedDoc = lastDoc;
            }

            return PipelineResult.builder()
                .skillName(updatedDoc.getMeta().getName())
                .domain(domain)
                .success(true)
                .skillDocument(updatedDoc)
                .build();
        } finally {
            lock.unlock();
        }
    }

    private PipelineResult generateSkillForCluster(
        DomainCluster cluster, Map<String, String> allDocuments, Submission submission
    ) {
        String domain = cluster.getDomain();
        Visibility visibility = Visibility.parse("public");

        // P0-9: Acquire domain lock
        ReentrantLock lock = getDomainLock(domain);
        lock.lock();
        try {
            // Collect documents for this cluster
            List<String> clusterDocs = cluster.getDocuments().stream()
                .map(allDocuments::get)
                .filter(Objects::nonNull)
                .collect(Collectors.toList());

            if (clusterDocs.isEmpty()) {
                return PipelineResult.builder()
                    .domain(domain)
                    .success(false)
                    .errorMessage("No documents found for cluster: " + domain)
                    .build();
            }

            // Generate skill
            SkillDocument skillDoc;
            if (cluster.getSuggestedType() == SkillType.PROCEDURE && clusterDocs.size() == 1) {
                skillDoc = generationService.generateProcedure(domain, clusterDocs.get(0), visibility);
            } else {
                skillDoc = generationService.generateKnowledge(domain, clusterDocs, visibility);
            }

            // Validate
            ValidationService.ValidationResult validationResult = validationService.validate(skillDoc);

            // Dedup
            List<SkillDocument> existingSkills = loadExistingSkillsForDomain(domain);
            DeduplicationService.DedupResult dedupResult =
                deduplicationService.checkDuplicate(skillDoc, existingSkills);

            // Save
            skillRepository.save(skillDoc.getMeta(), skillDoc.getBody());
            for (String docName : cluster.getDocuments()) {
                String docContent = allDocuments.get(docName);
                if (docContent != null) {
                    SkillType type = cluster.getSuggestedType() != null ? cluster.getSuggestedType() : SkillType.KNOWLEDGE;
                    skillRepository.saveRaw(domain, type, docName, docContent);
                }
            }

            List<String> warnings = new ArrayList<>();
            if (dedupResult.isDuplicate()) {
                warnings.add(dedupResult.getMergeSuggestion());
            }

            return PipelineResult.builder()
                .skillName(skillDoc.getMeta().getName())
                .domain(domain)
                .success(true)
                .skillDocument(skillDoc)
                .completeness(validationResult.getAutoCompleteness())
                .validationResult(validationResult)
                .dedupResult(dedupResult)
                .warnings(warnings)
                .build();
        } finally {
            lock.unlock();
        }
    }

    private List<SkillDocument> loadExistingSkillsForDomain(String domain) {
        return skillRepository.loadIndex().stream()
            .filter(meta -> domain.equals(meta.getDomain()))
            .map(meta -> skillRepository.findByName(meta.getName()))
            .filter(Optional::isPresent)
            .map(Optional::get)
            .collect(Collectors.toList());
    }

    private void updateStatus(Submission submission, ProcessingStatus newStatus) {
        ProcessingStatus current = submission.getStatus();
        if (!current.canTransitionTo(newStatus)) {
            throw new IllegalStateException(
                "Invalid status transition: " + current.getValue() + " -> " + newStatus.getValue());
        }
        submission.setStatus(newStatus);
        submission.setUpdatedAt(java.time.Instant.now());
    }

    /**
     * P0-9: Get or create a domain-level lock.
     */
    private ReentrantLock getDomainLock(String domain) {
        return domainLocks.computeIfAbsent(domain, k -> new ReentrantLock());
    }

    /**
     * P0-7: Determine if an error is retryable (AI timeout, network error).
     */
    private boolean isRetryableError(Exception e) {
        String msg = e.getMessage();
        if (msg == null) return false;
        String lower = msg.toLowerCase();
        return lower.contains("timeout") || lower.contains("network")
            || lower.contains("connection") || lower.contains("unavailable");
    }
}
