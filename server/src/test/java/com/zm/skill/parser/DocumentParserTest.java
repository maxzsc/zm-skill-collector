package com.zm.skill.parser;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DocumentParserTest {

    @TempDir
    static Path tempDir;
    static Path docxFile;
    static Path pdfFile;

    @BeforeAll
    static void createFixtures() throws Exception {
        // Create DOCX fixture programmatically
        docxFile = tempDir.resolve("sample.docx");
        try (XWPFDocument doc = new XWPFDocument()) {
            XWPFParagraph title = doc.createParagraph();
            title.createRun().setText("Payment Clearing Rules");
            title.getRuns().get(0).setBold(true);

            XWPFParagraph body = doc.createParagraph();
            body.createRun().setText("The clearing process runs at T+1 settlement.");

            XWPFParagraph body2 = doc.createParagraph();
            body2.createRun().setText("All transactions must be reconciled daily.");

            try (FileOutputStream out = new FileOutputStream(docxFile.toFile())) {
                doc.write(out);
            }
        }

        // Create PDF fixture programmatically
        pdfFile = tempDir.resolve("sample.pdf");
        try (PDDocument doc = new PDDocument()) {
            PDPage page = new PDPage();
            doc.addPage(page);
            try (PDPageContentStream cs = new PDPageContentStream(doc, page)) {
                cs.beginText();
                cs.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 12);
                cs.newLineAtOffset(50, 700);
                cs.showText("Refund Process Guide");
                cs.newLineAtOffset(0, -20);
                cs.showText("Step 1: Verify the refund request.");
                cs.endText();
            }
            doc.save(pdfFile.toFile());
        }
    }

    @Test
    void shouldParseMarkdown() {
        String content = new MarkdownParser().parse("# Title\n\nBody text\n\n- item");
        assertThat(content).contains("Title").contains("Body text").contains("item");
    }

    @Test
    void shouldParseMarkdownPreservesStructure() {
        String input = "# Heading\n\n## Subheading\n\nParagraph with **bold**.\n\n- list item 1\n- list item 2";
        String content = new MarkdownParser().parse(input);
        assertThat(content).contains("Heading");
        assertThat(content).contains("Subheading");
        assertThat(content).contains("list item 1");
    }

    @Test
    void shouldParseHtml() {
        String content = new HtmlParser().parse("<h1>Title</h1><p>Body</p>");
        assertThat(content).contains("Title").contains("Body");
    }

    @Test
    void shouldParseHtmlWithNestedElements() {
        String html = "<html><body><h1>Doc Title</h1><ul><li>Item A</li><li>Item B</li></ul></body></html>";
        String content = new HtmlParser().parse(html);
        assertThat(content).contains("Doc Title").contains("Item A").contains("Item B");
    }

    @Test
    void shouldParseDocxFromStream() throws Exception {
        InputStream is = Files.newInputStream(docxFile);
        String content = new DocxParser().parseFile(is);
        assertThat(content).contains("Payment Clearing Rules");
        assertThat(content).contains("T+1 settlement");
        assertThat(content).contains("reconciled daily");
    }

    @Test
    void shouldParsePdfFromStream() throws Exception {
        InputStream is = Files.newInputStream(pdfFile);
        String content = new PdfParser().parseFile(is);
        assertThat(content).contains("Refund Process Guide");
        assertThat(content).contains("Verify the refund request");
    }

    @Test
    void shouldSelectParserByExtension() {
        ParserFactory f = new ParserFactory();
        assertThat(f.getParser("a.md")).isInstanceOf(MarkdownParser.class);
        assertThat(f.getParser("b.docx")).isInstanceOf(DocxParser.class);
        assertThat(f.getParser("c.pdf")).isInstanceOf(PdfParser.class);
        assertThat(f.getParser("d.html")).isInstanceOf(HtmlParser.class);
        assertThat(f.getParser("e.htm")).isInstanceOf(HtmlParser.class);
    }

    @Test
    void shouldThrowForUnsupportedExtension() {
        ParserFactory f = new ParserFactory();
        assertThatThrownBy(() -> f.getParser("file.xyz"))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("Unsupported");
    }

    @Test
    void shouldHandleEmptyMarkdown() {
        String content = new MarkdownParser().parse("");
        assertThat(content).isEmpty();
    }

    @Test
    void shouldHandleEmptyHtml() {
        String content = new HtmlParser().parse("");
        assertThat(content).isNotNull();
    }
}
