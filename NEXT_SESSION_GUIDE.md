# Next Session Guide: Decision System with Explanation

## What This System Is

An AI-powered evaluation **decision system with explanation** for IT capstone project marking.
- Spring Boot backend + React/Ant Design frontend + MySQL
- Multi-round LLM evaluation (DeepSeek API)
- Rule packages define rubric criteria dynamically
- Full audit trail: system prompt, user prompt, raw LLM response, tokens

## Current State (2026-05-30)

The evaluation pipeline WORKS end-to-end:
- Upload .docx → parse → LLM evaluates → scores stored → results displayed
- Debug panel shows full prompt trace (system prompt, user prompt, raw response)
- Two rubric scales: 30-point (final report) and 10-point/HD-F (progress report)
- Backend running on port 8080, frontend on port 5173

## What Needs to Be Done (Priority Order)

### P0: Fix Evaluation Accuracy Bugs

**Bug 1: Overall score arithmetic is wrong**
- File: `backend/src/main/java/com/capstone/eval/evaluation/llm/multiround/SynthesisAggregator.java`
- Problem: LLM reports overall_score=8 but actual average of (2+8+8)/3=6. System trusts LLM.
- Fix: After parsing, RECOMPUTE overall from individual criterion scores. Snap to nearest valid level (10,8,6,4,2). Log warning if LLM's value differs.

**Bug 2: No level boundary reasoning in prompt**
- File: `backend/src/main/java/com/capstone/eval/evaluation/llm/multiround/DynamicPromptBuilder.java`
- Problem: LLM justifications say WHAT was decided, not WHY this level vs adjacent levels.
- Fix: Add to system prompt:
  ```
  For each criterion, you MUST explain:
  - Why you did NOT assign the level ABOVE your choice (what specific evidence is missing?)
  - Why you DID assign this level (what evidence matches this descriptor?)
  - Why the level BELOW does not apply (what evidence exceeds that threshold?)
  ```

### P1: Improve Reliability

**Bug 3: Screenshot instruction too weak**
- Same DynamicPromptBuilder file
- Problem: Prompt says don't penalize for missing images, but LLM still does
- Fix: Strengthen to CRITICAL-level instruction, or add post-processing filter

**Bug 4: Add decision_chain to output schema**
- Files: DynamicPromptBuilder (prompt), SynthesisAggregator (parsing), EvaluationRound model
- Add structured output:
  ```json
  "decision_chain": {
    "rubric_requirements_checked": [
      {"requirement": "...", "met": true/false/"partial", "evidence": "..."}
    ],
    "level_comparison": {
      "why_not_HD": "...",
      "why_D": "...",
      "why_not_C": "..."
    }
  }
  ```

### P2: UI & Configuration

**Task 5: Rule package / task type alignment**
- "meeting_diary" criterion shouldn't auto-apply to all submissions
- Need task-type-aware rule package assignment

**Task 6: Frontend explanation panel**
- New component showing decision chain visually
- Rubric requirements as green/red/yellow checklist
- Level boundary reasoning displayed

### Deferred (from earlier sessions)
- Enhanced CSV export
- Phase 2: Contribution extraction from reports
- Phase 3: Contribution-weighted score splitting

## How to Start the System

```bash
# Terminal 1: Backend
cd /Users/Gongqi/Documents/Agent/Education
export $(grep -v '^#' .env | xargs)
cd backend && ./mvnw spring-boot:run

# Terminal 2: Frontend
cd /Users/Gongqi/Documents/Agent/Education/frontend
npm run dev
```

Login: gongqi.lin@vu.edu.au / Vu3390

## Key Files for Decision System Changes

| File | What to change |
|------|---------------|
| `backend/.../evaluation/llm/multiround/SynthesisAggregator.java` | Overall score recomputation, decision_chain parsing |
| `backend/.../evaluation/llm/multiround/DynamicPromptBuilder.java` | System prompt: level boundary reasoning, arithmetic instruction, screenshot instruction |
| `backend/.../model/EvaluationResult.java` | May need field for decision_chain JSON |
| `backend/.../model/CriterionScore.java` | May need field for level_comparison JSON |
| `frontend/src/components/EvaluationDebugPanel.tsx` | Existing debug trace panel |
| `frontend/src/components/ScoreCard.tsx` | Where to add explanation display |

## Testing After Changes

1. Run evaluation on an existing submission (e.g., Group07, submission id=9)
2. Check debug panel → Copy Full Trace
3. Verify: overall_score matches average of criterion scores
4. Verify: each criterion has level boundary reasoning
5. Verify: no false penalties for screenshots

## Architecture Notes

- `RulePackage` → defines WHAT criteria to evaluate (dynamic)
- `LlmConfig` → defines HOW to call LLM (template, model, temperature)
- `EvaluationRound` → audit trail per LLM call (prompt + response + tokens)
- `EvaluationRoundPlanner` → decides single-pass vs multi-round based on doc length
- `DynamicPromptBuilder` → assembles system/user prompts from rules + config
- `RoundExecutor` → makes the actual LLM API call
- `SynthesisAggregator` → parses LLM JSON response into EvaluationResult + CriterionScores
