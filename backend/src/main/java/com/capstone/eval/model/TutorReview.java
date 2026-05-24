package com.capstone.eval.model;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "tutor_review")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TutorReview {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "submission_id", nullable = false)
    private Submission submission;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "tutor_id", nullable = false)
    private User tutor;

    @Column(nullable = false)
    private Integer overallScore;

    @Lob
    @Column(columnDefinition = "TEXT")
    private String overallComment;

    private LocalDateTime reviewedAt;

    @OneToMany(mappedBy = "tutorReview", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<TutorReviewDimension> dimensions = new ArrayList<>();

    @PrePersist
    protected void onCreate() {
        if (reviewedAt == null) {
            reviewedAt = LocalDateTime.now();
        }
    }
}
