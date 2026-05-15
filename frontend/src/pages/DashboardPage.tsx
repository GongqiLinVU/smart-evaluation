import { useEffect, useState } from 'react';
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
  Empty,
  Spin,
} from 'antd';
import {
  FileTextOutlined,
  TrophyOutlined,
  UploadOutlined,
  RiseOutlined,
} from '@ant-design/icons';
import type { ColumnsType } from 'antd/es/table';
import dayjs from 'dayjs';
import { listMySubmissions } from '../api/client';
import { useAuth } from '../context/AuthContext';
import type { SubmissionResponse } from '../types';

const { Title } = Typography;

function getLevelColor(level: string): string {
  const upper = level.toUpperCase();
  if (upper === 'EXCELLENT') return 'green';
  if (upper === 'PROFICIENT') return 'blue';
  if (upper === 'COMPETENT') return 'orange';
  if (upper === 'DEVELOPING') return 'gold';
  return 'red';
}

function getStatusColor(status: string): string {
  const upper = status.toUpperCase();
  if (upper === 'COMPLETED' || upper === 'EVALUATED') return 'green';
  if (upper === 'PENDING' || upper === 'UPLOADED') return 'blue';
  if (upper === 'FAILED') return 'red';
  return 'orange';
}

export default function DashboardPage() {
  const { user } = useAuth();
  const [submissions, setSubmissions] = useState<SubmissionResponse[]>([]);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    (async () => {
      try {
        const data = await listMySubmissions();
        setSubmissions(data);
      } catch (err) {
        console.error('Failed to fetch submissions:', err);
      } finally {
        setLoading(false);
      }
    })();
  }, []);

  const latestScore =
    submissions.length > 0 && submissions[0].latestScore !== null
      ? submissions[0].latestScore
      : null;

  const bestScore = submissions.reduce(
    (max, s) => (s.latestScore !== null && s.latestScore > max ? s.latestScore : max),
    0,
  );

  const improvement =
    submissions.length >= 2
      ? (() => {
          const scored = submissions
            .filter((s) => s.latestScore !== null)
            .sort(
              (a, b) =>
                dayjs(a.uploadedAt).unix() - dayjs(b.uploadedAt).unix(),
            );
          if (scored.length < 2) return null;
          return scored[scored.length - 1].latestScore! - scored[0].latestScore!;
        })()
      : null;

  const columns: ColumnsType<SubmissionResponse> = [
    {
      title: 'Project',
      dataIndex: 'projectName',
      key: 'projectName',
      render: (v: string | null) => v ?? <Tag>—</Tag>,
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
      title: 'File Name',
      dataIndex: 'fileName',
      key: 'fileName',
      ellipsis: true,
    },
    {
      title: 'Upload Date',
      dataIndex: 'uploadedAt',
      key: 'uploadedAt',
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
          <Tag>Pending</Tag>
        ),
    },
    {
      title: 'Actions',
      key: 'actions',
      render: (_, record) => (
        <Link to={`/submissions/${record.id}`}>
          <Button type="link">View Details</Button>
        </Link>
      ),
    },
  ];

  return (
    <div>
      <Title level={3}>Welcome, {user?.fullName}</Title>

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
              title="Latest Score"
              value={latestScore ?? '-'}
              suffix={latestScore !== null ? '/30' : ''}
              prefix={<TrophyOutlined />}
            />
          </Card>
        </Col>
        <Col xs={24} sm={6}>
          <Card>
            <Statistic
              title="Best Score"
              value={bestScore || '-'}
              suffix={bestScore ? '/30' : ''}
              prefix={<TrophyOutlined />}
              valueStyle={bestScore >= 24 ? { color: '#52c41a' } : undefined}
            />
          </Card>
        </Col>
        <Col xs={24} sm={6}>
          <Card>
            <Statistic
              title="Improvement"
              value={improvement !== null ? (improvement >= 0 ? `+${improvement}` : improvement) : '-'}
              prefix={<RiseOutlined />}
              valueStyle={
                improvement !== null && improvement > 0
                  ? { color: '#52c41a' }
                  : improvement !== null && improvement < 0
                    ? { color: '#ff4d4f' }
                    : undefined
              }
            />
          </Card>
        </Col>
      </Row>

      <Card
        title="My Submissions"
        extra={
          <Link to="/upload">
            <Button type="primary" icon={<UploadOutlined />}>
              New Submission
            </Button>
          </Link>
        }
      >
        <Spin spinning={loading}>
          {submissions.length === 0 && !loading ? (
            <Empty description="No submissions yet. Upload your first report!">
              <Link to="/upload">
                <Button type="primary">Upload Report</Button>
              </Link>
            </Empty>
          ) : (
            <Table
              columns={columns}
              dataSource={submissions}
              rowKey="id"
              pagination={{ pageSize: 10 }}
            />
          )}
        </Spin>
      </Card>
    </div>
  );
}
