package com.capstone.eval.controller;

import com.capstone.eval.dto.CompositeScoreResponse;
import com.capstone.eval.dto.ScoringWeightsRequest;
import com.capstone.eval.dto.ScoringWeightsResponse;
import com.capstone.eval.service.ScoringConfigService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/scoring")
@RequiredArgsConstructor
public class ScoringConfigController {

    private final ScoringConfigService scoringConfigService;

    @GetMapping("/weights")
    public ResponseEntity<ScoringWeightsResponse> getWeights() {
        return ResponseEntity.ok(scoringConfigService.getWeights());
    }

    @PutMapping("/weights")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ScoringWeightsResponse> updateWeights(
            @Valid @RequestBody ScoringWeightsRequest request) {
        return ResponseEntity.ok(scoringConfigService.updateWeights(request));
    }

    @GetMapping("/composite/{submissionId}")
    public ResponseEntity<CompositeScoreResponse> getCompositeScore(
            @PathVariable Long submissionId) {
        return ResponseEntity.ok(scoringConfigService.computeCompositeScore(submissionId));
    }
}
