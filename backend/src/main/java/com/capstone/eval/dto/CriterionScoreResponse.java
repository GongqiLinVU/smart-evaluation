package com.capstone.eval.dto;

import com.capstone.eval.model.CriterionScore;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collections;
import java.util.List;
import java.util.Map;

public record CriterionScoreResponse(
        String criterionName,
        Integer score,
        String level,
        String justification,
        List<Object> evidence,
        Map<String, Object> subScores,
        Double confidence,
        List<String> suggestions
) {

    private static final Logger log = LoggerFactory.getLogger(CriterionScoreResponse.class);

    public static CriterionScoreResponse fromEntity(CriterionScore cs, ObjectMapper mapper) {
        List<Object> evidenceList = deserializeObjectList(cs.getEvidence(), mapper);
        Map<String, Object> subScoresMap = deserializeMap(cs.getSubScores(), mapper);
        List<String> suggestionsList = deserializeStringList(cs.getSuggestions(), mapper);

        return new CriterionScoreResponse(
                cs.getCriterionName(),
                cs.getScore(),
                cs.getLevel() != null ? cs.getLevel().name() : null,
                cs.getJustification(),
                evidenceList,
                subScoresMap,
                cs.getConfidence(),
                suggestionsList
        );
    }

    private static List<Object> deserializeObjectList(String json, ObjectMapper mapper) {
        if (json == null || json.isBlank()) {
            return Collections.emptyList();
        }
        try {
            return mapper.readValue(json, new TypeReference<>() {});
        } catch (Exception e) {
            log.warn("Failed to deserialize evidence JSON: {}", e.getMessage());
            return Collections.emptyList();
        }
    }

    private static List<String> deserializeStringList(String json, ObjectMapper mapper) {
        if (json == null || json.isBlank()) {
            return Collections.emptyList();
        }
        try {
            return mapper.readValue(json, new TypeReference<>() {});
        } catch (Exception e) {
            log.warn("Failed to deserialize suggestions JSON: {}", e.getMessage());
            return Collections.emptyList();
        }
    }

    private static Map<String, Object> deserializeMap(String json, ObjectMapper mapper) {
        if (json == null || json.isBlank()) {
            return Collections.emptyMap();
        }
        try {
            return mapper.readValue(json, new TypeReference<>() {});
        } catch (Exception e) {
            log.warn("Failed to deserialize subScores JSON: {}", e.getMessage());
            return Collections.emptyMap();
        }
    }
}
