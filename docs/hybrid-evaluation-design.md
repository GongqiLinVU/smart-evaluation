# Hybrid Evaluation Engine Design

## Overview

The hybrid evaluation engine separates **perception** (LLM) from **judgment** (rules) to combine the strengths of both existing approaches while eliminating their weaknesses.

| Role | Who | Responsibility |
|------|-----|----------------|
| Witness | LLM | Extract factual observations + evidence quotes from the document |
| Judge | Rule Engine | Apply weighted scoring rules to the evidence to produce final scores |

The LLM never assigns scores. The rule engine never reads raw document text. Each does what it's best at.

---

## Motivation

### Problem with Rule-Based Alone
- Keyword matching cannot understand meaning, nuance, or quality of argumentation
- Misses content that uses unexpected vocabulary
- Cannot distinguish deep explanation from surface-level mention

### Problem with LLM Alone
- Scoring is a black box — hard to explain *why* a specific score was given
- Same document may receive different scores across runs
- Admin has limited control — only prompt engineering
- Verification requires re-asking the LLM (expensive, still opaque)

### Hybrid Solves Both
- LLM understands the document semantically (no keyword limitation)
- Rules make the final decision transparently (no black box)
- Every point gained/lost traces to: evidence → rule → score
- Admins tune rules directly without touching prompts
- Verification is simpler: "Did the LLM observe correctly?" not "Did the LLM score correctly?"

---

## Architecture

```
┌─────────────────────────────────────────────────────────────┐
│                   HybridEvaluationEngine                      │
│                   (EvaluationMethod.HYBRID)                   │
└──────────────────────────┬──────────────────────────────────┘
                           │
              ┌────────────┴────────────┐
              │                         │
              ▼                         ▼
┌──────────────────────┐   ┌──────────────────────────────┐
│  Evidence Extraction  │   │  Evidence-Based Rule Engine   │
│  Phase (LLM)          │   │  Phase (Deterministic)        │
│                       │   │                              │
│  - Reads document     │   │  - Reads EvidenceReport      │
│  - Answers predefined │   │  - Applies scoring rules     │
│    evidence questions  │   │  - Maps to PerformanceLevel  │
│  - Returns structured │   │  - Produces DecisionTrace    │
│    observations + cites│   │  - Generates explanation     │
└───────────┬───────────┘   └──────────────┬───────────────┘
            │                              │
            ▼                              ▼
     EvidenceReport                 EvaluationResult
     (JSON, stored)                 + DecisionTrace
                                    + Explanation
```

---

## Relationship to Existing Methods

The hybrid engine is a **third evaluation method** (`EvaluationMethod.HYBRID`), independent of the existing two:

```java
public enum EvaluationMethod {
    RULE_BASED,   // Existing: keyword/structure heuristics
    LLM,          // Existing: LLM scores directly
    HYBRID        // New: LLM extracts evidence, rules score
}
```

All three methods:
- Share the same `RulePackage` / `RulePackageItem` configuration
- Produce the same `EvaluationResult` output format
- Can be compared side-by-side on the same submission
- Use the same `ParsedDocument` as input

No changes to existing `RuleBasedEngine` or `LlmEvaluationEngine`.

---

## Core Concepts

### Evidence Questions

Each `RulePackageItem` gains an `evidenceQuestions` field — a JSON list of structured questions the LLM must answer about the document for that criterion.

```json
{
  "questions": [
    {
      "id": "METH_01",
      "text": "Does the report describe a specific development methodology (e.g., Agile, Waterfall, Scrum)?",
      "type": "BOOLEAN",
      "required": true
    },
    {
      "id": "METH_02",
      "text": "Is there a justification explaining WHY this methodology was chosen over alternatives?",
      "type": "BOOLEAN",
      "required": true
    },
    {
      "id": "METH_03",
      "text": "Are development phases or iterations described with timelines or milestones?",
      "type": "TERNARY",
      "required": true
    },
    {
      "id": "METH_04",
      "text": "How many methodology-related diagrams (Gantt charts, sprint boards, workflow diagrams) are present?",
      "type": "COUNT",
      "required": false
    },
    {
      "id": "METH_05",
      "text": "Is there evidence of the methodology being actually followed (e.g., sprint reviews, iteration outputs, meeting notes)?",
      "type": "TERNARY",
      "required": true
    },
    {
      "id": "METH_06",
      "text": "Rate the depth of methodology explanation: superficial mention vs. detailed description with rationale.",
      "type": "LIKERT_5",
      "required": true
    },
    {
      "id": "METH_07",
      "text": "Are there any contradictions between the described methodology and the actual implementation approach shown elsewhere in the report?",
      "type": "BOOLEAN",
      "required": false
    }
  ]
}
```

#### Question Types

| Type | Values | Use Case |
|------|--------|----------|
| `BOOLEAN` | `true` / `false` | Presence/absence checks |
| `TERNARY` | `yes` / `partial` / `no` | Graduated presence |
| `COUNT` | integer >= 0 | Counting structural elements |
| `LIKERT_5` | 1-5 | Subjective quality on a scale |
| `TEXT` | free text | Open observation (not scored directly, used for explanation) |

Every answer (except TEXT) must include a `citation` — a quote from the document supporting the observation. If no evidence found, the LLM must explicitly state "no evidence found" rather than guessing.

### Evidence Report

The LLM's output for one criterion:

```json
{
  "criterionKey": "METHODOLOGY",
  "observations": [
    {
      "questionId": "METH_01",
      "answer": true,
      "citation": {
        "sectionIndex": 3,
        "sectionName": "Development Methodology",
        "quote": "We adopted Scrum with 2-week sprints...",
        "confidence": 0.95
      }
    },
    {
      "questionId": "METH_02",
      "answer": false,
      "citation": null,
      "note": "Methodology is stated but no justification for why it was chosen"
    },
    {
      "questionId": "METH_06",
      "answer": 3,
      "citation": {
        "sectionIndex": 3,
        "sectionName": "Development Methodology",
        "quote": "Each sprint delivered a working increment...",
        "confidence": 0.8
      }
    }
  ]
}
```

### Scoring Rules

Each `RulePackageItem` also gains a `scoringRules` field — deterministic rules that map evidence observations to points:

```json
{
  "maxPoints": 10,
  "rules": [
    {
      "id": "SR_01",
      "description": "Methodology is described",
      "condition": "METH_01 == true",
      "points": 2
    },
    {
      "id": "SR_02",
      "description": "Choice is justified",
      "condition": "METH_02 == true",
      "points": 2
    },
    {
      "id": "SR_03",
      "description": "Phases described with timelines",
      "condition": "METH_03 == 'yes'",
      "points": 2,
      "partial": {
        "condition": "METH_03 == 'partial'",
        "points": 1
      }
    },
    {
      "id": "SR_04",
      "description": "Supporting diagrams present",
      "condition": "METH_04 >= 2",
      "points": 1,
      "partial": {
        "condition": "METH_04 >= 1",
        "points": 0.5
      }
    },
    {
      "id": "SR_05",
      "description": "Evidence of methodology being followed",
      "condition": "METH_05 == 'yes'",
      "points": 2,
      "partial": {
        "condition": "METH_05 == 'partial'",
        "points": 1
      }
    },
    {
      "id": "SR_06",
      "description": "Depth of explanation",
      "condition": "METH_06 >= 4",
      "points": 1,
      "partial": {
        "condition": "METH_06 >= 3",
        "points": 0.5
      }
    },
    {
      "id": "SR_07",
      "description": "Contradictions found (penalty)",
      "condition": "METH_07 == true",
      "points": -1
    }
  ],
  "levelMapping": {
    "Excellent": { "min": 9.0 },
    "Proficient": { "min": 7.0 },
    "Competent": { "min": 5.0 },
    "Developing": { "min": 3.0 },
    "Inadequate": { "min": 0 }
  }
}
```

### Decision Trace

The output of the rule engine for one criterion:

```json
{
  "criterionKey": "METHODOLOGY",
  "totalPoints": 6.5,
  "maxPoints": 10,
  "performanceLevel": "Proficient",
  "rulesTriggered": [
    { "ruleId": "SR_01", "fired": true, "points": 2, "reason": "METH_01 == true" },
    { "ruleId": "SR_02", "fired": false, "points": 0, "reason": "METH_02 == false (no justification)" },
    { "ruleId": "SR_03", "fired": "partial", "points": 1, "reason": "METH_03 == 'partial'" },
    { "ruleId": "SR_04", "fired": true, "points": 1, "reason": "METH_04 = 2 (>= 2)" },
    { "ruleId": "SR_05", "fired": "partial", "points": 1, "reason": "METH_05 == 'partial'" },
    { "ruleId": "SR_06", "fired": "partial", "points": 0.5, "reason": "METH_06 = 3 (>= 3)" },
    { "ruleId": "SR_07", "fired": true, "points": -1, "reason": "METH_07 == true (contradiction found)" }
  ],
  "explanation": "Scored 6.5/10 (Proficient). Methodology is described with some diagrams and partial evidence of being followed, but lacks justification for the choice and has a contradiction between described and actual approach."
}
```

---

## Evidence Question Generation (Admin Workflow)

### When It Happens

- Admin creates or edits a rule package
- Admin clicks "Generate Evidence Questions" for a criterion
- System calls LLM once to draft questions from the rubric text
- Admin reviews, edits, and saves

### Generation Prompt

```
Given this rubric criterion and its level descriptors, generate a set of 
factual evidence questions that a document reviewer should answer to determine 
the student's performance level.

Criterion: {rule.name}
Description: {rule.description}
LLM Criterion Prompt: {rule.llmCriterionPrompt}

Level descriptors:
- Excellent (30pts): {from scoringScale}
- Proficient (24pts): {from scoringScale}
- Competent (18pts): {from scoringScale}
- Developing (12pts): {from scoringScale}
- Inadequate (6pts): {from scoringScale}

Requirements:
1. Generate 6-10 questions that distinguish between performance levels
2. Each question must be answerable from the document alone
3. Use these types: BOOLEAN, TERNARY (yes/partial/no), COUNT, LIKERT_5
4. Questions should progress from basic (distinguishes Inadequate from Developing) 
   to advanced (distinguishes Proficient from Excellent)
5. Include at least one penalty question (checks for contradictions/errors)
6. Output JSON format with id, text, type, required fields
```

### Generation Also Produces Draft Scoring Rules

The same generation call produces both questions AND scoring rules as a paired set, since they must align. Admin reviews both together.

---

## Evaluation Flow (Runtime)

```
1. Orchestrator receives HYBRID evaluation request
       │
2. Load ParsedDocument + RulePackage with evidenceQuestions
       │
3. For each enabled RulePackageItem:
       │
       ├── 3a. Build LLM prompt with:
       │        - Document sections (from ParsedDocument)
       │        - Evidence questions for this criterion
       │        - Instructions: "Answer each question with evidence. Do NOT score."
       │
       ├── 3b. LLM returns EvidenceReport (structured observations + citations)
       │
       ├── 3c. Citation validation:
       │        - Check quoted text exists in document
       │        - Flag hallucinated citations
       │
       └── 3d. Store EvidenceReport as round metadata
       │
4. Pass all EvidenceReports to EvidenceRuleEngine:
       │
       ├── 4a. For each criterion, evaluate scoring rules against observations
       ├── 4b. Compute points, map to PerformanceLevel
       ├── 4c. Generate DecisionTrace
       └── 4d. Generate human-readable explanation
       │
5. Aggregate criterion scores → overall score (using package weights)
       │
6. Build EvaluationResult with:
       - CriterionScores (same format as other methods)
       - rawLlmResponse = EvidenceReport JSON (for transparency)
       - DecisionTrace stored in rounds metadata
       - explanation = rule-derived text
```

---

## Data Model Changes

### New Fields on RulePackageItem

```java
@Lob
@Column(columnDefinition = "TEXT")
private String evidenceQuestions;  // JSON: list of evidence questions

@Lob
@Column(columnDefinition = "TEXT")
private String scoringRules;      // JSON: scoring rules + level mapping
```

### New Enum Value

```java
public enum EvaluationMethod {
    RULE_BASED, LLM, HYBRID
}
```

### New Classes

| Class | Package | Purpose |
|-------|---------|---------|
| `HybridEvaluationEngine` | `evaluation.hybrid` | Orchestrates the hybrid flow |
| `EvidenceExtractor` | `evaluation.hybrid` | Builds LLM prompts, parses evidence responses |
| `EvidenceRuleEngine` | `evaluation.hybrid` | Evaluates scoring rules against observations |
| `EvidenceQuestionGenerator` | `evaluation.hybrid` | Admin tool: generates draft questions from rubric |
| `EvidenceReport` | `evaluation.hybrid.model` | Structured LLM output per criterion |
| `DecisionTrace` | `evaluation.hybrid.model` | Rule engine output per criterion |
| `ScoringRule` | `evaluation.hybrid.model` | Single scoring rule definition |
| `EvidenceQuestion` | `evaluation.hybrid.model` | Single question definition |
| `Observation` | `evaluation.hybrid.model` | Single LLM answer with citation |

### New Endpoints

| Method | Path | Purpose |
|--------|------|---------|
| `POST` | `/api/rule-packages/{id}/generate-questions/{itemId}` | Generate evidence questions for a rule item |
| `GET` | `/api/evaluations/{id}/decision-trace` | Get the decision trace for a hybrid evaluation |

---

## Token Cost Analysis

### Current LLM Method (per evaluation)

```
Initial eval:  ~20K tokens (full document + scoring prompt)
Verification:  ~16K tokens (evidence digest × 3 rounds)
Total:         ~36K tokens
```

### Hybrid Method (per evaluation)

```
Evidence extraction: ~15K tokens (document + questions, no scoring logic)
Rule engine:          0 tokens (deterministic)
Total:              ~15K tokens
```

**Savings: ~58% fewer tokens than LLM + verification**

Additionally, hybrid evaluations do NOT need the 3-round verification chain because:
- The rule engine is deterministic (no need to "verify" its math)
- If evidence is wrong, you only re-ask the specific question (not re-score everything)

---

## Verification in Hybrid Mode

Verification is simpler and cheaper:

1. **Citation Validation** (no LLM needed): Check that cited quotes actually exist in the document. Flag any that don't match.

2. **Spot-Check** (minimal LLM): For low-confidence observations (LLM reported confidence < 0.7), re-ask the specific question with extra context. Compare answers.

3. **Re-scoring** (no LLM needed): If any observations change, re-run the rule engine. Deterministic — same inputs always produce same output.

Cost: ~2-3K tokens for spot-checks only. Compare to ~16K for full LLM verification.

---

## Comparison Matrix

| Dimension | Rule-Based | LLM | Hybrid |
|-----------|-----------|-----|--------|
| Understands content | No (keywords only) | Yes | Yes (LLM extracts) |
| Scoring transparency | High (heuristic) | Low (black box) | High (rule trace) |
| Reproducibility | Perfect | Variable | High (rules fixed, LLM observations may vary slightly) |
| Admin control | Keyword configs | Prompt engineering | Rule thresholds + questions |
| Token cost | 0 | ~36K | ~15K |
| Verification cost | N/A | ~16K | ~3K |
| Explanation quality | Low (generic) | Medium (LLM text) | High (evidence → rule → score) |
| Setup effort | Low | Medium (prompt tuning) | Medium (question generation, one-time) |

---

## Implementation Plan

### Phase 1: Data Model + Question Generation
1. Add `evidenceQuestions` and `scoringRules` fields to `RulePackageItem`
2. Create `EvidenceQuestion` and `ScoringRule` model classes
3. Implement `EvidenceQuestionGenerator` (LLM-assisted admin tool)
4. Add admin UI for reviewing/editing generated questions and rules
5. Database migration

### Phase 2: Evidence Extraction
1. Create `EvidenceExtractor` — builds prompts, calls LLM, parses structured output
2. Create `EvidenceReport` / `Observation` model classes
3. Citation validation (check quotes exist in document)
4. Store evidence reports as evaluation round metadata

### Phase 3: Rule Engine
1. Create `EvidenceRuleEngine` — evaluates scoring rules against observations
2. Create `DecisionTrace` model
3. Implement rule condition parser (simple expression evaluator)
4. Level mapping from total points
5. Explanation generator (template-based from decision trace)

### Phase 4: Integration
1. Create `HybridEvaluationEngine` implementing `EvaluationEngine` interface pattern
2. Add `HYBRID` to `EvaluationMethod` enum
3. Wire into `EvaluationOrchestrator`
4. Add hybrid-specific verification (citation validation + spot-check)
5. Frontend: method selector, decision trace display

### Phase 5: Comparison Tooling
1. Side-by-side view: run all 3 methods on same submission
2. Agreement metrics: how often do methods agree on performance level?
3. Divergence highlighting: which criteria disagree most?
4. Cost dashboard: tokens used per method

---

## Edge Cases

### LLM Refuses to Answer a Question
- Mark observation as `UNANSWERABLE`
- Scoring rule treats it as the "weakest" answer (conservative)
- Flag for human review

### Citation Doesn't Match Document
- Mark observation confidence = 0
- Exclude from scoring (treat as if question wasn't answered)
- Log as potential hallucination

### All Questions Return "No Evidence"
- Score = 0 for that criterion (Inadequate level)
- Explanation clearly states: "No evidence found for any indicator"
- Admin should check if questions are appropriate for this document type

### Document Too Large for Single LLM Call
- Use the same chunking strategy as `EvaluationRoundPlanner`
- Each chunk answers questions for sections it contains
- Merge observations across chunks (take highest-confidence answer per question)

---

## Future Extensions

- **Adaptive questions**: If METH_01 = false, skip follow-up questions about methodology depth (save tokens)
- **Student self-assessment**: Let students answer the same evidence questions — compare against LLM observations
- **Inter-rater reliability**: Run evidence extraction N times, measure observation stability
- **Custom question types**: Regex-validated text, enum selection, multi-select
- **Learning from tutor reviews**: When tutor overrides a score, identify which evidence questions need adjustment
