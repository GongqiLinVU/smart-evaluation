import { useEffect, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import {
  Card,
  Form,
  Input,
  Upload,
  Button,
  Space,
  Typography,
  Select,
  message,
  Spin,
} from 'antd';
import { InboxOutlined } from '@ant-design/icons';
import type { UploadFile } from 'antd';
import { uploadSubmission, runEvaluation, listProjects, listProjectTasks } from '../api/client';
import { useAuth } from '../context/AuthContext';
import type { ProjectResponse, ProjectTaskResponse } from '../types';

const { Title, Text } = Typography;
const { Dragger } = Upload;

export default function UploadPage() {
  const { user } = useAuth();
  const [form] = Form.useForm();
  const navigate = useNavigate();
  const [fileList, setFileList] = useState<UploadFile[]>([]);
  const [submitting, setSubmitting] = useState(false);
  const [evaluating, setEvaluating] = useState(false);
  const [projects, setProjects] = useState<ProjectResponse[]>([]);
  const [tasks, setTasks] = useState<ProjectTaskResponse[]>([]);

  useEffect(() => {
    (async () => {
      try {
        const data = await listProjects();
        setProjects(data);
        if (data.length === 1) {
          form.setFieldsValue({ projectId: data[0].id });
          loadTasks(data[0].id);
        }
      } catch (err) {
        console.error('Failed to fetch projects:', err);
      }
    })();
  }, []); // eslint-disable-line react-hooks/exhaustive-deps

  const loadTasks = async (projectId: number) => {
    try {
      const data = await listProjectTasks(projectId);
      setTasks(data);
      form.setFieldsValue({ taskId: undefined });
      if (data.length === 1) {
        form.setFieldsValue({ taskId: data[0].id });
      }
    } catch {
      setTasks([]);
    }
  };

  const handleSubmit = async (values: { githubUrl?: string; projectId?: number; taskId?: number }) => {
    if (fileList.length === 0) {
      message.error('Please upload a .docx file');
      return;
    }

    const file = fileList[0].originFileObj;
    if (!file) {
      message.error('File not found. Please re-upload.');
      return;
    }

    setSubmitting(true);
    try {
      const submission = await uploadSubmission(
        file,
        user?.fullName ?? 'Unknown',
        values.githubUrl || undefined,
        values.projectId,
        values.taskId,
      );
      message.success(`Submission uploaded (Version ${submission.version})`);

      setSubmitting(false);
      setEvaluating(true);

      try {
        await runEvaluation(submission.id, 'RULE_BASED');
        message.success('Evaluation completed!');
        navigate(`/submissions/${submission.id}`);
      } catch {
        message.error('Evaluation failed. You can retry from the results page.');
        navigate(`/submissions/${submission.id}`);
      }
    } catch {
      message.error('Upload failed. Please try again.');
      setSubmitting(false);
    } finally {
      setEvaluating(false);
    }
  };

  return (
    <div style={{ maxWidth: 600, margin: '0 auto', paddingTop: 24 }}>
      <Card>
        <Title level={3} style={{ textAlign: 'center', marginBottom: 8 }}>
          Submit Your Capstone Report
        </Title>
        <Text
          type="secondary"
          style={{ display: 'block', textAlign: 'center', marginBottom: 32 }}
        >
          Upload your .docx report for automated evaluation
        </Text>

        <Spin
          spinning={submitting || evaluating}
          tip={evaluating ? 'Evaluating your document...' : 'Uploading...'}
        >
          <Form
            form={form}
            layout="vertical"
            onFinish={handleSubmit}
            autoComplete="off"
          >
            {projects.length > 0 && (
              <Form.Item
                label="Project"
                name="projectId"
                rules={[{ required: true, message: 'Please select a project' }]}
              >
                <Select
                  placeholder="Select your project"
                  size="large"
                  onChange={(v) => loadTasks(v)}
                  options={projects.map((p) => ({
                    label: `${p.name} (${p.academicYear} ${p.semester})`,
                    value: p.id,
                  }))}
                />
              </Form.Item>
            )}

            {tasks.length > 0 && (
              <Form.Item
                label="Task"
                name="taskId"
                rules={[{ required: true, message: 'Please select a task' }]}
              >
                <Select
                  placeholder="Select the task"
                  size="large"
                  options={tasks.map((t) => ({
                    label: t.name,
                    value: t.id,
                  }))}
                />
              </Form.Item>
            )}

            <Form.Item
              label="GitHub URL"
              name="githubUrl"
              rules={[
                {
                  type: 'url',
                  message: 'Please enter a valid URL',
                },
              ]}
            >
              <Input placeholder="https://github.com/user/repo (optional)" size="large" />
            </Form.Item>

            <Form.Item label="Capstone Report" required>
              <Dragger
                accept=".docx"
                maxCount={1}
                fileList={fileList}
                beforeUpload={() => false}
                onChange={({ fileList: newFileList }) => setFileList(newFileList)}
              >
                <p className="ant-upload-drag-icon">
                  <InboxOutlined />
                </p>
                <p className="ant-upload-text">
                  Click or drag your .docx file here
                </p>
                <p className="ant-upload-hint">
                  Only Microsoft Word (.docx) files are accepted
                </p>
              </Dragger>
            </Form.Item>

            <Form.Item>
              <Space style={{ width: '100%', justifyContent: 'center' }}>
                <Button
                  type="primary"
                  htmlType="submit"
                  size="large"
                  loading={submitting || evaluating}
                  disabled={fileList.length === 0}
                >
                  Submit & Evaluate
                </Button>
              </Space>
            </Form.Item>
          </Form>
        </Spin>
      </Card>
    </div>
  );
}
