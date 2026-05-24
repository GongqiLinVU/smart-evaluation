package com.capstone.eval.model;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "tutor_review_dimension")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TutorReviewDimension {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "tutor_review_id", nullable = false)
    private TutorReview tutorReview;

    @Column(nullable = false)
    private String dimensionName;

    @Column(nullable = false)
    private Integer score;

    @Column(nullable = false)
    private Integer maxScore;

    @Lob
    @Column(columnDefinition = "TEXT")
    private String justification;
}
