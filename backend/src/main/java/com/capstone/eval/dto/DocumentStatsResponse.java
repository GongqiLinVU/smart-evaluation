package com.capstone.eval.dto;

import com.capstone.eval.model.ParsedDocumentEntity;

import java.time.LocalDateTime;

public record DocumentStatsResponse(
        Integer headingCount,
        Integer sectionCount,
        Integer codeSnippetCount,
        Integer tableCount,
        Integer imageCount,
        Integer linkCount,
        Integer totalWordCount,
        LocalDateTime parsedAt
) {

    public static DocumentStatsResponse fromEntity(ParsedDocumentEntity entity) {
        return new DocumentStatsResponse(
                entity.getHeadingCount(),
                entity.getSectionCount(),
                entity.getCodeSnippetCount(),
                entity.getTableCount(),
                entity.getImageCount(),
                entity.getLinkCount(),
                entity.getTotalWordCount(),
                entity.getParsedAt()
        );
    }
}
