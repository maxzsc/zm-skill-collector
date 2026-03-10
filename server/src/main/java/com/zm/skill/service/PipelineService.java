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

    // In-memory submission store for tracking status
    private final Map<String, Submission> submissions = new ConcurrentHashMap<>();
    // Store documents associated with submissions
    private final Map<String, Map<String, String>> submissionDocuments = new ConcurrentHashMap<>();

    public PipelineService(
        ClassificationService classificationService,
        ClusteringService clusteringService,
        SkillGenerationService generationService,
        ValidationService validationService,
        DeduplicationService deduplicationService,
        SkillRepository skillRepository,
        GitService gitService,
        ParserFactory parserFactory
    ) {
        this.classificationService = classificationService;
        this.clusteringService = clusteringService;
        this.generationService = generationService;
        this.validationService = validationService;
        this.deduplicationService = deduplicationService;
        this.skillRepository = skillRepository;
        this.gitService = gitService;
        this.parserFactory = parserFactory;
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

        updateStatus(submission, ProcessingStatus.GENERATING);
        List<PipelineResult> results = new ArrayList<>();

        for (DomainCluster cluster : confirmedClusters) {
            try {
                PipelineResult result = generateSkillForCluster(cluster, documents, submission);
                results.add(result);
            } catch (Exception e) {
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
        if (allSuccess) {
            updateStatus(submission, ProcessingStatus.COMPLETED);
            gitService.commitAll("skill: generate/update skills");
        } else {
            submission.setStatus(ProcessingStatus.FAILED);
            submission.setErrorMessage("Some skills failed to generate");
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

            // Generate
            updateStatus(submission, ProcessingStatus.GENERATING);
            SkillDocument skillDoc;
            Visibility visibility = Visibility.parse("public");

            if (classification.getType() == SkillType.PROCEDURE) {
                skillDoc = generationService.generateProcedure(
                    classification.getDomain(), documentText, visibility);
            } else {
                skillDoc = generationService.generateKnowledge(
                    classification.getDomain(), List.of(documentText), visibility);
            }

            // Validate
            updateStatus(submission, ProcessingStatus.VALIDATING);
            ValidationService.ValidationResult validationResult = validationService.validate(skillDoc);

            // Dedup check
            updateStatus(submission, ProcessingStatus.DEDUP_CHECK);
            List<SkillDocument> existingSkills = loadExistingSkillsForDomain(classification.getDomain());
            DeduplicationService.DedupResult dedupResult =
                deduplicationService.checkDuplicate(skillDoc, existingSkills);

            // Save
            skillRepository.save(skillDoc.getMeta(), skillDoc.getBody());
            skillRepository.saveRaw(
                classification.getDomain(), classification.getType(), fileName, documentText);

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
                .domain(classification.getDomain())
                .success(true)
                .skillDocument(skillDoc)
                .completeness(validationResult.getAutoCompleteness())
                .validationResult(validationResult)
                .dedupResult(dedupResult)
                .warnings(warnings)
                .build();

        } catch (Exception e) {
            submission.setStatus(ProcessingStatus.FAILED);
            submission.setErrorMessage(e.getMessage());

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

    private PipelineResult generateSkillForCluster(
        DomainCluster cluster, Map<String, String> allDocuments, Submission submission
    ) {
        String domain = cluster.getDomain();
        Visibility visibility = Visibility.parse("public");

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
}
