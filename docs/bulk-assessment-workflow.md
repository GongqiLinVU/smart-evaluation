# Bulk Assessment Workflow Design

## Context

For the current semester, the system is not open for student registration. The workflow is:

1. Students submit group reports to tutor (or university system)
2. Tutor provides all reports to admin
3. Admin uploads all reports to the system for automated evaluation
4. System evaluates all reports (LLM + rule-based)
5. Admin exports per-student results (scores + feedback)
6. Tutor fills scores back into the university system

This design covers bulk upload, group management, batch evaluation, contribution-based score splitting, and result export.

---

## Data Model

### Group

A group is a team of 1–4 students submitting a shared report.

| Field | Type | Purpose |
|-------|------|---------|
| `id` | Long | Primary key |
| `groupCode` | String (unique within project) | External identifier (e.g., "Group01", "TeamAlpha") |
| `groupName` | String | Display name (optional, defaults to groupCode) |
| `project` | ManyToOne → Project | Which project this group belongs to |
| `createdAt` | DateTime | Auto-set |

### GroupMember

| Field | Type | Purpose |
|-------|------|---------|
| `id` | Long | Primary key |
| `group` | ManyToOne → Group | Parent group |
| `studentName` | String | Full name |
| `studentId` | String | University student ID (e.g., "s3901234") |
| `contributionPercent` | Double (nullable) | Contribution % (null = equal split) |

### Modified: Submission

Add optional group reference:

| New Field | Type | Purpose |
|-----------|------|---------|
| `group` | ManyToOne → Group (nullable) | Which group submitted this |

Existing fields (`task`, `project`, `version`, `filePath`, etc.) remain unchanged. A submission with a `group` is a group submission; without it, it's an individual submission (backward compatible).

---

## Workflow

### Option A: Upload First, Add Members Later

```
Admin uploads files → Groups auto-created from filenames
                    → Submissions created per group
Admin fills in group members (name, ID, contribution %)
Admin triggers batch evaluation
Admin exports results (per-student CSV)
```

### Option B: Create Groups First, Then Upload

```
Admin creates groups + members (manually or via CSV import)
Admin uploads files → matched to existing groups by code
Admin triggers batch evaluation
Admin exports results (per-student CSV)
```

Both workflows are supported. The system auto-creates groups from filenames if no matching group exists.

---

## Bulk Upload

### Endpoint

```
POST /api/tasks/{taskId}/bulk-upload
Content-Type: multipart/form-data
Body: files[] (multiple .pdf/.docx files)
```

### Filename Parsing

Files are parsed to extract the group code:

| Pattern | Example | Extracted Group Code |
|---------|---------|---------------------|
| `GroupXX_*` | `Group01_FinalReport.pdf` | `Group01` |
| `TeamXX_*` | `Team03_Progress.docx` | `Team03` |
| `XX_*` (digits) | `01_FinalReport.pdf` | `Group01` |
| No pattern match | `MyReport.pdf` | filename stem (`MyReport`) |

The parser is configurable — admin can set a regex pattern per project if needed.

### Processing Logic

For each uploaded file:

1. Parse group code from filename
2. Find or create `Group` with that code in the project
3. Create `Submission` linked to the group and task
4. Store file (existing upload mechanism)
5. Return summary: created/matched groups, submission count

### Response

```json
{
  "uploaded": 12,
  "groupsCreated": 10,
  "groupsMatched": 2,
  "submissions": [
    { "id": 45, "groupCode": "Group01", "filename": "Group01_FinalReport.pdf" },
    ...
  ],
  "errors": [
    { "filename": "corrupt.pdf", "reason": "File could not be parsed" }
  ]
}
```

---

## Group Management

### Endpoints

| Endpoint | Method | Description |
|----------|--------|-------------|
| `GET /api/projects/{projectId}/groups` | GET | List all groups with members |
| `POST /api/projects/{projectId}/groups` | POST | Create group with members |
| `PUT /api/groups/{id}` | PUT | Update group (name, members) |
| `DELETE /api/groups/{id}` | DELETE | Delete group (cascades to members, not submissions) |
| `POST /api/projects/{projectId}/groups/import` | POST | Import groups from CSV |

### Group Create/Update Request

```json
{
  "groupCode": "Group01",
  "groupName": "Alpha Team",
  "members": [
    { "studentName": "Alice Chen", "studentId": "s3901001", "contributionPercent": 30 },
    { "studentName": "Bob Wang", "studentId": "s3901002", "contributionPercent": 25 },
    { "studentName": "Carol Li", "studentId": "s3901003", "contributionPercent": 25 },
    { "studentName": "David Zhang", "studentId": "s3901004", "contributionPercent": 20 }
  ]
}
```

Contribution percentages must sum to 100 (validated). If all null, equal split is assumed.

### CSV Import Format

```csv
GroupCode,StudentName,StudentID,ContributionPercent
Group01,Alice Chen,s3901001,30
Group01,Bob Wang,s3901002,25
Group01,Carol Li,s3901003,25
Group01,David Zhang,s3901004,20
Group02,Eve Liu,s3901005,50
Group02,Frank Wu,s3901006,50
```

---

## Batch Evaluation

### Endpoint

```
POST /api/tasks/{taskId}/evaluate-all?method=LLM
```

### Behavior

1. Find all submissions for the task that have **no** evaluation result yet (or allow re-evaluation flag)
2. Queue them for sequential evaluation (to avoid LLM rate limits)
3. Return immediately with a batch job ID
4. Process evaluations one by one, updating status

### Progress Tracking

```
GET /api/tasks/{taskId}/evaluate-all/status
```

```json
{
  "total": 12,
  "completed": 7,
  "failed": 1,
  "pending": 4,
  "status": "IN_PROGRESS",
  "results": [
    { "submissionId": 45, "groupCode": "Group01", "status": "SUCCESS", "overallScore": 24 },
    { "submissionId": 46, "groupCode": "Group02", "status": "FAILED", "error": "LLM timeout" },
    ...
  ]
}
```

### Rate Limiting

- Sequential processing (one at a time) to respect LLM API rate limits
- Configurable delay between evaluations (default: 2 seconds)
- Retry failed evaluations up to 2 times before marking as FAILED
- Admin can re-trigger individual failed evaluations

---

## Contribution-Based Score Splitting

### Extraction Strategy

When the evaluation completes for a group submission, the system attempts to extract individual contributions:

**Source 1: Contribution table in document**

The LLM evaluation prompt can be extended to ask:
> "If a contribution table or author attribution is present, extract each member's name and contribution percentage."

Added to the output format:
```json
{
  "contributions": [
    { "name": "Alice Chen", "percent": 30 },
    { "name": "Bob Wang", "percent": 25 }
  ]
}
```

**Source 2: Admin-entered percentages**

Via the group management UI, admin can manually set contribution percentages per member.

**Priority:**
1. Admin-entered values (override everything)
2. LLM-extracted values (if found in document)
3. Equal split (fallback)

### Score Adjustment Formula

For each student in the group:

```
adjustment_factor = (student_contribution / average_contribution)
adjusted_score = base_group_score * min(adjustment_factor, 1.2)
```

The factor is capped at 1.2x to prevent runaway scores. A student with 50% contribution in a 2-person team gets 1.0x (50/50 = no adjustment). A student with 40% in a 4-person team (where average is 25%) gets 1.2x cap.

The adjusted score is still snapped to valid rubric values (6/12/18/24/30).

**Note:** This is optional. If contribution data isn't available or admin prefers uniform scoring, all members get the same group score.

---

## Result Export

### Endpoint

```
GET /api/tasks/{taskId}/export?format=csv
```

### CSV Output Format

```csv
GroupCode,GroupName,StudentName,StudentID,ContributionPercent,LogicScore,MethodologyScore,ImplementationScore,OverallScore,Level,Confidence,Feedback
Group01,Alpha Team,Alice Chen,s3901001,30,24,24,18,22,PROFICIENT,0.82,"Solid methodology section. Implementation could be more detailed in sections 4-5."
Group01,Alpha Team,Bob Wang,s3901002,25,24,24,18,22,PROFICIENT,0.82,"Solid methodology section. Implementation could be more detailed in sections 4-5."
Group01,Alpha Team,Carol Li,s3901003,25,24,24,18,22,PROFICIENT,0.82,"Solid methodology section. Implementation could be more detailed in sections 4-5."
Group01,Alpha Team,David Zhang,s3901004,20,24,24,18,22,PROFICIENT,0.82,"Solid methodology section. Implementation could be more detailed in sections 4-5."
Group02,...
```

If contribution-weighted scoring is enabled, individual scores may differ within the same group.

### Additional Export Options

| Parameter | Values | Description |
|-----------|--------|-------------|
| `format` | `csv`, `xlsx` | Output format |
| `includeEvidence` | `true`/`false` | Include evidence quotes (longer output) |
| `includeSuggestions` | `true`/`false` | Include per-criterion suggestions |
| `scoreMode` | `group`, `individual` | Uniform group scores or contribution-adjusted |

---

## Frontend Pages

### Bulk Upload Panel (on Task detail page)

- Drag-and-drop zone for multiple files
- File list preview with parsed group codes
- "Upload & Create Submissions" button
- Progress indicator during upload
- Summary card after upload (groups created, files processed, errors)

### Group Management Page (`/projects/{id}/groups`)

- Table of groups with expandable member list
- Create group modal (code, name, members)
- CSV import button
- Edit members inline (name, ID, contribution %)
- Link to view group's submissions

### Batch Evaluation Panel (on Task submissions list)

- "Evaluate All Pending" button with method selector (LLM/Rule-based)
- Progress bar during batch evaluation
- Status column per submission (Pending, Evaluating, Success, Failed)
- "Retry Failed" button

### Export Panel (on Task submissions list)

- "Export Results" button
- Options: format (CSV/XLSX), score mode (group/individual), include evidence/suggestions
- Download triggers immediately

---

## Implementation Phases

### Phase 1: Group Model + Bulk Upload

**Backend:**
- `Group` entity + `GroupMember` entity
- `GroupRepository`, `GroupMemberRepository`
- `GroupService` — CRUD, CSV import
- `BulkUploadService` — multi-file upload, filename parsing, group creation
- `BulkUploadController` — `POST /api/tasks/{taskId}/bulk-upload`
- `GroupController` — CRUD endpoints
- Modify `Submission` entity to add optional `group` field

**Frontend:**
- Group management page
- Bulk upload drag-and-drop panel on task page
- CSV import modal

### Phase 2: Batch Evaluation

**Backend:**
- `BatchEvaluationService` — sequential evaluation with progress tracking
- In-memory progress state (or database-backed for persistence)
- `POST /api/tasks/{taskId}/evaluate-all`
- `GET /api/tasks/{taskId}/evaluate-all/status`

**Frontend:**
- Batch evaluate button + progress bar
- Status indicators per submission

### Phase 3: Contribution Extraction + Export

**Backend:**
- Extend LLM output format to request contribution extraction
- `ContributionService` — resolve contribution per student (admin > LLM > equal)
- `ExportService` — generate CSV/XLSX with per-student scores
- `GET /api/tasks/{taskId}/export`

**Frontend:**
- Contribution editor on group detail
- Export button with options modal
- Preview table before download (optional)

---

## Design Decisions

| Decision | Choice | Rationale |
|----------|--------|-----------|
| Group is separate from User | Yes | Students don't have accounts this semester; group model is lightweight |
| Submission links to Group (not members) | Yes | One report per group; member details live on the Group entity |
| Filename-based group matching | Pattern-based | Simple, tutor can name files consistently; regex for edge cases |
| Sequential batch evaluation | Yes | Respects LLM rate limits; parallel would risk throttling |
| Contribution is optional | Yes | Not all reports include attribution; equal split is sensible default |
| Score adjustment capped at 1.2x | Yes | Prevents a 60% contributor from getting unrealistically inflated scores |
| Export includes raw + adjusted scores | Yes | Tutor can decide which to use for the university system |
