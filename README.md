# Smart Evaluation System

AI-powered evaluation decision system for IT capstone project marking. The system automates the assessment of student capstone reports using both rule-based and LLM-based evaluation methods, with a rubric-driven scoring framework.

## Features

- **Project Management** — Organize submissions by academic year/semester with sub-tasks (progress report, final document, presentation, etc.)
- **Task-Scoped Versioning** — Independent version history per student per task within a project
- **Dual Evaluation Methods** — Rule-based document analysis and LLM-powered evaluation
- **Rubric-Driven Scoring** — 30-point scale across 3 criteria (Logic & Argumentation, Methodology & Analysis, Implementation & Technical Quality) with 5 competency levels
- **Role-Based Access** — Admin, Tutor, and Student roles with scoped dashboards
- **Score Adjustments** — Tutors can override AI-generated scores with documented reasons
- **Student Feedback** — Students can rate and comment on evaluations

## Tech Stack

| Layer    | Technology                                              |
|----------|---------------------------------------------------------|
| Backend  | Java 17, Spring Boot 3.3, Spring Security, JWT, JPA    |
| Frontend | React 19, TypeScript, Vite, Ant Design 6               |
| Database | MySQL 8                                                 |
| LLM      | DeepSeek, OpenAI, Anthropic, Gemini (configurable)     |

## Prerequisites

- Java 17+
- Node.js 18+
- MySQL 8+

## Getting Started

### 1. Database Setup

```sql
CREATE DATABASE capstone_eval;
```

Hibernate `ddl-auto: update` will create/update tables automatically on startup.

### 2. Backend

```bash
cd backend
export MYSQL_PASSWORD=yourpassword      # or set in application.yml
./mvnw spring-boot:run
```

The API server starts on `http://localhost:8080`.

**Optional LLM configuration** (environment variables):
```bash
export EVAL_LLM_ENABLED=true
export EVAL_LLM_PROVIDER=deepseek       # deepseek | openai | anthropic | gemini
export EVAL_LLM_DEEPSEEK_API_KEY=sk-... # set the key for your chosen provider
```

### 3. Frontend

```bash
cd frontend
npm install
npm run dev
```

The dev server starts on `http://localhost:5173` and proxies API requests to the backend.

### 4. Default Admin Account

On first startup, register a user and promote them to ADMIN via SQL:

```sql
UPDATE users SET role = 'ADMIN' WHERE email = 'your-email@example.com';
```

## Project Structure

```
backend/
  src/main/java/com/capstone/eval/
    model/          # JPA entities (User, Project, ProjectTask, Submission, ...)
    repository/     # Spring Data JPA repositories
    service/        # Business logic
    controller/     # REST API endpoints
    dto/            # Request/response records
    evaluation/     # Rule-based & LLM evaluation engines
    security/       # JWT authentication & authorization
    config/         # CORS, security config
frontend/
  src/
    pages/          # Route-level page components
    components/     # Reusable UI components
    api/            # Axios API client
    context/        # React context (auth)
    types/          # TypeScript interfaces
docs/               # Architecture & design documents
```

## API Overview

| Endpoint                              | Method | Access        | Description                    |
|---------------------------------------|--------|---------------|--------------------------------|
| `/api/auth/register`                  | POST   | Public        | Register new user              |
| `/api/auth/login`                     | POST   | Public        | Login, returns JWT             |
| `/api/projects`                       | GET    | Authenticated | List projects (role-scoped)    |
| `/api/projects`                       | POST   | Admin/Tutor   | Create project                 |
| `/api/projects/{id}/tasks`            | GET    | Authenticated | List project tasks             |
| `/api/projects/{id}/members`          | GET    | Admin/Tutor   | List project members           |
| `/api/submissions/upload`             | POST   | Authenticated | Upload submission              |
| `/api/submissions/latest`             | GET    | Admin/Tutor   | Latest submissions per student |
| `/api/submissions/{id}`              | GET    | Authenticated | Submission detail              |
| `/api/submissions/{id}/evaluate`      | POST   | Admin/Tutor   | Run evaluation                 |
| `/api/submissions/{id}/adjust`        | POST   | Admin/Tutor   | Adjust score                   |
| `/api/rules`                          | CRUD   | Admin/Tutor   | Manage evaluation rules        |
| `/api/rule-packages`                  | CRUD   | Admin/Tutor   | Manage rule packages           |
| `/api/llm-config`                     | CRUD   | Admin/Tutor   | Manage LLM configurations      |
| `/api/llm-config/{id}/preview`        | POST   | Admin/Tutor   | Preview assembled LLM prompt   |
| `/api/scoring/weights`                | GET/PUT| Admin         | Configure scoring weights      |
| `/api/scoring/composite/{submissionId}` | GET  | Authenticated | Get composite score            |

## Documentation

Detailed design documents are available in the `docs/` directory:

| Document | Description |
|----------|-------------|
| [Rule Engine Design](docs/rule-engine-design.md) | Rule-based evaluation engine, rule packages, scoring configuration |
| [LLM Evaluation Design](docs/llm-evaluation-design.md) | Multi-round LLM evaluation, dynamic prompts, evidence format, LLM config |
| [Rubric Design](docs/rubric-design.md) | Detailed rubric with sub-criteria, scoring guides, evidence mapping |
| [System Architecture](docs/architecture.md) | Full system architecture, agent roles, decision pipeline |
| [System Overview](docs/system-overview.md) | High-level system overview |
| [Bulk Assessment Workflow](docs/bulk-assessment-workflow.md) | Bulk upload, group management, batch evaluation, export |
