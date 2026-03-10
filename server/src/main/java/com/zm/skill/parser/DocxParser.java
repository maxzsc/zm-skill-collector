package com.zm.skill.parser;

import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;

import java.io.InputStream;
import java.util.stream.Collectors;

/**
 * Parser for DOCX files using Apache POI.
 * Extracts text from all paragraphs.
 */
public class DocxParser implements DocumentParser {

    @Override
    public String parse(String content) {
        // DOCX is binary format; direct string parsing not applicable
        throw new UnsupportedOperationException(
            "DOCX is a binary format. Use parseFile(InputStream) instead.");
    }

    @Override
    public String parseFile(InputStream inputStream) {
        try (XWPFDocument doc = new XWPFDocument(inputStream)) {
            return doc.getParagraphs().stream()
                .map(XWPFParagraph::getText)
                .filter(text -> text != null && !text.isBlank())
                .collect(Collectors.joining("\n\n"));
        } catch (Exception e) {
            throw new RuntimeException("Failed to parse DOCX file", e);
        }
    }
}
