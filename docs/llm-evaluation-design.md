# LLM Evaluation Engine Design

## Overview

The LLM evaluation engine uses large language models to perform semantic, context-aware assessment of student capstone documents. Unlike the rule-based engine (which uses keyword matching), the LLM engine understands meaning, assesses quality of argumentation, and provides nuanced feedback with section-linked evidence.

Key capabilities:
- **Dynamic prompt assembly** from rule package criteria
- **Multi-round evaluation** with intelligent document splitting
- **Section-linked evidence** with sentiment and confidence
- **Configurable LLM settings** (model, temperature, prompt template)

---

## Architecture

```
                     Submission
                         |
                         v
              +---------------------+
              | EvaluationOrchestrator |
              +---------------------+
                         |
                         v
              +---------------------+
              | LlmEvaluationEngine |
              +---------------------+
                         |
          +--------------+--------------+
          |              |              |
          v              v              v
+------------------+ +---------+ +------------------+
| EvaluationRound  | | Dynamic | | Round            |
| Planner          | | Prompt  | | Executor         |
| (rule-based      | | Builder | | (LLM calls)      |
|  orchestrator)   | |         | |                  |
+------------------+ +---------+ +------------------+
                         |              |
                         v              v
              +---------------------+
              | SynthesisAggregator |
              +---------------------+
                         |
                         v
                 EvaluationResult
                 + EvaluationRound[]
```

---

## Multi-Round Strategy

The system uses a **rule-based orchestrator** (not an LLM) to decide how to split a document for evaluation. This keeps orchestration costs at zero while ensuring appropriate coverage.

### Strategy Selection

| Condition | Strategy | Rounds |
|-----------|----------|--------|
| < 3,000 words OR <= 5 sections | `SINGLE_PASS` | 1 evaluation round |
| 3,000 - 12,000 words | `TWO_PASS` | 2 parallel evaluation + 1 synthesis |
| > 12,000 words | `MULTI_PASS` | N chunks (~6,000 words each) + 1 synthesis |

### Round Types

- **SECTION_EVAL** — Evaluates a subset of document sections against assigned criteria
- **SYNTHESIS** — Reconciles results from multiple evaluation rounds into final scores

### Section Affinity

The planner matches sections to criteria via keyword analysis:

```
Section Heading Keywords         →  Criteria Assignment
─────────────────────────────────────────────────────────
methodology, approach, design,   →  METHODOLOGY
process, architecture
implementation, code, develop,   →  IMPLEMENTATION_DETAIL
function, module, technical
logic, explanation, reasoning,   →  LOGIC_EXPLANATION
decision, system, overview
```

Unmatched sections are evaluated against all criteria.

### Two-Pass Grouping

For medium documents, sections are split by affinity:
- **Round 1**: Sections with methodology/logic affinity
- **Round 2**: Sections with implementation affinity
- Sections matching multiple or no criteria go to whichever round has fewer words

### Multi-Pass Chunking

For long documents, sections are packed into chunks of ~6,000 words. Each chunk evaluates all criteria but only against its assigned sections. The synthesis round reconciles across chunks.

### Parallel Execution

Independent evaluation rounds execute in parallel via `CompletableFuture`. Only the synthesis round waits for all evaluation rounds to complete.

---

## Dynamic Prompt Assembly

### Template System

The system uses a template with three placeholders:

| Placeholder | Source | Purpose |
|-------------|--------|---------|
| `{{criteria_block}}` | Enabled rules' `llmCriterionPrompt` fields | What to evaluate |
| `{{output_format}}` | LlmConfig `outputFormatTemplate` or built-in default | How to structure the response |
| `{{additional_context}}` | LlmConfig `additionalContext` field | Tutor-provided notes |

### Default System Prompt Template

```
You are an experienced IT capstone project assessor evaluating a student's
technical implementation document.

Evaluate the following document against these criteria:

{{criteria_block}}

{{additional_context}}

Respond in the following JSON format:
{{output_format}}
```

### Criteria Block Assembly

For each enabled rule in the assigned rule package, the system appends its `llmCriterionPrompt`:

```
## Criterion: Logic Explanation (key: LOGIC_EXPLANATION)
[contents of rule.llmCriterionPrompt]

## Criterion: Methodology (key: METHODOLOGY)
[contents of rule.llmCriterionPrompt]

## Criterion: Implementation Detail (key: IMPLEMENTATION_DETAIL)
[contents of rule.llmCriterionPrompt]
```

If a rule has no `llmCriterionPrompt`, the system falls back to the rule's `description` field.

### Output Format

The LLM is instructed to return structured JSON:

```json
{
  "criteria": {
    "<ruleKey>": {
      "score": 6 | 12 | 18 | 24 | 30,
      "level": "EXCELLENT | PROFICIENT | COMPETENT | DEVELOPING | INADEQUATE",
      "confidence": 0.0 - 1.0,
      "justification": "Detailed explanation of why this score was given",
      "evidence": [
        {
          "sectionName": "System Architecture",
          "sectionIndex": 2,
          "quote": "Direct quote from the document",
          "sentiment": "positive | negative",
          "note": "Why this evidence matters"
        }
      ],
      "suggestions": [
        "Actionable improvement tied to a specific section"
      ]
    }
  },
  "overall_score": 24,
  "overall_level": "PROFICIENT",
  "overall_feedback": "Summative paragraph",
  "strengths": ["..."],
  "improvements": ["..."]
}
```

---

## LLM Configuration

### Entity: LlmConfig

| Field | Type | Purpose |
|-------|------|---------|
| `name` | String (unique) | Human identifier (e.g., "Strict Grading") |
| `systemPromptTemplate` | TEXT | Custom template (null = use built-in default) |
| `additionalContext` | TEXT | Tutor notes injected into prompt |
| `outputFormatTemplate` | TEXT | Custom output schema (null = use built-in default) |
| `temperature` | Double | LLM temperature (default: 0.1) |
| `maxTokens` | Integer | Max response tokens (default: 4096) |
| `provider` | String | Override provider (null = system default from env) |
| `model` | String | Override model (null = provider default) |
| `isDefault` | Boolean | Used when no project-specific config is set |

### Config Resolution

```
1. Project has a specific LlmConfig assigned → use it
2. Fallback → use the system default (isDefault = true)
3. No default exists → use built-in hardcoded template
```

### Provider Support

The system supports multiple LLM providers:

| Provider | Environment Variable | Default Model |
|----------|---------------------|---------------|
| DeepSeek | `EVAL_LLM_DEEPSEEK_API_KEY` | deepseek-chat |
| OpenAI | `EVAL_LLM_OPENAI_API_KEY` | gpt-4o |
| Anthropic | `EVAL_LLM_ANTHROPIC_API_KEY` | claude-sonnet-4-20250514 |
| Gemini | `EVAL_LLM_GEMINI_API_KEY` | gemini-pro |

The active provider is set via `EVAL_LLM_PROVIDER` or overridden per LlmConfig.

---

## Evidence Format

### Section-Linked Evidence

Each criterion score includes evidence items linking back to specific document sections:

```json
{
  "sectionName": "4.2 Database Design",
  "sectionIndex": 7,
  "quote": "We chose SQLite with WAL mode for concurrent read access",
  "sentiment": "positive",
  "note": "Demonstrates understanding of concurrency trade-offs"
}
```

| Field | Purpose |
|-------|---------|
| `sectionName` | Which section the evidence was found in |
| `sectionIndex` | Zero-based index into the parsed section list |
| `quote` | Direct text from the document |
| `sentiment` | Whether this supports (`positive`) or weakens (`negative`) the score |
| `note` | Evaluator's annotation explaining relevance |

### Confidence Score

Each criterion receives a confidence value (0.0 to 1.0) indicating how certain the LLM is about its assessment:

| Range | Meaning |
|-------|---------|
| 0.85 - 1.0 | High confidence — clear evidence supports the score |
| 0.7 - 0.84 | Moderate — some ambiguity but overall clear |
| 0.5 - 0.69 | Low — limited evidence or conflicting signals |
| < 0.5 | Very low — insufficient evidence (may need human review) |

### Suggestions

Per-criterion actionable suggestions tied to specific sections:

```json
"suggestions": [
  "Section 3 should explain WHY the layered architecture was chosen, not just describe it",
  "Add a comparison table of considered alternatives in the Methodology section"
]
```

---

## Evaluation Round Audit Trail

### Entity: EvaluationRound

Each LLM call is recorded as an `EvaluationRound`:

| Field | Purpose |
|-------|---------|
| `roundNumber` | Sequential order (1, 2, 3...) |
| `roundType` | `SECTION_EVAL` or `SYNTHESIS` |
| `inputSections` | JSON array of section indices included |
| `targetCriteria` | JSON array of rule keys evaluated |
| `rawResponse` | Full LLM response text |
| `promptTokens` | Input token count |
| `completionTokens` | Output token count |
| `status` | `SUCCESS` or `FAILED` |
| `startedAt` | Round start timestamp |
| `completedAt` | Round end timestamp |

This enables:
- Debugging evaluation quality issues
- Tracking per-round token costs
- Replaying evaluations with different configs
- Identifying slow or failing rounds

---

## Synthesis Logic

### Single-Pass

For `SINGLE_PASS` strategy, the single round output is parsed directly into the final `EvaluationResult`. No synthesis round is needed.

### Multi-Round Synthesis

For `TWO_PASS` and `MULTI_PASS`:

1. All evaluation round outputs are collected
2. A synthesis prompt is built containing:
   - Structured outputs from each round (not raw document text)
   - List of criteria to reconcile
   - Instruction to produce final reconciled scores
3. The LLM produces final scores considering all rounds
4. If synthesis fails, a fallback averages partial scores with `confidence = 0.5`

### Score Snapping

All scores are snapped to valid rubric values: `6, 12, 18, 24, 30`. If the LLM returns an intermediate value (e.g., 21), it's snapped to the nearest valid score (24).

---

## API Reference

### LLM Config Management

| Endpoint | Method | Access | Description |
|----------|--------|--------|-------------|
| `GET /api/llm-config` | GET | Admin/Tutor | List all configs |
| `GET /api/llm-config/{id}` | GET | Admin/Tutor | Get config details |
| `POST /api/llm-config` | POST | Admin/Tutor | Create config |
| `PUT /api/llm-config/{id}` | PUT | Admin/Tutor | Update config |
| `DELETE /api/llm-config/{id}` | DELETE | Admin/Tutor | Delete config |
| `POST /api/llm-config/{id}/preview` | POST | Admin/Tutor | Preview assembled prompt |

### Prompt Preview

The preview endpoint assembles the full system prompt without calling the LLM:

```
POST /api/llm-config/1/preview?rulePackageId=2
```

Response:
```json
{
  "systemPrompt": "You are an experienced IT capstone project assessor...",
  "estimatedTokens": 1847,
  "criteriaIncluded": ["LOGIC_EXPLANATION", "METHODOLOGY", "IMPLEMENTATION_DETAIL"]
}
```

### Running LLM Evaluation

```
POST /api/evaluations/run/{submissionId}?method=LLM
```

The system automatically:
1. Resolves the rule package for the submission
2. Loads the default (or project-specific) LLM config
3. Plans evaluation rounds based on document size
4. Executes rounds (parallel where possible)
5. Synthesizes final results
6. Persists EvaluationResult + EvaluationRound records

---

## Frontend Integration

### LLM Config Page (`/admin/llm-config`)

Three tabs:

1. **Configurations** — CRUD table for LLM configs with create/edit modal
2. **Prompt Preview** — Select config + rule package to see the assembled prompt
3. **How It Works** — Documentation explaining multi-round strategy

### Enhanced Result Display

The result page shows:
- Per-criterion score with progress bar (out of 30)
- Confidence indicator per criterion
- Section-linked evidence with colored sentiment badges
- Actionable suggestions with lightbulb icons
- Expandable sub-scores table

Evidence rendering:
- **Positive evidence**: Green background, checkmark icon, section tag
- **Negative evidence**: Amber background, warning icon, section tag
- **Quote**: Italicized in quotation marks
- **Note**: Secondary text below the quote

---

## Design Decisions

| Decision | Choice | Rationale |
|----------|--------|-----------|
| Orchestrator type | Rule-based (not LLM) | Zero cost, deterministic, fast; LLM orchestration adds latency and unpredictability |
| Round splitting | Word count + section count thresholds | Simple, predictable; avoids over-engineering for edge cases |
| Section affinity | Keyword matching on headings | Good enough for structured academic documents; headings are reliable signals |
| Score format | Fixed 5-level (6/12/18/24/30) | Matches institutional rubric; score snapping prevents invalid grades |
| Evidence format | Backward-compatible union type | Old string[] evaluations still render; new evaluations get rich evidence objects |
| Synthesis fallback | Average with low confidence | Better than failing entirely; confidence=0.5 signals human review needed |
| LlmConfig separate from RulePackage | Yes | Controls HOW (model, temp) vs WHAT (criteria); orthogonal concerns |
| Template placeholders | Simple string replacement | No templating engine dependency; three placeholders cover all use cases |

---

## Error Handling

| Scenario | Behavior |
|----------|----------|
| LLM API timeout | Round marked FAILED; if other rounds succeed, synthesis uses partial data |
| Invalid JSON response | Retry once with stricter format instruction; then mark FAILED |
| All rounds fail | Fall back to legacy single-call evaluation |
| No enabled rules in package | Fall back to legacy hardcoded prompt |
| LlmConfig not found | Use built-in default template |
| Score out of valid range | Snap to nearest valid value (6/12/18/24/30) |

---

## Token Cost Estimation

Approximate token usage per strategy:

| Strategy | System Prompt | User Prompt (per round) | Response | Total (3 criteria) |
|----------|--------------|------------------------|----------|---------------------|
| SINGLE_PASS | ~1,500 | ~3,000 | ~2,000 | ~6,500 |
| TWO_PASS | ~1,500 x2 + synthesis | ~3,000 x2 | ~2,000 x3 | ~15,000 |
| MULTI_PASS (3 chunks) | ~1,500 x3 + synthesis | ~6,000 x3 | ~2,000 x4 | ~30,000 |

Actual usage is recorded in `EvaluationRound.promptTokens` and `completionTokens` for cost tracking.
