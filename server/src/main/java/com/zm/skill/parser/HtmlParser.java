package com.zm.skill.parser;

import com.vladsch.flexmark.html2md.converter.FlexmarkHtmlConverter;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.stream.Collectors;

/**
 * Parser for HTML files using flexmark's HTML-to-Markdown converter.
 * Converts HTML to clean markdown text.
 */
public class HtmlParser implements DocumentParser {

    private final FlexmarkHtmlConverter converter = FlexmarkHtmlConverter.builder().build();

    @Override
    public String parse(String content) {
        if (content == null || content.isBlank()) {
            return "";
        }
        return converter.convert(content).strip();
    }

    @Override
    public String parseFile(InputStream inputStream) {
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(inputStream, StandardCharsets.UTF_8))) {
            String html = reader.lines().collect(Collectors.joining("\n"));
            return parse(html);
        } catch (Exception e) {
            throw new RuntimeException("Failed to parse HTML file", e);
        }
    }
}
