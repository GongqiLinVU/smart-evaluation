import axios from 'axios';
import type {
  AuthResponse,
  SubmissionResponse,
  SubmissionDetailResponse,
  EvaluationResultResponse,
  HealthResponse,
  UserResponse,
  StudentFeedbackResponse,
  ScoreAdjustmentResponse,
  ProjectResponse,
  ProjectMemberResponse,
  ProjectTaskResponse,
  TutorReviewResponse,
  ScoringWeightsResponse,
  CompositeScoreResponse,
  RulePackageResponse,
  RuleResponse,
  LlmConfigResponse,
  PromptPreviewResponse,
} from '../types';

const api = axios.create({
  baseURL: 'http://localhost:8080/api',
  timeout: 120000,
});

api.interceptors.request.use((config) => {
  const token = localStorage.getItem('token');
  if (token) {
    config.headers.Authorization = `Bearer ${token}`;
  }
  return config;
});

api.interceptors.response.use(
  (response) => response,
  (error) => {
    if (error.response?.status === 401) {
      localStorage.removeItem('token');
      localStorage.removeItem('user');
      if (window.location.pathname !== '/login') {
        window.location.href = '/login';
      }
    }
    return Promise.reject(error);
  },
);

// Auth
export async function register(
  email: string,
  password: string,
  fullName: string,
  academicYear?: string,
  semester?: string,
): Promise<AuthResponse> {
  const { data } = await api.post<AuthResponse>('/auth/register', {
    email,
    password,
    fullName,
    academicYear,
    semester,
  });
  return data;
}

export async function login(
  email: string,
  password: string,
): Promise<AuthResponse> {
  const { data } = await api.post<AuthResponse>('/auth/login', {
    email,
    password,
  });
  return data;
}

export async function refreshToken(): Promise<AuthResponse> {
  const { data } = await api.post<AuthResponse>('/auth/refresh');
  return data;
}

// Users
export async function getCurrentUser(): Promise<UserResponse> {
  const { data } = await api.get<UserResponse>('/users/me');
  return data;
}

export async function updateProfile(
  fullName: string,
  currentPassword?: string,
  newPassword?: string,
): Promise<UserResponse> {
  const { data } = await api.put<UserResponse>('/users/me', {
    fullName,
    currentPassword,
    newPassword,
  });
  return data;
}

export async function listUsers(
  academicYear?: string,
  semester?: string,
): Promise<UserResponse[]> {
  const { data } = await api.get<UserResponse[]>('/users', {
    params: {
      ...(academicYear ? { academicYear } : {}),
      ...(semester ? { semester } : {}),
    },
  });
  return data;
}

export async function changeUserRole(
  userId: number,
  role: string,
): Promise<UserResponse> {
  const { data } = await api.put<UserResponse>(`/users/${userId}/role`, {
    role,
  });
  return data;
}

export async function updateUserAcademicInfo(
  userId: number,
  academicYear?: string,
  semester?: string,
): Promise<UserResponse> {
  const { data } = await api.put<UserResponse>(
    `/users/${userId}/academic-info`,
    { academicYear, semester },
  );
  return data;
}

// Submissions
export async function uploadSubmission(
  file: File,
  studentName: string,
  githubUrl?: string,
  projectId?: number,
  taskId?: number,
): Promise<SubmissionResponse> {
  const formData = new FormData();
  formData.append('file', file);
  formData.append('studentName', studentName);
  if (githubUrl) formData.append('githubUrl', githubUrl);
  if (projectId) formData.append('projectId', String(projectId));
  if (taskId) formData.append('taskId', String(taskId));
  const { data } = await api.post<SubmissionResponse>(
    '/submissions/upload',
    formData,
  );
  return data;
}

export async function listSubmissions(): Promise<SubmissionResponse[]> {
  const { data } = await api.get<SubmissionResponse[]>('/submissions');
  return data;
}

export async function listLatestSubmissions(
  projectId?: number,
): Promise<SubmissionResponse[]> {
  const { data } = await api.get<SubmissionResponse[]>('/submissions/latest', {
    params: projectId ? { projectId } : undefined,
  });
  return data;
}

export async function listVersions(
  studentName: string,
  projectId?: number,
  taskId?: number,
): Promise<SubmissionResponse[]> {
  const { data } = await api.get<SubmissionResponse[]>(
    `/submissions/versions/${encodeURIComponent(studentName)}`,
    {
      params: {
        ...(projectId ? { projectId } : {}),
        ...(taskId ? { taskId } : {}),
      },
    },
  );
  return data;
}

export async function listMySubmissions(): Promise<SubmissionResponse[]> {
  const { data } = await api.get<SubmissionResponse[]>('/submissions/my');
  return data;
}

export async function getSubmissionDetail(
  id: number,
): Promise<SubmissionDetailResponse> {
  const { data } = await api.get<SubmissionDetailResponse>(
    `/submissions/${id}`,
  );
  return data;
}

// Evaluations
export async function runEvaluation(
  submissionId: number,
  method: 'RULE_BASED' | 'LLM' = 'RULE_BASED',
): Promise<EvaluationResultResponse> {
  const { data } = await api.post<EvaluationResultResponse>(
    `/evaluations/run/${submissionId}`,
    null,
    { params: { method } },
  );
  return data;
}

export async function getEvaluation(
  id: number,
): Promise<EvaluationResultResponse> {
  const { data } = await api.get<EvaluationResultResponse>(
    `/evaluations/${id}`,
  );
  return data;
}

export async function getHealth(): Promise<HealthResponse> {
  const { data } = await api.get<HealthResponse>('/evaluations/health');
  return data;
}

// Feedback
export async function submitFeedback(
  submissionId: number,
  rating: number,
  comment: string,
): Promise<StudentFeedbackResponse> {
  const { data } = await api.post<StudentFeedbackResponse>(
    `/submissions/${submissionId}/feedback`,
    { rating, comment },
  );
  return data;
}

export async function getFeedback(
  submissionId: number,
): Promise<StudentFeedbackResponse[]> {
  const { data } = await api.get<StudentFeedbackResponse[]>(
    `/submissions/${submissionId}/feedback`,
  );
  return data;
}

// Score adjustments
export async function adjustScore(
  evaluationId: number,
  adjustedScore: number,
  reason: string,
): Promise<ScoreAdjustmentResponse> {
  const { data } = await api.post<ScoreAdjustmentResponse>(
    `/evaluations/${evaluationId}/adjust`,
    { adjustedScore, reason },
  );
  return data;
}

export async function getAdjustments(
  evaluationId: number,
): Promise<ScoreAdjustmentResponse[]> {
  const { data } = await api.get<ScoreAdjustmentResponse[]>(
    `/evaluations/${evaluationId}/adjustments`,
  );
  return data;
}

// Projects
export async function listProjects(): Promise<ProjectResponse[]> {
  const { data } = await api.get<ProjectResponse[]>('/projects');
  return data;
}

export async function getProject(id: number): Promise<ProjectResponse> {
  const { data } = await api.get<ProjectResponse>(`/projects/${id}`);
  return data;
}

export async function createProject(req: {
  name: string;
  academicYear: string;
  semester: string;
  description?: string;
  rulePackageId?: number;
}): Promise<ProjectResponse> {
  const { data } = await api.post<ProjectResponse>('/projects', req);
  return data;
}

export async function updateProject(
  id: number,
  req: {
    name: string;
    academicYear: string;
    semester: string;
    description?: string;
    rulePackageId?: number;
  },
): Promise<ProjectResponse> {
  const { data } = await api.put<ProjectResponse>(`/projects/${id}`, req);
  return data;
}

export async function deleteProject(id: number): Promise<void> {
  await api.delete(`/projects/${id}`);
}

export async function listProjectMembers(
  projectId: number,
): Promise<ProjectMemberResponse[]> {
  const { data } = await api.get<ProjectMemberResponse[]>(
    `/projects/${projectId}/members`,
  );
  return data;
}

export async function addProjectMember(
  projectId: number,
  userId: number,
  role: string,
): Promise<ProjectMemberResponse> {
  const { data } = await api.post<ProjectMemberResponse>(
    `/projects/${projectId}/members`,
    { userId, role },
  );
  return data;
}

export async function bulkAddProjectMembers(
  projectId: number,
  members: { userId: number; role: string }[],
): Promise<ProjectMemberResponse[]> {
  const { data } = await api.post<ProjectMemberResponse[]>(
    `/projects/${projectId}/members/bulk`,
    members,
  );
  return data;
}

export async function removeProjectMember(
  projectId: number,
  userId: number,
): Promise<void> {
  await api.delete(`/projects/${projectId}/members/${userId}`);
}

// Project Tasks
export async function listProjectTasks(
  projectId: number,
): Promise<ProjectTaskResponse[]> {
  const { data } = await api.get<ProjectTaskResponse[]>(
    `/projects/${projectId}/tasks`,
  );
  return data;
}

export async function createProjectTask(
  projectId: number,
  req: { name: string; description?: string; displayOrder?: number; rulePackageId?: number },
): Promise<ProjectTaskResponse> {
  const { data } = await api.post<ProjectTaskResponse>(
    `/projects/${projectId}/tasks`,
    req,
  );
  return data;
}

export async function updateProjectTask(
  projectId: number,
  taskId: number,
  req: { name: string; description?: string; displayOrder?: number; rulePackageId?: number },
): Promise<ProjectTaskResponse> {
  const { data } = await api.put<ProjectTaskResponse>(
    `/projects/${projectId}/tasks/${taskId}`,
    req,
  );
  return data;
}

export async function deleteProjectTask(
  projectId: number,
  taskId: number,
): Promise<void> {
  await api.delete(`/projects/${projectId}/tasks/${taskId}`);
}

// Tutor Reviews
export async function saveTutorReview(
  submissionId: number,
  req: {
    overallScore: number;
    overallComment?: string;
    dimensions: {
      dimensionName: string;
      score: number;
      maxScore: number;
      justification?: string;
    }[];
  },
): Promise<TutorReviewResponse> {
  const { data } = await api.post<TutorReviewResponse>(
    `/submissions/${submissionId}/tutor-reviews`,
    req,
  );
  return data;
}

export async function getTutorReview(
  submissionId: number,
): Promise<TutorReviewResponse | null> {
  const { data } = await api.get<TutorReviewResponse | null>(
    `/submissions/${submissionId}/tutor-reviews`,
  );
  return data;
}

export async function addTutorReviewDimension(
  submissionId: number,
  dimension: {
    dimensionName: string;
    score: number;
    maxScore: number;
    justification?: string;
  },
): Promise<TutorReviewResponse> {
  const { data } = await api.post<TutorReviewResponse>(
    `/submissions/${submissionId}/tutor-reviews/dimensions`,
    dimension,
  );
  return data;
}

export async function removeTutorReviewDimension(
  submissionId: number,
  dimensionId: number,
): Promise<TutorReviewResponse> {
  const { data } = await api.delete<TutorReviewResponse>(
    `/submissions/${submissionId}/tutor-reviews/dimensions/${dimensionId}`,
  );
  return data;
}

// Scoring Config
export async function getScoringWeights(): Promise<ScoringWeightsResponse> {
  const { data } = await api.get<ScoringWeightsResponse>('/scoring/weights');
  return data;
}

export async function updateScoringWeights(req: {
  ruleBasedWeight: number;
  llmWeight: number;
  tutorWeight: number;
  maxScore?: number;
}): Promise<ScoringWeightsResponse> {
  const { data } = await api.put<ScoringWeightsResponse>('/scoring/weights', req);
  return data;
}

export async function getCompositeScore(
  submissionId: number,
): Promise<CompositeScoreResponse> {
  const { data } = await api.get<CompositeScoreResponse>(
    `/scoring/composite/${submissionId}`,
  );
  return data;
}

// Rules
export async function listRules(): Promise<RuleResponse[]> {
  const { data } = await api.get<RuleResponse[]>('/rules');
  return data;
}

export async function getRule(id: number): Promise<RuleResponse> {
  const { data } = await api.get<RuleResponse>(`/rules/${id}`);
  return data;
}

export async function createRule(req: {
  ruleKey: string;
  name: string;
  description?: string;
  category?: string;
  llmCriterionPrompt?: string;
}): Promise<RuleResponse> {
  const { data } = await api.post<RuleResponse>('/rules', req);
  return data;
}

export async function updateRule(
  id: number,
  req: {
    ruleKey: string;
    name: string;
    description?: string;
    category?: string;
    llmCriterionPrompt?: string;
  },
): Promise<RuleResponse> {
  const { data } = await api.put<RuleResponse>(`/rules/${id}`, req);
  return data;
}

export async function deleteRule(id: number): Promise<void> {
  await api.delete(`/rules/${id}`);
}

// Rule Packages
export async function listRulePackages(): Promise<RulePackageResponse[]> {
  const { data } = await api.get<RulePackageResponse[]>('/rule-packages');
  return data;
}

export async function getRulePackage(id: number): Promise<RulePackageResponse> {
  const { data } = await api.get<RulePackageResponse>(`/rule-packages/${id}`);
  return data;
}

export async function createRulePackage(req: {
  name: string;
  description?: string;
  logicEnabled: boolean;
  methodologyEnabled: boolean;
  implementationEnabled: boolean;
  logicWeight: number;
  methodologyWeight: number;
  implementationWeight: number;
  isDefault?: boolean;
  items?: { ruleId: number; enabled: boolean; weight: number }[];
}): Promise<RulePackageResponse> {
  const { data } = await api.post<RulePackageResponse>('/rule-packages', req);
  return data;
}

export async function updateRulePackage(
  id: number,
  req: {
    name: string;
    description?: string;
    logicEnabled: boolean;
    methodologyEnabled: boolean;
    implementationEnabled: boolean;
    logicWeight: number;
    methodologyWeight: number;
    implementationWeight: number;
    isDefault?: boolean;
    items?: { ruleId: number; enabled: boolean; weight: number }[];
  },
): Promise<RulePackageResponse> {
  const { data } = await api.put<RulePackageResponse>(`/rule-packages/${id}`, req);
  return data;
}

export async function deleteRulePackage(id: number): Promise<void> {
  await api.delete(`/rule-packages/${id}`);
}

// LLM Config
export async function listLlmConfigs(): Promise<LlmConfigResponse[]> {
  const { data } = await api.get<LlmConfigResponse[]>('/llm-config');
  return data;
}

export async function getLlmConfig(id: number): Promise<LlmConfigResponse> {
  const { data } = await api.get<LlmConfigResponse>(`/llm-config/${id}`);
  return data;
}

export async function createLlmConfig(req: {
  name: string;
  systemPromptTemplate?: string;
  additionalContext?: string;
  outputFormatTemplate?: string;
  temperature?: number;
  maxTokens?: number;
  provider?: string;
  model?: string;
  isDefault?: boolean;
}): Promise<LlmConfigResponse> {
  const { data } = await api.post<LlmConfigResponse>('/llm-config', req);
  return data;
}

export async function updateLlmConfig(
  id: number,
  req: {
    name: string;
    systemPromptTemplate?: string;
    additionalContext?: string;
    outputFormatTemplate?: string;
    temperature?: number;
    maxTokens?: number;
    provider?: string;
    model?: string;
    isDefault?: boolean;
  },
): Promise<LlmConfigResponse> {
  const { data } = await api.put<LlmConfigResponse>(`/llm-config/${id}`, req);
  return data;
}

export async function deleteLlmConfig(id: number): Promise<void> {
  await api.delete(`/llm-config/${id}`);
}

export async function previewLlmPrompt(
  configId: number,
  rulePackageId: number,
): Promise<PromptPreviewResponse> {
  const { data } = await api.post<PromptPreviewResponse>(
    `/llm-config/${configId}/preview`,
    null,
    { params: { rulePackageId } },
  );
  return data;
}

export default api;
