# Decision Discovery Report — Project Evaluation System

**Research Framework:** Decision Experience Theory (DET) — Pre-Architecture Survey  
**Hypothesis under investigation:** "Decision Episode is the minimal computable unit of human experience."  
**Date:** 2026-06-20  
**Analyst role:** Decision Intelligence Research Analyst  
**Scope:** Full stack — backend models, evaluation engines, frontend pages, business workflows, rubric logic

---

## Section 1: System Overview

### What this system is

The Project Evaluation System is an AI-assisted academic marking platform for IT capstone projects at a university. Its primary artifact is a student's **written technical implementation document** (.docx). The system orchestrates three distinct evaluation methods — rule-based signal detection, multi-round LLM scoring, and hybrid evidence extraction — and combines them with a human tutor review into a weighted composite score.

The system was explicitly designed to evolve from "just evaluation" into a **decision system with explanation**: every score must be traceable back to rubric requirements checked, level boundary reasoning, and evidence anchored to document sections.

### Decision-making actors

| Actor | Type | Role in the system |
|-------|------|--------------------|
| Student | Human | Submits document, selects project/task, reads feedback, rates experience |
| Tutor | Human | Reviews scores, overrides AI judgment, adds dimensional feedback, triggers verification |
| Admin | Human | Configures the entire decision framework — rules, packages, weights, LLM configs |
| Rule-Based Engine | AI (deterministic) | Scores documents by detecting structural signals and content keywords |
| LLM Engine | AI (generative) | Scores documents by semantic interpretation across multiple reasoning rounds |
| Hybrid Engine | AI (witness + judge) | LLM extracts observations only; deterministic rules apply scoring logic |
| EvaluationOrchestrator | System | Routes decisions to the right engine, manages state transitions |
| SynthesisAggregator | System | Reconciles scores across multiple LLM rounds into one result |
| ScoringConfigService | System | Computes the final composite score from weighted method contributions |

### Assessment context

Three active rubric scales are in use:

| Rubric | Total Points | Levels | Criteria |
|--------|-------------|--------|----------|
| Final Report (Technical Document) | 30 | EXCELLENT / PROFICIENT / COMPETENT / DEVELOPING / INADEQUATE (30/24/18/12/6) | Logic Explanation, Methodology, Implementation Detail |
| Progress Report | 10 | HD / D / C / P / F (10/8/6/4/2) | Meeting Diary, Progress Organisation, Completion Demo |
| Full Final Assessment | 70 | HD / D / C / P / F | Code+Manual (25), Technical Document (30), Poster (5), Video (5), Team Eval (1–5) |

The system currently evaluates **only the Technical Implementation Document component (30 points)** of the full assessment, leaving Code, Poster, Video, and Oral Presentation outside any automated evaluation loop.

---

## Section 2: Complete Decision Flow Diagram

```
═══════════════════════════════════════════════════════════════════════════════
                        DECISION FLOW — PROJECT EVALUATION SYSTEM
═══════════════════════════════════════════════════════════════════════════════

  ADMIN LAYER (Configuration Decisions)
  ┌──────────────────────────────────────────────────────────────────────────┐
  │  [D-A1] Define Rules           [D-A2] Configure Rule Package            │
  │  [D-A3] Set LLM Config         [D-A4] Assign Package to Project/Task    │
  │  [D-A5] Set Composite Weights  [D-A6] Generate Hybrid Evidence Qs       │
  │  [D-A7] Manage Users & Roles                                            │
  └──────────────────────────────────────────────────────────────────────────┘
                                    │
                                    ▼
  STUDENT LAYER (Submission Decisions)
  ┌──────────────────────────────────────────────────────────────────────────┐
  │  [D-S1] Select Project/Task    [D-S2] Attach GitHub URL                 │
  │  [D-S3] Upload Document        [D-S4] Version selection (implicit)      │
  └──────────────────────────────────────────────────────────────────────────┘
                                    │
                                    ▼
  SYSTEM LAYER (Automatic Decisions)
  ┌──────────────────────────────────────────────────────────────────────────┐
  │  [D-SY1] Resolve RulePackage   Task → Project → Default                 │
  │  [D-SY2] Resolve LlmConfig     Task → Default                           │
  │  [D-SY3] Document Parse        Extract sections, stats, structure        │
  │  [D-SY4] Plan Evaluation       Single / Two-pass / Multi-pass           │
  └──────────────────────────────────────────────────────────────────────────┘
                                    │
                      ┌─────────────┼─────────────┐
                      ▼             ▼              ▼
  ┌──────────────┐  ┌──────────────┐  ┌──────────────────────────┐
  │ RULE ENGINE  │  │  LLM ENGINE  │  │     HYBRID ENGINE        │
  │              │  │              │  │                          │
  │ [D-R1]       │  │ [D-L1]       │  │ [D-H1] LLM: Extract      │
  │ Detect       │  │ Plan rounds  │  │ evidence observations    │
  │ signals      │  │              │  │                          │
  │              │  │ [D-L2]       │  │ [D-H2] Rules: Score      │
  │ [D-R2]       │  │ Score each   │  │ deterministically        │
  │ Map to       │  │ criterion    │  │                          │
  │ PerformLevel │  │              │  │ [D-H3] Fire decision     │
  │              │  │ [D-L3]       │  │ trace rules              │
  │ [D-R3]       │  │ Synthesise   │  │                          │
  │ Weight &     │  │ across       │  │ [D-H4] Map points to     │
  │ aggregate    │  │ rounds       │  │ rubric level             │
  └──────┬───────┘  └──────┬───────┘  └──────────┬───────────────┘
         │                 │                      │
         └─────────────────┴──────────────────────┘
                                    │
                                    ▼
  SYNTHESIS LAYER
  ┌──────────────────────────────────────────────────────────────────────────┐
  │  [D-SY5] Store EvaluationResult                                         │
  │  [D-SY6] Compute CompositeScore    RuleBased% × w + LLM% × w + Tutor% × w │
  │  [D-SY7] Visibility Control        PUBLIC (student) vs INTERNAL (tutor) │
  └──────────────────────────────────────────────────────────────────────────┘
                                    │
                    ┌───────────────┼───────────────┐
                    ▼               ▼               ▼
  TUTOR LAYER (Human Review Decisions)
  ┌──────────────────────────────────────────────────────────────────────────┐
  │  [D-T1] Read AI result         [D-T2] Accept or challenge score         │
  │  [D-T3] Run Verification       [D-T4] Adjust score with reason          │
  │  [D-T5] Write Tutor Review     [D-T6] Add/remove review dimensions      │
  └──────────────────────────────────────────────────────────────────────────┘
                                    │
                                    ▼
  STUDENT EXPERIENCE LAYER
  ┌──────────────────────────────────────────────────────────────────────────┐
  │  [D-S5] Read feedback          [D-S6] Compare versions                  │
  │  [D-S7] Rate the feedback      [D-S8] Decide next action (appeal/revise) │
  └──────────────────────────────────────────────────────────────────────────┘

═══════════════════════════════════════════════════════════════════════════════
  BATCH PATHWAY (Admin-triggered, bypasses individual student decisions)
═══════════════════════════════════════════════════════════════════════════════

  [D-B1] Upload multiple files  →  [D-B2] Parse filenames for groups
  [D-B3] Match/create groups    →  [D-B4] Choose batch method (LLM/Hybrid)
  [D-B5] Sequential evaluation  →  [D-B6] Export results CSV

═══════════════════════════════════════════════════════════════════════════════
```

---

## Section 3: Decision Point Catalogue

Each entry follows this structure:
- **Decision Name** — short label
- **Description** — what is being decided
- **Inputs** — information consumed
- **Decision Maker** — Human / AI / System
- **Outputs** — what gets produced
- **Downstream Effects** — what changes as a result
- **Current Persistence Method** — where it is stored
- **Decision Episode Candidate** — Yes / No / Partial

---

### Cluster A — Administrative Configuration Decisions

---

**DP-A01: Define an Evaluation Rule**

| Field | Value |
|-------|-------|
| Description | Admin decides what a criterion is: its name, category, scoring prompt, signal keywords, and performance level descriptions. This is a meta-decision — it encodes the meaning of quality into the system. |
| Inputs | Admin's pedagogical knowledge; rubric document; category (LOGIC / METHODOLOGY / IMPLEMENTATION); llmCriterionPrompt text |
| Decision Maker | Human (Admin) |
| Outputs | `Rule` entity with ruleKey, description, llmCriterionPrompt |
| Downstream Effects | All future evaluations using this rule inherit its definition; if changed, all prior evaluations become retrospectively inconsistent |
| Persistence | `rule` table in MySQL; `ruleKey` is unique and acts as a stable identifier |
| Episode Candidate | **Partial** — the act of writing a criterion definition is a decision, but no outcome tracking exists against whether the rule actually differentiates student quality |

---

**DP-A02: Assemble a Rule Package**

| Field | Value |
|-------|-------|
| Description | Admin selects which rules apply, sets per-rule weights, enables/disables criteria, chooses a scoring scale (30-pt or 10-pt), and optionally sets max marks per criterion. |
| Inputs | Available rules; pedagogical goals for this assessment type; course-level decisions about weighting |
| Decision Maker | Human (Admin) |
| Outputs | `RulePackage` entity with enabled criteria, weights, scoringScale JSON |
| Downstream Effects | All evaluations using this package score against these weights; changing weights retroactively changes how future evaluations compare to past ones |
| Persistence | `rule_package` + `rule_package_item` tables |
| Episode Candidate | **Yes** — this is the foundational judgment about "what counts" in this assessment |

---

**DP-A03: Configure LLM Behaviour**

| Field | Value |
|-------|-------|
| Description | Admin writes the system prompt template, output format schema, temperature, max tokens, and chooses the AI model. This encodes the evaluator's "personality" and instructional framing. |
| Inputs | Prompt engineering knowledge; rubric requirements; desired LLM behaviour; provider/model capabilities |
| Decision Maker | Human (Admin) |
| Outputs | `LlmConfig` entity |
| Downstream Effects | Changes the quality, style, and structure of every LLM evaluation running with this config |
| Persistence | `llm_config` table |
| Episode Candidate | **Yes** — the decision about how to instruct an AI evaluator is itself a high-stakes judgment |

---

**DP-A04: Assign Package / Config to Project or Task**

| Field | Value |
|-------|-------|
| Description | Admin decides which rule package and LLM config applies to a project or specific task. If not set, the system cascades to the default. |
| Inputs | Project context; task-specific requirements; available packages and configs |
| Decision Maker | Human (Admin) |
| Outputs | `ProjectTask.rulePackage`, `ProjectTask.llmConfig` FK assignments |
| Downstream Effects | All submissions under this task are evaluated with these settings |
| Persistence | Foreign keys on `project_task` table |
| Episode Candidate | **Partial** — a policy decision, not easily reversible without re-evaluating all submissions |

---

**DP-A05: Set Composite Scoring Weights**

| Field | Value |
|-------|-------|
| Description | Admin decides how much the rule-based score, LLM score, and tutor review each contribute to the final composite score. Currently defaults to 40% / 30% / 30%. |
| Inputs | Trust in each evaluation method; availability of tutor reviews; institutional policy |
| Decision Maker | Human (Admin) |
| Outputs | `SystemConfig` entries for rule_based_weight, llm_weight, tutor_weight |
| Downstream Effects | All composite score calculations change immediately; student-visible scores change |
| Persistence | `system_config` key-value table |
| Episode Candidate | **Yes** — a high-stakes meta-judgment about epistemic trust in AI vs. human assessors |

---

**DP-A06: Generate Hybrid Evidence Questions**

| Field | Value |
|-------|-------|
| Description | Admin triggers LLM to auto-generate evidence questions and scoring rules for a criterion. Admin can then edit, accept, or reject the generated questions before saving. |
| Inputs | Rule's llmCriterionPrompt; rubric context; LLM response |
| Decision Maker | **Hybrid** — LLM proposes; human reviews and approves |
| Outputs | `RulePackageItem.evidenceQuestions` (JSON); `RulePackageItem.scoringRules` (JSON) |
| Downstream Effects | Shapes what observations the Hybrid engine will extract from every future document |
| Persistence | `rule_package_item` columns evidenceQuestions, scoringRules |
| Episode Candidate | **Yes** — a collaborative human-AI decision about what constitutes valid evidence |

---

**DP-A07: Manage Users and Roles**

| Field | Value |
|-------|-------|
| Description | Admin assigns roles (STUDENT / TUTOR / ADMIN), edits academic year and semester, and adds users to projects. Role assignment governs all downstream access permissions. |
| Inputs | Institutional enrolment data; admin judgment; bulk import via UI |
| Decision Maker | Human (Admin) |
| Outputs | `User.role`, `ProjectMember` records |
| Downstream Effects | Controls who can see evaluations, run batch jobs, adjust scores |
| Persistence | `user.role`, `project_member` table |
| Episode Candidate | **No** — administrative maintenance, not an evaluative decision |

---

### Cluster S — Student Submission Decisions

---

**DP-S01: Select Project and Task**

| Field | Value |
|-------|-------|
| Description | Student selects which project and which specific task their document belongs to. This determines which rubric and LLM config will evaluate their work. |
| Inputs | Available projects (from API); available tasks for project; student's own awareness of their assignment |
| Decision Maker | Human (Student) |
| Outputs | `submissionProjectId`, `submissionTaskId` in POST /api/submissions/upload |
| Downstream Effects | Triggers cascading rule package resolution (Task → Project → Default) |
| Persistence | `submission.project_id`, `submission.task_id` FK |
| Episode Candidate | **Partial** — if student selects wrong task, all downstream evaluation is mis-calibrated; no correction mechanism exists |

---

**DP-S02: Submit Document Version**

| Field | Value |
|-------|-------|
| Description | Student decides to upload their document (potentially a resubmission). The system auto-increments version. The decision to submit carries implicit confidence about readiness. |
| Inputs | The .docx document file; optional GitHub URL; student's internal assessment of readiness |
| Decision Maker | Human (Student) |
| Outputs | `Submission` entity with version N; auto-triggers rule-based evaluation |
| Downstream Effects | Version history created; rule-based evaluation runs immediately; LLM evaluation requires manual trigger |
| Persistence | `submission` table; file stored to disk |
| Episode Candidate | **Yes** — submission is a commitment act; the student's "readiness judgment" is implicit and unrecorded |

---

**DP-S03: Provide GitHub URL**

| Field | Value |
|-------|-------|
| Description | Student optionally attaches a GitHub repository URL. The system stores it but no current evaluation engine uses it; code quality is not evaluated automatically. |
| Inputs | GitHub repository URL (optional) |
| Decision Maker | Human (Student) |
| Outputs | `submission.github_url` stored but unused in evaluation |
| Downstream Effects | None currently — a decision with no downstream evaluation effect |
| Persistence | `submission.github_url` |
| Episode Candidate | **No** — dormant field; no decision logic attached |

---

**DP-S04: Rate the Feedback**

| Field | Value |
|-------|-------|
| Description | Student provides a 1–5 star rating and optional comment on the evaluation they received. This is the only explicit student reflection currently captured. |
| Inputs | Student's experience reading the feedback; star rating; optional comment |
| Decision Maker | Human (Student) |
| Outputs | `StudentFeedback` entity |
| Downstream Effects | Currently none — no learning loop or system calibration uses this data |
| Persistence | `student_feedback` table |
| Episode Candidate | **Yes** — the only mechanism for capturing student experiential response; currently disconnected from evaluation quality improvement |

---

### Cluster SY — System Automatic Decisions

---

**DP-SY01: Resolve Rule Package (Cascading)**

| Field | Value |
|-------|-------|
| Description | System determines which rule package applies to a submission by checking: Task-level → Project-level → System default. First match wins. |
| Inputs | `submission.task_id`, `submission.project_id`; FK relationships to rulePackage |
| Decision Maker | System (EvaluationOrchestrator) |
| Outputs | The resolved `RulePackage` object passed to the evaluation engine |
| Downstream Effects | Determines all criterion definitions, weights, and scoring scale for this evaluation |
| Persistence | Not persisted — re-computed on each evaluation run |
| Episode Candidate | **Partial** — a structural decision with significant outcome effects; its result should be recorded in EvaluationResult for traceability |

---

**DP-SY02: Choose Evaluation Round Strategy**

| Field | Value |
|-------|-------|
| Description | System decides whether to use SINGLE_PASS, TWO_PASS, or MULTI_PASS based on document length (word count + section count). This affects cost, accuracy, and context coverage. |
| Inputs | `ParsedDocument.totalWordCount`, `sectionCount`; thresholds: < 3,000 words → single; 3,000–12,000 → two-pass; > 12,000 → multi-pass |
| Decision Maker | System (EvaluationRoundPlanner) |
| Outputs | `EvaluationPlan` with strategy type and `RoundSpec` list |
| Downstream Effects | Determines how many LLM API calls are made, how sections are partitioned, and which synthesis logic runs |
| Persistence | Implicit in the number of `EvaluationRound` records stored |
| Episode Candidate | **Yes** — a consequential system judgment that currently has no audit entry |

---

**DP-SY03: Parse and Structure the Document**

| Field | Value |
|-------|-------|
| Description | System parses the .docx into structured sections, extracts statistics (word count, headings, code snippets, tables, images), and caches the result. Future evaluations reuse the cached parse. |
| Inputs | .docx file bytes |
| Decision Maker | System (DocumentParserService) |
| Outputs | `ParsedDocument` in memory; `ParsedDocumentEntity` cached in DB |
| Downstream Effects | All evaluation engines work from this parse; quality of parsing directly affects evaluation quality |
| Persistence | `parsed_document` table |
| Episode Candidate | **No** — preprocessing step, but parse quality failures are a silent error source |

---

**DP-SY04: Compute Composite Score**

| Field | Value |
|-------|-------|
| Description | System combines available evaluation method scores into a single composite score using configured weights, renormalising if some methods are unavailable. |
| Inputs | Latest EvaluationResult per method; SystemConfig weights; availability flags |
| Decision Maker | System (ScoringConfigService) |
| Outputs | `CompositeScoreResponse` with component breakdown and final level |
| Downstream Effects | This is the authoritative final score the student and tutor see |
| Persistence | Not persisted — recomputed on request |
| Episode Candidate | **Yes** — the final scoring decision should be a persisted artifact, not a volatile calculation |

---

**DP-SY05: Set Evaluation Visibility**

| Field | Value |
|-------|-------|
| Description | System decides whether an evaluation result is PUBLIC (student can see) or INTERNAL (tutor/admin only). Verification chain runs are always INTERNAL. |
| Inputs | `visibility` parameter from API call; evaluation method |
| Decision Maker | System / Human (triggered by caller intent) |
| Outputs | `EvaluationResult.visibility` |
| Downstream Effects | Controls what the student sees on their ResultPage |
| Persistence | `evaluation_result.visibility` column |
| Episode Candidate | **Partial** — a visibility gate, but its rationale is never recorded |

---

### Cluster R — Rule-Based Engine Decisions

---

**DP-R01: Detect Signals in Document**

| Field | Value |
|-------|-------|
| Description | Rule engine scans document sections for keyword signals associated with each criterion (e.g., "algorithm description", "methodology description"). Each signal has a weight. |
| Inputs | `ParsedDocument` sections; signal keyword lists per rule; section affinity mappings |
| Decision Maker | AI (deterministic rule engine) |
| Outputs | Sub-scores per signal (0–100); raw criterion score as weighted average |
| Downstream Effects | Determines criterion-level raw score which maps to PerformanceLevel |
| Persistence | `criterion_score.sub_scores` (JSON), `criterion_score.evidence` (JSON) |
| Episode Candidate | **Yes** — each signal detection is a micro-decision with traceable evidence |

---

**DP-R02: Map Raw Score to Performance Level**

| Field | Value |
|-------|-------|
| Description | System maps a 0–100 raw score to a 5-level PerformanceLevel (EXCELLENT/PROFICIENT/COMPETENT/DEVELOPING/INADEQUATE) and then to rubric points (30/24/18/12/6). |
| Inputs | Raw score (double); scoring scale (from RulePackage.scoringScale) |
| Decision Maker | System (PerformanceLevel.fromRawScore) |
| Outputs | PerformanceLevel enum; rubric point value |
| Downstream Effects | The rubric point value is what gets displayed and contributes to composite score |
| Persistence | `criterion_score.level`, `criterion_score.score` |
| Episode Candidate | **Yes** — a level boundary decision; but currently no reasoning is recorded for why a score of 64 is DEVELOPING instead of COMPETENT (threshold is 65) |

---

**DP-R03: Extract Strengths and Improvements**

| Field | Value |
|-------|-------|
| Description | Rule engine categorises criteria as strengths (raw > 65) or improvements (raw < 45), generating feedback labels. |
| Inputs | Raw criterion scores; thresholds |
| Decision Maker | System (RuleBasedEngine) |
| Outputs | `EvaluationResult.strengths`, `EvaluationResult.improvements` (JSON lists) |
| Downstream Effects | Student feedback narrative |
| Persistence | `evaluation_result.strengths`, `evaluation_result.improvements` (JSON columns) |
| Episode Candidate | **Partial** — threshold-based categorisation with no nuance |

---

### Cluster L — LLM Engine Decisions

---

**DP-L01: Score Each Criterion (LLM)**

| Field | Value |
|-------|-------|
| Description | LLM reads assigned document sections and produces a score, justification, evidence list, and confidence for each criterion. This is a semantic judgment by the AI model. |
| Inputs | Document sections (raw text); system prompt with rubric definitions; criterion description; output format schema |
| Decision Maker | AI (LLM — currently DeepSeek) |
| Outputs | JSON with criterion scores, justifications, evidence (section-linked), confidence |
| Downstream Effects | CriterionScore entities; feeds into synthesis and composite score |
| Persistence | `criterion_score` rows; `evaluation_round.raw_response` (full audit) |
| Episode Candidate | **Yes** — the core AI evaluative judgment; the most important decision point in the system |

---

**DP-L02: Synthesise Across Rounds**

| Field | Value |
|-------|-------|
| Description | When multi-round evaluation runs, the SynthesisAggregator reconciles criterion scores across rounds, resolves conflicts, and produces a single coherent EvaluationResult. Currently trusts LLM's self-reported overall score (a known bug). |
| Inputs | All per-round CriterionResults; synthesis prompt; LLM response |
| Decision Maker | **Hybrid** — LLM proposes synthesis; SynthesisAggregator structures it; known bug: overall score not recomputed by backend |
| Outputs | Final `EvaluationResult` with overallScore, overallLevel, overallFeedback |
| Downstream Effects | The authoritative single-method score shown in evaluation history |
| Persistence | `evaluation_result` row |
| Episode Candidate | **Yes** — a reconciliation decision that currently has a known arithmetic error |

---

**DP-L03: Assign Confidence Score**

| Field | Value |
|-------|-------|
| Description | LLM reports a confidence value (0.0–1.0) per criterion score. The frontend shows a warning if confidence < 70%. |
| Inputs | LLM's self-assessed uncertainty |
| Decision Maker | AI (LLM) |
| Outputs | `criterion_score.confidence`, `evaluation_result.confidence` |
| Downstream Effects | Low confidence triggers visual warning; no automated escalation currently |
| Persistence | `criterion_score.confidence` column |
| Episode Candidate | **Partial** — confidence is captured but not acted upon (no routing to mandatory human review) |

---

### Cluster H — Hybrid Engine Decisions

---

**DP-H01: Extract Evidence Observations (LLM as Witness)**

| Field | Value |
|-------|-------|
| Description | LLM answers predefined factual questions about the document (BOOLEAN, TERNARY, COUNT, LIKERT_5, TEXT types), each with a citation. LLM is explicitly forbidden from scoring. |
| Inputs | Document sections; evidence questions (JSON); citation requirement |
| Decision Maker | AI (LLM — witness role only) |
| Outputs | `EvidenceReport` with `Observation` list (questionId, answer, citation) |
| Downstream Effects | Feeds into rule engine as objective observations |
| Persistence | `evaluation_round` with roundType="HYBRID_EVIDENCE" |
| Episode Candidate | **Yes** — the separation of perception from judgment is architecturally novel |

---

**DP-H02: Fire Scoring Rules Against Observations (Rule Engine as Judge)**

| Field | Value |
|-------|-------|
| Description | Deterministic rule engine applies condition expressions against observations (e.g., "if METH_01 == true → +2 pts"). Each rule records whether it fired and why. |
| Inputs | `EvidenceReport.observations`; `ScoringRuleSet` from RulePackageItem |
| Decision Maker | AI (deterministic rules) |
| Outputs | `DecisionTrace` with rulesTriggered, totalPoints, maxPoints, performanceLevel |
| Downstream Effects | Fully auditable point accumulation per criterion |
| Persistence | `evaluation_result.rawLlmResponse` (DecisionTrace JSON) |
| Episode Candidate | **Yes** — the most transparent decision chain in the system |

---

### Cluster T — Tutor Human Review Decisions

---

**DP-T01: Accept or Challenge AI Score**

| Field | Value |
|-------|-------|
| Description | Tutor reads AI evaluation results on ResultPage and decides whether to accept, verify, or override. This is the central human-in-the-loop moment. |
| Inputs | EvaluationResult (scores, justifications, evidence, confidence); document knowledge; student context |
| Decision Maker | Human (Tutor) |
| Outputs | Implicit acceptance (no action) OR triggers DP-T02 / DP-T03 |
| Downstream Effects | If accepted, AI score stands in composite; if challenged, override or verification triggered |
| Persistence | **Not currently persisted** — a silent acceptance leaves no record |
| Episode Candidate | **Yes** — the most critical human decision in the system; currently produces no record when the answer is "accept" |

---

**DP-T02: Trigger Verification Chain**

| Field | Value |
|-------|-------|
| Description | Tutor clicks "Verify (3-Round Decision Chain)" to re-run the LLM evaluation independently for cross-checking. The new result is stored as INTERNAL visibility. |
| Inputs | Original EvaluationResult; tutor's suspicion of inaccuracy |
| Decision Maker | Human (Tutor) initiates; AI (LLM) executes |
| Outputs | New `EvaluationResult` with visibility=INTERNAL; comparison of original vs. verified scores |
| Downstream Effects | Provides second-opinion evidence for potential score adjustment |
| Persistence | New `evaluation_result` + `evaluation_round` rows |
| Episode Candidate | **Yes** — tutor's act of suspicion is itself a decision; currently only the verification result is stored, not the reason for triggering it |

---

**DP-T03: Adjust Score with Reason**

| Field | Value |
|-------|-------|
| Description | Tutor overrides the AI score by entering a new integer score (0–30) and a free-text reason. This is the system's formal human override mechanism. |
| Inputs | Original AI score; tutor's judgment; written reason |
| Decision Maker | Human (Tutor) |
| Outputs | `ScoreAdjustment` entity with originalScore, adjustedScore, reason |
| Downstream Effects | Adjustment history shown on ResultPage; affects composite score if tutor method is weighted |
| Persistence | `score_adjustment` table |
| Episode Candidate | **Yes** — the clearest example of a tracked human decision in the current system |

---

**DP-T04: Write Tutor Review**

| Field | Value |
|-------|-------|
| Description | Tutor writes dimensional feedback — overall score, overall comment, and configurable dimension cards each with score/max and justification. Tutors can add and remove dimensions freely. |
| Inputs | Student's document; AI evaluation results for context; tutor's own assessment |
| Decision Maker | Human (Tutor) |
| Outputs | `TutorReview` + `TutorReviewDimension` entities |
| Downstream Effects | Contributes to composite score (default 30% weight); provides qualitative feedback to student |
| Persistence | `tutor_review`, `tutor_review_dimension` tables |
| Episode Candidate | **Yes** — richest human evaluation record in the system |

---

**DP-T05: Set Evaluation Visibility for Student**

| Field | Value |
|-------|-------|
| Description | Implicit in the PUBLIC/INTERNAL flag: tutors running batch evaluations via SubmissionListPage default to INTERNAL, keeping student-visible scores clean until tutor is satisfied. |
| Inputs | Context of who triggered the evaluation; `visibility` param |
| Decision Maker | Human (Tutor) — implicit in workflow path taken |
| Outputs | `evaluation_result.visibility` |
| Downstream Effects | Controls student-visible result set |
| Persistence | `evaluation_result.visibility` |
| Episode Candidate | **Partial** |

---

### Cluster B — Batch Workflow Decisions

---

**DP-B01: Match Submission to Group**

| Field | Value |
|-------|-------|
| Description | System parses uploaded filenames to infer group identity (Group01_, Team03_, etc.). When no pattern matches, it creates a new group automatically. |
| Inputs | Filename string; existing group records; regex patterns |
| Decision Maker | System (bulk upload logic) |
| Outputs | `Submission.group` FK assignment; new Group records if needed |
| Downstream Effects | Group-level reporting; contribution-based score splitting |
| Persistence | `group`, `submission.group_id` |
| Episode Candidate | **Partial** — a heuristic match that can be wrong; no confidence score or admin confirmation step |

---

**DP-B02: Select Batch Evaluation Method**

| Field | Value |
|-------|-------|
| Description | Admin/Tutor selects LLM or Hybrid for batch evaluation. This choice applies uniformly to all submissions in the task. |
| Inputs | Available methods; cost/accuracy tradeoffs; task requirements |
| Decision Maker | Human (Admin/Tutor) |
| Outputs | `method` param passed to batch endpoint |
| Downstream Effects | All submissions evaluated with same method; LLM runs consume API tokens |
| Persistence | Implicit in the `evaluation_result.method` of each created record |
| Episode Candidate | **Partial** — a policy decision with no rationale storage |

---

**DP-B03: Export Results**

| Field | Value |
|-------|-------|
| Description | Admin exports evaluation results as CSV for delivery to university grading system. This is the terminal decision action — results leave the system. |
| Inputs | All EvaluationResult records for a task; composite scores; group member data |
| Decision Maker | Human (Admin) |
| Outputs | CSV file download |
| Downstream Effects | Grades submitted to LMS/university system; no feedback loop back |
| Persistence | Not persisted in system — one-way export |
| Episode Candidate | **Partial** — export timestamp could anchor a "finalization decision episode" |

---

## Section 4: Candidate Decision Episodes

A **Decision Episode** is proposed here as: *a bounded computational unit that captures (1) a decision context, (2) the information used, (3) the judgment made, (4) the outcome, and (5) a reflection or learning signal.*

The following are the strongest candidates for formalisation as Decision Episodes:

---

### DE-01: AI Criterion Scoring Episode
**Location:** `LlmEvaluationEngine` + `RoundExecutor`  
**Why:** The LLM produces a structured judgment (score + level + justification + evidence + confidence) per criterion. All five episode components are present or nearly present. The `EvaluationRound` already captures context + raw output. Missing: outcome tracking (did tutor accept?) and reflection loop.  
**Completeness:** Context ✅ | Information ✅ | Judgment ✅ | Outcome ❌ | Reflection ❌

---

### DE-02: Tutor Score Acceptance/Override Episode
**Location:** `ResultPage.tsx` → `ScoreAdjustment` or (silent acceptance)  
**Why:** When a tutor reads an AI score and decides to accept or override, this is the purest human decision moment in the system. Currently only overrides are persisted (`ScoreAdjustment`). Silent acceptance is completely invisible — there is no "I confirm this score" action.  
**Completeness:** Context ✅ | Information ✅ | Judgment ⚠️ (only if override) | Outcome ❌ | Reflection ❌

---

### DE-03: Hybrid Rule Firing Episode
**Location:** `EvidenceRuleEngine` → `DecisionTrace`  
**Why:** Each rule firing is a deterministic micro-decision with: precondition (observation), action (points awarded), and reason. The `DecisionTrace.rulesTriggered` list is the closest thing in the system to a structured decision log.  
**Completeness:** Context ✅ | Information ✅ | Judgment ✅ | Outcome ✅ | Reflection ❌

---

### DE-04: Level Boundary Classification Episode
**Location:** `PerformanceLevel.fromRawScore()` / LLM criterion scoring  
**Why:** The transition from a continuous score to a discrete level (COMPETENT vs PROFICIENT) is a consequential boundary decision. Currently a pure threshold lookup with no reasoning about why the boundary was crossed.  
**Completeness:** Context ✅ | Information ⚠️ (only raw score) | Judgment ✅ | Outcome ✅ | Reflection ❌

---

### DE-05: Student Submission Readiness Episode
**Location:** `UploadPage.tsx` → `POST /api/submissions/upload`  
**Why:** The student's act of submitting encodes an implicit self-assessment ("I believe this document is ready"). This judgment is never captured, only the submission artifact itself.  
**Completeness:** Context ⚠️ | Information ❌ | Judgment ❌ (implicit) | Outcome ✅ | Reflection ❌

---

### DE-06: Composite Score Synthesis Episode
**Location:** `ScoringConfigService.computeCompositeScore()`  
**Why:** The final composite score is a weighted judgment across three epistemically different methods. This synthesis deserves to be a persisted episode — not a computed-on-demand value — because it represents the system's final authoritative position.  
**Completeness:** Context ✅ | Information ✅ | Judgment ✅ | Outcome ✅ | Reflection ❌

---

### DE-07: Tutor Dimensional Review Episode
**Location:** `TutorReviewPanel` → `TutorReview` + `TutorReviewDimension`  
**Why:** The richest human evaluation record. Tutor writes explicit per-dimension scores with justifications. Lacks: comparison with AI scores as context, reflection on whether AI was directionally correct, and time-stamped review of prior versions.  
**Completeness:** Context ⚠️ | Information ✅ | Judgment ✅ | Outcome ✅ | Reflection ❌

---

### DE-08: Rule Package Design Episode
**Location:** `RulePackagesPage.tsx` → `RulePackage` entity  
**Why:** When an admin designs a rule package, they are making a meta-decision about what quality means for this assessment. This is the most upstream decision in the system and has zero outcome tracking — there is no feedback loop from "did this package produce fair evaluations?" back to "how should we adjust the rules?".  
**Completeness:** Context ⚠️ | Information ⚠️ | Judgment ✅ | Outcome ❌ | Reflection ❌

---

### DE-09: Evidence Question Authorship Episode
**Location:** `HybridEvaluationController` → `RulePackageItem.evidenceQuestions`  
**Why:** Admin + LLM collaboratively decide what questions will be asked of every future document. This is a design-time decision with run-time effects. No record of which questions were LLM-proposed vs. admin-edited.  
**Completeness:** Context ✅ | Information ⚠️ | Judgment ✅ | Outcome ❌ | Reflection ❌

---

### DE-10: Student Feedback Rating Episode
**Location:** `ResultPage.tsx` → `StudentFeedback`  
**Why:** Currently the only mechanism where a student reflects on their evaluation experience. A 1–5 star rating with optional comment is stored but goes nowhere — it is captured but not processed.  
**Completeness:** Context ✅ | Information ⚠️ | Judgment ✅ | Outcome ✅ | Reflection ❌

---

## Section 5: Recommended Locations to Insert a Future Decision Core

A **Decision Core** is proposed as a middleware layer that intercepts, wraps, persists, and potentially routes every Decision Episode. Below are the five highest-leverage insertion points, ranked by impact.

---

### Location 1: `EvaluationOrchestrator.evaluateSubmission()` — after result saved
**Rationale:** This is the single convergence point for all three evaluation methods. Every evaluation passes through here. Inserting a Decision Episode wrapper here would capture: method, inputs (submission, document stats, rule package), judgment (result), and the context for later outcome tracking.  
**Action:** Before returning, create a `DecisionEpisode` entity linking evaluationResultId + submissionId + method + inputs snapshot + timestamp.  
**Estimated effort:** Low — single interception point.

---

### Location 2: `ResultPage.tsx` — tutor "Accept" action (currently missing)
**Rationale:** The most critical missing decision record. When a tutor views an AI score and does nothing, the system treats it as acceptance — but this is invisible. Adding an explicit "Confirm Score" button (or even a passive "tutor viewed at [time]" event) would transform the missing acceptance episode into a trackable decision.  
**Action:** Add `POST /api/evaluations/{id}/confirm` endpoint + button on ResultPage. Store `EpisodeTutorConfirmation` with tutorId, timestamp, and optional note.  
**Estimated effort:** Medium — requires UI + backend endpoint.

---

### Location 3: `ScoringConfigService.computeCompositeScore()` — make it persist
**Rationale:** The composite score is the system's final word on a student's work. Currently it is ephemeral (recomputed on every request). Persisting it as a `FinalScoreEpisode` with the weights used and component breakdown would make the final judgment traceable and auditable across weight changes.  
**Action:** Add `persisted_composite_score` table; call save on first computation or on admin "finalise" action.  
**Estimated effort:** Medium.

---

### Location 4: `SynthesisAggregator.aggregate()` — add decision_chain field
**Rationale:** Already identified in P1 improvements. The synthesis step is where multi-round LLM scores are reconciled. Adding a `decision_chain` JSON field to `EvaluationResult` with rubric requirements checked, level comparison reasoning, and arithmetic recomputation would make this a proper decision record.  
**Action:** Extend `EvaluationResult.decisionChain` (new JSON column); modify `SynthesisAggregator` and `DynamicPromptBuilder` as per the existing P0/P1 fix plan.  
**Estimated effort:** Low — already planned.

---

### Location 5: `UploadPage.tsx` — capture student's self-assessment at submission time
**Rationale:** Before the student submits, ask one question: "How confident are you in this document? (1–5)". This single input would create a Student Self-Assessment Episode whose outcome (actual score) is known within minutes of submission. This is the seed of a genuine learning loop.  
**Action:** Add a pre-submission confidence slider to `UploadPage`; store as `SubmissionSelfAssessment(submissionId, confidence, timestamp)`.  
**Estimated effort:** Low on frontend; requires one new table.

---

## Section 6: Existing Data That Could Become Decision Episodes

The following data already exists in the system and contains all or most of the ingredients of a Decision Episode. It requires reframing, not new collection.

| Existing Data | Current Form | What Makes It an Episode | Missing Piece |
|---------------|-------------|--------------------------|---------------|
| `EvaluationRound` rows | Audit trail of each LLM call | Input (prompt) + Judgment (response) + Context (roundType, criteria) | Outcome tracking (was this round's judgment accepted by synthesis?) |
| `ScoreAdjustment` rows | Override record | Decision (adjustedScore) + Reason + Tutor identity + Timestamp | Outcome (did the adjusted score change subsequent submissions?) |
| `TutorReview` + `TutorReviewDimension` | Dimensional review | Multi-dimensional judgment + Justifications | Link to AI scores as pre-existing context; reflection on accuracy |
| `StudentFeedback` rows | Rating + comment | Student's experiential response | Connection to which specific evaluation triggered the response |
| `EvaluationResult` per method | Per-method score | Independent judgment per method | Cross-method comparison record; which method the tutor ultimately trusted |
| `ParsedDocumentEntity` | Structural parse cache | Document state at time of evaluation | Version delta (what changed between v1 and v2 document structure?) |
| `RulePackageItem.evidenceQuestions` | JSON config | Design decision about what counts as evidence | Record of which questions produced high-signal vs. low-signal observations |
| `DecisionTrace` (Hybrid) | Rule-firing log | Most complete micro-decision record in system | Student-readable explanation; link to tutor confirmation |
| `submission.version` | Integer counter | Implicit resubmission episode sequence | Self-assessment at each version; score trajectory |
| `LlmConfig.systemPromptTemplate` | Prompt text | Encoding of evaluator persona | Version history; A/B comparison of prompt versions on same documents |

---

## Section 7: Missing Data Needed for Outcome and Reflection Tracking

For the system to support a genuine decision learning loop, the following data does not currently exist and should be designed.

---

### Missing Class 1: Tutor Confirmation Record
**What it is:** An explicit record that a tutor reviewed and accepted an AI score.  
**Why missing:** Currently, inaction = acceptance. There is no "I reviewed this" event.  
**Impact:** Without it, it is impossible to distinguish "AI score accepted by tutor" from "AI score never reviewed by anyone".  
**Suggested entity:** `TutorConfirmation(id, evaluationResultId, tutorId, confirmedAt, note)`

---

### Missing Class 2: Student Self-Assessment at Submission
**What it is:** Student's own prediction of their score or confidence level before receiving the AI result.  
**Why missing:** The upload form collects no self-assessment.  
**Impact:** Without it, the gap between student expectation and AI/tutor score cannot be measured, which is one of the most educationally valuable signals.  
**Suggested entity:** `SubmissionSelfAssessment(id, submissionId, confidenceLevel, expectedLevel, submittedAt)`

---

### Missing Class 3: Cross-Version Learning Signal
**What it is:** A comparison record linking v1 and v2 (or vN-1 and vN) of the same student's submission, noting what changed structurally and how the score changed.  
**Why missing:** Version history exists (`submission.version`) but no differential analysis is run.  
**Impact:** Without it, the system cannot answer "did the feedback lead to improvement?" — a core question for any educational system.  
**Suggested entity:** `SubmissionVersionDelta(id, priorSubmissionId, newSubmissionId, structuralChanges JSON, scoreDelta, levelDelta)`

---

### Missing Class 4: Inter-Method Disagreement Record
**What it is:** An explicit record when rule-based and LLM scores diverge significantly (e.g., > 1 level difference) on the same criterion.  
**Why missing:** Both scores are stored but no comparison or flagging logic exists.  
**Impact:** Disagreements between methods are the system's most informative calibration signal. "Rule says PROFICIENT, LLM says COMPETENT on methodology" is more diagnostic than either score alone.  
**Suggested entity:** `MethodDisagreement(id, submissionId, criterionName, ruleScore, llmScore, levelDifference, flaggedAt, resolution)`

---

### Missing Class 5: Evidence Question Quality Signal
**What it is:** For the Hybrid engine, a record of which evidence questions produced useful observations vs. non-answers or low-confidence citations.  
**Why missing:** `EvidenceReport.observations` is stored per-evaluation but never aggregated across evaluations to judge question quality.  
**Impact:** Without it, the admin cannot know if the evidence questions they designed (or the LLM generated) are actually discriminating quality.  
**Suggested entity:** `EvidenceQuestionStats(questionId, packageItemId, totalAsked, nonAnswerCount, avgCitationConfidence, lastUpdated)`

---

### Missing Class 6: Composite Score Finalisation Event
**What it is:** A timestamp and actor record for when a composite score was "locked in" for submission to the university grading system.  
**Why missing:** The composite score is currently a live calculation that changes whenever weights are adjusted. There is no concept of "final" vs "provisional".  
**Impact:** Without it, a retroactive weight change could silently alter a score that a tutor believed was already submitted.  
**Suggested entity:** `FinalScoreDecision(id, submissionId, compositeScore, level, weightsUsed JSON, finalizedBy, finalizedAt)`

---

### Missing Class 7: Prompt Version Audit
**What it is:** A history of changes to `LlmConfig.systemPromptTemplate`, with the ability to link which evaluations ran with which prompt version.  
**Why missing:** `LlmConfig.updatedAt` exists but there is no version history; changing the prompt retroactively affects interpretation of all prior evaluations.  
**Impact:** Without prompt versioning, it is impossible to conduct controlled experiments on prompt quality ("did the new prompt with level boundary reasoning produce more consistent scores?").  
**Suggested entity:** `LlmConfigVersion(id, llmConfigId, versionNumber, systemPromptTemplate, createdAt, createdBy)`

---

### Missing Class 8: Appeal Decision Record
**What it is:** A structured record when a student formally disputes a score — what the original score was, what evidence they presented, what the outcome was.  
**Why missing:** The audit trail exists to support appeals, but there is no appeal workflow in the system. Appeals happen outside the system.  
**Impact:** Appeals are one of the richest sources of decision quality data — they represent explicit human challenges to AI judgments. Not capturing them is a major loss.  
**Suggested entity:** `ScoreAppeal(id, submissionId, studentId, originalScore, claimedScore, evidenceNote, reviewedBy, resolvedScore, resolvedAt, outcome)`

---

## Closing Observations for DET Research

The Project Evaluation System contains **at least 32 distinct decision points** across six actor types. Of these:

- **4 decision points** are fully persisted with structured outputs: LLM criterion scoring, score adjustment, tutor review, hybrid decision trace.
- **8 decision points** are partially persisted: round strategy, visibility, group matching, batch method selection, signal detection, level mapping.
- **20 decision points** produce no persisted record: tutor acceptance, composite finalisation, student self-assessment, version comparison, inter-method disagreement, prompt versioning.

The system's most critical insight for DET is this:

> **The most consequential human decision in the system — the tutor's acceptance or rejection of an AI score — produces no record when the answer is "accept". Experience happens, but is not captured.**

The system is architecturally capable of storing rich decision episodes. The infrastructure (MySQL, EvaluationRound, DecisionTrace) already exists. What is missing is the conceptual framing that treats every judgment — not just overrides — as a first-class event worth persisting, contextualising, and learning from.

The Hybrid engine's `DecisionTrace` and the LLM engine's `EvaluationRound` audit trail are the closest existing approximations to Decision Episodes. The gap between what they capture and what a full Decision Episode requires is primarily: *outcome tracking* (was this judgment confirmed or reversed?) and *reflection* (what does this pattern of judgments tell us about evaluation quality over time?).

These two missing dimensions — outcome and reflection — are the precise gaps that Decision Experience Theory is positioned to fill.

---

*End of Decision Discovery Report*  
*Total Decision Points catalogued: 32*  
*Candidate Decision Episodes identified: 10*  
*Missing data classes requiring design: 8*
