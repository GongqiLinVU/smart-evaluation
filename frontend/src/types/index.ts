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

export interface EvidenceItem {
  sectionName?: string;
  sectionIndex?: number;
  quote: string;
  sentiment?: 'positive' | 'negative';
  note?: string;
}

export interface CriterionScoreResponse {
  criterionName: string;
  score: number;
  level: string;
  justification: string;
  evidence: (string | EvidenceItem)[];
  subScores: Record<string, { score: number; max: number; note: string }>;
  confidence: number | null;
  suggestions: string[] | null;
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
  rawLlmResponse: string | null;
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
  maxLlmRunsPerSubmission: number;
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
  rulePackageId: number | null;
  rulePackageName: string | null;
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
  rulePackageId: number | null;
  rulePackageName: string | null;
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

export interface TutorReviewDimensionResponse {
  id: number;
  dimensionName: string;
  score: number;
  maxScore: number;
  justification: string;
}

export interface TutorReviewResponse {
  id: number;
  submissionId: number;
  tutorName: string;
  overallScore: number;
  overallComment: string;
  reviewedAt: string;
  dimensions: TutorReviewDimensionResponse[];
}

export interface ScoringWeightsResponse {
  ruleBasedWeight: number;
  llmWeight: number;
  tutorWeight: number;
  maxScore: number;
}

export interface ComponentScore {
  method: string;
  rawScore: number | null;
  rawMaxScore: number;
  percentage: number | null;
  weight: number;
  weightedContribution: number | null;
  available: boolean;
}

export interface CompositeScoreResponse {
  compositeScore: number | null;
  compositePercentage: number | null;
  maxScore: number;
  level: string | null;
  components: ComponentScore[];
  weights: ScoringWeightsResponse;
}

export interface RuleResponse {
  id: number;
  ruleKey: string;
  name: string;
  description: string | null;
  category: string | null;
  llmCriterionPrompt: string | null;
  builtIn: boolean;
  createdAt: string;
  updatedAt: string;
}

export interface RulePackageItemResponse {
  id: number;
  ruleId: number;
  ruleKey: string;
  ruleName: string;
  ruleCategory: string | null;
  enabled: boolean;
  weight: number;
}

export interface RulePackageResponse {
  id: number;
  name: string;
  description: string | null;
  isDefault: boolean;
  logicEnabled: boolean;
  methodologyEnabled: boolean;
  implementationEnabled: boolean;
  logicWeight: number;
  methodologyWeight: number;
  implementationWeight: number;
  items: RulePackageItemResponse[];
  createdAt: string;
  updatedAt: string;
}

export interface LlmConfigResponse {
  id: number;
  name: string;
  systemPromptTemplate: string | null;
  additionalContext: string | null;
  outputFormatTemplate: string | null;
  temperature: number | null;
  maxTokens: number | null;
  provider: string | null;
  model: string | null;
  isDefault: boolean;
  createdAt: string;
  updatedAt: string;
}

export interface PromptPreviewResponse {
  systemPrompt: string;
  estimatedTokens: number;
  criteriaIncluded: string[];
}
