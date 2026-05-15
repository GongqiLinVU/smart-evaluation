# Detailed Rubric Design for IT Capstone Final Report

## Source Rubric Analysis

The base rubric evaluates the **written technical implementation document** (not the code directly). It has three criteria dimensions assessed on a 5-level scale (30/24/18/12/6 points):

| Level | Points | Descriptor |
|-------|--------|------------|
| Excellent | 30 | Comprehensive, insightful, in-depth understanding demonstrated |
| Proficient | 24 | Thorough, clear, solid understanding |
| Competent | 18 | Sufficient but with minor gaps |
| Developing | 12 | Basic, limited depth |
| Inadequate | 6 | Fails to explain, disorganised, unclear |

### Base Criteria (from rubric)

1. **Logic Explanation** — How well does the student explain the logic behind each main function?
2. **Methodology** — How structured, clear, and justified is the methodology explanation?
3. **Implementation Detail** — How precise and complete are the implementation step descriptions?

---

## Expanded Rubric Design

Based on the sample submission structure and the base rubric, the following expanded rubric provides granular sub-criteria that an AI agent can evaluate with specific evidence pointers.

### Dimension 1: Logic Explanation (30 points)

Evaluates how well the student demonstrates understanding of their system's logic and decision-making.

| Sub-criterion | What to look for in the document | Evidence markers |
|---------------|----------------------------------|------------------|
| 1.1 Function purpose clarity | Each major function/module has its purpose clearly stated | Section headings like "Detailed Implementation", function descriptions |
| 1.2 Data flow explanation | How data moves between components is explained | Sequence diagrams, architecture descriptions, "how it works" narratives |
| 1.3 Decision rationale | WHY certain approaches were chosen over alternatives | "Why this layer is important", design pattern justifications |
| 1.4 Error/edge case awareness | Acknowledges and addresses failure scenarios | Timeout handling, fallback mechanisms, challenges section |
| 1.5 Technical depth | Goes beyond surface description to explain algorithms/logic | Extraction layers, deduplication logic, parsing strategies |

**Scoring Guide:**

- **30 (Excellent)**: Every major component has clear purpose + data flow + rationale. Technical depth shows the student could rebuild the system from the document alone. Edge cases explicitly addressed.
- **24 (Proficient)**: Most components well-explained. Data flow clear. Some rationale provided. Minor gaps in edge case coverage.
- **18 (Competent)**: Core components explained but some modules only superficially described. Data flow partially documented. Limited rationale.
- **12 (Developing)**: Only describes WHAT functions do, not WHY or HOW they interconnect. Major modules unexplained.
- **6 (Inadequate)**: Copy-paste of code without explanation, or explanations do not match actual functionality.

### Dimension 2: Methodology (30 points)

Evaluates how well the student justifies their technical approach and development process.

| Sub-criterion | What to look for in the document | Evidence markers |
|---------------|----------------------------------|------------------|
| 2.1 Architecture justification | Why this architecture was chosen | "4-layer modular architecture", layer explanations |
| 2.2 Technology selection rationale | Why specific tools/frameworks/languages were chosen | Mentions of Streamlit, FastAPI, SQLite with reasoning |
| 2.3 Development process | How the project was developed over time | Agile methodology, iteration descriptions, GitHub history references |
| 2.4 Design patterns | Recognition and application of design patterns | "Factory-like approach", "Layered architecture", "Failover mechanism" |
| 2.5 Comparison/validation | Comparison against alternatives to justify approach | Testing section comparing with Google/ChatGPT, requirements mapping |

**Scoring Guide:**

- **30 (Excellent)**: Clear architectural reasoning with alternatives considered. Technology choices justified with tradeoff analysis. Development process well-documented with evidence of iteration. Design patterns named and correctly applied.
- **24 (Proficient)**: Architecture explained with some justification. Technologies chosen with basic reasoning. Process described. Patterns mentioned.
- **18 (Competent)**: Architecture described but justification weak. Technologies listed without alternatives considered. Process mentioned but superficial.
- **12 (Developing)**: Architecture presented as given, no justification. No technology comparison. Process not documented.
- **6 (Inadequate)**: No discernible methodology. System appears assembled without planning or rationale.

### Dimension 3: Implementation Detail (30 points)

Evaluates the precision and completeness of implementation documentation.

| Sub-criterion | What to look for in the document | Evidence markers |
|---------------|----------------------------------|------------------|
| 3.1 Code-to-explanation mapping | Document references actual code with explanations | Code snippets (api_get function), file references (app.py, extractor.py) |
| 3.2 Configuration detail | Setup, configuration, and deployment described | SQLite WAL mode, page config, API base URL |
| 3.3 Data structure documentation | Database schema, data models documented | ERD, table definitions, field descriptions |
| 3.4 Integration documentation | How components connect and communicate | API endpoints, HTTP calls, parameter passing |
| 3.5 Completeness | All major modules covered, no significant gaps | Coverage of all tabs, all extraction layers, all endpoints |

**Scoring Guide:**

- **30 (Excellent)**: Every module has code references with explanations. Schema fully documented. All integrations specified with endpoints and parameters. No significant modules missing.
- **24 (Proficient)**: Most modules documented with code references. Schema mostly complete. Major integrations documented.
- **18 (Competent)**: Core modules documented but peripheral ones missing. Schema partially documented. Some integrations unclear.
- **12 (Developing)**: Only surface-level descriptions. Few code references. Schema incomplete. Integration points unclear.
- **6 (Inadequate)**: Minimal documentation. Cannot understand implementation from the document.

---

## Assessment Evidence Mapping

For the document-centric assessment, evidence is extracted from specific document sections:

```
Document Section                    → Primary Criterion    → Sub-criteria
─────────────────────────────────────────────────────────────────────────
Executive Summary                   → Logic (overview)     → 1.1, 1.2
Introduction / Purpose              → Methodology          → 2.1
System Architecture                 → All three            → 1.2, 2.1, 2.4, 3.4
Detailed Implementation (per module)→ Logic + Implementation → 1.1, 1.5, 3.1, 3.2
Sequence/Workflow Diagrams          → Logic                → 1.2, 1.4
ERD / Database Design               → Implementation      → 3.3
Testing & Validation                → Methodology          → 2.5
Challenges & Solutions              → Logic                → 1.4
Code Snippets (inline)              → Implementation      → 3.1
Screenshots / UI Documentation      → Implementation      → 3.5
GitHub link / Code reference        → Implementation      → 3.1 (verification)
```

---

## Agent Evaluation Strategy for Document Assessment

### Primary Assessment Flow (Document-Centric)

```
Input: Student .docx file + optional GitHub repo link
                    |
                    v
    +─────────────────────────────────+
    │  Document Parser Agent          │
    │  - Extract sections by heading  │
    │  - Identify code snippets       │
    │  - Catalogue diagrams/images    │
    │  - Map sections to rubric dims  │
    +─────────────────────────────────+
                    |
                    v
    +─────────────────────────────────+
    │  Logic Evaluation Agent         │
    │  - Assess explanation depth     │
    │  - Check data flow coverage     │
    │  - Verify decision rationale    │
    │  - Evaluate edge case awareness │
    +─────────────────────────────────+
                    |
    +─────────────────────────────────+
    │  Methodology Evaluation Agent   │
    │  - Assess architecture justify  │
    │  - Check tech selection reason  │
    │  - Evaluate process evidence    │
    │  - Verify pattern recognition   │
    +─────────────────────────────────+
                    |
    +─────────────────────────────────+
    │  Implementation Eval Agent      │
    │  - Check code-doc alignment     │
    │  - Assess schema completeness   │
    │  - Verify integration docs      │
    │  - Evaluate module coverage     │
    +─────────────────────────────────+
                    |
                    v
    +─────────────────────────────────+
    │  Code Verification Agent        │
    │  (Optional - if GitHub provided)│
    │  - Verify claims against code   │
    │  - Check function existence     │
    │  - Validate architecture claims │
    +─────────────────────────────────+
                    |
                    v
    +─────────────────────────────────+
    │  Synthesis & Scoring Agent      │
    │  - Aggregate sub-criteria       │
    │  - Apply scoring rubric         │
    │  - Generate justification       │
    │  - Compose feedback             │
    +─────────────────────────────────+
```

### Code Verification (Reference Only)

When a GitHub link is provided, the Code Verification Agent:
- Checks if functions mentioned in the document actually exist in the repo
- Validates that architecture claims match the actual project structure
- Verifies that the student understands their own code (not just documenting surface features)
- Does NOT independently evaluate code quality — only uses code to verify document claims

This keeps the assessment centered on the document while using code as supporting evidence.

---

## Sample Evaluation Output Structure

```json
{
  "assessment_id": "uuid",
  "student": "sample_student",
  "submission": "KidsSmart+ Technical Implementation Document",
  "overall_score": 24,
  "overall_level": "Proficient",
  "dimensions": {
    "logic_explanation": {
      "score": 24,
      "level": "Proficient",
      "sub_criteria": {
        "function_purpose_clarity": {
          "score": 5,
          "max": 6,
          "evidence": [
            "Each module (app.py, extractor.py, database.py) has clear purpose stated",
            "Section 3 provides detailed per-module explanation"
          ],
          "justification": "Strong module-level clarity. Each major component (Streamlit UI, FastAPI, ETL, Database) has purpose explicitly stated with 'Why this layer is important' explanations."
        },
        "data_flow_explanation": {
          "score": 5,
          "max": 6,
          "evidence": [
            "Sequence diagrams for Search & View, Login flows",
            "Architecture diagram shows data flow between layers"
          ],
          "justification": "Good coverage of main flows. Sequence diagrams trace user actions through all system layers. Minor gap: batch ETL flow not fully diagrammed."
        }
      }
    }
  },
  "feedback": {
    "strengths": ["..."],
    "improvements": ["..."],
    "summative": "..."
  },
  "confidence": 0.82,
  "flags": []
}
```
