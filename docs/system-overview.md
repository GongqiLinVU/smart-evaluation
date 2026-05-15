# Evaluation Decision System — System Overview

## What This System Does

An AI-powered assessment decision support system that reads student technical implementation documents (`.docx` submissions) for IT capstone projects and produces structured marking proposals with justification trails.

**The human assessor remains the decision-maker.** The system proposes marks, provides evidence-backed justifications, and generates draft feedback — the assessor confirms, adjusts, or overrides.

---

## Assessment Context

### Primary Artifact: Technical Document
- Students submit a `.docx` technical implementation document
- The document describes their project's architecture, logic, methodology, and implementation
- It contains: text explanations, code snippets, diagrams, screenshots, tables
- A GitHub repository link may be included as supplementary evidence

### Rubric Structure (30 points total)
| Criterion | Focus | Weight |
|-----------|-------|--------|
| Logic Explanation | Depth of understanding and clarity of function/system logic | 10 pts |
| Methodology | Justification of technical choices, process, patterns | 10 pts |
| Implementation Detail | Precision of documentation, code-to-doc mapping, completeness | 10 pts |

Each criterion scored at 5 levels: Excellent (30) / Proficient (24) / Competent (18) / Developing (12) / Inadequate (6)

### Key Insight
The assessment evaluates **how well the student explains and justifies their work in writing**, not the code quality directly. Code is reference material to verify claims made in the document.

---

## Agent Architecture (Document-Centric)

```
┌─────────────────────────────────────────────────────────────┐
│                    ASSESSMENT PIPELINE                        │
│                                                              │
│  ┌──────────────┐    ┌──────────────────────────────────┐   │
│  │   Document   │    │     Evaluation Agents (parallel)  │   │
│  │   Parser     │───▶│  ┌─────────┐ ┌──────┐ ┌──────┐  │   │
│  │   Agent      │    │  │ Logic   │ │Method│ │Impl  │  │   │
│  └──────────────┘    │  │ Agent   │ │Agent │ │Agent │  │   │
│         │            │  └────┬────┘ └──┬───┘ └──┬───┘  │   │
│         │            └───────┼─────────┼────────┼───────┘   │
│         │                    │         │        │           │
│         ▼                    ▼         ▼        ▼           │
│  ┌──────────────┐    ┌──────────────────────────────────┐   │
│  │   Code       │    │     Synthesis Agent               │   │
│  │   Verifier   │───▶│  - Aggregate scores               │   │
│  │   (optional) │    │  - Cross-check consistency        │   │
│  └──────────────┘    │  - Generate feedback               │   │
│                      │  - Flag low-confidence items       │   │
│                      └──────────────────────────────────┘   │
│                                     │                        │
│                                     ▼                        │
│                      ┌──────────────────────────────────┐   │
│                      │     Human Review Interface        │   │
│                      │  - Proposed score + justification  │   │
│                      │  - Evidence citations              │   │
│                      │  - Confidence indicators           │   │
│                      │  - Confirm / Override / Edit       │   │
│                      └──────────────────────────────────┘   │
└─────────────────────────────────────────────────────────────┘
```

---

## Agent Roles

### 1. Document Parser Agent
**Input**: Raw `.docx` file
**Output**: Structured document representation

Responsibilities:
- Parse document into sections (by headings)
- Extract code snippets (formatted text blocks)
- Identify diagrams/screenshots (note presence and placement)
- Extract tables and their content
- Identify GitHub/external links
- Map each section to relevant rubric criteria
- Produce a "document inventory" — what's present and what's missing

### 2. Logic Evaluation Agent
**Input**: Parsed document sections mapped to Logic criterion
**Output**: Sub-criterion scores with evidence

Evaluates:
- Does each module/function have its purpose clearly stated?
- Is the data flow between components explained?
- Are design decisions justified with reasoning?
- Are error handling and edge cases addressed?
- Is there technical depth beyond surface description?

### 3. Methodology Evaluation Agent
**Input**: Parsed document sections mapped to Methodology criterion
**Output**: Sub-criterion scores with evidence

Evaluates:
- Is the architecture choice justified with alternatives considered?
- Are technology selections rationalized?
- Is the development process documented (agile, iterations, commits)?
- Are design patterns recognized and correctly applied?
- Is validation/comparison against alternatives provided?

### 4. Implementation Detail Agent
**Input**: Parsed document sections mapped to Implementation criterion
**Output**: Sub-criterion scores with evidence

Evaluates:
- Do explanations reference actual code (file names, function names, snippets)?
- Are configurations and setup details documented?
- Is the database schema/data model properly described?
- Are integration points (APIs, endpoints) specified?
- Is coverage complete (no major modules undocumented)?

### 5. Code Verification Agent (Optional)
**Input**: GitHub repo URL + claims from document
**Output**: Verification report

Responsibilities:
- Clone repo at specified ref
- Verify that functions/files mentioned in document exist
- Check if architecture description matches actual project structure
- Flag discrepancies between document claims and actual code
- Does NOT assess code quality independently

### 6. Synthesis Agent
**Input**: All dimension evaluations + verification report
**Output**: Composite decision with feedback

Responsibilities:
- Map sub-criteria scores to rubric performance levels
- Detect inconsistencies across dimensions
- Generate constructive student feedback
- Flag items requiring human attention
- Produce confidence score per criterion

---

## Component & Skill Registry

### Reusable Skills (composable across agents)

| Skill | Description | Used by |
|-------|-------------|---------|
| `docx.parse` | Parse .docx into structured sections | Document Parser |
| `docx.extract_code` | Identify and extract code snippets | Document Parser |
| `docx.extract_tables` | Parse tables into structured data | Document Parser |
| `docx.extract_links` | Find and categorize hyperlinks | Document Parser |
| `section.map_to_rubric` | Map document sections to rubric criteria | Document Parser |
| `evaluate.criterion` | Score a section against a specific criterion | All Eval Agents |
| `evaluate.find_evidence` | Locate specific evidence in parsed doc | All Eval Agents |
| `evaluate.assess_depth` | Measure explanation depth/quality | Logic, Methodology |
| `evaluate.check_completeness` | Identify gaps in coverage | Implementation Agent |
| `git.clone` | Clone a repository | Code Verifier |
| `git.verify_structure` | Check project structure matches claims | Code Verifier |
| `git.find_function` | Search for function/class definitions | Code Verifier |
| `score.map_to_level` | Map numeric score to performance level | Synthesis |
| `score.aggregate` | Combine sub-criteria into dimension score | Synthesis |
| `feedback.generate` | Produce constructive feedback text | Synthesis |
| `feedback.cite_evidence` | Attach evidence references to feedback | Synthesis |

### Agent Skill Composition

```yaml
document_parser:
  skills: [docx.parse, docx.extract_code, docx.extract_tables, docx.extract_links, section.map_to_rubric]

logic_evaluator:
  skills: [evaluate.criterion, evaluate.find_evidence, evaluate.assess_depth]
  rubric_dimension: logic_explanation
  sub_criteria: [function_purpose, data_flow, decision_rationale, edge_case_awareness, technical_depth]

methodology_evaluator:
  skills: [evaluate.criterion, evaluate.find_evidence, evaluate.assess_depth]
  rubric_dimension: methodology
  sub_criteria: [architecture_justification, technology_rationale, development_process, design_patterns, validation_comparison]

implementation_evaluator:
  skills: [evaluate.criterion, evaluate.find_evidence, evaluate.check_completeness]
  rubric_dimension: implementation_detail
  sub_criteria: [code_doc_mapping, configuration_detail, data_structure_docs, integration_docs, module_coverage]

code_verifier:
  skills: [git.clone, git.verify_structure, git.find_function]
  mode: verification_only  # does not produce scores, only verification flags

synthesis:
  skills: [score.map_to_level, score.aggregate, feedback.generate, feedback.cite_evidence]
```

---

## Data Flow

```
1. INPUT
   Student submits: final_report.docx + (optional) github_url

2. PARSE
   Document Parser → StructuredDocument {
     sections: [{heading, level, content, code_snippets[], images[], tables[]}]
     links: [{url, context}]
     inventory: {has_architecture_diagram, has_erd, has_sequence_diagram, ...}
   }

3. EVALUATE (parallel)
   Logic Agent       → DimensionEvaluation {sub_scores[], evidence[], justifications[]}
   Methodology Agent → DimensionEvaluation {sub_scores[], evidence[], justifications[]}
   Implementation Agent → DimensionEvaluation {sub_scores[], evidence[], justifications[]}

4. VERIFY (optional, if github_url present)
   Code Verifier → VerificationReport {
     confirmed_claims: [],
     unverified_claims: [],
     discrepancies: []
   }

5. SYNTHESIZE
   Synthesis Agent → AssessmentProposal {
     overall_score: number,
     overall_level: string,
     dimension_scores: {},
     feedback: {strengths, improvements, summative},
     confidence: number,
     human_attention_flags: []
   }

6. HUMAN REVIEW
   Assessor sees proposal → Confirm / Override / Edit feedback → Final grade
```

---

## What Makes This Document-Centric

| Traditional Code Assessment | Our Document-Centric Approach |
|----------------------------|-------------------------------|
| Analyses source code directly | Analyses the student's written explanation |
| Evaluates code quality metrics | Evaluates explanation quality and depth |
| Runs tests, measures coverage | Reads the student's testing documentation |
| Judges architecture from code structure | Judges how well architecture is justified in writing |
| Code is the primary artifact | Document is the primary artifact, code is reference |

This is appropriate for capstone assessment because:
1. The rubric explicitly evaluates explanation, methodology, and documentation quality
2. A student who can't explain their work may not fully understand it
3. Written communication is a key graduate competency
4. Code alone doesn't demonstrate understanding of WHY decisions were made
