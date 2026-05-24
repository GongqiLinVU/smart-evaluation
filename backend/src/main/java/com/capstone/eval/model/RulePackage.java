package com.capstone.eval.model;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "rule_package")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RulePackage {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String name;

    @Lob
    @Column(columnDefinition = "TEXT")
    private String description;

    @Builder.Default
    @Column(nullable = false)
    private Boolean isDefault = false;

    @Column(nullable = false)
    private Double logicWeight;

    @Column(nullable = false)
    private Double methodologyWeight;

    @Column(nullable = false)
    private Double implementationWeight;

    @Column(nullable = false)
    @Builder.Default
    private Boolean logicEnabled = true;

    @Column(nullable = false)
    @Builder.Default
    private Boolean methodologyEnabled = true;

    @Column(nullable = false)
    @Builder.Default
    private Boolean implementationEnabled = true;

    @OneToMany(mappedBy = "rulePackage", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<RulePackageItem> items = new ArrayList<>();

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
