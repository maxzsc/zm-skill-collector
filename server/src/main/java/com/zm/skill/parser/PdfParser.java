package com.zm.skill.parser;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;

import java.io.InputStream;

/**
 * Parser for PDF files using Apache PDFBox.
 * Extracts all text content from PDF pages.
 */
public class PdfParser implements DocumentParser {

    @Override
    public String parse(String content) {
        // PDF is binary format; direct string parsing not applicable
        throw new UnsupportedOperationException(
            "PDF is a binary format. Use parseFile(InputStream) instead.");
    }

    @Override
    public String parseFile(InputStream inputStream) {
        try (PDDocument doc = Loader.loadPDF(inputStream.readAllBytes())) {
            PDFTextStripper stripper = new PDFTextStripper();
            return stripper.getText(doc).strip();
        } catch (Exception e) {
            throw new RuntimeException("Failed to parse PDF file", e);
        }
    }
}
