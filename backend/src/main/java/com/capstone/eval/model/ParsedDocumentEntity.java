package com.capstone.eval.model;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "parsed_document")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ParsedDocumentEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "submission_id", nullable = false, unique = true)
    private Submission submission;

    private Integer headingCount;

    private Integer sectionCount;

    private Integer codeSnippetCount;

    private Integer tableCount;

    private Integer imageCount;

    private Integer linkCount;

    private Integer totalWordCount;

    @Column(columnDefinition = "JSON")
    private String documentStructure;

    private LocalDateTime parsedAt;

    @PrePersist
    protected void onCreate() {
        if (parsedAt == null) {
            parsedAt = LocalDateTime.now();
        }
    }
}
