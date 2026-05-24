package com.capstone.eval.evaluation.llm.multiround;

import com.capstone.eval.model.RulePackageItem;
import com.capstone.eval.parser.DocumentSection;
import com.capstone.eval.parser.ParsedDocument;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

@Component
@Slf4j
public class EvaluationRoundPlanner {

    private static final int SINGLE_PASS_WORD_LIMIT = 3000;
    private static final int TWO_PASS_WORD_LIMIT = 12000;
    private static final int SINGLE_PASS_SECTION_LIMIT = 5;
    private static final int CHUNK_WORD_BUDGET = 6000;

    private static final Map<String, List<String>> CRITERIA_KEYWORDS = Map.of(
            "methodology", List.of("methodology", "approach", "design", "process", "framework",
                    "architecture", "planning", "strategy", "requirement", "specification"),
            "implementation_detail", List.of("implementation", "code", "development", "function",
                    "module", "component", "class", "api", "database", "deployment", "configuration"),
            "logic_explanation", List.of("logic", "explanation", "reasoning", "algorithm",
                    "flow", "decision", "analysis", "overview", "system", "how")
    );

    public EvaluationPlan planRounds(ParsedDocument document, List<RulePackageItem> enabledRules) {
        int totalWords = document.getTotalWordCount();
        int sectionCount = document.getSections().size();
        List<String> criteriaKeys = enabledRules.stream()
                .filter(item -> Boolean.TRUE.equals(item.getEnabled()))
                .map(item -> item.getRule().getRuleKey())
                .toList();

        if (totalWords < SINGLE_PASS_WORD_LIMIT || sectionCount <= SINGLE_PASS_SECTION_LIMIT) {
            return buildSinglePassPlan(document, criteriaKeys);
        } else if (totalWords <= TWO_PASS_WORD_LIMIT) {
            return buildTwoPassPlan(document, criteriaKeys);
        } else {
            return buildMultiPassPlan(document, criteriaKeys);
        }
    }

    private EvaluationPlan buildSinglePassPlan(ParsedDocument document, List<String> criteriaKeys) {
        log.info("Planning SINGLE_PASS: {} words, {} sections",
                document.getTotalWordCount(), document.getSections().size());

        List<Integer> allIndices = IntStream.range(0, document.getSections().size())
                .boxed().toList();

        RoundSpec round = new RoundSpec(1, RoundSpec.TYPE_SECTION_EVAL,
                allIndices, criteriaKeys, document.getTotalWordCount());

        return new EvaluationPlan(PlanStrategy.SINGLE_PASS, List.of(round), null);
    }

    private EvaluationPlan buildTwoPassPlan(ParsedDocument document, List<String> criteriaKeys) {
        log.info("Planning TWO_PASS: {} words, {} sections",
                document.getTotalWordCount(), document.getSections().size());

        Map<String, List<Integer>> affinityMap = computeSectionAffinity(document);

        Set<Integer> methodologySections = new HashSet<>();
        Set<Integer> implementationSections = new HashSet<>();

        for (String key : criteriaKeys) {
            List<Integer> affiliated = affinityMap.getOrDefault(key, Collections.emptyList());
            if (key.contains("methodology") || key.contains("design") || key.contains("process")) {
                methodologySections.addAll(affiliated);
            } else {
                implementationSections.addAll(affiliated);
            }
        }

        Set<Integer> allAssigned = new HashSet<>();
        allAssigned.addAll(methodologySections);
        allAssigned.addAll(implementationSections);

        for (int i = 0; i < document.getSections().size(); i++) {
            if (!allAssigned.contains(i)) {
                if (i < document.getSections().size() / 2) {
                    methodologySections.add(i);
                } else {
                    implementationSections.add(i);
                }
            }
        }

        List<String> round1Criteria = criteriaKeys.stream()
                .filter(k -> k.contains("methodology") || k.contains("logic"))
                .collect(Collectors.toList());
        List<String> round2Criteria = criteriaKeys.stream()
                .filter(k -> k.contains("implementation") || k.contains("logic"))
                .collect(Collectors.toList());

        if (round1Criteria.isEmpty()) round1Criteria = new ArrayList<>(criteriaKeys);
        if (round2Criteria.isEmpty()) round2Criteria = new ArrayList<>(criteriaKeys);

        List<Integer> round1Indices = methodologySections.stream().sorted().toList();
        List<Integer> round2Indices = implementationSections.stream().sorted().toList();

        int round1Words = round1Indices.stream()
                .mapToInt(i -> document.getSections().get(i).getWordCount()).sum();
        int round2Words = round2Indices.stream()
                .mapToInt(i -> document.getSections().get(i).getWordCount()).sum();

        RoundSpec round1 = new RoundSpec(1, RoundSpec.TYPE_SECTION_EVAL,
                round1Indices, round1Criteria, round1Words);
        RoundSpec round2 = new RoundSpec(2, RoundSpec.TYPE_SECTION_EVAL,
                round2Indices, round2Criteria, round2Words);
        RoundSpec synthesis = new RoundSpec(3, RoundSpec.TYPE_SYNTHESIS,
                Collections.emptyList(), criteriaKeys, 0);

        return new EvaluationPlan(PlanStrategy.TWO_PASS, List.of(round1, round2), synthesis);
    }

    private EvaluationPlan buildMultiPassPlan(ParsedDocument document, List<String> criteriaKeys) {
        log.info("Planning MULTI_PASS: {} words, {} sections",
                document.getTotalWordCount(), document.getSections().size());

        List<RoundSpec> rounds = new ArrayList<>();
        List<Integer> currentChunk = new ArrayList<>();
        int currentWords = 0;
        int roundNumber = 1;

        for (int i = 0; i < document.getSections().size(); i++) {
            int sectionWords = document.getSections().get(i).getWordCount();

            if (currentWords + sectionWords > CHUNK_WORD_BUDGET && !currentChunk.isEmpty()) {
                rounds.add(new RoundSpec(roundNumber++, RoundSpec.TYPE_SECTION_EVAL,
                        new ArrayList<>(currentChunk), criteriaKeys, currentWords));
                currentChunk.clear();
                currentWords = 0;
            }

            currentChunk.add(i);
            currentWords += sectionWords;
        }

        if (!currentChunk.isEmpty()) {
            rounds.add(new RoundSpec(roundNumber++, RoundSpec.TYPE_SECTION_EVAL,
                    new ArrayList<>(currentChunk), criteriaKeys, currentWords));
        }

        RoundSpec synthesis = new RoundSpec(roundNumber, RoundSpec.TYPE_SYNTHESIS,
                Collections.emptyList(), criteriaKeys, 0);

        return new EvaluationPlan(PlanStrategy.MULTI_PASS, rounds, synthesis);
    }

    private Map<String, List<Integer>> computeSectionAffinity(ParsedDocument document) {
        Map<String, List<Integer>> affinity = new HashMap<>();
        for (String criterionKey : CRITERIA_KEYWORDS.keySet()) {
            affinity.put(criterionKey, new ArrayList<>());
        }

        List<DocumentSection> sections = document.getSections();
        for (int i = 0; i < sections.size(); i++) {
            String heading = sections.get(i).getHeading().toLowerCase();
            for (Map.Entry<String, List<String>> entry : CRITERIA_KEYWORDS.entrySet()) {
                for (String keyword : entry.getValue()) {
                    if (heading.contains(keyword)) {
                        affinity.get(entry.getKey()).add(i);
                        break;
                    }
                }
            }
        }

        return affinity;
    }
}
