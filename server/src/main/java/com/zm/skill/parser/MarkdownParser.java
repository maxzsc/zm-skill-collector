package com.zm.skill.parser;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.stream.Collectors;

/**
 * Parser for Markdown files. Content is already in text format,
 * so this parser essentially passes through with minor normalization.
 */
public class MarkdownParser implements DocumentParser {

    @Override
    public String parse(String content) {
        if (content == null) {
            return "";
        }
        return content.strip();
    }

    @Override
    public String parseFile(InputStream inputStream) {
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(inputStream, StandardCharsets.UTF_8))) {
            String content = reader.lines().collect(Collectors.joining("\n"));
            return parse(content);
        } catch (Exception e) {
            throw new RuntimeException("Failed to parse markdown file", e);
        }
    }
}
