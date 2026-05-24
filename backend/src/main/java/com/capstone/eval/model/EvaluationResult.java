package com.capstone.eval.model;

import com.capstone.eval.model.enums.EvaluationMethod;
import com.capstone.eval.model.enums.PerformanceLevel;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "evaluation_result")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class EvaluationResult {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "submission_id", nullable = false)
    private Submission submission;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private EvaluationMethod method;

    @Column(nullable = false)
    private Integer overallScore;

    @Enumerated(EnumType.STRING)
    private PerformanceLevel overallLevel;

    @Lob
    @Column(columnDefinition = "TEXT")
    private String overallFeedback;

    @Column(columnDefinition = "JSON")
    private String strengths;

    @Column(columnDefinition = "JSON")
    private String improvements;

    private Double confidence;

    @Lob
    @Column(columnDefinition = "TEXT")
    private String rawLlmResponse;

    private LocalDateTime evaluatedAt;

    @OneToMany(mappedBy = "evaluationResult", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<CriterionScore> criterionScores = new ArrayList<>();

    @OneToMany(mappedBy = "evaluationResult", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<EvaluationRound> rounds = new ArrayList<>();

    @PrePersist
    protected void onCreate() {
        if (evaluatedAt == null) {
            evaluatedAt = LocalDateTime.now();
        }
    }
}
