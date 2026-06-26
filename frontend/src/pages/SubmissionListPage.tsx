import { useEffect, useState, useCallback } from 'react';
import { Link } from 'react-router-dom';
import { Table, Tag, Button, Spin, Typography, Space, Card, Select, message, Modal, Divider, Dropdown } from 'antd';
import { ReloadOutlined, EyeOutlined, RobotOutlined, CheckCircleOutlined, ExperimentOutlined } from '@ant-design/icons';
import type { ColumnsType } from 'antd/es/table';
import dayjs from 'dayjs';
import { listLatestSubmissions, listVersions, runEvaluation } from '../api/client';
import ProjectSelector from '../components/ProjectSelector';
import ScoreSummary from '../components/ScoreSummary';
import type { SubmissionResponse, EvaluationResultResponse } from '../types';

const { Title } = Typography;

function getStatusColor(status: string): string {
  const upper = status.toUpperCase();
  if (upper === 'COMPLETED' || upper === 'EVALUATED') return 'green';
  if (upper === 'PENDING' || upper === 'UPLOADED') return 'blue';
  if (upper === 'FAILED') return 'red';
  if (upper === 'PROCESSING') return 'orange';
  return 'default';
}

function getLevelColor(level: string): string {
  const upper = level.toUpperCase();
  if (upper === 'EXCELLENT') return 'green';
  if (upper === 'PROFICIENT') return 'blue';
  if (upper === 'COMPETENT') return 'orange';
  if (upper === 'DEVELOPING') return 'gold';
  return 'red';
}

export default function SubmissionListPage() {
  const [submissions, setSubmissions] = useState<SubmissionResponse[]>([]);
  const [loading, setLoading] = useState(true);
  const [versionOptions, setVersionOptions] = useState<
    Record<string, SubmissionResponse[]>
  >({});
  const [versionLoading, setVersionLoading] = useState<Record<string, boolean>>(
    {},
  );
  const [selectedProjectId, setSelectedProjectId] = useState<number | undefined>();
  const [evalRunning, setEvalRunning] = useState<Record<number, boolean>>({});
  const [evalResult, setEvalResult] = useState<EvaluationResultResponse | null>(null);

  const fetchSubmissions = useCallback(async (projectId?: number) => {
    setLoading(true);
    try {
      const data = await listLatestSubmissions(projectId);
      setSubmissions(data);
      setVersionOptions({});
    } catch (err) {
      console.error('Failed to fetch submissions:', err);
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    if (selectedProjectId !== undefined) {
      fetchSubmissions(selectedProjectId);
    }
  }, [selectedProjectId, fetchSubmissions]);

  const versionKey = (s: SubmissionResponse) =>
    `${s.studentName}::${s.taskId ?? 'none'}`;

  const loadVersions = async (record: SubmissionResponse) => {
    const key = versionKey(record);
    if (versionOptions[key]) return;
    setVersionLoading((prev) => ({ ...prev, [key]: true }));
    try {
      const versions = await listVersions(
        record.studentName,
        record.projectId ?? undefined,
        record.taskId ?? undefined,
      );
      setVersionOptions((prev) => ({ ...prev, [key]: versions }));
    } catch (err) {
      console.error('Failed to load versions:', err);
    } finally {
      setVersionLoading((prev) => ({ ...prev, [key]: false }));
    }
  };

  const handleVersionChange = (record: SubmissionResponse, submissionId: number) => {
    const key = versionKey(record);
    const versions = versionOptions[key];
    if (!versions) return;
    const selected = versions.find((v) => v.id === submissionId);
    if (!selected) return;
    setSubmissions((prev) =>
      prev.map((s) => (versionKey(s) === key ? selected : s)),
    );
  };

  const handleRunEval = async (record: SubmissionResponse, method: 'LLM' | 'HYBRID') => {
    setEvalRunning((prev) => ({ ...prev, [record.id]: true }));
    try {
      const result = await runEvaluation(record.id, method, 'INTERNAL');
      setEvalResult(result);
      fetchSubmissions(selectedProjectId);
    } catch (err: unknown) {
      const detail =
        (err as { response?: { data?: { error?: string } } })?.response?.data?.error;
      message.error(detail ?? `${method} evaluation failed`);
    } finally {
      setEvalRunning((prev) => ({ ...prev, [record.id]: false }));
    }
  };

  const columns: ColumnsType<SubmissionResponse> = [
    {
      title: 'Student Name',
      dataIndex: 'studentName',
      key: 'studentName',
      sorter: (a, b) => a.studentName.localeCompare(b.studentName),
    },
    {
      title: 'Task',
      dataIndex: 'taskName',
      key: 'taskName',
      render: (v: string | null) => v ?? <Tag>—</Tag>,
    },
    {
      title: 'Version',
      key: 'version',
      width: 150,
      render: (_, record) => {
        const key = versionKey(record);
        return record.totalVersions > 1 ? (
          <Select
            value={record.id}
            size="small"
            style={{ width: 120 }}
            loading={versionLoading[key]}
            onDropdownVisibleChange={(open) => {
              if (open) loadVersions(record);
            }}
            onChange={(id) => handleVersionChange(record, id)}
            options={
              versionOptions[key]
                ? versionOptions[key].map((v) => ({
                    label: `v${v.version}`,
                    value: v.id,
                  }))
                : [{ label: `v${record.version}`, value: record.id }]
            }
          />
        ) : (
          <Tag>v{record.version}</Tag>
        );
      },
    },
    {
      title: 'File Name',
      dataIndex: 'fileName',
      key: 'fileName',
      ellipsis: true,
    },
    {
      title: 'Upload Date',
      dataIndex: 'uploadedAt',
      key: 'uploadedAt',
      sorter: (a, b) => dayjs(a.uploadedAt).unix() - dayjs(b.uploadedAt).unix(),
      defaultSortOrder: 'descend',
      render: (date: string) => dayjs(date).format('YYYY-MM-DD HH:mm'),
    },
    {
      title: 'Status',
      dataIndex: 'status',
      key: 'status',
      render: (status: string) => (
        <Tag color={getStatusColor(status)}>{status}</Tag>
      ),
    },
    {
      title: 'Score',
      key: 'score',
      render: (_, record) =>
        record.latestScore !== null && record.latestLevel !== null ? (
          <span>
            <strong>{record.latestScore}</strong>/{record.latestMaxScore ?? 30}{' '}
            <Tag color={getLevelColor(record.latestLevel)}>
              {record.latestLevel}
            </Tag>
          </span>
        ) : (
          <Tag>Not evaluated</Tag>
        ),
    },
    {
      title: 'Actions',
      key: 'actions',
      render: (_, record) => (
        <Space>
          <Link to={`/submissions/${record.id}`}>
            <Button type="link" icon={<EyeOutlined />}>
              View
            </Button>
          </Link>
          <Dropdown
            menu={{
              items: [
                { key: 'LLM', label: 'LLM Eval', icon: <RobotOutlined /> },
                { key: 'HYBRID', label: 'Hybrid Eval', icon: <ExperimentOutlined /> },
              ],
              onClick: ({ key }) => handleRunEval(record, key as 'LLM' | 'HYBRID'),
            }}
          >
            <Button
              type="link"
              icon={<RobotOutlined />}
              loading={evalRunning[record.id]}
            >
              Evaluate
            </Button>
          </Dropdown>
        </Space>
      ),
    },
  ];

  return (
    <div>
      <div
        style={{
          display: 'flex',
          justifyContent: 'space-between',
          alignItems: 'center',
          marginBottom: 24,
        }}
      >
        <Title level={3} style={{ margin: 0 }}>
          All Submissions
        </Title>
        <Space>
          <ProjectSelector value={selectedProjectId} onChange={setSelectedProjectId} />
          <Button
            icon={<ReloadOutlined />}
            onClick={() => fetchSubmissions(selectedProjectId)}
            loading={loading}
          >
            Refresh
          </Button>
        </Space>
      </div>

      <Card>
        <Spin spinning={loading}>
          <Table
            columns={columns}
            dataSource={submissions}
            rowKey="id"
            pagination={{ pageSize: 10, showSizeChanger: true }}
            locale={{
              emptyText: 'No submissions yet.',
            }}
          />
        </Spin>
      </Card>

      <Modal
        title={
          <Space>
            <CheckCircleOutlined style={{ color: '#52c41a' }} />
            {evalResult?.method === 'HYBRID' ? 'Hybrid' : evalResult?.method === 'RULE_BASED' ? 'Rule-Based' : 'LLM'} Evaluation Complete (Internal)
          </Space>
        }
        open={evalResult !== null}
        onCancel={() => setEvalResult(null)}
        footer={[
          <Button key="close" onClick={() => setEvalResult(null)}>
            Close
          </Button>,
          evalResult && (
            <Link key="view" to={`/submissions/${evalResult.submissionId}`}>
              <Button type="primary" icon={<EyeOutlined />}>
                View Full Details
              </Button>
            </Link>
          ),
        ]}
        width={500}
      >
        {evalResult && (
          <div>
            <ScoreSummary
              score={evalResult.overallScore}
              maxScore={evalResult.maxScore}
              level={evalResult.overallLevel}
              method={evalResult.method}
            />
            <Divider />
            {evalResult.strengths.length > 0 && (
              <div style={{ marginBottom: 12 }}>
                <Typography.Text strong>Strengths:</Typography.Text>
                <ul style={{ margin: '4px 0', paddingLeft: 20 }}>
                  {evalResult.strengths.map((s, i) => (
                    <li key={i}>{s}</li>
                  ))}
                </ul>
              </div>
            )}
            {evalResult.improvements.length > 0 && (
              <div>
                <Typography.Text strong>Improvements:</Typography.Text>
                <ul style={{ margin: '4px 0', paddingLeft: 20 }}>
                  {evalResult.improvements.map((s, i) => (
                    <li key={i}>{s}</li>
                  ))}
                </ul>
              </div>
            )}
          </div>
        )}
      </Modal>
    </div>
  );
}
