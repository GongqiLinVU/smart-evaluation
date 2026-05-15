package com.capstone.eval.model;

import com.capstone.eval.model.enums.PerformanceLevel;
import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "criterion_score")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CriterionScore {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "evaluation_result_id", nullable = false)
    private EvaluationResult evaluationResult;

    @Column(nullable = false)
    private String criterionName;

    @Column(nullable = false)
    private Integer score;

    @Enumerated(EnumType.STRING)
    private PerformanceLevel level;

    @Lob
    @Column(columnDefinition = "TEXT")
    private String justification;

    @Column(columnDefinition = "JSON")
    private String evidence;

    @Column(columnDefinition = "JSON")
    private String subScores;
}
