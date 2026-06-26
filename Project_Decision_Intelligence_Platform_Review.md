# Project Decision Intelligence Platform — Technical & Strategic Review

**Date:** 2026-06-08  
**Scope:** Evaluate the existing Capstone Evaluation System and assess feasibility of evolving into a broader Project Decision Intelligence Platform.

---

## 1. Current System Overview

### 1.1 Architecture

| Layer | Technology | Notes |
|-------|-----------|-------|
| Backend | Java 17, Spring Boot 3.3, Spring Security, JPA/Hibernate | Monolithic, single-server deployment |
| Frontend | React 19, TypeScript, Vite, Ant Design 6 | SPA with role-based routing |
| Database | MySQL 8 | Hibernate auto-DDL, no migration tool |
| LLM Integration | DeepSeek, OpenAI, Anthropic, Gemini | Provider-abstracted via factory pattern |
| File Storage | Local filesystem (`./uploads`) | UUID-prefixed file storage |
| Auth | JWT (stateless, 24h expiry) | Role-based: ADMIN, TUTOR, STUDENT |

**Deployment model:** Single backend JAR + separate frontend dev server. No containerisation, no CI/CD pipeline observed.

### 1.2 Main Modules

| Module | Responsibility |
|--------|---------------|
| **Project Management** | Projects, tasks, members, groups |
| **Submission Pipeline** | Upload → parse → evaluate → result lifecycle |
| **Document Parser** | DOCX parsing with section extraction, heading detection |
| **Rule-Based Engine** | Keyword/signal detection with weighted scoring |
| **LLM Evaluation Engine** | Multi-round semantic assessment with synthesis |
| **Hybrid Engine** | LLM evidence extraction + deterministic rule scoring |
| **Scoring Configuration** | Composite weights, score adjustments, performance levels |
| **Feedback Loop** | Tutor reviews, student feedback, score adjustments |
| **Batch Processing** | Bulk upload, group management, sequential evaluation |
| **Export** | CSV export with per-student breakdown |
| **LLM Config Management** | Provider/model/prompt template CRUD |
| **Rule Package Management** | Configurable rule sets with enable/disable per project/task |

### 1.3 Data Collected

| Data Category | Examples |
|---------------|----------|
| Documents | Student DOCX reports (progress, final) |
| Parsed Content | Section structure, headings, word count, code snippets, tables, images |
| Evaluation Scores | Per-criterion scores, evidence citations, confidence levels |
| LLM Traces | Full prompts, raw responses, token usage per round |
| Human Judgement | Tutor review scores per dimension, score adjustments with reasons |
| Student Response | Satisfaction ratings and comments on evaluations |
| Configuration | Rule packages, LLM configs, scoring weights |
| Group Data | Team composition, contribution percentages |

### 1.4 Current Evaluation Workflow

```
[Upload DOCX] → [Parse Document] → [Select Method] → [Evaluate] → [Score + Evidence] → [Human Review] → [Final Grade]
                                          │
                        ┌─────────────────┼─────────────────┐
                        ▼                 ▼                 ▼
                   Rule-Based          LLM-Based         Hybrid
                   (signals +         (multi-round       (LLM extracts
                    keywords)          semantic)         evidence, rules
                                                        score)
```

1. Student uploads DOCX → system stores file and tracks version per task
2. Document parsed into structured sections (cached for reuse)
3. Evaluator selects method (Rule-Based, LLM, or Hybrid)
4. Engine produces: overall score, per-criterion breakdown, evidence, suggestions
5. Composite score computed (configurable: Rule 40% + LLM 30% + Tutor 30%)
6. Tutor can override with score adjustment (audit trail)
7. Student can view result and provide feedback

### 1.5 AI Usage

| Component | AI Role |
|-----------|---------|
| LLM Evaluation Engine | Semantic understanding of document quality, evidence extraction |
| Multi-Round Planner | Rule-based orchestrator (no LLM) decides chunking strategy |
| Evidence Extractor (Hybrid) | LLM answers structured evidence questions per criterion |
| Verification Chain | LLM cross-checks prior evaluation for consistency |
| Synthesis Aggregator | LLM combines multi-pass outputs into final assessment |
| Dynamic Prompt Builder | Assembles rubric-aware prompts with criteria blocks |

### 1.6 Human Feedback Usage

| Component | Human Role |
|-----------|-----------|
| Tutor Review | Per-dimension scoring and qualitative comments |
| Score Adjustment | Override AI scores with documented justification |
| Student Feedback | Rate helpfulness and fairness of evaluation |
| Composite Weighting | Admin configures how much human vs AI scores count |
| Rule Configuration | Admin/Tutor define evaluation criteria and packages |

---

## 2. Current Strengths

### 2.1 Reusable Components for a Broader Platform

| Component | Reusability | Notes |
|-----------|------------|-------|
| **EvaluationEngine interface** | HIGH | Abstract contract works for any document assessment |
| **LLM Provider Factory** | HIGH | Already supports 4 providers; easily extensible |
| **Multi-round evaluation pipeline** | HIGH | Document chunking strategy is content-agnostic |
| **Evidence format** | HIGH | Section-linked citations with confidence applicable to any document |
| **Rule Package system** | MEDIUM-HIGH | Configurable criteria weights and enable/disable per context |
| **Scoring scale system** | MEDIUM-HIGH | Dynamic valid score values already supported |
| **Composite scoring** | MEDIUM | Weighted multi-source aggregation is universal |
| **Audit trail (EvaluationRound)** | HIGH | Token tracking and prompt logging applicable everywhere |
| **JWT auth + RBAC** | HIGH | Roles easily extended |
| **Document parser** | MEDIUM | DOCX only; would need PDF, slides, pitch decks |
| **Batch processing** | MEDIUM | Sequential with delay; needs async queue for scale |

### 2.2 Evaluation Logic Modularity

**Yes, the evaluation logic is well-modularised:**
- Strategy pattern via `EvaluationEngine` interface allows drop-in new engines
- Rule-Based, LLM, and Hybrid engines are independent and swappable
- `EvaluationOrchestrator` cleanly separates orchestration from execution
- Each rule (LogicExplanationRule, MethodologyRule, etc.) is a discrete class

**Limitation:** The individual rules are hardcoded for IT capstone assessment (keyword lists like "algorithm", "meeting diary", "Gantt chart"). These would need replacement, not just configuration.

### 2.3 Rubric Configurability

**Currently configurable:**
- Rule packages with custom weights per criterion
- Custom scoring scales (6/12/18/24/30, or 2/4/6/8/10, or custom)
- LLM prompt templates with dynamic placeholders
- Evidence questions per rule (Hybrid mode)
- Scoring rules per rule (Hybrid mode, JSON-defined)

**Not yet configurable:**
- Criteria names and definitions are partially hardcoded (logic, methodology, implementation)
- Performance level names (EXCELLENT, PROFICIENT, etc.) are enum-bound
- Document sections expected are implicitly IT-capstone-shaped
- Rule implementations contain domain-specific keyword lists

### 2.4 Multi-Project-Type Support

**Partially ready:**
- Project/Task hierarchy supports different assessment types
- Each task can have its own RulePackage and LlmConfig
- Scoring scales are configurable

**Not ready:**
- No concept of "evaluation scenario" (pitch vs report vs proposal)
- No support for non-document inputs (live presentations, video, slides)
- No panel/multi-reviewer workflow
- No time-constrained evaluation mode (live judging)

---

## 3. Current Limitations

### 3.1 Tightly Coupled to Milestone Assessment

| Aspect | Coupling Issue |
|--------|---------------|
| **Domain terminology** | Entities: `capstone_eval` package, "Student", "Tutor", "Submission" |
| **Rule implementations** | Hardcoded keyword lists for IT capstone topics |
| **3 fixed criteria** | Logic Explanation, Methodology, Implementation Detail baked into data model |
| **Document assumptions** | Expects DOCX with academic report structure (sections, headings) |
| **Performance levels** | HD/D/C/P/F academic grading scale in enum |
| **Feedback language** | Auto-generated feedback references "your report" and academic contexts |
| **Batch workflow** | Assumes team-based coursework with group codes in filenames |
| **Seeded data** | Default rules and configs reference NIT3004 course structure |

### 3.2 Difficult to Reuse for Other Scenarios

| Target Scenario | Key Gaps |
|-----------------|----------|
| **Startup pitch evaluation** | No slide/video parsing; no real-time scoring; no panel multi-judge workflow; no market/team/traction criteria |
| **Incubator selection** | No application form intake; no comparison/ranking across applicants; no interview scoring |
| **Grant assessment** | No proposal template parsing; no budget analysis; no compliance checks; no multi-reviewer consensus |
| **Investment screening** | No financial model analysis; no market sizing evaluation; no deal memo generation; no portfolio fit scoring |

### 3.3 Architectural Risks & Limitations

| Risk | Impact | Severity |
|------|--------|----------|
| **No database migration tool** | Schema changes risk data loss; no version control on schema | HIGH |
| **Local file storage** | Not scalable; single-server only; no CDN or replication | HIGH |
| **Sequential batch processing** | 2s delay per evaluation; 100 submissions = 3+ minutes blocking | MEDIUM |
| **No async/queue system** | Long-running evaluations block HTTP threads | MEDIUM |
| **MySQL ENUM constraints** | PerformanceLevel and EvaluationMethod enums limit extensibility | MEDIUM |
| **No test suite** | No unit or integration tests observed | HIGH |
| **Single-tenant design** | No org/tenant isolation; all data in shared tables | HIGH |
| **No API versioning** | Breaking changes would affect all consumers | MEDIUM |
| **Hardcoded feedback templates** | Cannot customise auto-generated feedback language | LOW |
| **No WebSocket/SSE** | No real-time progress updates for long evaluations | LOW |

---

## 4. Decision Intelligence Potential

### 4.1 Mode A: Continuous Decision Intelligence

**Use cases:** Milestone tracking, research project monitoring, incubation program oversight, long-term project health.

| Capability | Current Support | Gap to Fill |
|------------|----------------|-------------|
| Periodic submission evaluation | YES (multi-version per task) | Need automated scheduling |
| Progress tracking over time | PARTIAL (progress report rubric exists) | Need trend analysis, trajectory scoring |
| Multi-stage evaluation | YES (Project → Tasks) | Need stage gates, conditional advancement |
| Evidence accumulation | YES (citations per round) | Need cross-submission evidence linking |
| Human oversight | YES (tutor review) | Need multi-reviewer workflows, consensus |
| Feedback loops | YES (student feedback) | Need action-tracking from feedback |
| Configurable criteria | PARTIAL | Need scenario-specific criterion libraries |

**Assessment:** The existing Project/Task hierarchy and multi-evaluation-method architecture provide a solid foundation. Adding scheduling, trend analysis, and multi-reviewer consensus would enable continuous intelligence.

### 4.2 Mode B: Rapid Decision Intelligence

**Use cases:** Pitch competitions, demo days, accelerator selection, grant panels, investment screening.

| Capability | Current Support | Gap to Fill |
|------------|----------------|-------------|
| Real-time scoring | NO | Need live input, WebSocket updates |
| Multi-judge panel | NO | Need concurrent multi-reviewer with aggregation |
| Time-constrained evaluation | NO | Need timer-based evaluation windows |
| Comparison/ranking | NO | Need cross-submission comparative scoring |
| Non-document inputs | NO | Need video, slides, live presentation capture |
| Quick decision brief | NO | Need automated decision summary generation |
| Audience scoring | NO | Need lightweight scoring interface for large groups |
| Deal memo / recommendation | NO | Need structured output beyond scores |

**Assessment:** Rapid mode requires significantly more new components. The LLM evaluation engine and evidence format are reusable, but the interaction model, input types, and output formats all need rethinking.

### 4.3 Combined Platform Viability

The two modes share these core requirements:
- AI-powered document/content analysis
- Configurable evaluation criteria
- Evidence-based scoring with audit trails
- Human-in-the-loop feedback
- Multi-method evaluation (automated + human)
- Composite scoring with configurable weights

**Verdict:** A unified platform is architecturally viable. The shared evaluation kernel (AI analysis + configurable criteria + evidence + audit) applies to both modes. The difference is primarily in:
- **Input handling** (documents vs live presentations vs applications)
- **Timing** (asynchronous vs real-time)
- **Reviewer model** (single tutor vs panel of judges)
- **Output format** (grade + feedback vs decision brief + ranking)

---

## 5. Upgrade vs Rebuild Recommendation

### 5.1 Comparison Matrix

| Dimension | Option 1: Upgrade Existing | Option 2: Rebuild from Scratch |
|-----------|---------------------------|-------------------------------|
| **Technical Effort** | 8–12 weeks for core refactor + 4–6 weeks per new scenario | 16–24 weeks for MVP equivalent to current + new features |
| **Reusability** | ~60% of current code reusable (evaluation engines, LLM providers, auth, DTOs) | 0% code reuse; but ~80% of design decisions reusable |
| **Speed to Market** | Faster MVP (4–6 weeks to first new scenario) | Slower (12+ weeks to first usable product) |
| **Risk** | Medium — refactoring may encounter hidden coupling | Medium-High — greenfield risk of scope creep and over-engineering |
| **Future Scalability** | Limited by monolithic architecture unless migrated incrementally | Can design for multi-tenant, event-driven from day one |
| **Product Positioning** | "Evaluation platform that grew into decision intelligence" | "Purpose-built decision intelligence platform" |
| **Maintainability** | Increasing tech debt if not properly refactored | Clean slate but requires full test suite and documentation from start |
| **Data Migration** | None needed; existing data preserved | Requires ETL for any existing evaluation data |

### 5.2 Detailed Analysis

#### Option 1: Upgrade

**Pros:**
- Working evaluation pipeline already handles the hardest problem (AI + rules + evidence)
- LLM provider abstraction, multi-round evaluation, and hybrid engine are sophisticated and tested
- Faster path to validating market interest in new scenarios
- Existing university users continue uninterrupted
- Domain knowledge embedded in code (rubric design, scoring logic) preserved

**Cons:**
- Package name `com.capstone.eval` and entity names (Student, Tutor) create cognitive friction
- No test suite means refactoring carries regression risk
- MySQL without migrations makes schema evolution risky
- Local file storage and synchronous processing limit scale
- Three hardcoded criteria in data model constrain flexibility

**Refactoring Priority:**
1. Abstract criteria from hardcoded 3 to N-configurable
2. Rename domain concepts (Submission → Assessment, Tutor → Reviewer, Student → Participant)
3. Add Flyway/Liquibase for schema management
4. Replace local storage with S3-compatible object store
5. Add async evaluation queue (Redis + worker threads)
6. Extract evaluation kernel into a separate module

#### Option 2: Rebuild

**Pros:**
- Clean multi-tenant architecture from day one
- Modern stack choices (event-driven, containerised, API-first)
- No legacy coupling to academic assessment
- Can target broader market positioning immediately
- Proper test infrastructure from start

**Cons:**
- 4–6 months before reaching feature parity with current system
- Risk of losing embedded domain knowledge
- No existing user base for validation during development
- Higher upfront cost with no revenue during build phase
- May over-engineer for unknown market requirements

**Architecture Choices if Rebuilding:**
- TypeScript/Node.js or Kotlin/Spring Boot
- PostgreSQL with proper migrations
- Event-driven evaluation pipeline (message queue)
- Multi-tenant with org/workspace isolation
- S3 for document storage
- WebSocket for real-time updates
- Docker + Kubernetes deployment

### 5.3 Recommendation: Upgrade (Option 1)

**Rationale:**
1. The evaluation kernel (AI + evidence + rules + synthesis) is the hardest and most valuable component — it already works
2. The hybrid evaluation engine is a genuine technical differentiator that took significant R&D
3. Market validation with existing university users provides a revenue base during expansion
4. Refactoring incrementally is safer than a big-bang rewrite with unknown requirements
5. New scenarios (pitch evaluation, grant assessment) can be validated with minimal new code by configuring existing engines differently

---

## 6. Recommended Target Architecture

### 6.1 Module Overview

```
┌─────────────────────────────────────────────────────────────────────┐
│                     PROJECT DECISION INTELLIGENCE PLATFORM           │
├─────────────────────────────────────────────────────────────────────┤
│                                                                     │
│  ┌──────────────┐  ┌──────────────┐  ┌──────────────────────────┐  │
│  │ Project      │  │ Document     │  │ AI Evaluation Engine     │  │
│  │ Intake       │  │ Analysis     │  │  ┌─────┐ ┌───┐ ┌──────┐ │  │
│  │ Module       │──│ Module       │──│  │Rule │ │LLM│ │Hybrid│ │  │
│  └──────────────┘  └──────────────┘  │  └─────┘ └───┘ └──────┘ │  │
│         │                │           └──────────────────────────┘  │
│         │                │                      │                   │
│  ┌──────────────┐  ┌──────────────┐  ┌──────────────────────────┐  │
│  │ Rubric       │  │ Human        │  │ Decision Brief           │  │
│  │ Config       │  │ Feedback     │  │ Generator                │  │
│  │ Engine       │  │ Module       │  │                          │  │
│  └──────────────┘  └──────────────┘  └──────────────────────────┘  │
│         │                │                      │                   │
│  ┌──────────────┐  ┌──────────────┐  ┌──────────────────────────┐  │
│  │ Panel        │  │ Q&A Recom-   │  │ Evidence & Audit         │  │
│  │ Dashboard    │  │ mendation    │  │ Trail                    │  │
│  │              │  │ Engine       │  │                          │  │
│  └──────────────┘  └──────────────┘  └──────────────────────────┘  │
│                                                                     │
│  ┌──────────────────────────────────────────────────────────────┐   │
│  │              Multi-Scenario Configuration Layer               │   │
│  │  [University] [Pitch Comp] [Incubator] [Grant] [Investment]  │   │
│  └──────────────────────────────────────────────────────────────┘   │
└─────────────────────────────────────────────────────────────────────┘
```

### 6.2 Module Specifications

#### A. Project Intake Module

| Feature | Description |
|---------|-------------|
| **Scenario templates** | Pre-configured intakes for university, pitch, grant, investment |
| **Multi-format upload** | DOCX, PDF, PPTX, video links, structured forms |
| **Application forms** | Configurable intake forms per scenario (team info, financials, timeline) |
| **Bulk intake** | ZIP upload, folder import, API-based batch submission |
| **Auto-classification** | Detect document type and route to appropriate evaluation pipeline |
| **Version tracking** | Track revisions with diff highlighting |

**Upgrade from current:** Extend `Submission` entity to support multiple file types and structured metadata. Add `Scenario` entity to define intake requirements.

#### B. Document Analysis Module

| Feature | Description |
|---------|-------------|
| **Multi-format parsing** | DOCX (existing), PDF (add), PPTX (add), video transcription (add) |
| **Structure extraction** | Headings, sections, tables, figures — already works for DOCX |
| **Content fingerprinting** | Detect plagiarism, template usage, AI-generated content |
| **Key metric extraction** | Numbers, dates, financial figures, team sizes from text |
| **Section classification** | Auto-label sections by topic (problem, solution, market, team, etc.) |
| **Evidence indexing** | Build searchable evidence index per document |

**Upgrade from current:** Extend `DocxParser` to a multi-format `DocumentAnalyzer` with pluggable parsers. Add PDF parser (Apache PDFBox) and PPTX parser (Apache POI).

#### C. AI Evaluation Engine

| Feature | Description |
|---------|-------------|
| **Strategy selection** | Rule-based, LLM, Hybrid — already exists |
| **Multi-round evaluation** | Document chunking with synthesis — already exists |
| **Evidence extraction** | Citation-linked observations — already exists |
| **Scenario-specific prompts** | Template library per scenario (pitch, grant, thesis, etc.) |
| **Comparative evaluation** | Score relative to cohort/batch, not just absolute |
| **Confidence calibration** | Track accuracy over time and adjust confidence thresholds |
| **Parallel evaluation** | Multiple criteria evaluated concurrently |

**Upgrade from current:** Mostly reuse existing engines. Add comparative scoring mode and confidence calibration feedback loop.

#### D. Rubric Configuration Engine

| Feature | Description |
|---------|-------------|
| **N-criteria support** | Remove 3-criterion limit; support 3–20 configurable criteria |
| **Scenario templates** | Pre-built rubric templates (startup: team/market/product/traction/financials) |
| **Custom scoring scales** | Already partially supported; extend to percentage, letter grade, pass/fail |
| **Criterion dependencies** | "If team_score < 3, cap overall at Competent" |
| **Weighting schemes** | Per-scenario configurable weights |
| **Rubric versioning** | Track rubric changes over time, maintain evaluation context |

**Upgrade from current:** Refactor from hardcoded 3 criteria to dynamic `List<Criterion>`. Replace PerformanceLevel enum with configurable level system.

#### E. Human Feedback Module

| Feature | Description |
|---------|-------------|
| **Multi-reviewer assignment** | Assign N reviewers per submission (panel mode) |
| **Consensus tracking** | Flag disagreements, trigger reconciliation |
| **Calibration sessions** | Show reviewers the same submission and compare scores |
| **Reviewer weighting** | Experienced reviewers carry more weight |
| **Conflict resolution** | Workflows for handling disagreements |
| **Review templates** | Per-scenario review forms |

**Upgrade from current:** Extend `TutorReview` to support multiple reviews per submission. Add consensus calculation and disagreement detection.

#### F. Decision Brief Generator

| Feature | Description |
|---------|-------------|
| **Automated summaries** | Generate 1-page decision brief from evaluation data |
| **Recommendation engine** | "Advance to next round" / "Reject" / "Invite for interview" |
| **Risk flags** | Highlight concerns (low confidence scores, criterion failures, reviewer disagreement) |
| **Comparative positioning** | Rank within cohort with percentile |
| **Export formats** | PDF brief, email digest, Slack notification |

**New module:** Build as a post-evaluation synthesis step using existing LLM infrastructure.

#### G. Panel Dashboard

| Feature | Description |
|---------|-------------|
| **Real-time scoring** | WebSocket-driven live score updates during events |
| **Multi-judge view** | See all judge scores side-by-side |
| **Leaderboard** | Live ranking during competitions |
| **Timer/scheduling** | Evaluation windows with countdown |
| **Audience participation** | QR-code based audience scoring |
| **Discussion threads** | Per-submission reviewer discussion |

**New module:** Requires WebSocket infrastructure, new frontend views, and real-time scoring aggregation.

#### H. Q&A Recommendation Engine

| Feature | Description |
|---------|-------------|
| **Gap identification** | Detect what's missing from submission for full evaluation |
| **Suggested questions** | Generate follow-up questions for panel interviews |
| **Due diligence checklist** | Auto-generate verification tasks from claims |
| **Risk probing** | Suggest questions targeting identified weaknesses |
| **Follow-up tracking** | Track which questions have been addressed |

**Upgrade from current:** Extend existing `EvidenceQuestionGenerator` (Hybrid engine). Currently generates evidence questions per criterion; expand to generate interview/panel questions.

#### I. Evidence & Audit Trail

| Feature | Description |
|---------|-------------|
| **Full traceability** | Every score → evidence → document section → raw text |
| **Decision chain** | Which rules fired, which LLM rounds contributed |
| **Token cost tracking** | Already exists per round |
| **Version history** | Score changes over time with reason |
| **Compliance export** | Generate audit reports for governance |
| **Bias detection** | Track scoring patterns for systematic bias |

**Upgrade from current:** Largely exists. Add bias detection and compliance report generation.

#### J. Multi-Scenario Support

| Scenario | Criteria Template | Input Type | Review Model | Output |
|----------|------------------|------------|--------------|--------|
| University Milestone | Logic, Methodology, Implementation | DOCX report | Tutor review | Grade + feedback |
| Startup Pitch | Team, Market, Product, Traction, Financials | Slides + video | Judge panel | Score + ranking |
| Incubator Selection | Innovation, Feasibility, Team, Market Fit | Application form | Committee | Accept/Reject + brief |
| Grant Assessment | Methodology, Impact, Budget, Feasibility | Proposal PDF | Peer review panel | Score + recommendation |
| Investment Screening | Market, Team, Product, Unit Economics | Deck + memo | Partner meeting | Pass/Reject + memo |
| Corporate Innovation | Strategic Fit, Feasibility, ROI, Risk | Business case | Committee | Prioritisation score |

**Implementation:** Add `Scenario` entity with template configurations. Each scenario defines: intake format, criteria, scoring scale, reviewer model, and output format.

---

## 7. Product Strategy

### 7.1 Evolution Roadmap

```
Phase 1 (Current)          Phase 2 (Q3 2026)           Phase 3 (Q1 2027)         Phase 4 (Q3 2027)
─────────────────          ─────────────────           ─────────────────         ─────────────────
University Milestone  →    Multi-Scenario Engine  →    Rapid Decision Mode  →   Platform & API
Evaluation                 (configurable criteria,     (panel dashboard,         (white-label,
                           PDF support, scenario       real-time scoring,        marketplace,
                           templates)                  live events)              integrations)
```

### 7.2 Market Positioning Per Segment

#### Universities (Current + Expand)

| Current State | Expansion Opportunity |
|---------------|----------------------|
| IT capstone reports | Any discipline (engineering, business, health sciences) |
| Progress + final reports | Theses, dissertations, research proposals |
| Single tutor review | Examination committee (multi-reviewer) |
| One course (NIT3004) | Institution-wide deployment |

**Go-to-market:** Expand from single course to department, then institution. Add PDF support and configurable rubrics for other disciplines.

#### Startup Competitions

| Value Proposition | Differentiation |
|-------------------|----------------|
| AI pre-screening of applications | Reduce human review from 200 to top 30 |
| Consistent evaluation across judges | Calibrate panel scoring |
| Evidence-based decision briefs | Explain why each decision was made |
| Real-time event scoring | Live leaderboard during demo days |

**Go-to-market:** Partner with 2–3 university entrepreneurship centres that already use your evaluation system. Offer competition mode as an add-on.

#### Incubators / Accelerators

| Value Proposition | Differentiation |
|-------------------|----------------|
| Standardised application review | Apply same criteria consistently across 500+ applications |
| Progress monitoring for cohort | Track incubated startups against milestones |
| Automated due diligence questions | Generate interview prep for selection committee |
| Alumni comparison | How does this applicant compare to successful alumni? |

**Go-to-market:** Continuous mode for cohort tracking + rapid mode for application selection. Natural extension of university pipeline.

#### Government Grant Assessment

| Value Proposition | Differentiation |
|-------------------|----------------|
| Compliance checking | Verify proposal meets all mandatory criteria |
| Conflict-of-interest detection | Flag reviewer-applicant relationships |
| Multi-panel consensus | Track inter-reviewer reliability |
| Audit trail for public accountability | Full decision transparency |

**Go-to-market:** Offer as compliance + efficiency tool. Grant panels review 100s of proposals under time pressure; AI pre-screening + evidence extraction saves reviewer time.

#### Corporate Innovation Programs

| Value Proposition | Differentiation |
|-------------------|----------------|
| Strategic alignment scoring | Evaluate against corporate strategy criteria |
| Portfolio balance | Consider pipeline diversity when scoring |
| Stage-gate evaluation | Automated milestone reviews with human approval |
| ROI prediction | Comparative analysis against past internal projects |

**Go-to-market:** Position as "internal Shark Tank" tool for corporate innovation teams. Stage-gate evaluation is the continuous mode; quarterly reviews are the rapid mode.

#### Angel Investors / Early-Stage Funds

| Value Proposition | Differentiation |
|-------------------|----------------|
| Deal flow screening | Score inbound decks against fund thesis |
| Pattern matching | Compare to past investments (what worked) |
| Due diligence acceleration | Auto-generate question lists from pitch claims |
| Partner meeting prep | Decision brief with evidence and risk flags |

**Go-to-market:** Offer freemium for individual angels; paid tier for syndicates/funds. Evidence-based scoring addresses the "gut feel" problem in early-stage investing.

### 7.3 Pricing Model Evolution

| Phase | Model | Target |
|-------|-------|--------|
| Phase 1 | Free / institutional license | Universities |
| Phase 2 | Per-scenario pricing ($500–2000/event) | Competitions, grants |
| Phase 3 | SaaS subscription ($200–1000/month) | Incubators, corporate |
| Phase 4 | Platform + API (usage-based) | Funds, large institutions |

---

## 8. Final Recommendation

### 8.1 Decision: Upgrade the Current System

**Rationale:**
- The evaluation kernel (3 engines + evidence + audit trail + composite scoring) represents 70% of the hard technical work for a decision intelligence platform
- The hybrid evaluation engine (LLM perception + rule judgment) is a genuine differentiator that no competitor offers out-of-the-box
- Rebuilding would take 4–6 months with no revenue; upgrading can deliver new value in 4–6 weeks
- Market validation should happen with minimal code, not maximum code

### 8.2 Priority Refactoring (First 2 Weeks)

| Priority | Task | Effort | Impact |
|----------|------|--------|--------|
| P0 | Add Flyway database migrations | 2 days | Unlocks safe schema evolution |
| P0 | Make criteria dynamic (N criteria instead of hardcoded 3) | 3 days | Core flexibility requirement |
| P1 | Add `Scenario` entity and scenario templates | 2 days | Multi-use-case support |
| P1 | Add PDF parser (Apache PDFBox) | 2 days | Expands input types |
| P1 | Rename domain concepts in API (backward-compatible aliases) | 1 day | Professional positioning |
| P2 | Add async evaluation queue | 3 days | Scalability |
| P2 | Replace local storage with S3-compatible | 1 day | Deployment flexibility |

### 8.3 What to Reuse

| Component | Reuse Strategy |
|-----------|---------------|
| Evaluation engines (all 3) | Keep as-is; configure per scenario |
| LLM provider abstraction | Keep as-is; already multi-provider |
| Multi-round evaluation pipeline | Keep as-is; works for any document |
| Evidence format and audit trail | Keep as-is; universal |
| Rule package system | Extend to support N criteria |
| Composite scoring | Keep as-is; add more component types |
| Auth + RBAC | Extend roles (add REVIEWER, PANELIST, ORGANISER) |
| Batch processing | Keep for bulk scenarios; add async mode |
| Frontend components | Keep ScoreCard, EvidencePanel, FeedbackPanel; add new views |

### 8.4 MVP Scope: Next 4–6 Weeks

**Goal:** Deliver a working "Startup Pitch Evaluation" scenario alongside the existing university scenario, proving the platform can support multiple use cases.

#### Week 1–2: Core Platform Refactoring

- [ ] Add Flyway migrations for current schema
- [ ] Refactor `CriterionScore` to support N dynamic criteria (not just logic/methodology/implementation)
- [ ] Add `Scenario` entity (name, type, criteria template, intake config, output format)
- [ ] Add PDF document parser
- [ ] Create scenario-specific rubric templates (university + pitch competition)
- [ ] Add `REVIEWER` and `ORGANISER` roles

#### Week 3–4: Pitch Evaluation Scenario

- [ ] Create pitch competition scenario template with 5 criteria (Team, Market, Product, Traction, Financials)
- [ ] Build pitch-specific LLM prompt templates
- [ ] Build pitch-specific rule package (keyword signals for market validation, revenue mention, team credentials)
- [ ] Add multi-reviewer assignment (2–3 judges per submission)
- [ ] Build judge panel view (see all submissions, score inline)
- [ ] Build ranking/leaderboard view

#### Week 5–6: Decision Output & Polish

- [ ] Build decision brief generator (1-page summary per submission)
- [ ] Add Q&A recommendation engine (suggest questions for pitch Q&A)
- [ ] Build comparison view (side-by-side submissions)
- [ ] Add CSV/PDF export for competition results
- [ ] End-to-end testing with sample pitch decks
- [ ] Deploy demo instance for stakeholder review

#### Success Criteria

| Metric | Target |
|--------|--------|
| University scenario continues working unchanged | 100% backward compatible |
| Pitch scenario evaluates a PDF pitch deck | End-to-end in < 60 seconds |
| 3 judges can independently score same submission | Panel view functional |
| System generates decision brief | Auto-generated 1-page summary |
| System suggests Q&A questions | 3–5 targeted questions per pitch |

---

## Appendix A: Technology Recommendations for Upgrade

| Concern | Current | Recommended |
|---------|---------|-------------|
| Schema management | Hibernate auto-DDL | Flyway migrations |
| File storage | Local filesystem | MinIO (S3-compatible, self-hosted) or AWS S3 |
| Async processing | None | Spring `@Async` + `CompletableFuture` (short-term); Redis + worker (medium-term) |
| Real-time updates | Polling | Spring WebSocket + STOMP |
| PDF parsing | Not supported | Apache PDFBox |
| Slide parsing | Not supported | Apache POI (PPTX) |
| Testing | None | JUnit 5 + Testcontainers (MySQL) + MockMvc |
| Deployment | Manual JAR | Docker Compose (short-term); Kubernetes (long-term) |
| Monitoring | None | Micrometer + Prometheus + Grafana |

## Appendix B: Risk Register

| Risk | Probability | Impact | Mitigation |
|------|------------|--------|-----------|
| Refactoring breaks existing evaluations | Medium | High | Add test suite before refactoring; migration scripts for data |
| LLM costs spike with new scenarios | Medium | Medium | Token budgets per evaluation; caching of common assessments |
| Market doesn't want AI-assisted judging | Low | High | Position as "decision support" not "replacement"; human always has final say |
| Competitors build similar platform | Medium | Medium | Speed to market + hybrid engine differentiator + domain expertise in education |
| Multi-tenant adds complexity too early | Low | Medium | Start single-tenant; add org isolation when first enterprise customer arrives |
| PDF parsing quality insufficient | Medium | Low | Fall back to LLM-based extraction for poorly formatted PDFs |

## Appendix C: Competitive Positioning

| Competitor Type | Their Approach | Our Differentiation |
|-----------------|---------------|---------------------|
| LMS built-in grading (Canvas, Moodle) | Simple rubric + manual scoring | AI-powered with evidence extraction |
| AI grading tools (Gradescope, Turnitin) | Code/essay-focused, no decision intelligence | Multi-scenario, configurable, evidence-based |
| Judging platforms (Judgify, Evalato) | Form-based manual scoring only | AI pre-screening + human review hybrid |
| VC deal flow tools (Affinity, Harmonic) | CRM-focused, no content evaluation | Deep content analysis + scoring |
| Grant management (Submittable) | Workflow-focused, no AI evaluation | AI evaluation kernel + decision briefs |

**Unique position:** The only platform combining AI-powered content analysis (3 evaluation methods), configurable criteria, evidence-based scoring, and human-in-the-loop decision support across multiple scenario types.

---

*End of review. Recommended next action: Begin Week 1 refactoring with Flyway migration setup and dynamic criteria model.*
