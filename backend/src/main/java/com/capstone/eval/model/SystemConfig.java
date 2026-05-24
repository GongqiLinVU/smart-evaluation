package com.capstone.eval.model;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "system_config")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SystemConfig {

    @Id
    @Column(name = "config_key", nullable = false, unique = true)
    private String key;

    @Column(name = "config_value", nullable = false)
    private String value;

    private String description;

    private LocalDateTime updatedAt;

    @PrePersist
    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
