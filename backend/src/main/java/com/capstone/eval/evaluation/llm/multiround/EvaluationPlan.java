package com.capstone.eval.evaluation.llm.multiround;

import java.util.List;

public record EvaluationPlan(
        PlanStrategy strategy,
        List<RoundSpec> evaluationRounds,
        RoundSpec synthesisRound
) {}
