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
  TeamOutlined,
  FileTextOutlined,
  CheckCircleOutlined,
  EyeOutlined,
} from '@ant-design/icons';
import type { ColumnsType } from 'antd/es/table';
import dayjs from 'dayjs';
import { listUsers, listLatestSubmissions } from '../api/client';
import { useAuth } from '../context/AuthContext';
import ProjectSelector from '../components/ProjectSelector';
import type { UserResponse, SubmissionResponse } from '../types';

const { Title } = Typography;

function getRoleColor(role: string): string {
  const upper = role.toUpperCase();
  if (upper === 'ADMIN') return 'red';
  if (upper === 'TUTOR') return 'purple';
  return 'blue';
}

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

export default function AdminDashboardPage() {
  const { user } = useAuth();
  const [users, setUsers] = useState<UserResponse[]>([]);
  const [submissions, setSubmissions] = useState<SubmissionResponse[]>([]);
  const [loading, setLoading] = useState(true);
  const [selectedProjectId, setSelectedProjectId] = useState<number | undefined>();

  const fetchData = useCallback(async (projectId?: number) => {
    setLoading(true);
    try {
      const [usersData, subsData] = await Promise.all([
        listUsers(),
        listLatestSubmissions(projectId),
      ]);
      setUsers(usersData);
      setSubmissions(subsData);
    } catch (err) {
      console.error('Failed to fetch data:', err);
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    fetchData(selectedProjectId);
  }, [selectedProjectId, fetchData]);

  const studentCount = users.filter(
    (u) => u.role.toUpperCase() === 'STUDENT',
  ).length;
  const tutorCount = users.filter(
    (u) => u.role.toUpperCase() === 'TUTOR',
  ).length;
  const evaluated = submissions.filter((s) => s.latestScore !== null).length;

  const userColumns: ColumnsType<UserResponse> = [
    {
      title: 'Name',
      dataIndex: 'fullName',
      key: 'fullName',
    },
    {
      title: 'Email',
      dataIndex: 'email',
      key: 'email',
    },
    {
      title: 'Role',
      dataIndex: 'role',
      key: 'role',
      render: (role: string) => (
        <Tag color={getRoleColor(role)}>{role}</Tag>
      ),
    },
    {
      title: 'Registered',
      dataIndex: 'createdAt',
      key: 'createdAt',
      render: (date: string) => dayjs(date).format('YYYY-MM-DD'),
    },
  ];

  const submissionColumns: ColumnsType<SubmissionResponse> = [
    {
      title: 'Student',
      dataIndex: 'studentName',
      key: 'studentName',
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
      title: 'Uploaded',
      dataIndex: 'uploadedAt',
      key: 'uploadedAt',
      defaultSortOrder: 'descend',
      sorter: (a, b) => dayjs(a.uploadedAt).unix() - dayjs(b.uploadedAt).unix(),
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
        <Link to={`/submissions/${record.id}`}>
          <Button type="link" icon={<EyeOutlined />}>
            View
          </Button>
        </Link>
      ),
    },
  ];

  return (
    <div>
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: 24 }}>
        <Title level={3} style={{ margin: 0 }}>Welcome, {user?.fullName}</Title>
        <ProjectSelector value={selectedProjectId} onChange={setSelectedProjectId} allowAll />
      </div>

      <Row gutter={16} style={{ marginBottom: 24 }}>
        <Col xs={24} sm={6}>
          <Card>
            <Statistic
              title="Total Users"
              value={users.length}
              prefix={<TeamOutlined />}
            />
          </Card>
        </Col>
        <Col xs={24} sm={6}>
          <Card>
            <Statistic
              title="Students"
              value={studentCount}
              valueStyle={{ color: '#1890ff' }}
            />
          </Card>
        </Col>
        <Col xs={24} sm={6}>
          <Card>
            <Statistic
              title="Tutors"
              value={tutorCount}
              valueStyle={{ color: '#722ed1' }}
            />
          </Card>
        </Col>
        <Col xs={24} sm={6}>
          <Card>
            <Statistic
              title="Submissions"
              value={submissions.length}
              prefix={<FileTextOutlined />}
              suffix={
                <span style={{ fontSize: 14, color: '#52c41a' }}>
                  {evaluated > 0 && (
                    <>
                      <CheckCircleOutlined /> {evaluated} evaluated
                    </>
                  )}
                </span>
              }
            />
          </Card>
        </Col>
      </Row>

      <Spin spinning={loading}>
        <Row gutter={16}>
          <Col xs={24} lg={12}>
            <Card
              title="Recent Users"
              extra={
                <Link to="/admin/users">
                  <Button type="link">Manage Users</Button>
                </Link>
              }
              style={{ marginBottom: 24 }}
            >
              <Table
                columns={userColumns}
                dataSource={users.slice(0, 5)}
                rowKey="id"
                pagination={false}
                size="small"
              />
            </Card>
          </Col>
          <Col xs={24} lg={12}>
            <Card
              title="Recent Submissions"
              extra={
                <Link to="/submissions">
                  <Button type="link">View All</Button>
                </Link>
              }
              style={{ marginBottom: 24 }}
            >
              <Table
                columns={submissionColumns}
                dataSource={[...submissions]
                  .sort(
                    (a, b) =>
                      dayjs(b.uploadedAt).unix() - dayjs(a.uploadedAt).unix(),
                  )
                  .slice(0, 5)}
                rowKey="id"
                pagination={false}
                size="small"
              />
            </Card>
          </Col>
        </Row>
      </Spin>
    </div>
  );
}
