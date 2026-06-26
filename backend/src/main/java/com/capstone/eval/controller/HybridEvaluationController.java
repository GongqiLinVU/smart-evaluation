package com.capstone.eval.controller;

import com.capstone.eval.dto.EvidenceQuestionGenerationResponse;
import com.capstone.eval.evaluation.hybrid.EvidenceQuestionGenerator;
import com.capstone.eval.evaluation.hybrid.model.EvidenceQuestionSet;
import com.capstone.eval.evaluation.hybrid.model.ScoringRuleSet;
import com.capstone.eval.model.LlmConfig;
import com.capstone.eval.model.RulePackageItem;
import com.capstone.eval.repository.LlmConfigRepository;
import com.capstone.eval.repository.RulePackageItemRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/hybrid")
@RequiredArgsConstructor
@Slf4j
public class HybridEvaluationController {

    private final RulePackageItemRepository rulePackageItemRepository;
    private final LlmConfigRepository llmConfigRepository;
    private final EvidenceQuestionGenerator questionGenerator;
    private final ObjectMapper objectMapper;

    @PostMapping("/rule-packages/{packageId}/generate-questions/{itemId}")
    @PreAuthorize("hasAnyRole('ADMIN', 'TUTOR')")
    @Transactional
    public ResponseEntity<EvidenceQuestionGenerationResponse> generateQuestions(
            @PathVariable Long packageId,
            @PathVariable Long itemId,
            @RequestParam(defaultValue = "true") boolean save) {

        RulePackageItem item = rulePackageItemRepository.findById(itemId)
                .orElseThrow(() -> new IllegalArgumentException("RulePackageItem not found: " + itemId));

        if (!item.getRulePackage().getId().equals(packageId)) {
            throw new IllegalArgumentException("Item " + itemId + " does not belong to package " + packageId);
        }

        LlmConfig llmConfig = llmConfigRepository.findByIsDefaultTrue().orElse(null);

        EvidenceQuestionGenerator.GenerationResult result = questionGenerator.generate(item, llmConfig);

        if (save) {
            try {
                item.setEvidenceQuestions(objectMapper.writeValueAsString(result.getQuestions()));
                item.setScoringRules(objectMapper.writeValueAsString(result.getScoringRules()));
                rulePackageItemRepository.save(item);
                log.info("Saved generated questions and rules for item id={}", itemId);
            } catch (Exception e) {
                throw new RuntimeException("Failed to serialize and save generation result", e);
            }
        }

        return ResponseEntity.ok(EvidenceQuestionGenerationResponse.builder()
                .questions(result.getQuestions())
                .scoringRules(result.getScoringRules())
                .saved(save)
                .build());
    }

    @PutMapping("/rule-packages/{packageId}/items/{itemId}/evidence-questions")
    @PreAuthorize("hasAnyRole('ADMIN', 'TUTOR')")
    @Transactional
    public ResponseEntity<Void> updateEvidenceQuestions(
            @PathVariable Long packageId,
            @PathVariable Long itemId,
            @RequestBody EvidenceQuestionSet questions) {

        RulePackageItem item = rulePackageItemRepository.findById(itemId)
                .orElseThrow(() -> new IllegalArgumentException("RulePackageItem not found: " + itemId));

        if (!item.getRulePackage().getId().equals(packageId)) {
            throw new IllegalArgumentException("Item " + itemId + " does not belong to package " + packageId);
        }

        try {
            item.setEvidenceQuestions(objectMapper.writeValueAsString(questions));
            rulePackageItemRepository.save(item);
        } catch (Exception e) {
            throw new RuntimeException("Failed to serialize evidence questions", e);
        }

        return ResponseEntity.ok().build();
    }

    @PutMapping("/rule-packages/{packageId}/items/{itemId}/scoring-rules")
    @PreAuthorize("hasAnyRole('ADMIN', 'TUTOR')")
    @Transactional
    public ResponseEntity<Void> updateScoringRules(
            @PathVariable Long packageId,
            @PathVariable Long itemId,
            @RequestBody ScoringRuleSet scoringRules) {

        RulePackageItem item = rulePackageItemRepository.findById(itemId)
                .orElseThrow(() -> new IllegalArgumentException("RulePackageItem not found: " + itemId));

        if (!item.getRulePackage().getId().equals(packageId)) {
            throw new IllegalArgumentException("Item " + itemId + " does not belong to package " + packageId);
        }

        try {
            item.setScoringRules(objectMapper.writeValueAsString(scoringRules));
            rulePackageItemRepository.save(item);
        } catch (Exception e) {
            throw new RuntimeException("Failed to serialize scoring rules", e);
        }

        return ResponseEntity.ok().build();
    }

    @GetMapping("/rule-packages/{packageId}/items/{itemId}/evidence-questions")
    @Transactional(readOnly = true)
    public ResponseEntity<EvidenceQuestionSet> getEvidenceQuestions(
            @PathVariable Long packageId,
            @PathVariable Long itemId) {

        RulePackageItem item = rulePackageItemRepository.findById(itemId)
                .orElseThrow(() -> new IllegalArgumentException("RulePackageItem not found: " + itemId));

        if (!item.getRulePackage().getId().equals(packageId)) {
            throw new IllegalArgumentException("Item " + itemId + " does not belong to package " + packageId);
        }

        if (item.getEvidenceQuestions() == null) {
            return ResponseEntity.noContent().build();
        }

        try {
            EvidenceQuestionSet questions = objectMapper.readValue(
                    item.getEvidenceQuestions(), EvidenceQuestionSet.class);
            return ResponseEntity.ok(questions);
        } catch (Exception e) {
            throw new RuntimeException("Failed to deserialize evidence questions", e);
        }
    }

    @GetMapping("/rule-packages/{packageId}/items/{itemId}/scoring-rules")
    @Transactional(readOnly = true)
    public ResponseEntity<ScoringRuleSet> getScoringRules(
            @PathVariable Long packageId,
            @PathVariable Long itemId) {

        RulePackageItem item = rulePackageItemRepository.findById(itemId)
                .orElseThrow(() -> new IllegalArgumentException("RulePackageItem not found: " + itemId));

        if (!item.getRulePackage().getId().equals(packageId)) {
            throw new IllegalArgumentException("Item " + itemId + " does not belong to package " + packageId);
        }

        if (item.getScoringRules() == null) {
            return ResponseEntity.noContent().build();
        }

        try {
            ScoringRuleSet rules = objectMapper.readValue(
                    item.getScoringRules(), ScoringRuleSet.class);
            return ResponseEntity.ok(rules);
        } catch (Exception e) {
            throw new RuntimeException("Failed to deserialize scoring rules", e);
        }
    }
}
