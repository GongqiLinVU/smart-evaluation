package com.capstone.eval.service;

import com.capstone.eval.exception.DocumentParseException;
import com.capstone.eval.model.ParsedDocumentEntity;
import com.capstone.eval.model.Submission;
import com.capstone.eval.parser.DocxParser;
import com.capstone.eval.parser.ParsedDocument;
import com.capstone.eval.repository.ParsedDocumentRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;

import java.io.FileInputStream;
import java.io.InputStream;
import java.nio.file.Path;
import java.nio.file.Paths;

@Service
public class DocumentParserService {

    private final DocxParser docxParser;
    private final ParsedDocumentRepository parsedDocumentRepository;
    private final ObjectMapper objectMapper;

    public DocumentParserService(DocxParser docxParser,
                                 ParsedDocumentRepository parsedDocumentRepository,
                                 ObjectMapper objectMapper) {
        this.docxParser = docxParser;
        this.parsedDocumentRepository = parsedDocumentRepository;
        this.objectMapper = objectMapper;
    }

    /**
     * Parse the document referenced by the given submission, persist the parsed
     * structure, and return the in-memory {@link ParsedDocument}.
     *
     * @param submission the submission whose file should be parsed
     * @return the parsed document model
     */
    public ParsedDocument parseAndStore(Submission submission) {
        Path filePath = Paths.get(submission.getFilePath());

        // 1. Parse the .docx file
        ParsedDocument parsedDocument;
        try (InputStream inputStream = new FileInputStream(filePath.toFile())) {
            parsedDocument = docxParser.parse(inputStream);
        } catch (DocumentParseException dpe) {
            throw dpe;
        } catch (Exception e) {
            throw new DocumentParseException("Failed to read file for parsing: " + e.getMessage());
        }

        // 2. Serialize the full parsed structure to JSON for storage
        String documentStructureJson;
        try {
            documentStructureJson = objectMapper.writeValueAsString(parsedDocument);
        } catch (JsonProcessingException e) {
            throw new DocumentParseException("Failed to serialize parsed document to JSON: " + e.getMessage());
        }

        // 3. Build and persist the entity
        ParsedDocumentEntity entity = ParsedDocumentEntity.builder()
                .submission(submission)
                .headingCount(parsedDocument.getHeadingCount())
                .sectionCount(parsedDocument.getSections().size())
                .codeSnippetCount(parsedDocument.getCodeSnippetCount())
                .tableCount(parsedDocument.getTableCount())
                .imageCount(parsedDocument.getImageCount())
                .linkCount(parsedDocument.getLinkCount())
                .totalWordCount(parsedDocument.getTotalWordCount())
                .documentStructure(documentStructureJson)
                .build();

        parsedDocumentRepository.save(entity);

        return parsedDocument;
    }
}
