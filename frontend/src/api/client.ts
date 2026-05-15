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
  req: { name: string; description?: string; displayOrder?: number },
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
  req: { name: string; description?: string; displayOrder?: number },
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

export default api;
