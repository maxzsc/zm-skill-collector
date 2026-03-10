package com.zm.skill.parser;

import java.io.InputStream;

/**
 * Interface for parsing different document formats into plain text/markdown.
 */
public interface DocumentParser {

    /**
     * Parse text content directly (for text-based formats like markdown, html).
     */
    String parse(String content);

    /**
     * Parse from an input stream (for binary formats like docx, pdf).
     */
    String parseFile(InputStream inputStream);
}
