import { useEffect, useState } from 'react';
import {
  Table,
  Tag,
  Select,
  Typography,
  Card,
  Spin,
  message,
  Space,
  Button,
  Input,
  Modal,
} from 'antd';
import { UsergroupAddOutlined } from '@ant-design/icons';
import type { ColumnsType } from 'antd/es/table';
import dayjs from 'dayjs';
import {
  listUsers,
  changeUserRole,
  updateUserAcademicInfo,
  listProjects,
  bulkAddProjectMembers,
} from '../api/client';
import type { UserResponse, ProjectResponse } from '../types';

const { Title } = Typography;

function getRoleColor(role: string): string {
  const upper = role.toUpperCase();
  if (upper === 'ADMIN') return 'red';
  if (upper === 'TUTOR') return 'purple';
  return 'blue';
}

export default function AdminUsersPage() {
  const [users, setUsers] = useState<UserResponse[]>([]);
  const [loading, setLoading] = useState(true);
  const [filterYear, setFilterYear] = useState<string | undefined>();
  const [filterSemester, setFilterSemester] = useState<string | undefined>();
  const [selectedRowKeys, setSelectedRowKeys] = useState<React.Key[]>([]);

  const [bulkModalOpen, setBulkModalOpen] = useState(false);
  const [projects, setProjects] = useState<ProjectResponse[]>([]);
  const [bulkProjectId, setBulkProjectId] = useState<number | undefined>();
  const [bulkLoading, setBulkLoading] = useState(false);

  const fetchUsers = async () => {
    setLoading(true);
    try {
      const data = await listUsers();
      setUsers(data);
    } catch (err) {
      console.error('Failed to fetch users:', err);
      message.error('Failed to load users');
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    fetchUsers();
  }, []);

  const handleRoleChange = async (userId: number, newRole: string) => {
    try {
      await changeUserRole(userId, newRole);
      message.success('Role updated');
      fetchUsers();
    } catch {
      message.error('Failed to update role');
    }
  };

  const handleAcademicInfoChange = async (
    userId: number,
    academicYear?: string,
    semester?: string,
  ) => {
    try {
      await updateUserAcademicInfo(userId, academicYear, semester);
      message.success('Academic info updated');
      fetchUsers();
    } catch {
      message.error('Failed to update academic info');
    }
  };

  const filteredUsers = users.filter((u) => {
    if (filterYear && u.academicYear !== filterYear) return false;
    if (filterSemester && u.semester !== filterSemester) return false;
    return true;
  });

  const yearOptions = [
    ...new Set(users.map((u) => u.academicYear).filter(Boolean)),
  ].map((y) => ({ label: y!, value: y! }));

  const openBulkModal = async () => {
    setBulkProjectId(undefined);
    setBulkModalOpen(true);
    try {
      const data = await listProjects();
      setProjects(data);
    } catch {
      message.error('Failed to load projects');
    }
  };

  const handleBulkAdd = async () => {
    if (!bulkProjectId) {
      message.warning('Please select a project');
      return;
    }
    const studentIds = selectedRowKeys.map((k) => Number(k));
    if (studentIds.length === 0) {
      message.warning('No users selected');
      return;
    }
    setBulkLoading(true);
    try {
      const members = studentIds.map((userId) => ({
        userId,
        role: 'STUDENT',
      }));
      await bulkAddProjectMembers(bulkProjectId, members);
      message.success(`${studentIds.length} student(s) added to project`);
      setBulkModalOpen(false);
      setSelectedRowKeys([]);
    } catch {
      message.error('Failed to add students to project');
    } finally {
      setBulkLoading(false);
    }
  };

  const columns: ColumnsType<UserResponse> = [
    {
      title: 'Name',
      dataIndex: 'fullName',
      key: 'fullName',
      sorter: (a, b) => a.fullName.localeCompare(b.fullName),
    },
    {
      title: 'Email',
      dataIndex: 'email',
      key: 'email',
    },
    {
      title: 'Role',
      key: 'role',
      render: (_, record) => (
        <Tag color={getRoleColor(record.role)}>{record.role}</Tag>
      ),
    },
    {
      title: 'Academic Year',
      dataIndex: 'academicYear',
      key: 'academicYear',
      width: 150,
      render: (v: string | null, record) => (
        <Input
          size="small"
          defaultValue={v ?? ''}
          placeholder="e.g. 2025/2026"
          style={{ width: 120 }}
          onBlur={(e) => {
            const newVal = e.target.value || undefined;
            if (newVal !== (record.academicYear ?? undefined)) {
              handleAcademicInfoChange(record.id, newVal, record.semester ?? undefined);
            }
          }}
          onPressEnter={(e) => (e.target as HTMLInputElement).blur()}
        />
      ),
    },
    {
      title: 'Semester',
      dataIndex: 'semester',
      key: 'semester',
      width: 130,
      render: (v: string | null, record) => (
        <Select
          size="small"
          value={v ?? undefined}
          allowClear
          placeholder="—"
          style={{ width: 110 }}
          onChange={(newVal) =>
            handleAcademicInfoChange(record.id, record.academicYear ?? undefined, newVal)
          }
          options={[
            { label: 'S1', value: 'S1' },
            { label: 'S2', value: 'S2' },
            { label: 'Summer', value: 'Summer' },
          ]}
        />
      ),
    },
    {
      title: 'Registered',
      dataIndex: 'createdAt',
      key: 'createdAt',
      render: (date: string) => dayjs(date).format('YYYY-MM-DD HH:mm'),
    },
    {
      title: 'Change Role',
      key: 'actions',
      render: (_, record) => (
        <Select
          value={record.role}
          onChange={(value) => handleRoleChange(record.id, value)}
          style={{ width: 120 }}
          options={[
            { label: 'Student', value: 'STUDENT' },
            { label: 'Tutor', value: 'TUTOR' },
            { label: 'Admin', value: 'ADMIN' },
          ]}
        />
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
          User Management
        </Title>
        <Space>
          <Input
            placeholder="Academic Year"
            allowClear
            value={filterYear}
            onChange={(e) => setFilterYear(e.target.value || undefined)}
            style={{ width: 140 }}
          />
          <Select
            placeholder="Semester"
            allowClear
            value={filterSemester}
            onChange={(v) => setFilterSemester(v)}
            style={{ width: 130 }}
            options={[
              { label: 'S1', value: 'S1' },
              { label: 'S2', value: 'S2' },
              { label: 'Summer', value: 'Summer' },
            ]}
          />
          <Button
            type="primary"
            icon={<UsergroupAddOutlined />}
            disabled={selectedRowKeys.length === 0}
            onClick={openBulkModal}
          >
            Add to Project ({selectedRowKeys.length})
          </Button>
        </Space>
      </div>

      <Card>
        <Spin spinning={loading}>
          <Table
            columns={columns}
            dataSource={filteredUsers}
            rowKey="id"
            pagination={{ pageSize: 20 }}
            rowSelection={{
              selectedRowKeys,
              onChange: setSelectedRowKeys,
            }}
          />
        </Spin>
      </Card>

      <Modal
        title="Add Selected Users to Project"
        open={bulkModalOpen}
        onCancel={() => setBulkModalOpen(false)}
        onOk={handleBulkAdd}
        confirmLoading={bulkLoading}
      >
        <p>
          Adding <strong>{selectedRowKeys.length}</strong> selected user(s) as
          students to:
        </p>
        <Select
          placeholder="Select a project"
          style={{ width: '100%' }}
          value={bulkProjectId}
          onChange={setBulkProjectId}
          options={projects.map((p) => ({
            label: `${p.name} (${p.academicYear} ${p.semester})`,
            value: p.id,
          }))}
        />
      </Modal>
    </div>
  );
}
