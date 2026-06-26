import { useEffect, useState } from 'react';
import {
  Card,
  Table,
  Button,
  Typography,
  Space,
  Modal,
  Form,
  Input,
  Select,
  Tag,
  Spin,
  message,
  Popconfirm,
} from 'antd';
import {
  PlusOutlined,
  EditOutlined,
  DeleteOutlined,
  TeamOutlined,
  UserAddOutlined,
  UnorderedListOutlined,
} from '@ant-design/icons';
import type { ColumnsType } from 'antd/es/table';
import dayjs from 'dayjs';
import {
  listProjects,
  createProject,
  updateProject,
  deleteProject,
  listProjectMembers,
  addProjectMember,
  removeProjectMember,
  listUsers,
  listProjectTasks,
  createProjectTask,
  updateProjectTask,
  deleteProjectTask,
  listRulePackages,
  listLlmConfigs,
} from '../api/client';
import { useAuth } from '../context/AuthContext';
import type { ProjectResponse, ProjectMemberResponse, UserResponse, ProjectTaskResponse, RulePackageResponse, LlmConfigResponse } from '../types';

const { Title } = Typography;

export default function ProjectsPage() {
  const { isAdmin } = useAuth();
  const [projects, setProjects] = useState<ProjectResponse[]>([]);
  const [loading, setLoading] = useState(true);

  const [modalOpen, setModalOpen] = useState(false);
  const [editingProject, setEditingProject] = useState<ProjectResponse | null>(null);
  const [form] = Form.useForm();

  const [membersModalOpen, setMembersModalOpen] = useState(false);
  const [selectedProject, setSelectedProject] = useState<ProjectResponse | null>(null);
  const [members, setMembers] = useState<ProjectMemberResponse[]>([]);
  const [membersLoading, setMembersLoading] = useState(false);

  const [addMemberModalOpen, setAddMemberModalOpen] = useState(false);
  const [allUsers, setAllUsers] = useState<UserResponse[]>([]);
  const [addMemberForm] = Form.useForm();

  const [tasksModalOpen, setTasksModalOpen] = useState(false);
  const [tasksProject, setTasksProject] = useState<ProjectResponse | null>(null);
  const [projectTasks, setProjectTasks] = useState<ProjectTaskResponse[]>([]);
  const [tasksLoading, setTasksLoading] = useState(false);
  const [taskModalOpen, setTaskModalOpen] = useState(false);
  const [editingTask, setEditingTask] = useState<ProjectTaskResponse | null>(null);
  const [taskForm] = Form.useForm();
  const [rulePackages, setRulePackages] = useState<RulePackageResponse[]>([]);
  const [llmConfigs, setLlmConfigs] = useState<LlmConfigResponse[]>([]);

  const fetchProjects = async () => {
    setLoading(true);
    try {
      const data = await listProjects();
      setProjects(data);
    } catch {
      message.error('Failed to load projects');
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    fetchProjects();
    listRulePackages().then(setRulePackages).catch(() => {});
    listLlmConfigs().then(setLlmConfigs).catch(() => {});
  }, []);

  const handleCreateOrUpdate = async (values: {
    name: string;
    academicYear: string;
    semester: string;
    description?: string;
    rulePackageId?: number;
  }) => {
    try {
      if (editingProject) {
        await updateProject(editingProject.id, values);
        message.success('Project updated');
      } else {
        await createProject(values);
        message.success('Project created');
      }
      setModalOpen(false);
      setEditingProject(null);
      form.resetFields();
      fetchProjects();
    } catch {
      message.error('Operation failed');
    }
  };

  const handleDelete = async (id: number) => {
    try {
      await deleteProject(id);
      message.success('Project deleted');
      fetchProjects();
    } catch {
      message.error('Failed to delete project');
    }
  };

  const openEdit = (project: ProjectResponse) => {
    setEditingProject(project);
    form.setFieldsValue({
      name: project.name,
      academicYear: project.academicYear,
      semester: project.semester,
      description: project.description,
      rulePackageId: project.rulePackageId,
    });
    setModalOpen(true);
  };

  const openCreate = () => {
    setEditingProject(null);
    form.resetFields();
    setModalOpen(true);
  };

  const openMembers = async (project: ProjectResponse) => {
    setSelectedProject(project);
    setMembersModalOpen(true);
    setMembersLoading(true);
    try {
      const data = await listProjectMembers(project.id);
      setMembers(data);
    } catch {
      message.error('Failed to load members');
    } finally {
      setMembersLoading(false);
    }
  };

  const handleRemoveMember = async (userId: number) => {
    if (!selectedProject) return;
    try {
      await removeProjectMember(selectedProject.id, userId);
      message.success('Member removed');
      const data = await listProjectMembers(selectedProject.id);
      setMembers(data);
      fetchProjects();
    } catch {
      message.error('Failed to remove member');
    }
  };

  const openAddMember = async () => {
    addMemberForm.resetFields();
    setAddMemberModalOpen(true);
    try {
      const users = await listUsers();
      const memberIds = new Set(members.map((m) => m.userId));
      setAllUsers(users.filter((u) => !memberIds.has(u.id)));
    } catch {
      message.error('Failed to load users');
    }
  };

  const handleAddMember = async (values: { userId: number; role: string }) => {
    if (!selectedProject) return;
    try {
      await addProjectMember(selectedProject.id, values.userId, values.role);
      message.success('Member added');
      setAddMemberModalOpen(false);
      const data = await listProjectMembers(selectedProject.id);
      setMembers(data);
      fetchProjects();
    } catch {
      message.error('Failed to add member');
    }
  };

  const openTasks = async (project: ProjectResponse) => {
    setTasksProject(project);
    setTasksModalOpen(true);
    setTasksLoading(true);
    try {
      const data = await listProjectTasks(project.id);
      setProjectTasks(data);
    } catch {
      message.error('Failed to load tasks');
    } finally {
      setTasksLoading(false);
    }
  };

  const openCreateTask = () => {
    setEditingTask(null);
    taskForm.resetFields();
    setTaskModalOpen(true);
  };

  const openEditTask = (task: ProjectTaskResponse) => {
    setEditingTask(task);
    taskForm.setFieldsValue({
      name: task.name,
      description: task.description,
      displayOrder: task.displayOrder,
      rulePackageId: task.rulePackageId,
      llmConfigId: task.llmConfigId,
    });
    setTaskModalOpen(true);
  };

  const handleCreateOrUpdateTask = async (values: {
    name: string;
    description?: string;
    displayOrder?: number;
    rulePackageId?: number;
    llmConfigId?: number;
  }) => {
    if (!tasksProject) return;
    try {
      if (editingTask) {
        await updateProjectTask(tasksProject.id, editingTask.id, values);
        message.success('Task updated');
      } else {
        await createProjectTask(tasksProject.id, values);
        message.success('Task created');
      }
      setTaskModalOpen(false);
      setEditingTask(null);
      taskForm.resetFields();
      const data = await listProjectTasks(tasksProject.id);
      setProjectTasks(data);
    } catch {
      message.error('Operation failed');
    }
  };

  const handleDeleteTask = async (taskId: number) => {
    if (!tasksProject) return;
    try {
      await deleteProjectTask(tasksProject.id, taskId);
      message.success('Task deleted');
      const data = await listProjectTasks(tasksProject.id);
      setProjectTasks(data);
    } catch {
      message.error('Failed to delete task');
    }
  };

  const columns: ColumnsType<ProjectResponse> = [
    {
      title: 'Name',
      dataIndex: 'name',
      key: 'name',
      sorter: (a, b) => a.name.localeCompare(b.name),
    },
    {
      title: 'Academic Year',
      dataIndex: 'academicYear',
      key: 'academicYear',
    },
    {
      title: 'Semester',
      dataIndex: 'semester',
      key: 'semester',
    },
    {
      title: 'Rule Package',
      dataIndex: 'rulePackageName',
      key: 'rulePackageName',
      render: (name: string | null) =>
        name ? <Tag color="geekblue">{name}</Tag> : <Tag>Default</Tag>,
    },
    {
      title: 'Members',
      dataIndex: 'memberCount',
      key: 'memberCount',
      render: (count: number) => <Tag>{count}</Tag>,
    },
    {
      title: 'Created',
      dataIndex: 'createdAt',
      key: 'createdAt',
      render: (date: string) => dayjs(date).format('YYYY-MM-DD'),
    },
    {
      title: 'Actions',
      key: 'actions',
      render: (_, record) => (
        <Space>
          <Button
            type="link"
            icon={<TeamOutlined />}
            onClick={() => openMembers(record)}
          >
            Members
          </Button>
          <Button
            type="link"
            icon={<UnorderedListOutlined />}
            onClick={() => openTasks(record)}
          >
            Tasks
          </Button>
          <Button
            type="link"
            icon={<EditOutlined />}
            onClick={() => openEdit(record)}
          >
            Edit
          </Button>
          {isAdmin && (
            <Popconfirm
              title="Delete this project?"
              onConfirm={() => handleDelete(record.id)}
            >
              <Button type="link" danger icon={<DeleteOutlined />}>
                Delete
              </Button>
            </Popconfirm>
          )}
        </Space>
      ),
    },
  ];

  const memberColumns: ColumnsType<ProjectMemberResponse> = [
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
        <Tag color={role === 'TUTOR' ? 'purple' : 'blue'}>{role}</Tag>
      ),
    },
    {
      title: 'Joined',
      dataIndex: 'joinedAt',
      key: 'joinedAt',
      render: (date: string) => dayjs(date).format('YYYY-MM-DD'),
    },
    {
      title: 'Actions',
      key: 'actions',
      render: (_, record) => (
        <Popconfirm
          title="Remove this member?"
          onConfirm={() => handleRemoveMember(record.userId)}
        >
          <Button type="link" danger>
            Remove
          </Button>
        </Popconfirm>
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
          Projects
        </Title>
        <Button type="primary" icon={<PlusOutlined />} onClick={openCreate}>
          Create Project
        </Button>
      </div>

      <Card>
        <Spin spinning={loading}>
          <Table
            columns={columns}
            dataSource={projects}
            rowKey="id"
            pagination={{ pageSize: 10, showSizeChanger: true }}
          />
        </Spin>
      </Card>

      <Modal
        title={editingProject ? 'Edit Project' : 'Create Project'}
        open={modalOpen}
        onCancel={() => {
          setModalOpen(false);
          setEditingProject(null);
        }}
        onOk={() => form.submit()}
      >
        <Form form={form} layout="vertical" onFinish={handleCreateOrUpdate}>
          <Form.Item
            name="name"
            label="Project Name"
            rules={[{ required: true, message: 'Please enter a project name' }]}
          >
            <Input placeholder="e.g. Capstone 2026" />
          </Form.Item>
          <Form.Item
            name="academicYear"
            label="Academic Year"
            rules={[{ required: true, message: 'Please enter the academic year' }]}
          >
            <Input placeholder="e.g. 2025/2026" />
          </Form.Item>
          <Form.Item
            name="semester"
            label="Semester"
            rules={[{ required: true, message: 'Please select a semester' }]}
          >
            <Select
              placeholder="Select semester"
              options={[
                { label: 'Semester 1', value: 'S1' },
                { label: 'Semester 2', value: 'S2' },
                { label: 'Summer', value: 'Summer' },
              ]}
            />
          </Form.Item>
          <Form.Item name="description" label="Description">
            <Input.TextArea rows={3} placeholder="Optional description" />
          </Form.Item>
          <Form.Item name="rulePackageId" label="Rule Package">
            <Select
              placeholder="Use default rule package"
              allowClear
              options={rulePackages.map((rp) => ({
                label: `${rp.name}${rp.isDefault ? ' (Default)' : ''}`,
                value: rp.id,
              }))}
            />
          </Form.Item>
        </Form>
      </Modal>

      <Modal
        title={`Members — ${selectedProject?.name ?? ''}`}
        open={membersModalOpen}
        onCancel={() => setMembersModalOpen(false)}
        footer={null}
        width={700}
      >
        <div style={{ marginBottom: 16 }}>
          <Button icon={<UserAddOutlined />} onClick={openAddMember}>
            Add Member
          </Button>
        </div>
        <Spin spinning={membersLoading}>
          <Table
            columns={memberColumns}
            dataSource={members}
            rowKey="id"
            pagination={false}
            size="small"
          />
        </Spin>
      </Modal>

      <Modal
        title="Add Member"
        open={addMemberModalOpen}
        onCancel={() => setAddMemberModalOpen(false)}
        onOk={() => addMemberForm.submit()}
      >
        <Form form={addMemberForm} layout="vertical" onFinish={handleAddMember}>
          <Form.Item
            name="userId"
            label="User"
            rules={[{ required: true, message: 'Please select a user' }]}
          >
            <Select
              showSearch
              placeholder="Search user by name"
              optionFilterProp="label"
              options={allUsers.map((u) => ({
                label: `${u.fullName} (${u.email})`,
                value: u.id,
              }))}
            />
          </Form.Item>
          <Form.Item
            name="role"
            label="Role in Project"
            rules={[{ required: true, message: 'Please select a role' }]}
          >
            <Select
              placeholder="Select role"
              options={[
                { label: 'Student', value: 'STUDENT' },
                { label: 'Tutor', value: 'TUTOR' },
              ]}
            />
          </Form.Item>
        </Form>
      </Modal>

      <Modal
        title={`Tasks — ${tasksProject?.name ?? ''}`}
        open={tasksModalOpen}
        onCancel={() => setTasksModalOpen(false)}
        footer={null}
        width={950}
      >
        <div style={{ marginBottom: 16 }}>
          <Button icon={<PlusOutlined />} onClick={openCreateTask}>
            Add Task
          </Button>
        </div>
        <Spin spinning={tasksLoading}>
          <Table
            columns={[
              {
                title: 'Order',
                dataIndex: 'displayOrder',
                key: 'displayOrder',
                width: 70,
              },
              {
                title: 'Name',
                dataIndex: 'name',
                key: 'name',
              },
              {
                title: 'Description',
                dataIndex: 'description',
                key: 'description',
                ellipsis: true,
              },
              {
                title: 'Rule Package',
                dataIndex: 'rulePackageName',
                key: 'rulePackageName',
                render: (name: string | null) =>
                  name ? <Tag color="geekblue">{name}</Tag> : <Tag>Inherit</Tag>,
              },
              {
                title: 'LLM Config',
                dataIndex: 'llmConfigName',
                key: 'llmConfigName',
                render: (name: string | null) =>
                  name ? <Tag color="purple">{name}</Tag> : <Tag>Default</Tag>,
              },
              {
                title: 'Actions',
                key: 'actions',
                render: (_: unknown, record: ProjectTaskResponse) => (
                  <Space>
                    <Button type="link" icon={<EditOutlined />} onClick={() => openEditTask(record)}>
                      Edit
                    </Button>
                    <Popconfirm
                      title="Delete this task?"
                      onConfirm={() => handleDeleteTask(record.id)}
                    >
                      <Button type="link" danger icon={<DeleteOutlined />}>
                        Delete
                      </Button>
                    </Popconfirm>
                  </Space>
                ),
              },
            ]}
            dataSource={projectTasks}
            rowKey="id"
            pagination={false}
            size="small"
          />
        </Spin>
      </Modal>

      <Modal
        title={editingTask ? 'Edit Task' : 'Create Task'}
        open={taskModalOpen}
        onCancel={() => {
          setTaskModalOpen(false);
          setEditingTask(null);
        }}
        onOk={() => taskForm.submit()}
      >
        <Form form={taskForm} layout="vertical" onFinish={handleCreateOrUpdateTask}>
          <Form.Item
            name="name"
            label="Task Name"
            rules={[{ required: true, message: 'Please enter a task name' }]}
          >
            <Input placeholder="e.g. Progress Report" />
          </Form.Item>
          <Form.Item name="description" label="Description">
            <Input.TextArea rows={2} placeholder="Optional description" />
          </Form.Item>
          <Form.Item name="displayOrder" label="Display Order">
            <Input type="number" placeholder="0" style={{ width: 100 }} />
          </Form.Item>
          <Form.Item name="rulePackageId" label="Rule Package">
            <Select
              placeholder="Inherit from project"
              allowClear
              options={rulePackages.map((rp) => ({
                label: `${rp.name}${rp.isDefault ? ' (Default)' : ''}`,
                value: rp.id,
              }))}
            />
          </Form.Item>
          <Form.Item name="llmConfigId" label="LLM Config" extra="Which LLM prompt config to use when running LLM evaluation for this task">
            <Select
              placeholder="Use default LLM config"
              allowClear
              options={llmConfigs.map((c) => ({
                label: `${c.name}${c.isDefault ? ' (Default)' : ''}`,
                value: c.id,
              }))}
            />
          </Form.Item>
        </Form>
      </Modal>
    </div>
  );
}
