package com.zm.skill.ai.prompts;

/**
 * Prompt template for document classification.
 * Enforces JSON output format.
 */
public final class ClassificationPrompt {

    private ClassificationPrompt() {}

    public static String build(String documentText) {
        return """
            Analyze the following document and classify it. Return ONLY a JSON object with these fields:

            {
                "type": "knowledge" or "procedure",
                "domain": "<domain name in lowercase, e.g. payment, risk, user>",
                "category": "<procedure category if type=procedure: dev/ops/test/biz-operation/analysis/collaboration, null otherwise>",
                "doc_type": "<document type: architecture/api/guide/runbook/faq/meeting-notes/misc>",
                "confidence": <0.0 to 1.0>,
                "summary_preview": "<one-line summary, max 50 chars>"
            }

            Classification rules:
            - "knowledge": factual information, architecture docs, API docs, domain knowledge, FAQs
            - "procedure": step-by-step instructions, runbooks, operational guides, workflows
            - Domain should be a single word describing the business domain
            - Confidence reflects how clearly the document fits the classification

            Document:
            ---
            %s
            ---

            Return ONLY the JSON object, no other text.
            """.formatted(documentText);
    }
}
