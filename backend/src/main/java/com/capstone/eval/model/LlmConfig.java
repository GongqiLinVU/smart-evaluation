package com.capstone.eval.model;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "llm_config")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class LlmConfig {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String name;

    @Lob
    @Column(columnDefinition = "TEXT")
    private String systemPromptTemplate;

    @Lob
    @Column(columnDefinition = "TEXT")
    private String additionalContext;

    @Lob
    @Column(columnDefinition = "TEXT")
    private String outputFormatTemplate;

    private Double temperature;

    private Integer maxTokens;

    private String provider;

    private String model;

    @Builder.Default
    @Column(nullable = false)
    private Boolean isDefault = false;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
