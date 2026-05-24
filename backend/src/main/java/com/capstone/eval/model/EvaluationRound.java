package com.capstone.eval.model;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "evaluation_round")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class EvaluationRound {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "evaluation_result_id", nullable = false)
    private EvaluationResult evaluationResult;

    @Column(nullable = false)
    private Integer roundNumber;

    @Column(nullable = false)
    private String roundType;

    @Column(columnDefinition = "JSON")
    private String inputSections;

    @Column(columnDefinition = "JSON")
    private String targetCriteria;

    @Lob
    @Column(columnDefinition = "TEXT")
    private String rawResponse;

    private Integer promptTokens;

    private Integer completionTokens;

    @Column(nullable = false)
    private String status;

    private LocalDateTime startedAt;

    private LocalDateTime completedAt;
}
