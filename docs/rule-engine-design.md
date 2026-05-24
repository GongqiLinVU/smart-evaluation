# Rule-Based Evaluation Engine Design

## Overview

The rule-based evaluation engine performs deterministic, keyword-driven analysis of student capstone documents. It operates without LLM calls, using pattern matching, section detection, and heuristic scoring to produce fast, reproducible results.

---

## Core Concepts

### Rules

A **Rule** is a single evaluation criterion with:

| Field | Purpose |
|-------|---------|
| `ruleKey` | Unique identifier (e.g., `LOGIC_EXPLANATION`) |
| `name` | Human-readable name |
| `description` | What the rule evaluates |
| `category` | Grouping: `LOGIC`, `METHODOLOGY`, or `IMPLEMENTATION` |
| `llmCriterionPrompt` | Full LLM evaluation instruction (used by LLM engine, not rule engine) |

Rules are the atomic unit shared between both evaluation engines. The rule-based engine uses their keys and categories; the LLM engine uses their `llmCriterionPrompt` field.

### Rule Packages

A **Rule Package** is a curated collection of rules with per-rule weighting and enable/disable toggles. It controls which criteria are evaluated and how scores are weighted.

```
Rule Package: "Final Report Standard"
  |
  +-- RulePackageItem: LOGIC_EXPLANATION (enabled, weight: 1.0)
  +-- RulePackageItem: METHODOLOGY (enabled, weight: 1.0)
  +-- RulePackageItem: IMPLEMENTATION_DETAIL (enabled, weight: 1.0)
  +-- RulePackageItem: CUSTOM_RULE_X (disabled, weight: 0.5)
```

**Package-level settings:**

| Field | Purpose |
|-------|---------|
| `logicEnabled` | Master toggle for LOGIC category rules |
| `methodologyEnabled` | Master toggle for METHODOLOGY category rules |
| `implementationEnabled` | Master toggle for IMPLEMENTATION category rules |
| `logicWeight` | Weight multiplier for all LOGIC rules |
| `methodologyWeight` | Weight multiplier for all METHODOLOGY rules |
| `implementationWeight` | Weight multiplier for all IMPLEMENTATION rules |
| `isDefault` | Whether this package is used when no project-specific package is assigned |

### Package Resolution

When an evaluation runs, the system resolves which rule package to use:

```
1. Submission has a Task → use Task's rulePackageId (if set)
2. Submission has a Project → use Project's rulePackageId (if set)
3. Fallback → use the system default package (isDefault = true)
```

---

## Rule-Based Scoring Engine

### Architecture

```
ParsedDocument
       |
       v
+------------------+     +------------------+
| Section Detector | --> | Keyword Matcher  |
+------------------+     +------------------+
       |                         |
       v                         v
+------------------+     +------------------+
| Structure Scorer | --> | Content Scorer   |
+------------------+     +------------------+
       |                         |
       +------------+------------+
                    |
                    v
           +------------------+
           | Score Aggregator |
           +------------------+
                    |
                    v
            EvaluationResult
```

### How It Works

**1. Document Parsing**

The `DocumentParserService` extracts structured content from uploaded `.docx` files:
- Section headings and hierarchy
- Paragraph text per section
- Code blocks and snippets
- Images and diagrams (detected, not OCR'd)
- Word count per section and total

**2. Section Mapping**

Each section heading is matched against keyword dictionaries to determine which rubric dimension it relates to:

| Keywords in Heading | Mapped Dimension |
|--------------------|------------------|
| methodology, approach, design, process, architecture | Methodology |
| implementation, code, development, function, module | Implementation |
| logic, explanation, reasoning, decision, system | Logic |

**3. Criterion Evaluation**

For each enabled rule in the package, the engine:

1. Identifies relevant sections via keyword mapping
2. Scans for evidence indicators (keywords, patterns, structural elements)
3. Computes a raw quality signal based on:
   - Presence/absence of expected content
   - Depth indicators (word count, sub-heading depth, code snippet count)
   - Structural quality (proper sections, logical flow)
   - Technical vocabulary density

**4. Score Mapping**

Raw quality signals are mapped to the 5-level rubric scale:

| Level | Points | Quality Signal Range |
|-------|--------|---------------------|
| Excellent | 30 | Signal >= 0.85 |
| Proficient | 24 | Signal >= 0.65 |
| Competent | 18 | Signal >= 0.45 |
| Developing | 12 | Signal >= 0.25 |
| Inadequate | 6 | Signal < 0.25 |

**5. Evidence Collection**

The engine collects textual evidence for each score:
- Relevant quotes from the document
- Section references where evidence was found
- Specific indicators that influenced the score

---

## Built-in Rules

### LOGIC_EXPLANATION

Evaluates how well the student explains the logic behind their system.

**What it looks for:**
- Function/module purpose explanations
- Data flow descriptions
- Decision rationale ("why" not just "what")
- Error/edge case awareness
- Technical depth beyond surface descriptions

### METHODOLOGY

Evaluates the student's technical approach and development process.

**What it looks for:**
- Architecture justification
- Technology selection rationale
- Development process documentation
- Design pattern recognition
- Comparison against alternatives

### IMPLEMENTATION_DETAIL

Evaluates precision and completeness of implementation documentation.

**What it looks for:**
- Code-to-explanation mapping
- Configuration details
- Data structure documentation
- Integration documentation
- Module coverage completeness

---

## Custom Rules

Tutors and admins can create custom rules with:

1. A unique `ruleKey` (e.g., `TESTING_COVERAGE`)
2. A descriptive `name` and `description`
3. A `category` (determines which weight group it falls under)
4. An optional `llmCriterionPrompt` (used only by the LLM engine)

Custom rules can be added to any rule package and weighted independently.

---

## API Reference

### Rules CRUD

| Endpoint | Method | Description |
|----------|--------|-------------|
| `GET /api/rules` | GET | List all rules |
| `GET /api/rules/{id}` | GET | Get rule details |
| `POST /api/rules` | POST | Create rule |
| `PUT /api/rules/{id}` | PUT | Update rule |
| `DELETE /api/rules/{id}` | DELETE | Delete rule |

### Rule Packages CRUD

| Endpoint | Method | Description |
|----------|--------|-------------|
| `GET /api/rule-packages` | GET | List all packages |
| `GET /api/rule-packages/{id}` | GET | Get package with items |
| `POST /api/rule-packages` | POST | Create package with items |
| `PUT /api/rule-packages/{id}` | PUT | Update package and items |
| `DELETE /api/rule-packages/{id}` | DELETE | Delete package |

### Request/Response Examples

**Create Rule:**
```json
{
  "ruleKey": "TESTING_COVERAGE",
  "name": "Testing Coverage",
  "description": "Evaluates test strategy and coverage documentation",
  "category": "IMPLEMENTATION",
  "llmCriterionPrompt": "Evaluate how well the student documents their testing strategy..."
}
```

**Create Rule Package:**
```json
{
  "name": "Final Report v2",
  "description": "Enhanced rubric for final submissions",
  "logicEnabled": true,
  "methodologyEnabled": true,
  "implementationEnabled": true,
  "logicWeight": 1.0,
  "methodologyWeight": 1.0,
  "implementationWeight": 1.0,
  "isDefault": false,
  "items": [
    { "ruleId": 1, "enabled": true, "weight": 1.0 },
    { "ruleId": 2, "enabled": true, "weight": 1.5 },
    { "ruleId": 3, "enabled": true, "weight": 1.0 }
  ]
}
```

---

## Scoring Configuration

The system supports a **composite scoring** model that blends results from multiple evaluation methods:

| Component | Default Weight | Description |
|-----------|---------------|-------------|
| Rule-based | 0.3 | Deterministic keyword/pattern analysis |
| LLM-based | 0.5 | AI-powered semantic evaluation |
| Tutor review | 0.2 | Human expert assessment |

Weights are configurable via `GET/PUT /api/scoring/weights`. The composite score is computed as:

```
composite = (rule_score * rule_weight + llm_score * llm_weight + tutor_score * tutor_weight)
            / sum_of_available_weights
```

Only components with actual scores contribute; missing components are excluded from the denominator.

---

## Data Seeding

On application startup, `RuleDataSeeder` ensures the three built-in rules exist. If they already exist but lack an `llmCriterionPrompt`, it backfills the default prompts. This ensures both engines can function immediately without manual configuration.
