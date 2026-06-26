package com.capstone.eval.model;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "rule_package_item",
        uniqueConstraints = @UniqueConstraint(columnNames = {"rule_package_id", "rule_id"}))
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RulePackageItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "rule_package_id", nullable = false)
    private RulePackage rulePackage;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "rule_id", nullable = false)
    private Rule rule;

    @Column(nullable = false)
    @Builder.Default
    private Boolean enabled = true;

    @Column(nullable = false)
    @Builder.Default
    private Double weight = 1.0;

    private Integer maxPoints;

    @Lob
    @Column(columnDefinition = "TEXT")
    private String evidenceQuestions;

    @Lob
    @Column(columnDefinition = "TEXT")
    private String scoringRules;
}
