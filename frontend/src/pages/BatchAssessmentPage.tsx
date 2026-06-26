import { useState, useEffect, useRef } from 'react';
import { useNavigate } from 'react-router-dom';
import {
  Card,
  Steps,
  Button,
  Select,
  Upload,
  Table,
  Modal,
  Form,
  Input,
  InputNumber,
  Space,
  Typography,
  Tag,
  Progress,
  Alert,
  message,
  Popconfirm,
  Collapse,
  Descriptions,
  Empty,
  Divider,
} from 'antd';
import {
  CloudUploadOutlined,
  TeamOutlined,
  ThunderboltOutlined,
  DownloadOutlined,
  PlusOutlined,
  DeleteOutlined,
  EditOutlined,
  ImportOutlined,
  CheckCircleOutlined,
  CloseCircleOutlined,
  LoadingOutlined,
  ClockCircleOutlined,
  WarningOutlined,
  FileTextOutlined,
  PictureOutlined,
  EyeOutlined,
} from '@ant-design/icons';
import type { UploadFile } from 'antd/es/upload/interface';
import type {
  ProjectResponse,
  ProjectTaskResponse,
  GroupResponse,
  GroupSubmissionInfoResponse,
  BulkUploadResponse,
  BatchStatusResponse,
} from '../types';
import {
  listProjects,
  listProjectTasks,
  listGroups,
  createGroup,
  updateGroup,
  deleteGroup,
  importGroupsCsv,
  getGroupSubmissions,
  bulkUpload,
  startBatchEvaluation,
  getBatchStatus,
  exportTaskResults,
} from '../api/client';

const { Title, Text } = Typography;

export default function BatchAssessmentPage() {
  const navigate = useNavigate();
  const [currentStep, setCurrentStep] = useState(0);
  const [projects, setProjects] = useState<ProjectResponse[]>([]);
  const [tasks, setTasks] = useState<ProjectTaskResponse[]>([]);
  const [selectedProject, setSelectedProject] = useState<number | null>(null);
  const [selectedTask, setSelectedTask] = useState<number | null>(null);
  const [groups, setGroups] = useState<GroupResponse[]>([]);
  const [groupSubmissions, setGroupSubmissions] = useState<GroupSubmissionInfoResponse[]>([]);
  const [fileList, setFileList] = useState<UploadFile[]>([]);
  const [uploadResult, setUploadResult] = useState<BulkUploadResponse | null>(null);
  const [uploading, setUploading] = useState(false);
  const [batchStatus, setBatchStatus] = useState<BatchStatusResponse | null>(null);
  const [batchRunning, setBatchRunning] = useState(false);
  const [batchMethod, setBatchMethod] = useState<'LLM' | 'HYBRID'>('LLM');
  const [groupModalOpen, setGroupModalOpen] = useState(false);
  const [editingGroup, setEditingGroup] = useState<GroupResponse | null>(null);
  const [groupForm] = Form.useForm();
  const pollRef = useRef<ReturnType<typeof setInterval> | null>(null);

  useEffect(() => {
    listProjects().then(setProjects).catch(() => message.error('Failed to load projects'));
    return () => { if (pollRef.current) clearInterval(pollRef.current); };
  }, []);

  useEffect(() => {
    if (selectedProject) {
      listProjectTasks(selectedProject).then(setTasks).catch(() => {});
      listGroups(selectedProject).then(setGroups).catch(() => {});
    } else {
      setTasks([]);
      setGroups([]);
    }
  }, [selectedProject]);

  const refreshGroups = () => {
    if (selectedProject) listGroups(selectedProject).then(setGroups).catch(() => {});
    if (selectedTask) getGroupSubmissions(selectedTask).then(setGroupSubmissions).catch(() => {});
  };

  const handleUpload = async () => {
    if (!selectedTask) { message.warning('Select a task first'); return; }
    if (fileList.length === 0) { message.warning('Add files to upload'); return; }

    setUploading(true);
    try {
      const files = fileList.map(f => f.originFileObj as File);
      const result = await bulkUpload(selectedTask, files);
      setUploadResult(result);
      refreshGroups();
      message.success(`Uploaded ${result.uploaded} file(s), ${result.groupsCreated} group(s) created`);
      setFileList([]);
      setCurrentStep(2);
    } catch (err: any) {
      message.error(err.response?.data?.message || 'Upload failed');
    } finally {
      setUploading(false);
    }
  };

  const handleStartBatch = async () => {
    if (!selectedTask) return;
    setBatchRunning(true);
    try {
      await startBatchEvaluation(selectedTask, batchMethod);
      message.success('Batch evaluation started');
      pollRef.current = setInterval(async () => {
        try {
          const status = await getBatchStatus(selectedTask);
          setBatchStatus(status);
          if (status.status === 'COMPLETED' || status.status === 'NOT_STARTED') {
            if (pollRef.current) clearInterval(pollRef.current);
            setBatchRunning(false);
          }
        } catch { /* ignore poll errors */ }
      }, 3000);
    } catch (err: any) {
      message.error(err.response?.data?.message || 'Failed to start batch');
      setBatchRunning(false);
    }
  };

  const handleExport = async () => {
    if (!selectedTask) return;
    try {
      const blob = await exportTaskResults(selectedTask);
      const url = URL.createObjectURL(blob);
      const a = document.createElement('a');
      a.href = url;
      a.download = `task_${selectedTask}_results.csv`;
      a.click();
      URL.revokeObjectURL(url);
      message.success('Export downloaded');
    } catch (err: any) {
      message.error('Export failed');
    }
  };

  const handleGroupSave = async (values: any) => {
    try {
      const members = values.members || [];
      if (editingGroup) {
        await updateGroup(editingGroup.id, { groupCode: values.groupCode, groupName: values.groupName, members });
      } else {
        await createGroup(selectedProject!, { groupCode: values.groupCode, groupName: values.groupName, members });
      }
      message.success(editingGroup ? 'Group updated' : 'Group created');
      setGroupModalOpen(false);
      refreshGroups();
    } catch (err: any) {
      message.error(err.response?.data?.message || 'Save failed');
    }
  };

  const handleGroupDelete = async (id: number) => {
    try {
      await deleteGroup(id);
      message.success('Group deleted');
      refreshGroups();
    } catch (err: any) {
      message.error(err.response?.data?.message || 'Delete failed');
    }
  };

  const handleCsvImport = async (file: File) => {
    if (!selectedProject) return;
    try {
      await importGroupsCsv(selectedProject, file);
      message.success('CSV imported');
      refreshGroups();
    } catch (err: any) {
      message.error(err.response?.data?.message || 'Import failed');
    }
  };

  const openEditGroup = (group: GroupResponse) => {
    setEditingGroup(group);
    groupForm.setFieldsValue({
      groupCode: group.groupCode,
      groupName: group.groupName,
      members: group.members.map(m => ({
        studentName: m.studentName,
        studentId: m.studentId,
        email: m.email,
        contributionPercent: m.contributionPercent,
      })),
    });
    setGroupModalOpen(true);
  };

  const openCreateGroup = () => {
    setEditingGroup(null);
    groupForm.resetFields();
    setGroupModalOpen(true);
  };

  const steps = [
    { title: 'Select', icon: <TeamOutlined /> },
    { title: 'Upload', icon: <CloudUploadOutlined /> },
    { title: 'Groups', icon: <TeamOutlined /> },
    { title: 'Evaluate', icon: <ThunderboltOutlined /> },
    { title: 'Export', icon: <DownloadOutlined /> },
  ];

  return (
    <div style={{ maxWidth: 1100, margin: '0 auto', padding: '24px' }}>
      <Title level={3}>Batch Assessment</Title>
      <Steps current={currentStep} items={steps} style={{ marginBottom: 32 }} onChange={(step) => {
        setCurrentStep(step);
        if (step === 2 && selectedTask) {
          getGroupSubmissions(selectedTask).then(setGroupSubmissions).catch(() => {});
        }
      }} />

      {currentStep === 0 && (
        <Card title="Select Project & Task">
          <Space direction="vertical" style={{ width: '100%' }} size="large">
            <div>
              <Text strong>Project</Text>
              <Select
                style={{ width: '100%', marginTop: 8 }}
                placeholder="Select project"
                value={selectedProject}
                onChange={(v) => { setSelectedProject(v); setSelectedTask(null); }}
                options={projects.map(p => ({ label: `${p.name} (${p.academicYear} ${p.semester})`, value: p.id }))}
              />
            </div>
            {selectedProject && (
              <div>
                <Text strong>Task</Text>
                <Select
                  style={{ width: '100%', marginTop: 8 }}
                  placeholder="Select task"
                  value={selectedTask}
                  onChange={setSelectedTask}
                  options={tasks.map(t => ({ label: t.name, value: t.id }))}
                />
              </div>
            )}
            <Button type="primary" disabled={!selectedTask} onClick={() => setCurrentStep(1)}>
              Continue
            </Button>
          </Space>
        </Card>
      )}

      {currentStep === 1 && (
        <Card title="Bulk Upload Reports">
          <Alert
            type="info"
            showIcon
            style={{ marginBottom: 16 }}
            message="Upload group reports"
            description="Files named like Group01_FinalReport.pdf or Team03_Progress.docx will auto-create groups from the filename prefix."
          />
          <Upload.Dragger
            multiple
            fileList={fileList}
            beforeUpload={(file) => {
              setFileList(prev => [...prev, { ...file, uid: file.uid || String(Date.now()), originFileObj: file } as any]);
              return false;
            }}
            onRemove={(file) => setFileList(prev => prev.filter(f => f.uid !== file.uid))}
            accept=".pdf,.docx,.doc"
          >
            <p className="ant-upload-drag-icon"><CloudUploadOutlined style={{ fontSize: 48 }} /></p>
            <p className="ant-upload-text">Click or drag files here</p>
            <p className="ant-upload-hint">Supports .pdf, .docx, .doc</p>
          </Upload.Dragger>

          <Space style={{ marginTop: 16 }}>
            <Button type="primary" onClick={handleUpload} loading={uploading} disabled={fileList.length === 0}>
              Upload {fileList.length} file(s)
            </Button>
            <Button onClick={() => setCurrentStep(2)}>Skip to Groups</Button>
          </Space>

          {uploadResult && (
            <Card size="small" style={{ marginTop: 16 }}>
              <Descriptions column={3} size="small">
                <Descriptions.Item label="Uploaded">{uploadResult.uploaded}</Descriptions.Item>
                <Descriptions.Item label="Groups Created">{uploadResult.groupsCreated}</Descriptions.Item>
                <Descriptions.Item label="Groups Matched">{uploadResult.groupsMatched}</Descriptions.Item>
              </Descriptions>
              {uploadResult.errors.length > 0 && (
                <Alert type="warning" style={{ marginTop: 8 }}
                  message={`${uploadResult.errors.length} error(s)`}
                  description={uploadResult.errors.map(e => `${e.filename}: ${e.reason}`).join('\n')}
                />
              )}
            </Card>
          )}
        </Card>
      )}

      {currentStep === 2 && (
        <Card
          title={`Groups (${groups.length})`}
          extra={
            <Space>
              <Upload accept=".csv" showUploadList={false} beforeUpload={(file) => { handleCsvImport(file); return false; }}>
                <Button icon={<ImportOutlined />}>Import CSV</Button>
              </Upload>
              <Button type="primary" icon={<PlusOutlined />} onClick={openCreateGroup}>Add Group</Button>
            </Space>
          }
        >
          <Alert
            type="info"
            showIcon
            style={{ marginBottom: 16 }}
            message="Group-student linking"
            description="Add student emails to group members. When students register and upload, they are automatically linked to their group. CSV format: GroupCode,StudentName,StudentID,ContributionPercent,Email"
          />
          {groupSubmissions.some(gs => gs.imageHeavy) && (
            <Alert
              type="warning"
              showIcon
              icon={<WarningOutlined />}
              style={{ marginBottom: 16 }}
              message="Image-heavy submissions detected"
              description="Some submissions contain many images/screenshots. LLM text-based evaluation may not assess image content accurately. Consider using a multimodal LLM config or manual tutor review for these groups."
            />
          )}
          {groups.length === 0 ? (
            <Empty description="No groups yet. Upload files or create groups manually." />
          ) : (
            <Collapse
              items={groups.map(g => {
                const subInfo = groupSubmissions.find(gs => gs.groupId === g.id);
                return {
                  key: g.id,
                  label: (
                    <Space>
                      <Text strong>{g.groupCode}</Text>
                      {g.groupName && g.groupName !== g.groupCode && <Text type="secondary">({g.groupName})</Text>}
                      <Tag>{g.members.length} member(s)</Tag>
                      {subInfo && subInfo.documentStats && (
                        <>
                          <Tag icon={<FileTextOutlined />} color="blue">
                            {subInfo.documentStats.totalWordCount.toLocaleString()} words
                          </Tag>
                          {subInfo.imageHeavy ? (
                            <Tag icon={<PictureOutlined />} color="orange">
                              {subInfo.documentStats.imageCount} images
                            </Tag>
                          ) : subInfo.documentStats.imageCount > 0 ? (
                            <Tag icon={<PictureOutlined />}>
                              {subInfo.documentStats.imageCount} images
                            </Tag>
                          ) : null}
                        </>
                      )}
                      {subInfo && !subInfo.documentStats && (
                        <Tag icon={<LoadingOutlined />}>Parsing...</Tag>
                      )}
                    </Space>
                  ),
                  extra: (
                    <Space onClick={e => e.stopPropagation()}>
                      <Button size="small" icon={<EditOutlined />} onClick={() => openEditGroup(g)} />
                      <Popconfirm title="Delete this group?" onConfirm={() => handleGroupDelete(g.id)}>
                        <Button size="small" danger icon={<DeleteOutlined />} />
                      </Popconfirm>
                    </Space>
                  ),
                  children: (
                    <div>
                      {subInfo && subInfo.documentStats && (
                        <div style={{ marginBottom: 12, padding: '8px 12px', background: '#fafafa', borderRadius: 6 }}>
                          <Space size="large" wrap>
                            <Text type="secondary">File: <Text strong>{subInfo.fileName}</Text></Text>
                            <Text type="secondary">Words: <Text strong>{subInfo.documentStats.totalWordCount.toLocaleString()}</Text></Text>
                            <Text type="secondary">Sections: <Text strong>{subInfo.documentStats.sectionCount}</Text></Text>
                            <Text type="secondary">Headings: <Text strong>{subInfo.documentStats.headingCount}</Text></Text>
                            <Text type="secondary">Tables: <Text strong>{subInfo.documentStats.tableCount}</Text></Text>
                            <Text type="secondary">Code: <Text strong>{subInfo.documentStats.codeSnippetCount}</Text></Text>
                            <Text type="secondary">Images: <Text strong>{subInfo.documentStats.imageCount}</Text></Text>
                            <Text type="secondary">Links: <Text strong>{subInfo.documentStats.linkCount}</Text></Text>
                          </Space>
                          {subInfo.imageHeavy && (
                            <Alert
                              type="warning"
                              showIcon
                              icon={<WarningOutlined />}
                              style={{ marginTop: 8 }}
                              message={`This submission has ${subInfo.documentStats.imageCount} images. Text-based LLM evaluation may miss visual content. Consider multimodal evaluation or manual review.`}
                            />
                          )}
                        </div>
                      )}
                      {g.members.length === 0 ? (
                        <Text type="secondary">No members added yet</Text>
                      ) : (
                        <Table
                          size="small"
                          pagination={false}
                          dataSource={g.members}
                          rowKey="id"
                          columns={[
                            { title: 'Name', dataIndex: 'studentName' },
                            { title: 'Student ID', dataIndex: 'studentId', render: (v: string | null) => v || '-' },
                            { title: 'Email', dataIndex: 'email', render: (v: string | null) => v || '-' },
                            { title: 'Linked', dataIndex: 'linked', render: (v: boolean) => v ? <Tag color="green">Yes</Tag> : <Tag>No</Tag> },
                            { title: 'Contribution %', dataIndex: 'contributionPercent', render: (v: number | null) => v != null ? `${v}%` : 'Equal' },
                          ]}
                        />
                      )}
                    </div>
                  ),
                };
              })}
            />
          )}
          <Divider />
          <Space>
            <Button type="primary" onClick={() => setCurrentStep(3)}>Continue to Evaluate</Button>
            <Button onClick={refreshGroups}>Refresh</Button>
          </Space>
        </Card>
      )}

      {currentStep === 3 && (
        <Card title="Batch Evaluation">
          <Alert
            type="info"
            showIcon
            style={{ marginBottom: 16 }}
            message="Run AI evaluation on all pending submissions"
            description="Submissions are evaluated sequentially with a 2-second delay between calls to respect API rate limits."
          />
          <Space direction="vertical" size="middle">
            <Space>
              <Text>Method:</Text>
              <Select
                value={batchMethod}
                onChange={(v) => setBatchMethod(v)}
                style={{ width: 200 }}
                options={[
                  { value: 'LLM', label: 'LLM (direct scoring)' },
                  { value: 'HYBRID', label: 'Hybrid (evidence + rules)' },
                ]}
              />
            </Space>
            <Button
              type="primary"
              icon={<ThunderboltOutlined />}
              onClick={handleStartBatch}
              loading={batchRunning}
              disabled={batchRunning}
              size="large"
            >
              {batchRunning ? 'Evaluating...' : 'Start Batch Evaluation'}
            </Button>
          </Space>

          {batchStatus && batchStatus.status !== 'NOT_STARTED' && (
            <div style={{ marginTop: 24 }}>
              <Progress
                percent={batchStatus.total > 0 ? Math.round(((batchStatus.completed + batchStatus.failed) / batchStatus.total) * 100) : 0}
                status={batchStatus.failed > 0 ? 'exception' : batchStatus.status === 'COMPLETED' ? 'success' : 'active'}
              />
              <Space style={{ marginTop: 8 }}>
                <Tag color="green">{batchStatus.completed} completed</Tag>
                {batchStatus.failed > 0 && <Tag color="red">{batchStatus.failed} failed</Tag>}
                <Tag>{batchStatus.total - batchStatus.completed - batchStatus.failed} pending</Tag>
              </Space>
              <Table
                size="small"
                style={{ marginTop: 16 }}
                pagination={false}
                dataSource={batchStatus.items}
                rowKey="submissionId"
                columns={[
                  { title: 'Group', dataIndex: 'groupCode' },
                  {
                    title: 'Status',
                    dataIndex: 'status',
                    render: (s: string) => {
                      if (s === 'SUCCESS') return <Tag icon={<CheckCircleOutlined />} color="success">Success</Tag>;
                      if (s === 'FAILED') return <Tag icon={<CloseCircleOutlined />} color="error">Failed</Tag>;
                      if (s === 'IN_PROGRESS') return <Tag icon={<LoadingOutlined />} color="processing">Evaluating</Tag>;
                      return <Tag icon={<ClockCircleOutlined />}>Pending</Tag>;
                    },
                  },
                  { title: 'Score', dataIndex: 'overallScore', render: (v: number | null) => v ?? '-' },
                  { title: 'Error', dataIndex: 'error', render: (v: string | null) => v ? <Text type="danger" ellipsis>{v}</Text> : '-' },
                  {
                    title: 'Action',
                    dataIndex: 'submissionId',
                    render: (id: number, record: any) =>
                      record.status === 'SUCCESS' ? (
                        <Button
                          type="link"
                          size="small"
                          icon={<EyeOutlined />}
                          onClick={() => navigate(`/submissions/${id}`)}
                        >
                          View
                        </Button>
                      ) : null,
                  },
                ]}
              />
            </div>
          )}

          <Divider />
          <Button onClick={() => setCurrentStep(4)}>Continue to Export</Button>
        </Card>
      )}

      {currentStep === 4 && (
        <Card title="Export Results">
          <Alert
            type="info"
            showIcon
            style={{ marginBottom: 16 }}
            message="Download per-student results"
            description="Export a CSV with one row per student, including group info, scores, level, confidence, and feedback."
          />
          <Button type="primary" icon={<DownloadOutlined />} size="large" onClick={handleExport}>
            Export CSV
          </Button>
        </Card>
      )}

      <Modal
        title={editingGroup ? 'Edit Group' : 'Create Group'}
        open={groupModalOpen}
        onCancel={() => setGroupModalOpen(false)}
        footer={null}
        width={700}
      >
        <Form form={groupForm} layout="vertical" onFinish={handleGroupSave}>
          <Form.Item name="groupCode" label="Group Code" rules={[{ required: true }]}>
            <Input placeholder="e.g., Group01" />
          </Form.Item>
          <Form.Item name="groupName" label="Group Name">
            <Input placeholder="Optional display name" />
          </Form.Item>

          <Divider>Members</Divider>
          <Form.List name="members">
            {(fields, { add, remove }) => (
              <>
                {fields.map(({ key, name, ...restField }) => (
                  <Space key={key} align="baseline" style={{ display: 'flex', marginBottom: 8 }}>
                    <Form.Item {...restField} name={[name, 'studentName']} rules={[{ required: true, message: 'Name required' }]}>
                      <Input placeholder="Student Name" style={{ width: 150 }} />
                    </Form.Item>
                    <Form.Item {...restField} name={[name, 'studentId']}>
                      <Input placeholder="Student ID" style={{ width: 100 }} />
                    </Form.Item>
                    <Form.Item {...restField} name={[name, 'email']}>
                      <Input placeholder="Email" style={{ width: 180 }} />
                    </Form.Item>
                    <Form.Item {...restField} name={[name, 'contributionPercent']}>
                      <InputNumber placeholder="%" min={0} max={100} style={{ width: 70 }} />
                    </Form.Item>
                    <Button type="text" danger icon={<DeleteOutlined />} onClick={() => remove(name)} />
                  </Space>
                ))}
                <Button type="dashed" onClick={() => add()} icon={<PlusOutlined />} block>
                  Add Member
                </Button>
              </>
            )}
          </Form.List>

          <Form.Item style={{ marginTop: 16 }}>
            <Space>
              <Button type="primary" htmlType="submit">
                {editingGroup ? 'Update' : 'Create'}
              </Button>
              <Button onClick={() => setGroupModalOpen(false)}>Cancel</Button>
            </Space>
          </Form.Item>
        </Form>
      </Modal>
    </div>
  );
}
