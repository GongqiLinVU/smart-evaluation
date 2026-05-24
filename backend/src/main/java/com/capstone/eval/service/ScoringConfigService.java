package com.capstone.eval.service;

import com.capstone.eval.dto.*;
import com.capstone.eval.model.EvaluationResult;
import com.capstone.eval.model.SystemConfig;
import com.capstone.eval.model.TutorReview;
import com.capstone.eval.model.enums.EvaluationMethod;
import com.capstone.eval.repository.EvaluationResultRepository;
import com.capstone.eval.repository.SystemConfigRepository;
import com.capstone.eval.repository.TutorReviewRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class ScoringConfigService {

    private static final String KEY_RULE_BASED_WEIGHT = "scoring.weight.rule_based";
    private static final String KEY_LLM_WEIGHT = "scoring.weight.llm";
    private static final String KEY_TUTOR_WEIGHT = "scoring.weight.tutor";
    private static final String KEY_MAX_SCORE = "scoring.max_score";

    private static final double DEFAULT_RULE_BASED_WEIGHT = 40.0;
    private static final double DEFAULT_LLM_WEIGHT = 30.0;
    private static final double DEFAULT_TUTOR_WEIGHT = 30.0;
    private static final int DEFAULT_MAX_SCORE = 100;

    private final SystemConfigRepository configRepository;
    private final EvaluationResultRepository evaluationResultRepository;
    private final TutorReviewRepository tutorReviewRepository;

    public ScoringWeightsResponse getWeights() {
        double rbWeight = getDoubleConfig(KEY_RULE_BASED_WEIGHT, DEFAULT_RULE_BASED_WEIGHT);
        double llmWeight = getDoubleConfig(KEY_LLM_WEIGHT, DEFAULT_LLM_WEIGHT);
        double tutorWeight = getDoubleConfig(KEY_TUTOR_WEIGHT, DEFAULT_TUTOR_WEIGHT);
        int maxScore = getIntConfig(KEY_MAX_SCORE, DEFAULT_MAX_SCORE);
        return new ScoringWeightsResponse(rbWeight, llmWeight, tutorWeight, maxScore);
    }

    @Transactional
    public ScoringWeightsResponse updateWeights(ScoringWeightsRequest request) {
        double total = request.ruleBasedWeight() + request.llmWeight() + request.tutorWeight();
        if (Math.abs(total - 100.0) > 0.01) {
            throw new IllegalArgumentException("Weights must sum to 100. Current sum: " + total);
        }

        saveConfig(KEY_RULE_BASED_WEIGHT, String.valueOf(request.ruleBasedWeight()),
                "Weight for rule-based evaluation (%)");
        saveConfig(KEY_LLM_WEIGHT, String.valueOf(request.llmWeight()),
                "Weight for LLM evaluation (%)");
        saveConfig(KEY_TUTOR_WEIGHT, String.valueOf(request.tutorWeight()),
                "Weight for tutor review (%)");

        if (request.maxScore() != null) {
            saveConfig(KEY_MAX_SCORE, String.valueOf(request.maxScore()),
                    "Maximum composite score");
        }

        return getWeights();
    }

    public CompositeScoreResponse computeCompositeScore(Long submissionId) {
        ScoringWeightsResponse weights = getWeights();

        Optional<EvaluationResult> latestRuleBased = evaluationResultRepository
                .findBySubmissionIdAndMethodOrderByEvaluatedAtDesc(submissionId, EvaluationMethod.RULE_BASED)
                .stream().findFirst();

        Optional<EvaluationResult> latestLlm = evaluationResultRepository
                .findBySubmissionIdAndMethodOrderByEvaluatedAtDesc(submissionId, EvaluationMethod.LLM)
                .stream().findFirst();

        Optional<TutorReview> tutorReview = tutorReviewRepository
                .findFirstBySubmissionIdOrderByReviewedAtDesc(submissionId);

        List<CompositeScoreResponse.ComponentScore> components = new ArrayList<>();
        double totalAvailableWeight = 0;
        double weightedSum = 0;

        // Rule-based component: score is out of 30
        if (latestRuleBased.isPresent()) {
            int rawScore = latestRuleBased.get().getOverallScore();
            int rawMax = 30;
            double pct = (rawScore / (double) rawMax) * 100;
            components.add(new CompositeScoreResponse.ComponentScore(
                    "RULE_BASED", rawScore, rawMax, pct,
                    weights.ruleBasedWeight(), null, true));
            totalAvailableWeight += weights.ruleBasedWeight();
            weightedSum += pct * weights.ruleBasedWeight();
        } else {
            components.add(new CompositeScoreResponse.ComponentScore(
                    "RULE_BASED", null, 30, null,
                    weights.ruleBasedWeight(), null, false));
        }

        // LLM component: score is out of 30
        if (latestLlm.isPresent()) {
            int rawScore = latestLlm.get().getOverallScore();
            int rawMax = 30;
            double pct = (rawScore / (double) rawMax) * 100;
            components.add(new CompositeScoreResponse.ComponentScore(
                    "LLM", rawScore, rawMax, pct,
                    weights.llmWeight(), null, true));
            totalAvailableWeight += weights.llmWeight();
            weightedSum += pct * weights.llmWeight();
        } else {
            components.add(new CompositeScoreResponse.ComponentScore(
                    "LLM", null, 30, null,
                    weights.llmWeight(), null, false));
        }

        // Tutor component: score is out of 30 (overallScore)
        if (tutorReview.isPresent()) {
            int rawScore = tutorReview.get().getOverallScore();
            int rawMax = 30;
            double pct = (rawScore / (double) rawMax) * 100;
            components.add(new CompositeScoreResponse.ComponentScore(
                    "TUTOR", rawScore, rawMax, pct,
                    weights.tutorWeight(), null, true));
            totalAvailableWeight += weights.tutorWeight();
            weightedSum += pct * weights.tutorWeight();
        } else {
            components.add(new CompositeScoreResponse.ComponentScore(
                    "TUTOR", null, 30, null,
                    weights.tutorWeight(), null, false));
        }

        if (totalAvailableWeight == 0) {
            return new CompositeScoreResponse(null, null, weights.maxScore(), null, components, weights);
        }

        // Normalize: redistribute among available components
        double compositePercentage = weightedSum / totalAvailableWeight;
        double compositeScore = (compositePercentage / 100.0) * weights.maxScore();

        // Recalculate weighted contributions
        List<CompositeScoreResponse.ComponentScore> finalComponents = new ArrayList<>();
        for (CompositeScoreResponse.ComponentScore c : components) {
            if (c.available()) {
                double normalizedWeight = c.weight() / totalAvailableWeight * 100;
                double contribution = (c.percentage() * normalizedWeight) / 100;
                finalComponents.add(new CompositeScoreResponse.ComponentScore(
                        c.method(), c.rawScore(), c.rawMaxScore(), c.percentage(),
                        normalizedWeight, contribution, true));
            } else {
                finalComponents.add(c);
            }
        }

        String level = determineLevel(compositePercentage);

        return new CompositeScoreResponse(
                Math.round(compositeScore * 10.0) / 10.0,
                Math.round(compositePercentage * 10.0) / 10.0,
                weights.maxScore(),
                level,
                finalComponents,
                weights
        );
    }

    private String determineLevel(double percentage) {
        if (percentage >= 85) return "EXCELLENT";
        if (percentage >= 65) return "PROFICIENT";
        if (percentage >= 45) return "COMPETENT";
        if (percentage >= 25) return "DEVELOPING";
        return "INADEQUATE";
    }

    private double getDoubleConfig(String key, double defaultValue) {
        return configRepository.findById(key)
                .map(c -> Double.parseDouble(c.getValue()))
                .orElse(defaultValue);
    }

    private int getIntConfig(String key, int defaultValue) {
        return configRepository.findById(key)
                .map(c -> Integer.parseInt(c.getValue()))
                .orElse(defaultValue);
    }

    private void saveConfig(String key, String value, String description) {
        SystemConfig config = configRepository.findById(key)
                .orElse(SystemConfig.builder().key(key).build());
        config.setValue(value);
        config.setDescription(description);
        configRepository.save(config);
    }
}
