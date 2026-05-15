package com.capstone.eval.evaluation;

import com.capstone.eval.model.EvaluationResult;
import com.capstone.eval.model.Submission;
import com.capstone.eval.parser.ParsedDocument;

/**
 * Common interface for all evaluation strategies (rule-based, LLM-based, etc.).
 */
public interface EvaluationEngine {

    /**
     * Evaluate a parsed student document and produce a scored result.
     *
     * @param document  the parsed representation of the student's .docx report
     * @param submission the submission metadata
     * @return a fully populated EvaluationResult (not yet persisted)
     */
    EvaluationResult evaluate(ParsedDocument document, Submission submission);
}
