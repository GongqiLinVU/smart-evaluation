import { useEffect, useState, useCallback } from 'react';
import { Link } from 'react-router-dom';
import {
  Card,
  Table,
  Tag,
  Button,
  Typography,
  Row,
  Col,
  Statistic,
  Spin,
} from 'antd';
import {
  FileTextOutlined,
  CheckCircleOutlined,
  ClockCircleOutlined,
  EyeOutlined,
} from '@ant-design/icons';
import type { ColumnsType } from 'antd/es/table';
import dayjs from 'dayjs';
import { listLatestSubmissions } from '../api/client';
import { useAuth } from '../context/AuthContext';
import ProjectSelector from '../components/ProjectSelector';
import type { SubmissionResponse } from '../types';

const { Title } = Typography;

function getStatusColor(status: string): string {
  const upper = status.toUpperCase();
  if (upper === 'COMPLETED' || upper === 'EVALUATED') return 'green';
  if (upper === 'PENDING' || upper === 'UPLOADED') return 'blue';
  if (upper === 'FAILED') return 'red';
  return 'orange';
}

function getLevelColor(level: string): string {
  const upper = level.toUpperCase();
  if (upper === 'EXCELLENT') return 'green';
  if (upper === 'PROFICIENT') return 'blue';
  if (upper === 'COMPETENT') return 'orange';
  if (upper === 'DEVELOPING') return 'gold';
  return 'red';
}

export default function TutorDashboardPage() {
  const { user } = useAuth();
  const [submissions, setSubmissions] = useState<SubmissionResponse[]>([]);
  const [loading, setLoading] = useState(true);
  const [selectedProjectId, setSelectedProjectId] = useState<number | undefined>();

  const fetchSubmissions = useCallback(async (projectId?: number) => {
    setLoading(true);
    try {
      const data = await listLatestSubmissions(projectId);
      setSubmissions(data);
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

  const evaluated = submissions.filter(
    (s) => s.latestScore !== null,
  );
  const pending = submissions.filter(
    (s) => s.latestScore === null,
  );
  const avgScore =
    evaluated.length > 0
      ? evaluated.reduce((sum, s) => sum + s.latestScore!, 0) / evaluated.length
      : 0;

  const columns: ColumnsType<SubmissionResponse> = [
    {
      title: 'Student',
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
      dataIndex: 'version',
      key: 'version',
      width: 80,
      render: (v: number) => <Tag>v{v}</Tag>,
    },
    {
      title: 'File',
      dataIndex: 'fileName',
      key: 'fileName',
      ellipsis: true,
    },
    {
      title: 'Uploaded',
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
            <strong>{record.latestScore}</strong>/30{' '}
            <Tag color={getLevelColor(record.latestLevel)}>
              {record.latestLevel}
            </Tag>
          </span>
        ) : (
          <Tag color="orange">Needs Review</Tag>
        ),
    },
    {
      title: 'Actions',
      key: 'actions',
      render: (_, record) => (
        <Link to={`/submissions/${record.id}`}>
          <Button type="link" icon={<EyeOutlined />}>
            Review
          </Button>
        </Link>
      ),
    },
  ];

  return (
    <div>
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: 24 }}>
        <Title level={3} style={{ margin: 0 }}>Welcome, {user?.fullName}</Title>
        <ProjectSelector value={selectedProjectId} onChange={setSelectedProjectId} />
      </div>

      <Row gutter={16} style={{ marginBottom: 24 }}>
        <Col xs={24} sm={6}>
          <Card>
            <Statistic
              title="Total Submissions"
              value={submissions.length}
              prefix={<FileTextOutlined />}
            />
          </Card>
        </Col>
        <Col xs={24} sm={6}>
          <Card>
            <Statistic
              title="Evaluated"
              value={evaluated.length}
              prefix={<CheckCircleOutlined />}
              valueStyle={{ color: '#52c41a' }}
            />
          </Card>
        </Col>
        <Col xs={24} sm={6}>
          <Card>
            <Statistic
              title="Pending Review"
              value={pending.length}
              prefix={<ClockCircleOutlined />}
              valueStyle={pending.length > 0 ? { color: '#faad14' } : undefined}
            />
          </Card>
        </Col>
        <Col xs={24} sm={6}>
          <Card>
            <Statistic
              title="Average Score"
              value={avgScore ? avgScore.toFixed(1) : '-'}
              suffix={avgScore ? '/30' : ''}
            />
          </Card>
        </Col>
      </Row>

      <Card
        title="All Submissions"
        extra={
          <Link to="/submissions">
            <Button type="link">View All</Button>
          </Link>
        }
      >
        <Spin spinning={loading}>
          <Table
            columns={columns}
            dataSource={submissions}
            rowKey="id"
            pagination={{ pageSize: 10, showSizeChanger: true }}
          />
        </Spin>
      </Card>
    </div>
  );
}
