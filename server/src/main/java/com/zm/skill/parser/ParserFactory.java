package com.zm.skill.parser;

import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * Factory that selects the appropriate DocumentParser based on file extension.
 */
@Component
public class ParserFactory {

    private final Map<String, DocumentParser> parsers = Map.of(
        "md", new MarkdownParser(),
        "markdown", new MarkdownParser(),
        "docx", new DocxParser(),
        "pdf", new PdfParser(),
        "html", new HtmlParser(),
        "htm", new HtmlParser()
    );

    /**
     * Get the appropriate parser for a given filename.
     *
     * @param filename the filename (e.g., "document.pdf")
     * @return the appropriate DocumentParser
     * @throws IllegalArgumentException if the file extension is not supported
     */
    public DocumentParser getParser(String filename) {
        String extension = getExtension(filename);
        DocumentParser parser = parsers.get(extension);
        if (parser == null) {
            throw new IllegalArgumentException(
                "Unsupported file extension: ." + extension
                + ". Supported formats: " + parsers.keySet());
        }
        return parser;
    }

    private String getExtension(String filename) {
        int lastDot = filename.lastIndexOf('.');
        if (lastDot < 0) {
            throw new IllegalArgumentException("Filename has no extension: " + filename);
        }
        return filename.substring(lastDot + 1).toLowerCase();
    }
}
