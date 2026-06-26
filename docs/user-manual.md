# User Manual — Capstone Evaluation System

## Table of Contents

1. [Overview](#overview)
2. [Getting Started](#getting-started)
3. [Student Guide](#student-guide)
4. [Tutor Guide](#tutor-guide)
5. [Admin Guide](#admin-guide)

---

## Overview

The Capstone Evaluation System automates the assessment of student capstone project reports using AI-driven (LLM) evaluation combined with configurable rubrics and tutor review. It supports group-based projects, multi-criteria scoring, and hybrid workflows where students self-upload and admins perform bulk assessment.

### System Roles

| Role | Description |
|------|-------------|
| **Student** | Registers, uploads reports, views evaluation feedback |
| **Tutor** | Manages projects/tasks/groups, runs evaluations, reviews results |
| **Admin** | Full system access including user management, scoring config, and system settings |

---

## Getting Started

### Registration

1. Navigate to the system login page
2. Click **Register** to create a new account
3. Fill in: email, password, full name, academic year, and semester
4. After registration you are assigned the **Student** role by default
5. An Admin can promote your account to Tutor or Admin if needed

### Login

1. Enter your email and password
2. Upon successful login, you are redirected to your role-appropriate dashboard

### Profile

All roles can access **Profile** from the top-right menu to:
- Update display name
- Change password (requires current password)

---

## Student Guide

### Dashboard

After login, the Student Dashboard shows:
- Your recent submissions and their evaluation status
- Quick access to upload a new submission

### Uploading a Report

1. Navigate to **Upload** from the sidebar menu
2. Select the **Project** your submission belongs to (if applicable)
3. Select the **Task** within that project
4. Enter your name as it appears in your group
5. (Optional) Provide a GitHub repository URL for code reference
6. Drag-and-drop or click to select your report file (.pdf, .docx, .doc)
7. Click **Upload**

After uploading, your submission is automatically linked to your group if your email matches a group member record (exact email match, or your email prefix matches a student ID).

### Viewing Results

1. Navigate to your submission from the dashboard or submission list
2. The **Result Page** shows:
   - **Overall Score** and performance level
   - **Per-Criterion Breakdown** — score, level, and justification for each evaluation criterion (e.g., Logic, Methodology, Implementation)
   - **Evidence** — section-linked quotes from your report with sentiment indicators
   - **Confidence** — how confident the system is in each score
   - **Suggestions** — specific improvement recommendations
   - **Strengths & Areas for Improvement** — summary feedback

Only evaluations with **PUBLIC** visibility are shown to students. Internal evaluations (used for calibration) are hidden.

### Submitting Feedback

On any evaluated submission you can:
1. Rate the evaluation quality (1-5 stars)
2. Leave a comment about the accuracy or helpfulness of the feedback

This helps tutors calibrate and improve the evaluation system.

---

## Tutor Guide

Tutors have access to all Student features plus project management, evaluation control, and result export.

### Projects

Navigate to **Projects** from the sidebar.

#### Creating a Project

1. Click **Create Project**
2. Fill in: Name, Academic Year, Semester, Description (optional)
3. Select a **Rule Package** — this determines which evaluation criteria are used
4. Click **Create**

#### Managing Project Members

1. Open a project and go to the **Members** tab
2. Click **Add Member** to add individual students/tutors
3. Use **Bulk Add** to add multiple students at once from the user list
4. Set member role (STUDENT or TUTOR)
5. Remove members with the delete button

#### Managing Tasks

Each project can have multiple tasks (e.g., Progress Report, Final Report).

1. Open a project and go to the **Tasks** tab
2. Click **Add Task**
3. Fill in: Name, Description, Display Order
4. Assign a **Rule Package** (overrides project default for this task)
5. Assign an **LLM Config** (controls AI model settings for this task)

### Groups

Navigate to **Batch Assessment** > Step 3 (Groups), or manage via project context.

#### Creating Groups Manually

1. Click **Add Group**
2. Enter Group Code (e.g., "Group01") and optional Group Name
3. Add members with: Student Name, Student ID, Email, Contribution %
4. Click **Create**

#### Importing Groups via CSV

1. Click **Import CSV**
2. Upload a CSV file with format:
   ```
   GroupCode,StudentName,StudentID,ContributionPercent,Email
   Group01,Alice Smith,s1234567,50,alice.smith@student.vu.edu.au
   Group01,Bob Jones,s7654321,50,bob.jones@student.vu.edu.au
   Group02,Carol White,s2345678,100,carol.white@student.vu.edu.au
   ```
3. The Email column is optional but enables automatic student-group linking

#### Group-Student Linking

When a student registers and their email matches a group member record, the system automatically links them. Matching strategies (tried in order):
1. Direct user ID match (if previously linked)
2. Exact email match
3. Email prefix matches student ID (e.g., `s1234567@student.vu.edu.au` matches student ID `s1234567`)

The **Linked** column in the group member table shows green "Yes" when a student account has been matched.

### Submissions

Navigate to **Submissions** from the sidebar to view all submissions across projects.

- Filter by project to narrow the list
- Click a submission to view its detail and evaluation results
- View version history for any student

### Running Evaluations

#### Individual Evaluation

1. Open a submission detail page
2. Click **Run Evaluation**
3. Choose method: **LLM** (AI-driven) or **Rule-Based**
4. Choose visibility: **Public** (student-visible) or **Internal** (tutor-only)
5. Wait for the result to appear

#### Batch Evaluation

1. Navigate to **Batch Assessment**
2. Select Project and Task (Step 1)
3. Upload reports in bulk (Step 2) — filenames like `Group01_Report.pdf` auto-match groups
4. Review groups with document analysis (Step 3) — after upload, you're automatically taken here
5. Click **Start Batch Evaluation** (Step 4)
6. Monitor progress — submissions are evaluated sequentially with rate-limiting
7. Export results when complete (Step 5)

After bulk upload, the system automatically parses each .docx file and shows document statistics per group (word count, sections, images, tables, code snippets). This happens in the background — use the **Refresh** button to update if parsing is still in progress.

The system deduplicates submissions: if both a student self-upload and admin bulk-upload exist for the same group, only the latest one is evaluated.

#### Image-Heavy Submission Warning

Groups with 5 or more images are flagged with an orange warning. Text-based LLM evaluation cannot analyze visual content (screenshots, diagrams, charts). For image-heavy submissions:
- Switch to a **multimodal LLM config** (if available) that can process images
- Or plan a manual tutor review for those specific groups

### Tutor Reviews

Provide manual scoring alongside AI evaluation:

1. Open a submission detail page
2. Go to the **Tutor Review** section
3. Enter an overall score and comment
4. Add dimension scores (e.g., Methodology: 8/10 with justification)
5. Save the review

Tutor reviews contribute to the composite score based on configured weights.

### Score Adjustments

If an AI evaluation seems off:

1. Open the evaluation result
2. Click **Adjust Score**
3. Enter the adjusted score and a reason
4. The adjustment is logged with your name and timestamp

### Rules & Rule Packages

Navigate to **Rules** from the sidebar.

#### Rules Tab

Rules define individual evaluation criteria. Each rule has:
- **Rule Key** — unique identifier (e.g., `logic_reasoning`)
- **Name** — display name
- **Category** — grouping (Logic, Methodology, Implementation)
- **Description** — what the rule checks
- **LLM Criterion Prompt** — the detailed instruction sent to the AI for this criterion (level descriptors, what to look for)

#### Rule Packages Tab

Packages bundle rules with weights and configuration:
- Enable/disable individual criteria
- Set weight per criterion
- Configure the scoring scale (point values per performance level)
- Mark one package as the system default

### LLM Configuration

Navigate to **LLM Config** from the sidebar.

Create and manage AI evaluation configurations:
- **Name** — descriptive identifier
- **Provider** — AI service provider (e.g., deepseek, openai)
- **Model** — specific model version (e.g., deepseek-chat, gpt-4o)
- **Multimodal** — toggle ON if the model can process images/screenshots (e.g., GPT-4o, Claude). When ON, the system can evaluate visual content in submissions. Use this for image-heavy reports.
- **Temperature** — controls output variability (0 = deterministic, higher = more creative)
- **Max Tokens** — response length limit
- **System Prompt Template** — main evaluation instructions with placeholders:
  - `{{criteria_block}}` — auto-filled from rule package criteria
  - `{{output_format}}` — auto-filled response format specification
  - `{{additional_context}}` — tutor-provided extra instructions
- **Additional Context** — custom instructions (e.g., "Focus on methodology rigor")
- **Output Format Template** — expected JSON response structure

Use **Preview Prompt** to see the fully assembled prompt before running evaluations.
Use **Duplicate** to create variations from an existing config.

### Exporting Results

1. Navigate to **Batch Assessment** > Step 5 (Export)
2. Click **Export CSV**
3. Downloaded file contains one row per student:
   ```
   GroupCode,GroupName,StudentName,StudentID,ContributionPercent,OverallScore,Level,Confidence,Feedback
   ```

---

## Admin Guide

Admins have all Tutor capabilities plus system-wide management.

### User Management

Navigate to **Users** from the sidebar (Admin menu section).

- View all registered users with their role, academic year, semester
- **Change Role** — promote/demote users (Student, Tutor, Admin)
- **Update Academic Info** — set academic year and semester for any user
- Filter users by academic year or semester

### Scoring Configuration

Navigate to **Scoring Settings** from the sidebar.

Configure how the composite score is calculated from multiple evaluation sources:

| Weight | Source | Description |
|--------|--------|-------------|
| Rule-Based Weight | Rule engine evaluation | Automated checks |
| LLM Weight | AI evaluation | Deep report analysis |
| Tutor Weight | Manual tutor review | Human expert scoring |

- Weights should sum to 1.0 (100%)
- Set **Max Score** (default: 30 for the 5-level rubric)
- Changes apply to all future composite score calculations

### System Defaults

Admins control:
- Which Rule Package is the system default (used when no project/task-specific package is assigned)
- Which LLM Config is the default
- Scoring weight distribution

### Batch Assessment Workflow (Full)

The recommended workflow for batch-assessing a cohort:

1. **Create Project** — set academic year, semester, assign rule package
2. **Create Task(s)** — one per deliverable (Progress Report, Final Report)
3. **Import Groups** — CSV with student names, IDs, emails, contribution splits
4. **Optionally** invite students to register and self-upload (hybrid mode)
5. **Bulk Upload** remaining reports (filenames containing group codes)
6. **Run Batch Evaluation** — LLM evaluates all pending submissions
7. **Review** results on the Result Page; add tutor reviews where needed
8. **Export CSV** — per-student results with scores, levels, and feedback
9. **Adjust** individual scores if the AI assessment needs correction

### Hybrid Workflow (Student Self-Upload + Admin Bulk Assessment)

Both paths converge consistently:
- Students register with their university email and upload their own report
- Admin also bulk-uploads reports for the same task
- The system links student uploads to groups automatically via email matching
- Deduplication ensures only the latest submission per group is evaluated and exported
- No double-counting occurs regardless of which path is used

---

## Appendix: Evaluation Methods

### LLM Evaluation (AI)

Multi-round AI analysis of the submitted report:
- **Single Pass** — documents under 3000 words evaluated in one call
- **Two Pass** — medium documents split into 2 parallel rounds + synthesis
- **Multi Pass** — long documents chunked (~6000 words each) + synthesis round

Each criterion receives: score, level, confidence, evidence (with section references), and suggestions.

### Rule-Based Evaluation

Automated structural checks against enabled rules. Fast but less nuanced than LLM evaluation.

### Tutor Review

Manual human scoring with per-dimension breakdown. Serves as ground truth for calibration.

### Composite Score

Weighted combination of all available evaluation sources:
```
composite = (rule_weight * rule_score) + (llm_weight * llm_score) + (tutor_weight * tutor_score)
```
Only available sources contribute; weights are renormalized if a source is missing.

---

## Appendix: Scoring Scale

Default 5-level rubric (30 points maximum):

| Level | Points | Description |
|-------|--------|-------------|
| Excellent | 30 | Comprehensive, thorough, innovative |
| Very Good | 24 | Strong with minor gaps |
| Good | 18 | Adequate coverage, some weaknesses |
| Satisfactory | 12 | Basic understanding, significant gaps |
| Poor | 6 | Minimal evidence of understanding |

The scoring scale is configurable per Rule Package.
