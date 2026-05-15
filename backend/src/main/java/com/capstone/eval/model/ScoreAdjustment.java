package com.capstone.eval.model;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "score_adjustment")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ScoreAdjustment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "evaluation_id", nullable = false)
    private EvaluationResult evaluation;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "tutor_id", nullable = false)
    private User tutor;

    @Column(nullable = false)
    private Integer originalScore;

    @Column(nullable = false)
    private Integer adjustedScore;

    @Column(columnDefinition = "TEXT")
    private String reason;

    private LocalDateTime adjustedAt;

    @PrePersist
    protected void onCreate() {
        if (adjustedAt == null) {
            adjustedAt = LocalDateTime.now();
        }
    }
}
