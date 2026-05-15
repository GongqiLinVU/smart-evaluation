import { useEffect, useState, useCallback } from 'react';
import { useParams, Link } from 'react-router-dom';
import {
  Descriptions,
  Card,
  Tag,
  Button,
  Spin,
  Alert,
  Divider,
  Row,
  Col,
  Typography,
  Space,
  message,
  Rate,
  Input,
  Form,
  InputNumber,
  List,
} from 'antd';
import {
  ArrowLeftOutlined,
  RobotOutlined,
  ToolOutlined,
} from '@ant-design/icons';
import dayjs from 'dayjs';
import {
  getSubmissionDetail,
  runEvaluation,
  submitFeedback,
  getFeedback,
  adjustScore,
  getAdjustments,
} from '../api/client';
import { useAuth } from '../context/AuthContext';
import type {
  SubmissionDetailResponse,
  EvaluationResultResponse,
  StudentFeedbackResponse,
  ScoreAdjustmentResponse,
} from '../types';
import ScoreSummary from '../components/ScoreSummary';
import ScoreCard from '../components/ScoreCard';
import DocumentStats from '../components/DocumentStats';
import FeedbackPanel from '../components/FeedbackPanel';

const { Title, Text } = Typography;
const { TextArea } = Input;

function getStatusColor(status: string): string {
  const upper = status.toUpperCase();
  if (upper === 'COMPLETED' || upper === 'EVALUATED') return 'green';
  if (upper === 'PENDING' || upper === 'UPLOADED') return 'blue';
  if (upper === 'FAILED') return 'red';
  if (upper === 'PROCESSING') return 'orange';
  return 'default';
}

export default function ResultPage() {
  const { id } = useParams<{ id: string }>();
  const { isAdmin, isTutor } = useAuth();
  const [detail, setDetail] = useState<SubmissionDetailResponse | null>(null);
  const [loading, setLoading] = useState(true);
  const [evalLoading, setEvalLoading] = useState(false);
  const [evalMethod, setEvalMethod] = useState<string | null>(null);
  const [feedbacks, setFeedbacks] = useState<StudentFeedbackResponse[]>([]);
  const [adjustments, setAdjustments] = useState<
    Record<number, ScoreAdjustmentResponse[]>
  >({});
  const [feedbackForm] = Form.useForm();
  const [feedbackLoading, setFeedbackLoading] = useState(false);

  const fetchDetail = useCallback(async () => {
    if (!id) return;
    setLoading(true);
    try {
      const data = await getSubmissionDetail(Number(id));
      setDetail(data);

      const fb = await getFeedback(Number(id));
      setFeedbacks(fb);

      const adjMap: Record<number, ScoreAdjustmentResponse[]> = {};
      for (const ev of data.evaluations) {
        const adj = await getAdjustments(ev.id);
        if (adj.length > 0) adjMap[ev.id] = adj;
      }
      setAdjustments(adjMap);
    } catch {
      message.error('Failed to load submission details');
    } finally {
      setLoading(false);
    }
  }, [id]);

  useEffect(() => {
    fetchDetail();
  }, [fetchDetail]);

  const handleRunEvaluation = async (method: 'RULE_BASED' | 'LLM') => {
    if (!id) return;
    setEvalLoading(true);
    setEvalMethod(method);
    try {
      await runEvaluation(Number(id), method);
      message.success(
        `${method === 'RULE_BASED' ? 'Rule-based' : 'LLM'} evaluation completed!`,
      );
      await fetchDetail();
    } catch {
      message.error('Evaluation failed. Please try again.');
    } finally {
      setEvalLoading(false);
      setEvalMethod(null);
    }
  };

  const handleFeedback = async (values: {
    rating: number;
    comment: string;
  }) => {
    if (!id) return;
    setFeedbackLoading(true);
    try {
      await submitFeedback(Number(id), values.rating, values.comment);
      message.success('Thank you for your feedback!');
      feedbackForm.resetFields();
      const fb = await getFeedback(Number(id));
      setFeedbacks(fb);
    } catch {
      message.error('Failed to submit feedback');
    } finally {
      setFeedbackLoading(false);
    }
  };

  const handleAdjustScore = async (
    evaluationId: number,
    score: number,
    reason: string,
  ) => {
    try {
      await adjustScore(evaluationId, score, reason);
      message.success('Score adjusted');
      await fetchDetail();
    } catch {
      message.error('Failed to adjust score');
    }
  };

  if (loading) {
    return (
      <div style={{ textAlign: 'center', paddingTop: 100 }}>
        <Spin size="large" tip="Loading submission details..." />
      </div>
    );
  }

  if (!detail) {
    return (
      <Alert
        message="Submission Not Found"
        description="The requested submission could not be found."
        type="error"
        showIcon
        action={
          <Link to="/">
            <Button>Back to Dashboard</Button>
          </Link>
        }
      />
    );
  }

  const { submission, documentStats, evaluations } = detail;
  const hasRuleBased = evaluations.some((e) => e.method === 'RULE_BASED');

  return (
    <Spin
      spinning={evalLoading}
      tip={`Running ${evalMethod === 'RULE_BASED' ? 'rule-based' : 'LLM'} evaluation...`}
    >
      <div>
        {/* Header */}
        <div
          style={{
            display: 'flex',
            justifyContent: 'space-between',
            alignItems: 'center',
            marginBottom: 24,
          }}
        >
          <Link to="/">
            <Button icon={<ArrowLeftOutlined />}>Back</Button>
          </Link>
          <Space>
            {!hasRuleBased && (
              <Button
                type="primary"
                icon={<ToolOutlined />}
                onClick={() => handleRunEvaluation('RULE_BASED')}
                loading={evalLoading && evalMethod === 'RULE_BASED'}
              >
                Run Rule-Based Evaluation
              </Button>
            )}
            <Button
              icon={<RobotOutlined />}
              onClick={() => handleRunEvaluation('LLM')}
              loading={evalLoading && evalMethod === 'LLM'}
            >
              Run LLM Evaluation
            </Button>
          </Space>
        </div>

        {/* Submission Info */}
        <Card style={{ marginBottom: 24 }}>
          <Descriptions
            title="Submission Information"
            bordered
            column={{ xs: 1, sm: 2, md: 3 }}
          >
            <Descriptions.Item label="Student Name">
              {submission.studentName}
            </Descriptions.Item>
            {submission.projectName && (
              <Descriptions.Item label="Project">
                {submission.projectName}
              </Descriptions.Item>
            )}
            {submission.taskName && (
              <Descriptions.Item label="Task">
                <Tag>{submission.taskName}</Tag>
              </Descriptions.Item>
            )}
            <Descriptions.Item label="Version">
              <Tag color="blue">v{submission.version}</Tag>
            </Descriptions.Item>
            <Descriptions.Item label="File Name">
              {submission.fileName}
            </Descriptions.Item>
            <Descriptions.Item label="File Size">
              {(submission.fileSizeBytes / 1024).toFixed(1)} KB
            </Descriptions.Item>
            <Descriptions.Item label="Upload Date">
              {dayjs(submission.uploadedAt).format('YYYY-MM-DD HH:mm:ss')}
            </Descriptions.Item>
            <Descriptions.Item label="Status">
              <Tag color={getStatusColor(submission.status)}>
                {submission.status}
              </Tag>
            </Descriptions.Item>
            {submission.githubUrl && (
              <Descriptions.Item label="GitHub">
                <a
                  href={submission.githubUrl}
                  target="_blank"
                  rel="noopener noreferrer"
                >
                  {submission.githubUrl}
                </a>
              </Descriptions.Item>
            )}
          </Descriptions>
        </Card>

        {/* Document Stats */}
        {documentStats && <DocumentStats stats={documentStats} />}

        {/* Evaluations */}
        {evaluations.length === 0 ? (
          <Alert
            message="No Evaluations Yet"
            description="This submission has not been evaluated. Click the button above to run an evaluation."
            type="info"
            showIcon
          />
        ) : (
          evaluations.map((evaluation: EvaluationResultResponse) => (
            <Card
              key={evaluation.id}
              style={{ marginBottom: 24 }}
              title={
                <Space>
                  <Title level={4} style={{ margin: 0 }}>
                    Evaluation #{evaluation.id}
                  </Title>
                  <Tag
                    color={
                      evaluation.method === 'RULE_BASED' ? 'purple' : 'cyan'
                    }
                  >
                    {evaluation.method === 'RULE_BASED' ? 'Rule-Based' : 'LLM'}
                  </Tag>
                  <Tag>
                    {dayjs(evaluation.evaluatedAt).format('YYYY-MM-DD HH:mm')}
                  </Tag>
                </Space>
              }
            >
              <ScoreSummary
                score={evaluation.overallScore}
                level={evaluation.overallLevel}
                method={evaluation.method}
              />

              <Divider />

              <Title level={5}>Criterion Scores</Title>
              <Row gutter={[16, 16]} style={{ marginBottom: 24 }}>
                {evaluation.criteria.map((criterion) => (
                  <Col key={criterion.criterionName} xs={24} md={8}>
                    <ScoreCard criterion={criterion} />
                  </Col>
                ))}
              </Row>

              <Divider />

              <FeedbackPanel
                strengths={evaluation.strengths}
                improvements={evaluation.improvements}
                overallFeedback={evaluation.overallFeedback}
              />

              {/* Score Adjustments History */}
              {adjustments[evaluation.id] &&
                adjustments[evaluation.id].length > 0 && (
                  <>
                    <Divider />
                    <Title level={5}>Score Adjustments</Title>
                    <List
                      size="small"
                      dataSource={adjustments[evaluation.id]}
                      renderItem={(adj) => (
                        <List.Item>
                          <Text>
                            <strong>{adj.tutorName}</strong> adjusted score from{' '}
                            {adj.originalScore} to {adj.adjustedScore}
                            {adj.reason && ` — "${adj.reason}"`} (
                            {dayjs(adj.adjustedAt).format('YYYY-MM-DD HH:mm')})
                          </Text>
                        </List.Item>
                      )}
                    />
                  </>
                )}

              {/* Tutor: Adjust Score */}
              {(isAdmin || isTutor) && (
                <>
                  <Divider />
                  <Title level={5}>Adjust Score</Title>
                  <AdjustScoreForm
                    currentScore={evaluation.overallScore}
                    onSubmit={(score, reason) =>
                      handleAdjustScore(evaluation.id, score, reason)
                    }
                  />
                </>
              )}
            </Card>
          ))
        )}

        {/* Student Feedback */}
        <Card title="Your Feedback" style={{ marginBottom: 24 }}>
          {feedbacks.length > 0 && (
            <List
              style={{ marginBottom: 16 }}
              dataSource={feedbacks}
              renderItem={(fb) => (
                <List.Item>
                  <div>
                    <Rate disabled value={fb.rating} />
                    {fb.comment && (
                      <Text style={{ display: 'block', marginTop: 4 }}>
                        {fb.comment}
                      </Text>
                    )}
                    <Text type="secondary" style={{ fontSize: 12 }}>
                      {fb.studentName} -{' '}
                      {dayjs(fb.createdAt).format('YYYY-MM-DD HH:mm')}
                    </Text>
                  </div>
                </List.Item>
              )}
            />
          )}

          <Form
            form={feedbackForm}
            layout="vertical"
            onFinish={handleFeedback}
          >
            <Form.Item
              name="rating"
              label="How would you rate this evaluation?"
              rules={[{ required: true, message: 'Please give a rating' }]}
            >
              <Rate />
            </Form.Item>
            <Form.Item name="comment" label="Comments (optional)">
              <TextArea rows={3} placeholder="Share your thoughts..." />
            </Form.Item>
            <Button
              type="primary"
              htmlType="submit"
              loading={feedbackLoading}
            >
              Submit Feedback
            </Button>
          </Form>
        </Card>
      </div>
    </Spin>
  );
}

function AdjustScoreForm({
  currentScore,
  onSubmit,
}: {
  currentScore: number;
  onSubmit: (score: number, reason: string) => Promise<void>;
}) {
  const [form] = Form.useForm();
  const [loading, setLoading] = useState(false);

  const handleFinish = async (values: {
    adjustedScore: number;
    reason: string;
  }) => {
    setLoading(true);
    try {
      await onSubmit(values.adjustedScore, values.reason);
      form.resetFields();
    } finally {
      setLoading(false);
    }
  };

  return (
    <Form
      form={form}
      layout="inline"
      onFinish={handleFinish}
      initialValues={{ adjustedScore: currentScore }}
    >
      <Form.Item
        name="adjustedScore"
        label="New Score"
        rules={[{ required: true }]}
      >
        <InputNumber min={0} max={30} />
      </Form.Item>
      <Form.Item name="reason" label="Reason">
        <Input placeholder="Reason for adjustment" style={{ width: 300 }} />
      </Form.Item>
      <Form.Item>
        <Button type="primary" htmlType="submit" loading={loading}>
          Adjust
        </Button>
      </Form.Item>
    </Form>
  );
}
