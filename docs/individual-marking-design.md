# Individual Marking & Feedback Design

## Problem

Capstone submissions are group-level: one PDF per team, one `EvaluationResult` per submission, one score shared across all members. This is unfair when team members contributed unequally. Students are required to include their names in a contribution section, but that section is never read by the evaluator.

---

## Goals

1. **Parse** the contribution section of the submitted report to identify who contributed what.
2. **Derive individual scores** from the group score, adjusted by each member's contribution.
3. **Generate individual feedback** that speaks to each student's own work.
4. **Display** per-student results in the UI alongside the group result.
5. **Allow tutor override** of any individual score before it becomes final.

---

## Proposed Data Flow

```
Group Submission (PDF)
        │
        ▼
[Step 1] Contribution Extractor (LLM)
        │  reads contribution section
        │  outputs: List<MemberContribution>
        │    - studentName
        │    - contributionSummary (what they did, in their own words)
        │    - estimatedPercent (0–100, derived from text; null if not stated)
        ▼
[Step 2] Group Evaluation (existing LLM / Hybrid engine)
        │  outputs: EvaluationResult (group-level, unchanged)
        ▼
[Step 3] Individual Score Derivation (rule-based, no extra LLM call)
        │  inputs: group score + each member's contribution weight
        │  outputs: IndividualMark per member
        │    - baseScore (= group score)
        │    - adjustedScore (= baseScore × adjustmentFactor)
        │    - adjustmentFactor derived from contribution weight (see below)
        ▼
[Step 4] Individual Feedback Generator (LLM, one call per member)
        │  inputs: group criterion scores + member's contributionSummary
        │  outputs: personalized strengths, improvements, overall feedback
        ▼
[Step 5] Tutor Review & Override (manual, in UI)
        │  tutor can edit adjustedScore per member
        │  tutor can add a personal note per member
        ▼
IndividualMark (persisted, linked to EvaluationResult + GroupMember)
```

---

## Score Derivation Formula

The goal is to reward high contributors above the group score and penalize low contributors below it, while keeping the average equal to the group score.

```
normalizedWeight(i) = contributionPercent(i) / averageContributionPercent

adjustedScore(i) = clamp(
  groupScore × normalizedWeight(i),
  groupScore × FLOOR_FACTOR,   // e.g. 0.6 — can't fall below 60% of group score
  groupScore × CEIL_FACTOR     // e.g. 1.2 — can't exceed 120% of group score
)
```

**Example** — group score 18/25, 3 members with contributions 50%, 30%, 20%:

| Member | Contribution | Weight (÷33.3%) | Raw adjusted | Clamped (floor 0.6, ceil 1.2) |
|--------|-------------|-----------------|--------------|-------------------------------|
| A      | 50%         | 1.50            | 27.0 → cap   | 21.6 / 25                    |
| B      | 30%         | 0.90            | 16.2         | 16.2 / 25                    |
| C      | 20%         | 0.60            | 10.8 → floor | 10.8 / 25                    |

Floor and ceiling factors are **configurable** per task (stored in `ProjectTask` or a new config table).

If contribution percentages are **not stated** in the report, the system defaults to equal weighting (adjustedScore = groupScore for all) and flags this for tutor review.

---

## New Entities

### `IndividualMark`

```
individual_mark
  id                  BIGINT PK
  evaluation_result   FK → evaluation_result.id   (the group eval)
  group_member        FK → group_member.id
  base_score          INT      (= group overallScore)
  adjusted_score      INT      (after contribution weighting)
  max_score           INT      (= group maxScore)
  adjustment_factor   DOUBLE   (normalizedWeight, stored for audit)
  contribution_text   TEXT     (raw text extracted from report for this member)
  overall_feedback    TEXT     (personalized feedback)
  strengths           JSON     (List<String>)
  improvements        JSON     (List<String>)
  tutor_note          TEXT     (optional manual note added by tutor)
  tutor_override      INT NULL (if set, overrides adjusted_score in display)
  finalized           BOOLEAN  (false = pending tutor review, true = released)
  created_at          DATETIME
  updated_at          DATETIME
```

`EvaluationResult` → `OneToMany(IndividualMark)`, cascaded.

---

## New Backend Components

| Component | Responsibility |
|---|---|
| `ContributionExtractor` | LLM call: reads contribution section, returns `List<MemberContribution>` with name, summary, and estimated percent |
| `IndividualScoreCalculator` | Pure Java: applies the formula above, produces `adjustedScore` + `adjustmentFactor` per member |
| `IndividualFeedbackGenerator` | LLM call per member: given group criterion scores + contribution summary, returns personalized strengths/improvements/feedback |
| `IndividualMarkService` | Orchestrates extractor → calculator → generator → persist |
| `IndividualMarkController` | REST endpoints (below) |

---

## New API Endpoints

```
POST   /api/evaluations/{evalId}/individual-marks
       Body: { memberId?: Long }   // omit to generate for all members
       → triggers extraction + scoring + feedback for one or all members
       → returns List<IndividualMarkResponse>

GET    /api/evaluations/{evalId}/individual-marks
       → returns all IndividualMark records for this evaluation

PATCH  /api/individual-marks/{markId}
       Body: { tutorOverride?: Int, tutorNote?: String, finalized?: Boolean }
       → tutor overrides score or adds a note

GET    /api/submissions/{submissionId}/individual-marks
       → shortcut: returns individual marks across all evaluations for this submission
```

---

## Contribution Extraction — LLM Prompt Design

The `ContributionExtractor` sends a single LLM call with the full document (same truncation logic as `EvidenceExtractor`) and asks:

```
Read the contribution section of this capstone report.
For each named student, extract:
  - Their name (exactly as written)
  - A concise summary of what they contributed (1-2 sentences)
  - Their stated contribution percentage, if explicitly mentioned (null otherwise)

Respond with ONLY a JSON array:
[
  {
    "studentName": "<name>",
    "contributionSummary": "<what they did>",
    "statedPercent": <number or null>
  }
]
```

The extracted names are then fuzzy-matched against `GroupMember.studentName` to link records. Unmatched names are flagged for tutor attention.

---

## Individual Feedback — LLM Prompt Design

One call per member, cheap (max 400 tokens, temperature 0.3):

```
You are writing individual feedback for a student in a group capstone project.

Group assessment summary:
  [list of criterion name + level + one-line justification]
  Overall: [level] [score/max]

This student's contribution:
  [contributionSummary from extraction]

Write personalized feedback for this student only. Respond with ONLY JSON:
{
  "overall_feedback": "<2-3 sentences acknowledging their specific contribution and the group result>",
  "strengths": ["<strength 1>", "<strength 2>"],
  "improvements": ["<suggestion 1 tied to their contribution area>", "<suggestion 2>"]
}
```

---

## Trigger Points

Individual marking is **not automatic** — it is triggered explicitly by the tutor because:
- Contribution extraction can fail (section missing, unclear names)
- The tutor may want to run group eval first and review before generating individual marks
- Not all tasks require individual marking (e.g. progress checks may use group scores only)

Trigger options:
1. **Manual** — tutor clicks "Generate Individual Marks" button on the evaluation detail page
2. **Batch** — new endpoint to run individual marking for all submissions in a task at once
3. **Auto (optional, future)** — flag on `ProjectTask` to auto-trigger after every hybrid/LLM eval

---

## UI Changes

### Evaluation Detail Page (`ResultPage`)

Add a new **"Individual Marks"** tab next to the existing criteria tab when `individualMarks` exist:

```
[ Criteria Scores ]  [ Individual Marks ]  [ Debug ]

Individual Marks tab shows a table:
  Member Name | Contribution % | Group Score | Adjusted Score | Level | Actions
  ------------|----------------|-------------|----------------|-------|--------
  Alice        | 50%           | 18/25       | 21/25          | PROF  | View / Edit
  Bob          | 30%           | 18/25       | 16/25          | COMP  | View / Edit
  Carol        | 20%           | 18/25       | 11/25          | DEVEL | View / Edit

Clicking "View" opens a drawer with personalized strengths, improvements, overall feedback.
Clicking "Edit" lets the tutor override the score and add a note.
```

### Submission List Page

Add an indicator badge on rows where individual marks exist and are not yet finalized (e.g., an amber "Pending review" chip).

---

## What Is Not In Scope (for this design)

- **Student-facing individual feedback release**: The `finalized` flag is persisted but the student portal UI is out of scope here.
- **Per-criterion individual scoring**: Adjusting individual scores at criterion level (not just overall) — too complex for the first version.
- **Automatic contribution detection from git/code**: Out of scope — this design relies solely on the contribution section in the PDF.
- **Peer assessment integration**: Students rating each other — a separate workflow.

---

## Open Questions for Tutor

1. **Floor/ceiling factors**: What are acceptable bounds? Suggested 0.6–1.2 (±20% / can't fall below 60%). Should these be per-task or system-wide?
2. **Equal-contribution fallback**: If the contribution section is absent or ambiguous, should the system give everyone the group score (safest), or block and require tutor input?
3. **Release control**: Should individual marks be hidden from students until the tutor finalizes all marks in the group? Or released per-student as they are finalized?
4. **Score scale**: Should `adjusted_score` snap to the same valid-score scale as the group rubric, or allow any integer within the max?
