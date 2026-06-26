package com.capstone.eval.parser;

import com.capstone.eval.exception.DocumentParseException;
import org.apache.poi.xwpf.usermodel.*;
import org.openxmlformats.schemas.drawingml.x2006.wordprocessingDrawing.CTInline;
import org.openxmlformats.schemas.drawingml.x2006.wordprocessingDrawing.CTAnchor;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTDrawing;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTR;
import org.springframework.stereotype.Component;

import java.io.InputStream;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Component
public class DocxParser {

    /**
     * Technical terms dictionary for vocabulary density calculation.
     */
    private static final Set<String> TECHNICAL_TERMS = Set.of(
            "api", "endpoint", "database", "query", "function", "method", "class",
            "module", "framework", "architecture", "algorithm", "parameter",
            "variable", "interface", "implementation", "deployment", "server",
            "client", "request", "response", "authentication", "authorization",
            "cache", "session", "token", "middleware", "controller", "service",
            "repository", "model", "schema", "table", "column", "index",
            "foreign key", "primary key", "sql", "nosql", "rest", "http",
            "json", "xml", "html", "css", "javascript", "python", "java",
            "docker", "kubernetes", "ci/cd", "pipeline", "git", "branch",
            "commit", "merge", "etl", "scraping", "parsing", "regex",
            "component", "route", "state", "hook", "callback", "async",
            "synchronous", "thread", "process", "memory", "cpu", "latency",
            "throughput", "scalability", "microservice", "monolith",
            "dependency", "injection", "pattern", "factory", "singleton",
            "observer", "mvc", "layer", "tier", "protocol", "socket",
            "encryption", "hashing", "ssl", "tls", "cors", "csrf",
            "unit test", "integration test", "coverage", "assertion",
            "mock", "stub", "fixture", "migration", "orm", "jpa",
            "hibernate", "spring", "fastapi", "streamlit", "flask", "django",
            "react", "angular", "vue", "node", "npm", "webpack", "vite"
    );

    /**
     * Font families that indicate monospaced / code content.
     */
    private static final Set<String> CODE_FONTS = Set.of(
            "Courier New", "Consolas", "Monaco", "Menlo", "monospace",
            "Courier", "Source Code Pro", "Fira Code"
    );

    /**
     * Regex for detecting URLs in paragraph text.
     */
    private static final Pattern URL_PATTERN =
            Pattern.compile("https?://[\\w\\-._~:/?#\\[\\]@!$&'()*+,;=%]+", Pattern.CASE_INSENSITIVE);

    /**
     * Heading style prefixes recognised by the parser (case-insensitive comparison).
     */
    private static final Pattern HEADING_STYLE_PATTERN =
            Pattern.compile("^(?:Heading|heading)(\\d)$", Pattern.CASE_INSENSITIVE);

    /**
     * Parse a .docx input stream into a {@link ParsedDocument}.
     */
    public ParsedDocument parse(InputStream inputStream) {
        try (XWPFDocument document = new XWPFDocument(inputStream)) {
            return doParse(document);
        } catch (DocumentParseException dpe) {
            throw dpe;
        } catch (Exception e) {
            throw new DocumentParseException("Failed to parse DOCX document: " + e.getMessage());
        }
    }

    // ------------------------------------------------------------------ core
    private ParsedDocument doParse(XWPFDocument document) {

        List<XWPFParagraph> paragraphs = document.getParagraphs();

        // ----- 1. Collect raw section boundaries (heading indices) ----------
        List<SectionBoundary> boundaries = new ArrayList<>();
        for (int i = 0; i < paragraphs.size(); i++) {
            XWPFParagraph p = paragraphs.get(i);
            int level = detectHeadingLevel(p);
            if (level > 0) {
                boundaries.add(new SectionBoundary(i, level, p.getText().trim()));
            }
        }

        // ----- 2. Build sections -------------------------------------------
        List<DocumentSection> sections = new ArrayList<>();
        for (int b = 0; b < boundaries.size(); b++) {
            SectionBoundary current = boundaries.get(b);
            int startIdx = current.paragraphIndex + 1; // paragraphs after the heading
            int endIdx = (b + 1 < boundaries.size()) ? boundaries.get(b + 1).paragraphIndex : paragraphs.size();

            StringBuilder contentBuilder = new StringBuilder();
            List<String> codeSnippets = new ArrayList<>();
            int sectionImages = 0;
            StringBuilder currentCodeBlock = new StringBuilder();
            boolean inCodeBlock = false;

            List<String> sectionImageDescs = new ArrayList<>();

            for (int i = startIdx; i < endIdx; i++) {
                XWPFParagraph p = paragraphs.get(i);
                String text = p.getText().trim();

                // Image detection via CTP XML markers
                if (containsImage(p)) {
                    sectionImages++;
                    List<String> descs = extractImageDescriptions(p);
                    sectionImageDescs.addAll(descs);
                }

                boolean isCode = isCodeParagraph(p);
                if (isCode) {
                    if (!inCodeBlock) {
                        inCodeBlock = true;
                        currentCodeBlock.setLength(0);
                    }
                    if (currentCodeBlock.length() > 0) {
                        currentCodeBlock.append("\n");
                    }
                    currentCodeBlock.append(text);
                } else {
                    if (inCodeBlock) {
                        codeSnippets.add(currentCodeBlock.toString());
                        inCodeBlock = false;
                    }
                    if (!text.isEmpty()) {
                        contentBuilder.append(text).append("\n");
                    }
                }
            }
            // Flush trailing code block
            if (inCodeBlock && currentCodeBlock.length() > 0) {
                codeSnippets.add(currentCodeBlock.toString());
            }

            String sectionContent = contentBuilder.toString().trim();
            int wordCount = countWords(sectionContent);
            List<String> techTerms = extractTechnicalTerms(sectionContent);
            double density = (wordCount > 0) ? (double) techTerms.size() / wordCount : 0.0;

            sections.add(DocumentSection.builder()
                    .heading(current.heading)
                    .headingLevel(current.level)
                    .content(sectionContent)
                    .wordCount(wordCount)
                    .codeSnippets(codeSnippets)
                    .imageCount(sectionImages)
                    .imageDescriptions(sectionImageDescs)
                    .tableCount(0) // tables are counted globally below and attributed after
                    .tableContents(new ArrayList<>()) // populated by attributeTableCounts
                    .technicalTerms(techTerms)
                    .technicalVocabularyDensity(Math.round(density * 10000.0) / 10000.0)
                    .build());
        }

        // ----- 3. Global counts --------------------------------------------
        int globalImages = document.getAllPictures().size();
        int globalTables = document.getTables().size();
        List<String> externalLinks = extractLinks(paragraphs);

        int totalCodeSnippets = sections.stream().mapToInt(s -> s.getCodeSnippets().size()).sum();
        int totalWordCount = sections.stream().mapToInt(DocumentSection::getWordCount).sum();

        // Attribute table counts to sections based on body element ordering
        attributeTableCounts(document, boundaries, sections);

        // ----- 4. Detect well-known section presence -----------------------
        boolean hasExecutiveSummary = false;
        boolean hasArchitecture = false;
        boolean hasTesting = false;
        boolean hasConclusion = false;
        boolean hasMethodology = false;
        boolean hasImplementation = false;

        for (DocumentSection s : sections) {
            String lower = s.getHeading().toLowerCase();
            if (lower.contains("executive summary") || lower.contains("abstract") || lower.contains("overview")) {
                hasExecutiveSummary = true;
            }
            if (lower.contains("architecture") || lower.contains("system design") || lower.contains("design")) {
                hasArchitecture = true;
            }
            if (lower.contains("test") || lower.contains("testing") || lower.contains("quality assurance")) {
                hasTesting = true;
            }
            if (lower.contains("conclusion") || lower.contains("summary") || lower.contains("future work")) {
                hasConclusion = true;
            }
            if (lower.contains("methodology") || lower.contains("approach") || lower.contains("method")) {
                hasMethodology = true;
            }
            if (lower.contains("implementation") || lower.contains("development") || lower.contains("coding")) {
                hasImplementation = true;
            }
        }

        return ParsedDocument.builder()
                .sections(sections)
                .totalWordCount(totalWordCount)
                .headingCount(boundaries.size())
                .codeSnippetCount(totalCodeSnippets)
                .tableCount(globalTables)
                .imageCount(globalImages)
                .linkCount(externalLinks.size())
                .externalLinks(externalLinks)
                .hasExecutiveSummary(hasExecutiveSummary)
                .hasArchitectureSection(hasArchitecture)
                .hasTestingSection(hasTesting)
                .hasConclusionSection(hasConclusion)
                .hasMethodologySection(hasMethodology)
                .hasImplementationSection(hasImplementation)
                .build();
    }

    // -------------------------------------------------------- heading detection

    /**
     * Returns the heading level (1-9) if the paragraph is a heading, or 0 if it is not.
     * Checks the Word style name first (e.g. "Heading1", "Heading2").
     * Falls back to outline level from the paragraph properties.
     */
    private int detectHeadingLevel(XWPFParagraph paragraph) {
        String styleName = paragraph.getStyle();
        if (styleName != null) {
            Matcher m = HEADING_STYLE_PATTERN.matcher(styleName);
            if (m.matches()) {
                return Integer.parseInt(m.group(1));
            }
            // Some documents use style ids like "Heading1" with varying case
            String lower = styleName.toLowerCase();
            if (lower.startsWith("heading")) {
                String suffix = lower.substring("heading".length()).trim();
                try {
                    int level = Integer.parseInt(suffix);
                    if (level >= 1 && level <= 9) {
                        return level;
                    }
                } catch (NumberFormatException ignored) {
                    // not a heading style
                }
            }
        }

        // Fallback: check outline level in paragraph properties
        try {
            if (paragraph.getCTP().getPPr() != null && paragraph.getCTP().getPPr().getOutlineLvl() != null) {
                int outlineLevel = paragraph.getCTP().getPPr().getOutlineLvl().getVal().intValue();
                if (outlineLevel >= 0 && outlineLevel <= 8) {
                    return outlineLevel + 1; // outline levels are 0-based
                }
            }
        } catch (Exception ignored) {
            // safe fallback
        }

        return 0;
    }

    // -------------------------------------------------------- code detection

    /**
     * Determine if a paragraph should be treated as code.
     * Checks run fonts first, then falls back to content heuristics.
     */
    private boolean isCodeParagraph(XWPFParagraph paragraph) {
        // Check if any run uses a monospaced / code font
        for (XWPFRun run : paragraph.getRuns()) {
            String fontFamily = run.getFontFamily();
            if (fontFamily != null && CODE_FONTS.contains(fontFamily)) {
                return true;
            }
        }

        // Content-based heuristic
        String text = paragraph.getText().trim();
        return isCodeContent(text);
    }

    /**
     * Heuristic: does the text look like source code?
     */
    private boolean isCodeContent(String text) {
        if (text == null || text.isEmpty()) {
            return false;
        }

        // Common code patterns
        if (text.startsWith("import ") || text.startsWith("from ") || text.startsWith("package ")) {
            return true;
        }
        if (text.startsWith("def ") || text.startsWith("class ") || text.startsWith("function ")) {
            return true;
        }
        if (text.startsWith("public ") || text.startsWith("private ") || text.startsWith("protected ")) {
            return true;
        }
        if (text.startsWith("const ") || text.startsWith("let ") || text.startsWith("var ")) {
            return true;
        }
        if (text.startsWith("return ") || text.startsWith("if (") || text.startsWith("for (") || text.startsWith("while (")) {
            return true;
        }
        // Arrow functions, curly braces, common code endings
        if (text.contains("() =>") || text.contains("=> {") || text.matches(".*\\{\\s*$") || text.equals("}")) {
            return true;
        }
        // Lines that look like assignments: variable = something;
        if (text.matches("^\\s*\\w+\\s*=\\s*.+;\\s*$")) {
            return true;
        }
        // Indented by 4+ spaces (common in code blocks)
        if (text.matches("^\\s{4,}\\S.*")) {
            return true;
        }
        // Lines with typical code markers
        if (text.startsWith("@") && text.length() > 1 && Character.isLetter(text.charAt(1))) {
            return true; // annotations like @Override, @Component
        }
        // SQL-ish
        if (text.toUpperCase().startsWith("SELECT ") || text.toUpperCase().startsWith("INSERT ")
                || text.toUpperCase().startsWith("CREATE TABLE")) {
            return true;
        }
        // Shell commands
        if (text.startsWith("$ ") || text.startsWith("pip install ") || text.startsWith("npm ")) {
            return true;
        }

        return false;
    }

    // -------------------------------------------------------- image detection

    /**
     * Check if a paragraph contains an embedded image by inspecting the underlying XML.
     */
    private boolean containsImage(XWPFParagraph paragraph) {
        String xml = paragraph.getCTP().toString();
        return xml.contains("wp:inline") || xml.contains("wp:anchor");
    }

    /**
     * Extract alt-text, title, and description from images embedded in a paragraph.
     * This provides the LLM with context about what images show without needing vision.
     */
    private List<String> extractImageDescriptions(XWPFParagraph paragraph) {
        List<String> descriptions = new ArrayList<>();
        try {
            for (XWPFRun run : paragraph.getRuns()) {
                CTR ctr = run.getCTR();
                if (ctr == null) continue;
                for (CTDrawing drawing : ctr.getDrawingList()) {
                    // Inline images
                    for (CTInline inline : drawing.getInlineList()) {
                        String desc = buildImageDescription(
                                inline.getDocPr() != null ? inline.getDocPr().getName() : null,
                                inline.getDocPr() != null ? inline.getDocPr().getTitle() : null,
                                inline.getDocPr() != null ? inline.getDocPr().getDescr() : null
                        );
                        if (desc != null) descriptions.add(desc);
                    }
                    // Anchored images
                    for (CTAnchor anchor : drawing.getAnchorList()) {
                        String desc = buildImageDescription(
                                anchor.getDocPr() != null ? anchor.getDocPr().getName() : null,
                                anchor.getDocPr() != null ? anchor.getDocPr().getTitle() : null,
                                anchor.getDocPr() != null ? anchor.getDocPr().getDescr() : null
                        );
                        if (desc != null) descriptions.add(desc);
                    }
                }
            }
        } catch (Exception e) {
            // Graceful fallback — don't fail parsing if image metadata extraction fails
        }
        return descriptions;
    }

    private String buildImageDescription(String name, String title, String descr) {
        StringBuilder sb = new StringBuilder();
        if (title != null && !title.isBlank()) {
            sb.append(title);
        }
        if (descr != null && !descr.isBlank()) {
            if (sb.length() > 0) sb.append(" — ");
            sb.append(descr);
        }
        if (sb.length() == 0 && name != null && !name.isBlank()
                && !name.startsWith("Picture ") && !name.startsWith("Image ")) {
            sb.append(name);
        }
        return sb.length() > 0 ? sb.toString() : null;
    }

    // -------------------------------------------------------- link extraction

    /**
     * Extract all URLs found in paragraph text using a regex pattern.
     */
    private List<String> extractLinks(List<XWPFParagraph> paragraphs) {
        Set<String> links = new LinkedHashSet<>();
        for (XWPFParagraph p : paragraphs) {
            String text = p.getText();
            if (text != null) {
                Matcher matcher = URL_PATTERN.matcher(text);
                while (matcher.find()) {
                    links.add(matcher.group());
                }
            }
        }
        return new ArrayList<>(links);
    }

    // --------------------------------------------------- table attribution

    /**
     * Attribute table counts and content to sections based on body element ordering.
     * Tables that appear between two headings belong to the section started by the first heading.
     */
    private void attributeTableCounts(XWPFDocument document,
                                       List<SectionBoundary> boundaries,
                                       List<DocumentSection> sections) {
        if (sections.isEmpty()) {
            return;
        }

        List<IBodyElement> bodyElements = document.getBodyElements();

        int currentSection = -1;
        int paragraphIndex = 0;

        for (IBodyElement element : bodyElements) {
            if (element instanceof XWPFParagraph) {
                for (int b = 0; b < boundaries.size(); b++) {
                    if (boundaries.get(b).paragraphIndex == paragraphIndex) {
                        currentSection = b;
                        break;
                    }
                }
                paragraphIndex++;
            } else if (element instanceof XWPFTable table) {
                if (currentSection >= 0 && currentSection < sections.size()) {
                    DocumentSection sec = sections.get(currentSection);
                    sec.setTableCount(sec.getTableCount() + 1);
                    String tableText = extractTableContent(table);
                    if (!tableText.isBlank()) {
                        sec.getTableContents().add(tableText);
                    }
                }
            }
        }
    }

    /**
     * Extract table content as a markdown-style text representation.
     * Limits to first 20 rows to avoid oversized content.
     */
    private String extractTableContent(XWPFTable table) {
        List<XWPFTableRow> rows = table.getRows();
        if (rows.isEmpty()) return "";

        StringBuilder sb = new StringBuilder();
        int maxRows = Math.min(rows.size(), 20);

        for (int r = 0; r < maxRows; r++) {
            XWPFTableRow row = rows.get(r);
            List<String> cells = new ArrayList<>();
            for (XWPFTableCell cell : row.getTableCells()) {
                String cellText = cell.getText().replace("\n", " ").replace("\t", " ").trim();
                if (cellText.length() > 100) {
                    cellText = cellText.substring(0, 100) + "...";
                }
                cells.add(cellText);
            }
            sb.append("| ").append(String.join(" | ", cells)).append(" |\n");
            if (r == 0) {
                sb.append("|").append(" --- |".repeat(cells.size())).append("\n");
            }
        }
        if (rows.size() > maxRows) {
            sb.append(String.format("| ... (%d more rows) |\n", rows.size() - maxRows));
        }
        return sb.toString().trim();
    }

    // -------------------------------------------------------- word counting

    /**
     * Count words by splitting on whitespace. Ignores empty tokens.
     */
    private int countWords(String text) {
        if (text == null || text.isBlank()) {
            return 0;
        }
        String[] tokens = text.trim().split("\\s+");
        return tokens.length;
    }

    // ------------------------------------------------- technical term extraction

    /**
     * Extract recognised technical terms from the given text.
     * Multi-word terms are checked first, then single-word terms.
     */
    private List<String> extractTechnicalTerms(String text) {
        if (text == null || text.isBlank()) {
            return Collections.emptyList();
        }

        String lower = text.toLowerCase();
        Set<String> found = new LinkedHashSet<>();

        for (String term : TECHNICAL_TERMS) {
            if (term.contains(" ")) {
                // Multi-word term: search as substring
                if (lower.contains(term)) {
                    found.add(term);
                }
            }
        }

        // Single-word terms: split text into words and check membership
        String[] words = lower.split("[^a-z0-9/]+");
        for (String word : words) {
            if (!word.isEmpty() && TECHNICAL_TERMS.contains(word)) {
                found.add(word);
            }
        }

        return found.stream().sorted().collect(Collectors.toList());
    }

    // -------------------------------------------------------- helper record

    /**
     * Internal record to track heading boundaries while walking paragraphs.
     */
    private static class SectionBoundary {
        final int paragraphIndex;
        final int level;
        final String heading;

        SectionBoundary(int paragraphIndex, int level, String heading) {
            this.paragraphIndex = paragraphIndex;
            this.level = level;
            this.heading = heading;
        }
    }
}
