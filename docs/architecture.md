# AI-Powered Evaluation Decision System for IT Capstone Project Marking

## Architecture Document

---

## 1. Domain Model

### 1.1 Entity Relationship Overview

```
Programme
  |
  +-- Course
        |
        +-- Cohort (semester instance)
              |
              +-- CapstoneProject
              |     |
              |     +-- StudentSubmission[] ------+
              |     +-- Milestone[]               |
              |           |                       |
              |           +-- Evidence[]  <-------+
              |
              +-- AssessmentScheme
                    |
                    +-- Rubric
                    |     |
                    |     +-- RubricDimension[]
                    |           |
                    |           +-- RubricCriterion[]
                    |                 |
                    |                 +-- PerformanceLevel[]
                    |
                    +-- ModerationPolicy
                    +-- CalibrationConfig

Decision
  |
  +-- EvidenceEvaluation[]    (per-criterion scoring with justification)
  +-- AgentReasoning[]        (full chain-of-thought audit trail)
  +-- HumanOverride?          (assessor confirmation or correction)
  +-- AssessorFeedback         (final narrative feedback)

CalibrationSession
  |
  +-- CalibrationSample[]
  +-- InterMarkerAgreement
  +-- AdjustmentPolicy
```

### 1.2 Core Entity Definitions

**Assessment** — Top-level container linking a student project to an assessment scheme for a given milestone or final submission. Tracks lifecycle state: `draft | in_progress | agent_proposed | human_review | finalised | appealed`.

**Milestone** — A defined checkpoint within the capstone timeline (e.g., proposal, design review, alpha build, final submission). Each milestone specifies which rubric dimensions are evaluated and what evidence types are expected.

**Rubric** — A versioned, structured scoring guide. Contains dimensions, each dimension contains criteria, each criterion contains performance levels with descriptions and point ranges. Rubrics are immutable once published for a cohort; changes create new versions.

**RubricDimension** — One of the six standard IT capstone dimensions:
- `technical_implementation` — code quality, functionality, correctness
- `architecture_design` — system design, patterns, scalability decisions
- `testing` — test coverage, test strategy, quality assurance
- `documentation` — technical docs, user docs, inline documentation
- `process_methodology` — version control discipline, agile practices, planning
- `presentation_communication` — demo quality, written clarity, defence performance

**RubricCriterion** — A specific assessable element within a dimension (e.g., under `testing`: "unit test coverage", "integration test strategy", "edge case handling").

**PerformanceLevel** — A descriptor within a criterion mapping qualitative description to a score band (e.g., Excellent/4, Proficient/3, Developing/2, Beginning/1, Not Demonstrated/0).

**Evidence** — A typed artefact collected from a submission. Types include:
- `source_code` — repository snapshot at a point in time
- `git_history` — commit log, branch structure, contribution patterns
- `documentation` — README, design docs, API docs, user guides
- `ci_cd_artifacts` — build logs, test reports, deployment configs
- `demo_recording` — video/screencast of working software
- `peer_review` — structured feedback from team members
- `presentation_slides` — slide decks from project defence
- `reflection` — student self-assessment and reflection

**Decision** — The composite marking outcome for one assessment. Contains per-criterion evaluations, an overall score, narrative feedback, and the full audit trail of agent reasoning. Lifecycle: `proposed | confirmed | overridden | finalised`.

**EvidenceEvaluation** — A single agent judgment linking one piece of evidence to one criterion, containing: the extracted observations, the proposed performance level, a confidence score (0.0-1.0), and a natural-language justification.

**AgentReasoning** — An immutable audit record capturing: which agent produced it, the input context, the chain-of-thought, the output decision, timestamps, and model/version metadata.

**StudentSubmission** — The collection of all evidence artefacts a student or team submits for a given milestone.

**AssessorFeedback** — The final human-reviewed narrative feedback combining agent-proposed feedback with human assessor edits. Includes formative comments per dimension and a summative statement.

**HumanOverride** — Records when a human assessor changes an agent-proposed score, capturing: original score, new score, assessor identity, and the reason for override.

### 1.3 Data Schema Conventions

All entities carry: `id` (UUID), `created_at`, `updated_at`, `version` (for optimistic concurrency), and `metadata` (extensible JSON). Evidence and Decision entities are append-only for auditability — updates create new versions rather than mutating.

---

## 2. Agent Roles and Skills

### 2.1 Agent Architecture Overview

```
                         +---------------------------+
                         |   Orchestrator Agent      |
                         |   (workflow coordination) |
                         +---------------------------+
                                    |
              +---------------------+---------------------+
              |                     |                     |
    +---------v--------+  +--------v---------+  +--------v---------+
    | Evidence Collector|  | Dimension Agents |  | Synthesis Agent  |
    | Agent             |  | (6 specialists)  |  | (aggregation)    |
    +------------------+  +------------------+  +------------------+
                                    |
                           +--------+--------+
                           |   |   |   |   | |
                           v   v   v   v   v v
                        [Tech][Arch][Test][Doc][Process][Pres]
                                    |
                         +----------v----------+
                         | Calibration Agent   |
                         | (consistency check) |
                         +---------------------+
                                    |
                         +----------v----------+
                         | Feedback Composer   |
                         | Agent               |
                         +---------------------+
```

### 2.2 Agent Definitions

#### 2.2.1 Orchestrator Agent

**Purpose**: Manages the end-to-end assessment workflow for a submission. Determines which agents to invoke, in what order, handles dependencies, manages retries, and tracks overall pipeline state.

**Skills/Tools**:
- `workflow.execute(pipeline_definition, submission_id)` — Runs a defined pipeline
- `workflow.branch(condition, path_a, path_b)` — Conditional routing
- `workflow.parallel(tasks[])` — Fan-out parallel execution
- `workflow.gate(approval_required, reviewer_role)` — Human-in-the-loop gate
- `state.read(submission_id)` — Read current pipeline state
- `state.transition(submission_id, new_state)` — Advance state machine
- `notify.send(recipient, channel, message)` — Alert humans when needed

**Behavioural Rules**:
- Never skips the human review gate before finalisation
- Logs every state transition as an AgentReasoning record
- Enforces timeout policies (no submission stuck in `in_progress` for more than configurable duration)

#### 2.2.2 Evidence Collector Agent

**Purpose**: Gathers, normalises, and indexes all evidence artefacts for a submission. Performs no evaluation — purely extraction and structuring.

**Skills/Tools**:
- `git.clone_and_snapshot(repo_url, ref)` — Clone repo at specific commit/tag
- `git.analyse_history(repo_path)` — Extract commit frequency, branch patterns, contributor stats
- `code.parse_structure(repo_path)` — Build AST-level project structure map
- `code.compute_metrics(repo_path)` — Lines of code, cyclomatic complexity, dependency counts
- `ci.fetch_reports(ci_provider, project_id)` — Pull build/test/coverage reports
- `docs.extract(repo_path, doc_paths[])` — Identify and extract documentation files
- `media.transcribe(video_url)` — Transcribe demo recordings
- `lms.fetch_submission(lms_id, assignment_id, student_id)` — Pull submission from LMS
- `peer.collect(survey_id)` — Gather peer review responses
- `evidence.index(evidence[])` — Store structured evidence bundle

**Behavioural Rules**:
- Produces an `EvidenceBundle` — a structured, read-only snapshot of all evidence for downstream agents
- Flags missing expected evidence (e.g., no test reports found) as gaps rather than failures
- Sanitises student PII from evidence before passing to evaluation agents

#### 2.2.3 Dimension Agents (Six Specialists)

Each dimension has a dedicated agent. They share a common skill interface but are configured with dimension-specific rubric knowledge, evaluation heuristics, and evidence mapping.

**Common Skill Interface for All Dimension Agents**:
- `rubric.load(rubric_id, dimension)` — Load the relevant rubric criteria
- `evidence.query(bundle_id, evidence_type, filters)` — Retrieve specific evidence from the bundle
- `evaluate.criterion(criterion, evidence_refs[], context)` — Produce an EvidenceEvaluation for one criterion
- `evaluate.dimension_summary(criterion_evaluations[])` — Aggregate criterion scores into a dimension score
- `justify.generate(evaluation, evidence_refs[])` — Produce human-readable justification with evidence citations
- `confidence.assess(evaluation)` — Self-assess confidence, flagging low-confidence items for human attention
- `compare.exemplar(evaluation, calibration_samples[])` — Compare against calibration exemplars for consistency

**Dimension-Specific Configurations**:

**(a) Technical Implementation Agent**
- Additional skills: `code.review(file_paths[], criteria)`, `code.check_functionality(test_results)`, `code.detect_patterns(repo_path, pattern_library)`
- Evidence focus: source code, test results, CI build logs
- Evaluation heuristics: checks for code correctness, appropriate language/framework usage, error handling, performance considerations

**(b) Architecture and Design Agent**
- Additional skills: `architecture.detect(repo_path)`, `architecture.evaluate_decisions(design_docs, code_structure)`, `diagram.analyse(image_path)`
- Evidence focus: design documents, code structure, dependency graphs, configuration files
- Evaluation heuristics: separation of concerns, appropriate pattern usage, scalability considerations, technology selection rationale

**(c) Testing Agent**
- Additional skills: `testing.coverage_analysis(coverage_report)`, `testing.strategy_evaluation(test_files[])`, `testing.quality_assessment(test_code)`
- Evidence focus: test files, coverage reports, CI test results, test documentation
- Evaluation heuristics: test pyramid adherence, meaningful assertions, edge case coverage, test naming and organisation

**(d) Documentation Agent**
- Additional skills: `docs.completeness_check(doc_inventory, expected_docs[])`, `docs.quality_assess(document, doc_type)`, `docs.technical_accuracy(docs, code_structure)`
- Evidence focus: README, API docs, user guides, inline code comments, design documents
- Evaluation heuristics: completeness, clarity, accuracy relative to implementation, appropriate audience targeting

**(e) Process and Methodology Agent**
- Additional skills: `git.contribution_analysis(git_history, team_members[])`, `process.timeline_check(milestones, git_history)`, `process.methodology_assessment(project_management_artifacts)`
- Evidence focus: git history, project boards, sprint records, meeting notes, milestone submissions
- Evaluation heuristics: consistent commit history, meaningful commit messages, branch strategy, evidence of iterative development, team collaboration patterns

**(f) Presentation and Communication Agent**
- Additional skills: `media.evaluate_demo(transcript, rubric_criteria)`, `writing.assess(text, criteria)`, `presentation.structure_check(slides)`
- Evidence focus: demo recordings/transcripts, presentation slides, written reports, project defence notes
- Evaluation heuristics: clarity of explanation, demonstration of working software, audience awareness, response to questions, visual communication quality

#### 2.2.4 Synthesis Agent

**Purpose**: Aggregates dimension-level evaluations into a coherent overall assessment. Detects inconsistencies across dimensions, applies weighting policies, and produces the composite Decision.

**Skills/Tools**:
- `aggregate.weighted_score(dimension_scores[], weight_policy)` — Compute overall score
- `consistency.cross_check(dimension_evaluations[])` — Detect contradictions
- `threshold.check(overall_score, pass_criteria)` — Apply pass/fail thresholds and flag borderline cases
- `anomaly.detect(evaluation, cohort_statistics)` — Flag statistical outliers for human review
- `decision.compose(evaluations[], aggregation, anomalies[])` — Produce the full Decision record

**Behavioural Rules**:
- Always flags borderline cases (within configurable margin of grade boundaries) for mandatory human review
- Applies configurable dimension weights per milestone type
- Produces a structured justification for the overall grade, not just a number

#### 2.2.5 Calibration Agent

**Purpose**: Ensures consistency across the cohort by comparing evaluations against calibration benchmarks and detecting marker drift.

**Skills/Tools**:
- `calibration.load_samples(cohort_id, dimension)` — Load pre-assessed calibration submissions
- `calibration.compare(proposed_evaluation, benchmark_evaluations[])` — Measure alignment
- `calibration.drift_detect(agent_id, evaluations[], time_window)` — Detect scoring drift over time
- `calibration.adjust(evaluation, adjustment_policy)` — Propose score adjustments with justification
- `moderation.flag(evaluation, reason)` — Flag evaluations needing moderation panel review
- `statistics.cohort_distribution(cohort_id, dimension)` — Compute and report score distributions

**Behavioural Rules**:
- Runs after every batch of evaluations, not just at the end
- Never auto-adjusts scores without producing an auditable adjustment record
- Reports inter-marker agreement metrics (Cohen's kappa equivalent for agent vs. human)

#### 2.2.6 Feedback Composer Agent

**Purpose**: Transforms raw evaluations into pedagogically appropriate, constructive student-facing feedback.

**Skills/Tools**:
- `feedback.narrative(evaluations[], tone_policy, audience)` — Generate narrative feedback
- `feedback.formative(dimension_evaluation, improvement_suggestions)` — Per-dimension formative comments
- `feedback.summative(decision, grade_descriptor)` — Overall summative statement
- `feedback.personalise(feedback_draft, student_context)` — Adjust for individual student context
- `feedback.format(feedback, output_format)` — Render as markdown, HTML, PDF, or LMS-compatible format

**Behavioural Rules**:
- Feedback is always constructive — identifies strengths before areas for improvement
- Every critique is paired with a specific, actionable suggestion
- Uses evidence citations so students can see exactly what was assessed
- Never produces feedback that could not be justified by the rubric

---

## 3. Component Architecture

### 3.1 Layered Component Model

```
+============================================================================+
|                          PRESENTATION LAYER                                 |
|  +------------------+  +------------------+  +------------------+          |
|  | Assessor         |  | Student          |  | Admin            |          |
|  | Dashboard        |  | Feedback Portal  |  | Console          |          |
|  +------------------+  +------------------+  +------------------+          |
+============================================================================+
|                          API GATEWAY LAYER                                  |
|  +------------------+  +------------------+  +------------------+          |
|  | REST API         |  | Webhook          |  | GraphQL API      |          |
|  | (external)       |  | Receivers        |  | (internal)       |          |
|  +------------------+  +------------------+  +------------------+          |
+============================================================================+
|                          WORKFLOW LAYER                                     |
|  +------------------+  +------------------+  +------------------+          |
|  | Pipeline         |  | State Machine    |  | Task Queue       |          |
|  | Engine           |  | Manager          |  | (async dispatch) |          |
|  +------------------+  +------------------+  +------------------+          |
+============================================================================+
|                          AGENT LAYER                                        |
|  +------------------+  +------------------+  +------------------+          |
|  | Agent Runtime    |  | Skill Registry   |  | Tool Executor    |          |
|  | (LLM interface)  |  | (plugin system)  |  | (sandboxed)      |          |
|  +------------------+  +------------------+  +------------------+          |
+============================================================================+
|                          CORE SERVICES LAYER                                |
|  +------------+ +------------+ +------------+ +------------+               |
|  | Evidence   | | Rubric     | | Decision   | | Calibration|               |
|  | Service    | | Service    | | Service    | | Service    |               |
|  +------------+ +------------+ +------------+ +------------+               |
|  +------------+ +------------+ +------------+ +------------+               |
|  | Feedback   | | Audit      | | Notification| | Analytics |               |
|  | Service    | | Service    | | Service     | | Service   |               |
|  +------------+ +------------+ +------------+ +------------+               |
+============================================================================+
|                          INTEGRATION LAYER                                  |
|  +------------+ +------------+ +------------+ +------------+               |
|  | LMS        | | Git        | | CI/CD      | | Media      |               |
|  | Adapter    | | Adapter    | | Adapter    | | Adapter    |               |
|  +------------+ +------------+ +------------+ +------------+               |
+============================================================================+
|                          INFRASTRUCTURE LAYER                               |
|  +------------+ +------------+ +------------+ +------------+               |
|  | Database   | | Object     | | Message    | | LLM        |               |
|  | (Postgres) | | Store (S3) | | Broker     | | Gateway    |               |
|  +------------+ +------------+ +------------+ +------------+               |
+============================================================================+
```

### 3.2 Reusable Components

#### 3.2.1 Pipeline Engine

A declarative workflow engine that executes assessment pipelines defined as configuration (YAML). Pipelines are composed of stages; stages contain steps; steps invoke agents or services.

```yaml
pipeline: capstone_final_assessment
version: 2
triggers:
  - event: submission.created
    filter: milestone_type == "final"

stages:
  - name: evidence_collection
    steps:
      - agent: evidence_collector
        skill: git.clone_and_snapshot
        input: submission.repo_url
      - agent: evidence_collector
        skill: code.parse_structure
        depends_on: [step_0]
      - agent: evidence_collector
        skill: ci.fetch_reports
        parallel: true
      - agent: evidence_collector
        skill: docs.extract
        parallel: true
      - agent: evidence_collector
        skill: evidence.index
        depends_on: [all_previous]

  - name: dimension_evaluation
    parallel: true
    steps:
      - agent: technical_implementation
        input: evidence_bundle
      - agent: architecture_design
        input: evidence_bundle
      - agent: testing
        input: evidence_bundle
      - agent: documentation
        input: evidence_bundle
      - agent: process_methodology
        input: evidence_bundle
      - agent: presentation_communication
        input: evidence_bundle

  - name: synthesis
    steps:
      - agent: synthesis
        input: dimension_evaluations[]

  - name: calibration
    steps:
      - agent: calibration
        input: proposed_decision

  - name: feedback_generation
    steps:
      - agent: feedback_composer
        input: calibrated_decision

  - name: human_review
    type: gate
    config:
      required_reviewers: 1
      auto_approve_if:
        all_confidence_above: 0.85
        no_anomalies: true
        not_borderline: true
```

#### 3.2.2 Agent Runtime

The execution environment for agents. Manages:
- **LLM Interface**: Abstracts the underlying model, handling prompt construction, token management, and response parsing
- **Context Window Management**: Intelligently selects which evidence to include based on relevance scoring and token budget
- **Tool Dispatch**: Routes agent tool calls to the appropriate service
- **Retry and Fallback**: Handles transient failures, rate limits, and model unavailability
- **Observability**: Emits structured logs, traces (OpenTelemetry compatible), and metrics

#### 3.2.3 Skill Registry

A plugin system where agent skills are registered, versioned, and discoverable. Each skill is a self-contained unit with:
- **Manifest**: Name, version, description, input/output schemas (JSON Schema), required permissions
- **Implementation**: The actual tool code (may be a function, an API call, or a sub-agent invocation)
- **Test Suite**: Validation tests that run during registration to ensure skill correctness

Skills are composed into agent configurations. A dimension agent's capability is fully defined by which skills it has access to. This makes it possible to:
- Add new evidence types by registering new skills on the Evidence Collector
- Extend evaluation capabilities by adding skills to dimension agents
- Create new specialised agents by composing existing skills differently

#### 3.2.4 Evidence Service

Manages the lifecycle of evidence artefacts:
- **Ingestion**: Accepts raw artefacts from integration adapters, normalises format, computes checksums
- **Storage**: Stores raw artefacts in object storage, structured metadata in the database
- **Indexing**: Creates searchable indexes for text-based evidence (code, documentation)
- **Bundling**: Composes evidence bundles for specific assessments, respecting milestone scope
- **Versioning**: Every evidence artefact is immutable; re-submissions create new versions

#### 3.2.5 Rubric Service

Manages rubric definitions and their application:
- **CRUD**: Create, read, update rubric definitions (with version control)
- **Publishing**: Lock a rubric version for a cohort (no further edits)
- **Mapping**: Map evidence types to rubric dimensions and criteria
- **Weights**: Manage dimension and criterion weighting policies per milestone type
- **Export/Import**: Support IMS Global/Caliper rubric interchange formats

#### 3.2.6 Decision Service

Manages the decision lifecycle:
- **Proposal**: Records agent-proposed decisions with full justification
- **Review**: Supports human review workflow (approve, override, request re-evaluation)
- **Finalisation**: Locks decisions, computes final grades, records grade history
- **Appeal**: Handles grade appeal workflow (re-evaluation with different/additional agents)
- **Bulk Operations**: Supports batch finalisation, bulk export for grade submission

#### 3.2.7 Audit Service

Provides the transparency and accountability backbone:
- **Immutable Log**: Every agent reasoning step, human action, and system event is recorded
- **Trace Linking**: Links related audit records into complete assessment traces
- **Query Interface**: Supports queries like "show me all evidence that influenced this score"
- **Compliance Export**: Generates audit reports suitable for academic quality assurance reviews
- **Retention Policy**: Manages data retention in accordance with institutional policies

#### 3.2.8 Calibration Service

Manages assessment calibration and moderation:
- **Benchmark Management**: Store and retrieve calibration samples (pre-assessed submissions with agreed scores)
- **Agreement Metrics**: Compute inter-rater reliability (agent-vs-agent, agent-vs-human, human-vs-human)
- **Drift Detection**: Monitor scoring patterns over time and across the cohort
- **Moderation Workflow**: Support moderation panels where multiple assessors review flagged submissions
- **Distribution Analysis**: Provide cohort-level statistics and flag anomalous distributions

---

## 4. Decision Pipeline

### 4.1 End-to-End Data Flow

```
TRIGGER                    COLLECT                      EVALUATE
+------------------+      +-------------------+        +---------------------+
| Submission       | ---> | Evidence          | -----> | Dimension Agents    |
| Event from LMS   |      | Collector Agent   |        | (parallel, 6x)     |
| or manual trigger|      |                   |        |                     |
+------------------+      | Outputs:          |        | Each outputs:       |
                          | - EvidenceBundle  |        | - CriterionEvals[]  |
                          | - GapReport       |        | - DimensionScore    |
                          +-------------------+        | - Confidence        |
                                                       | - Justifications[]  |
                                                       +---------------------+
                                                                |
                                                                v
CALIBRATE                  SYNTHESISE
+---------------------+   +---------------------+
| Calibration Agent   |<--| Synthesis Agent      |
|                     |   |                      |
| Outputs:            |   | Outputs:             |
| - Adjustment recs   |   | - CompositeDecision  |
| - Consistency flags |   | - CrossCheckReport   |
| - Moderation flags  |   | - AnomalyFlags[]     |
+---------------------+   +---------------------+
         |
         v
COMPOSE                    REVIEW                       FINALISE
+---------------------+   +---------------------+      +------------------+
| Feedback Composer   |-->| Human Review Gate   | ---> | Decision         |
| Agent               |   |                     |      | Finalisation     |
|                     |   | Assessor can:       |      |                  |
| Outputs:            |   | - Confirm           |      | Outputs:         |
| - DraftFeedback     |   | - Override + reason |      | - FinalGrade     |
| - FormativeComments |   | - Request re-eval   |      | - FinalFeedback  |
| - SummativeStatement|   | - Edit feedback     |      | - AuditRecord    |
+---------------------+   +---------------------+      +------------------+
                                                                |
                                                                v
                                                       +------------------+
                                                       | Grade Export to   |
                                                       | LMS / Student    |
                                                       | Portal           |
                                                       +------------------+
```

### 4.2 Detailed Pipeline Stages

**Stage 1: Trigger and Ingestion**

When a submission event arrives (from LMS webhook, manual trigger, or scheduled batch), the Orchestrator Agent creates an Assessment record in `draft` state and dispatches the Evidence Collector Agent.

**Stage 2: Evidence Collection**

The Evidence Collector Agent pulls all artefacts from configured sources in three phases:
1. **Fetch**: Pull raw artefacts (clone repo, download files, fetch CI reports)
2. **Analyse**: Compute derived evidence (code metrics, git statistics, structural analysis)
3. **Bundle**: Package into a normalised EvidenceBundle with a manifest listing what was found and what is missing

The gap report identifies expected evidence not found — gaps are not failures but informative signals.

**Stage 3: Dimension Evaluation (Parallel)**

All six dimension agents execute in parallel. Each agent:
1. Loads its rubric criteria via the Rubric Service
2. Queries the EvidenceBundle for relevant evidence types
3. For each criterion, produces an `EvidenceEvaluation` with observations, proposed performance level, confidence score, and justification with evidence citations
4. Aggregates criterion evaluations into a dimension score
5. Flags low-confidence criteria (below 0.7) for mandatory human review

**Stage 4: Synthesis**

The Synthesis Agent:
1. Applies the weighting policy (e.g., technical 30%, architecture 20%, testing 15%, documentation 15%, process 10%, presentation 10%)
2. Computes weighted composite score
3. Performs cross-dimensional consistency checks
4. Applies grade boundary thresholds and identifies borderline cases
5. Runs anomaly detection against cohort statistics

**Stage 5: Calibration**

Compares the proposed decision against:
1. Pre-assessed calibration samples for this cohort
2. Historical scoring patterns for this agent configuration
3. Running cohort statistics (mean, standard deviation, distribution shape)

**Stage 6: Feedback Composition**

Transforms raw evaluations into student-facing feedback:
1. Per-dimension formative feedback (strengths, areas for improvement, specific suggestions)
2. Summative narrative
3. Constructive tone with evidence citations

**Stage 7: Human Review**

The assessor sees: proposed grade, score breakdown, justification trail, calibration report, draft feedback, and confidence heatmap. They can confirm, override (with reason), edit feedback, request re-evaluation, or escalate.

**Stage 8: Finalisation and Export**

Locks the decision, computes final grade, generates complete audit trail, publishes to LMS, and notifies the student.

### 4.3 Error Handling

- **Evidence collection failures**: Partial bundles accepted; missing evidence noted in gap report
- **Agent evaluation failures**: Individual dimension agents retried independently; persistent failures route to manual assessment
- **Calibration failures**: Insufficient samples trigger mandatory human review
- **Pipeline timeout**: Orchestrator escalates to human review with partial results

---

## 5. Integration Points

### 5.1 LMS Integration (Canvas, Moodle, Blackboard)

```
Inbound (LMS -> System):
  - Submission events (webhook or polling)
  - Student/course roster sync
  - Assignment/rubric definitions
  - Existing grades (for calibration)

Outbound (System -> LMS):
  - Grade publication (via LTI Advantage / REST API)
  - Feedback posting (as assignment comments)
  - Rubric score population (per-criterion if LMS supports it)
```

**Adapter Interface**:
```typescript
interface LMSAdapter {
  syncCourseRoster(courseId: string): Promise<Student[]>
  watchSubmissions(assignmentId: string, callback: SubmissionHandler): void
  fetchSubmission(assignmentId: string, studentId: string): Promise<LMSSubmission>
  publishGrade(assignmentId: string, studentId: string, grade: Grade): Promise<void>
  publishFeedback(assignmentId: string, studentId: string, feedback: Feedback): Promise<void>
  publishRubricScores(assignmentId: string, studentId: string, scores: RubricScores): Promise<void>
  importRubric(assignmentId: string): Promise<RubricDefinition>
  exportRubric(rubric: RubricDefinition, assignmentId: string): Promise<void>
}
```

**Protocol Support**: LTI 1.3 / LTI Advantage for SSO and grade passback; REST APIs for submission retrieval; Caliper/xAPI for learning analytics events.

### 5.2 Git Repository Integration (GitHub, GitLab, Bitbucket)

```typescript
interface GitAdapter {
  cloneAtRef(repoUrl: string, ref: string, targetDir: string): Promise<string>
  getCommitHistory(repoUrl: string, since?: Date, until?: Date): Promise<Commit[]>
  getBranchStructure(repoUrl: string): Promise<BranchInfo[]>
  getPullRequests(repoUrl: string, state: 'open' | 'closed' | 'all'): Promise<PullRequest[]>
  getContributorStats(repoUrl: string): Promise<ContributorStats[]>
  getLanguageBreakdown(repoUrl: string): Promise<LanguageStats>
}
```

### 5.3 CI/CD Integration (GitHub Actions, GitLab CI, Jenkins)

```typescript
interface CICDAdapter {
  getBuildHistory(projectId: string, limit: number): Promise<Build[]>
  getTestReport(buildId: string): Promise<TestReport>
  getCoverageReport(buildId: string): Promise<CoverageReport>
  getDeploymentHistory(projectId: string): Promise<Deployment[]>
  getBuildArtifacts(buildId: string, patterns: string[]): Promise<Artifact[]>
  getPipelineConfig(projectId: string): Promise<PipelineConfig>
}
```

### 5.4 Authentication and Authorization

Role-based access control:
- **Admin**: Full system configuration, rubric management, pipeline configuration
- **Course Coordinator**: Cohort setup, calibration management, moderation oversight, bulk operations
- **Assessor**: Review and confirm/override agent decisions, edit feedback, view audit trails
- **Moderator**: Cross-assessor comparison, moderation panel participation, calibration oversight
- **Student**: View own grades and feedback (read-only, after release)

---

## 6. Cross-Cutting Concerns

### 6.1 Audit Trail Architecture

Every action produces an audit event structured as a DAG (directed acyclic graph):

```typescript
interface AuditEvent {
  id: string                          // UUID
  timestamp: string                   // ISO8601
  actor: { type: 'agent' | 'human' | 'system', id: string, version?: string }
  action: string                      // e.g., "evaluate.criterion", "decision.override"
  target: { type: string, id: string }
  input_summary: Record<string, any>
  output_summary: Record<string, any>
  reasoning?: string                  // agent chain-of-thought
  evidence_refs: string[]             // links to evidence artefacts used
  parent_events: string[]             // causal predecessors
  metadata: Record<string, any>       // model version, latency, token counts
}
```

### 6.2 Prompt Management

Agent prompts are managed as versioned, parameterised templates:

```typescript
interface PromptTemplate {
  id: string                         // e.g., "technical_implementation.evaluate_criterion.v3"
  agent: string
  skill: string
  version: number
  template: string                   // Parameterised template
  parameters: ParameterSchema[]
  system_prompt: string              // Agent persona and behavioural rules
  examples: FewShotExample[]         // Calibration examples
  token_budget: { max_input: number, max_output: number }
  active: boolean
}
```

### 6.3 Configuration Hierarchy

```
System Defaults
  -> Institution Overrides
    -> Programme Overrides
      -> Course Overrides
        -> Cohort Overrides
          -> Milestone Overrides
```

Every configurable parameter follows this cascade. More specific levels override more general levels.

---

## 7. Technology Stack

| Layer | Technology | Rationale |
|-------|-----------|-----------|
| Language | TypeScript (Node.js) | Strong typing, excellent async, broad ecosystem |
| API | Fastify + tRPC | Type-safe API with good performance |
| Database | PostgreSQL | JSONB for flexible metadata, ACID for audit integrity |
| Object Storage | S3-compatible (MinIO self-hosted) | Evidence artefact storage |
| Message Broker | BullMQ (Redis-backed) | Pipeline decoupling, job scheduling, retries |
| LLM Gateway | Anthropic SDK | Claude as primary model with abstraction layer |
| Search | PostgreSQL full-text (initial), Elasticsearch (at scale) | Evidence indexing |
| Observability | OpenTelemetry + Grafana | Distributed tracing across agent pipeline |
| Auth | Passport.js with SAML2 | Institutional SSO integration |
| Deployment | Docker Compose (dev), Kubernetes (prod) | Horizontal scaling |

---

## 8. Project Structure

```
src/
  domain/                    # Domain model entities and value objects
    entities/
    value-objects/

  agents/                    # Agent definitions and configurations
    orchestrator/
    evidence-collector/
    dimensions/
      technical-implementation/
      architecture-design/
      testing/
      documentation/
      process-methodology/
      presentation-communication/
      shared/
    synthesis/
    calibration/
    feedback-composer/

  skills/                    # Reusable skill implementations
    registry.ts
    git/
    code/
    ci/
    docs/
    media/
    evaluation/

  services/                  # Core business services
    evidence.service.ts
    rubric.service.ts
    decision.service.ts
    calibration.service.ts
    feedback.service.ts
    audit.service.ts
    notification.service.ts
    analytics.service.ts

  pipeline/                  # Workflow engine
    engine.ts
    state-machine.ts
    task-queue.ts
    definitions/             # Pipeline YAML definitions

  integrations/              # External system adapters
    lms/
    git/
    cicd/

  api/                       # API layer
    routes/
    middleware/
    webhooks/

  config/                    # Configuration management

  infrastructure/            # Database, storage, messaging
    database/
    storage/
    messaging/
    llm/
```

---

## 9. Implementation Phases

| Phase | Duration | Scope |
|-------|----------|-------|
| 1. Foundation | Weeks 1-3 | Domain model, Rubric Service, Evidence Service, Audit Service, Agent Runtime, Skill Registry |
| 2. Core Pipeline | Weeks 4-6 | Evidence Collector, one Dimension Agent prototype, Pipeline Engine, basic Synthesis, Human Review API |
| 3. Full Evaluation | Weeks 7-9 | Remaining dimension agents, parallel execution, full Synthesis, Feedback Composer, Calibration |
| 4. Integration | Weeks 10-12 | LMS adapter (Canvas), Git adapter (GitHub), CI/CD adapter, webhooks, grade publication |
| 5. Calibration & Quality | Weeks 13-15 | Full calibration workflow, moderation panels, agreement metrics, analytics dashboards |
| 6. Scale & Polish | Weeks 16-18 | Horizontal scaling, batch processing, additional adapters, performance optimisation |

---

## 10. Key Design Decisions

| Decision | Choice | Rationale |
|----------|--------|-----------|
| Agent architecture | Separate agent per dimension | Independent tuning, testing, versioning; quality and debuggability over LLM call count |
| Human involvement | Always in pipeline, configurable auto-confirm | Academic accountability requires oversight; auto-confirm is an optimisation, not default |
| Evaluation approach | Structured rubric-based | Decomposable, auditable, calibratable; supports academic appeals process |
| Pipeline definition | Declarative YAML data | Non-technical staff can customise workflows; visual editor possible |
| Evidence handling | Immutable snapshots | Fixed point-in-time view ensures audit trail consistency; enables appeals against same evidence |
