export interface UserResponse {
  id: number;
  email: string;
  fullName: string;
  role: string;
  academicYear: string | null;
  semester: string | null;
  createdAt: string;
}

export interface AuthResponse {
  token: string;
  user: UserResponse;
}

export interface SubmissionResponse {
  id: number;
  studentName: string;
  fileName: string;
  fileSizeBytes: number;
  githubUrl: string | null;
  version: number;
  totalVersions: number;
  uploadedAt: string;
  status: string;
  latestScore: number | null;
  latestLevel: string | null;
  projectId: number | null;
  projectName: string | null;
  taskId: number | null;
  taskName: string | null;
}

export interface CriterionScoreResponse {
  criterionName: string;
  score: number;
  level: string;
  justification: string;
  evidence: string[];
  subScores: Record<string, { score: number; max: number; note: string }>;
}

export interface EvaluationResultResponse {
  id: number;
  submissionId: number;
  method: string;
  overallScore: number;
  overallLevel: string;
  overallFeedback: string;
  strengths: string[];
  improvements: string[];
  confidence: number | null;
  evaluatedAt: string;
  criteria: CriterionScoreResponse[];
}

export interface DocumentStatsResponse {
  headingCount: number;
  sectionCount: number;
  codeSnippetCount: number;
  tableCount: number;
  imageCount: number;
  linkCount: number;
  totalWordCount: number;
  parsedAt: string;
}

export interface SubmissionDetailResponse {
  submission: SubmissionResponse;
  documentStats: DocumentStatsResponse | null;
  evaluations: EvaluationResultResponse[];
}

export interface HealthResponse {
  status: string;
  llmEnabled: boolean;
  llmProvider: string;
}

export interface StudentFeedbackResponse {
  id: number;
  submissionId: number;
  studentName: string;
  rating: number;
  comment: string;
  createdAt: string;
}

export interface ProjectResponse {
  id: number;
  name: string;
  academicYear: string;
  semester: string;
  description: string | null;
  createdAt: string;
  memberCount: number;
}

export interface ProjectMemberResponse {
  id: number;
  userId: number;
  fullName: string;
  email: string;
  role: string;
  joinedAt: string;
}

export interface ProjectTaskResponse {
  id: number;
  projectId: number;
  name: string;
  description: string | null;
  displayOrder: number;
  createdAt: string;
}

export interface ScoreAdjustmentResponse {
  id: number;
  evaluationId: number;
  tutorName: string;
  originalScore: number;
  adjustedScore: number;
  reason: string;
  adjustedAt: string;
}
