import { useState, useEffect } from 'react';
import {
  Card,
  Table,
  Button,
  Modal,
  Form,
  Input,
  InputNumber,
  Slider,
  Switch,
  Select,
  Space,
  Typography,
  Tag,
  Popconfirm,
  message,
  Tabs,
  Alert,
  Descriptions,
} from 'antd';
import {
  PlusOutlined,
  EditOutlined,
  DeleteOutlined,
  CopyOutlined,
  EyeOutlined,
  RobotOutlined,
  ThunderboltOutlined,
} from '@ant-design/icons';
import type { LlmConfigResponse, PromptPreviewResponse, RulePackageResponse } from '../types';
import {
  listLlmConfigs,
  createLlmConfig,
  updateLlmConfig,
  deleteLlmConfig,
  duplicateLlmConfig,
  previewLlmPrompt,
  listRulePackages,
} from '../api/client';

const { Title, Text, Paragraph } = Typography;
const { TextArea } = Input;

export default function LlmConfigPage() {
  const [configs, setConfigs] = useState<LlmConfigResponse[]>([]);
  const [packages, setPackages] = useState<RulePackageResponse[]>([]);
  const [loading, setLoading] = useState(true);
  const [modalOpen, setModalOpen] = useState(false);
  const [editingId, setEditingId] = useState<number | null>(null);
  const [saving, setSaving] = useState(false);
  const [previewOpen, setPreviewOpen] = useState(false);
  const [preview, setPreview] = useState<PromptPreviewResponse | null>(null);
  const [previewLoading, setPreviewLoading] = useState(false);
  const [previewConfigId, setPreviewConfigId] = useState<number | null>(null);
  const [previewPkgId, setPreviewPkgId] = useState<number | null>(null);
  const [form] = Form.useForm();

  useEffect(() => {
    fetchData();
  }, []);

  const fetchData = async () => {
    setLoading(true);
    try {
      const [cfgs, pkgs] = await Promise.all([listLlmConfigs(), listRulePackages()]);
      setConfigs(cfgs);
      setPackages(pkgs);
    } catch (err: any) {
      message.error(err.response?.data?.message || 'Failed to load data');
    } finally {
      setLoading(false);
    }
  };

  const openCreate = () => {
    setEditingId(null);
    form.resetFields();
    form.setFieldsValue({ temperature: 0.1, maxTokens: 4096, isDefault: false });
    setModalOpen(true);
  };

  const openEdit = (config: LlmConfigResponse) => {
    setEditingId(config.id);
    form.setFieldsValue({
      name: config.name,
      systemPromptTemplate: config.systemPromptTemplate,
      additionalContext: config.additionalContext,
      outputFormatTemplate: config.outputFormatTemplate,
      temperature: config.temperature ?? 0.1,
      maxTokens: config.maxTokens ?? 4096,
      provider: config.provider,
      model: config.model,
      isDefault: config.isDefault,
    });
    setModalOpen(true);
  };

  const handleSave = async (values: any) => {
    setSaving(true);
    try {
      if (editingId) {
        await updateLlmConfig(editingId, values);
        message.success('Configuration updated');
      } else {
        await createLlmConfig(values);
        message.success('Configuration created');
      }
      setModalOpen(false);
      fetchData();
    } catch (err: any) {
      message.error(err.response?.data?.message || 'Save failed');
    } finally {
      setSaving(false);
    }
  };

  const handleDelete = async (id: number) => {
    try {
      await deleteLlmConfig(id);
      message.success('Configuration deleted');
      fetchData();
    } catch (err: any) {
      message.error(err.response?.data?.message || 'Delete failed');
    }
  };

  const handleDuplicate = async (id: number) => {
    try {
      const copy = await duplicateLlmConfig(id);
      message.success(`Created "${copy.name}"`);
      fetchData();
    } catch (err: any) {
      message.error(err.response?.data?.message || 'Duplicate failed');
    }
  };

  const handlePreview = async () => {
    if (!previewConfigId || !previewPkgId) {
      message.warning('Select a config and rule package to preview');
      return;
    }
    setPreviewLoading(true);
    try {
      const result = await previewLlmPrompt(previewConfigId, previewPkgId);
      setPreview(result);
    } catch (err: any) {
      message.error(err.response?.data?.message || 'Preview failed');
    } finally {
      setPreviewLoading(false);
    }
  };

  const columns = [
    {
      title: 'Name',
      dataIndex: 'name',
      key: 'name',
      render: (name: string, record: LlmConfigResponse) => (
        <Space>
          <Text strong>{name}</Text>
          {record.isDefault && <Tag color="blue">Default</Tag>}
        </Space>
      ),
    },
    {
      title: 'Provider',
      dataIndex: 'provider',
      key: 'provider',
      render: (v: string | null) => v || <Text type="secondary">System default</Text>,
    },
    {
      title: 'Model',
      dataIndex: 'model',
      key: 'model',
      render: (v: string | null) => v || <Text type="secondary">Provider default</Text>,
    },
    {
      title: 'Temperature',
      dataIndex: 'temperature',
      key: 'temperature',
      render: (v: number | null) => v?.toFixed(2) ?? '0.10',
    },
    {
      title: 'Max Tokens',
      dataIndex: 'maxTokens',
      key: 'maxTokens',
      render: (v: number | null) => v ?? 4096,
    },
    {
      title: 'Actions',
      key: 'actions',
      render: (_: any, record: LlmConfigResponse) => (
        <Space>
          <Button size="small" icon={<EditOutlined />} onClick={() => openEdit(record)} />
          <Button size="small" icon={<CopyOutlined />} onClick={() => handleDuplicate(record.id)} title="Duplicate" />
          <Popconfirm title="Delete this config?" onConfirm={() => handleDelete(record.id)}>
            <Button size="small" danger icon={<DeleteOutlined />} />
          </Popconfirm>
        </Space>
      ),
    },
  ];

  return (
    <div style={{ maxWidth: 1200, margin: '0 auto', padding: '24px' }}>
      <Space align="center" style={{ marginBottom: 16 }}>
        <RobotOutlined style={{ fontSize: 24 }} />
        <Title level={3} style={{ margin: 0 }}>LLM Configuration</Title>
      </Space>

      <Tabs
        items={[
          {
            key: 'configs',
            label: 'Configurations',
            children: (
              <>
                <Alert
                  type="info"
                  showIcon
                  style={{ marginBottom: 16 }}
                  message="LLM configs control how the AI evaluator behaves"
                  description="Each config defines the system prompt template, model settings, and optional tutor context. The default config is used when running LLM evaluations unless overridden."
                />
                <Card
                  title="LLM Configurations"
                  extra={
                    <Button type="primary" icon={<PlusOutlined />} onClick={openCreate}>
                      New Config
                    </Button>
                  }
                >
                  <Table
                    dataSource={configs}
                    columns={columns}
                    rowKey="id"
                    loading={loading}
                    pagination={false}
                  />
                </Card>
              </>
            ),
          },
          {
            key: 'preview',
            label: 'Prompt Preview',
            children: (
              <Card title="Preview Assembled Prompt">
                <Space direction="vertical" style={{ width: '100%' }} size="middle">
                  <Alert
                    type="info"
                    showIcon
                    message="See what the LLM receives"
                    description="Select a config and rule package to preview the fully assembled system prompt that gets sent to the LLM during evaluation."
                  />
                  <Space>
                    <Select
                      style={{ width: 250 }}
                      placeholder="Select LLM Config"
                      onChange={(v) => setPreviewConfigId(v)}
                      options={configs.map((c) => ({ label: c.name, value: c.id }))}
                    />
                    <Select
                      style={{ width: 250 }}
                      placeholder="Select Rule Package"
                      onChange={(v) => setPreviewPkgId(v)}
                      options={packages.map((p) => ({ label: p.name, value: p.id }))}
                    />
                    <Button
                      type="primary"
                      icon={<EyeOutlined />}
                      loading={previewLoading}
                      onClick={handlePreview}
                    >
                      Preview
                    </Button>
                  </Space>
                  {preview && (
                    <Card size="small">
                      <Descriptions column={2} size="small" style={{ marginBottom: 12 }}>
                        <Descriptions.Item label="Estimated Tokens">
                          {preview.estimatedTokens.toLocaleString()}
                        </Descriptions.Item>
                        <Descriptions.Item label="Criteria Included">
                          {preview.criteriaIncluded.map((c) => (
                            <Tag key={c} color="blue">{c}</Tag>
                          ))}
                        </Descriptions.Item>
                      </Descriptions>
                      <pre
                        style={{
                          background: '#f5f5f5',
                          padding: 16,
                          borderRadius: 8,
                          maxHeight: 500,
                          overflow: 'auto',
                          fontSize: 12,
                          whiteSpace: 'pre-wrap',
                        }}
                      >
                        {preview.systemPrompt}
                      </pre>
                    </Card>
                  )}
                </Space>
              </Card>
            ),
          },
          {
            key: 'howItWorks',
            label: 'How It Works',
            children: (
              <Card>
                <Title level={4}>
                  <ThunderboltOutlined /> Multi-Round Evaluation Strategy
                </Title>
                <Paragraph>
                  The system uses a <strong>rule-based orchestrator</strong> to intelligently split
                  documents into evaluation rounds based on their size:
                </Paragraph>
                <Descriptions column={1} bordered size="small">
                  <Descriptions.Item label="Short documents (< 3,000 words or &le; 5 sections)">
                    Single-pass: one LLM call evaluates all criteria at once
                  </Descriptions.Item>
                  <Descriptions.Item label="Medium documents (3,000 &ndash; 12,000 words)">
                    Two-pass: sections grouped by affinity (methodology vs implementation),
                    evaluated in parallel, then synthesized
                  </Descriptions.Item>
                  <Descriptions.Item label="Long documents (&gt; 12,000 words)">
                    Multi-pass: sections chunked into ~6,000 word groups, each evaluated
                    independently, then synthesized into final scores
                  </Descriptions.Item>
                </Descriptions>

                <Title level={5} style={{ marginTop: 24 }}>Evidence Format</Title>
                <Paragraph>
                  Each criterion score includes <strong>section-linked evidence</strong>:
                </Paragraph>
                <ul>
                  <li><strong>Section Name</strong> &mdash; which section the evidence was found in</li>
                  <li><strong>Quote</strong> &mdash; direct text from the document</li>
                  <li><strong>Sentiment</strong> &mdash; whether this evidence supports (positive) or weakens (negative) the score</li>
                  <li><strong>Confidence</strong> &mdash; per-criterion certainty (0.0 to 1.0)</li>
                  <li><strong>Suggestions</strong> &mdash; actionable improvements tied to specific sections</li>
                </ul>

                <Title level={5} style={{ marginTop: 24 }}>Template Placeholders</Title>
                <Paragraph>Use these in the System Prompt Template:</Paragraph>
                <ul>
                  <li><code>{'{{criteria_block}}'}</code> &mdash; dynamically assembled from enabled rules' LLM criterion prompts</li>
                  <li><code>{'{{output_format}}'}</code> &mdash; the JSON output schema</li>
                  <li><code>{'{{additional_context}}'}</code> &mdash; tutor-provided context notes</li>
                </ul>
              </Card>
            ),
          },
        ]}
      />

      <Modal
        title={editingId ? 'Edit LLM Config' : 'Create LLM Config'}
        open={modalOpen}
        onCancel={() => setModalOpen(false)}
        footer={null}
        width={800}
      >
        <Form form={form} layout="vertical" onFinish={handleSave}>
          <Form.Item name="name" label="Name" rules={[{ required: true }]}>
            <Input placeholder="e.g., Default, Strict Grading, Lenient" />
          </Form.Item>

          <Form.Item name="provider" label="Provider">
            <Select
              allowClear
              placeholder="Use system default"
              options={[
                { label: 'DeepSeek', value: 'deepseek' },
                { label: 'OpenAI', value: 'openai' },
              ]}
            />
          </Form.Item>

          <Form.Item name="model" label="Model">
            <Input placeholder="e.g., deepseek-chat, gpt-4o (leave empty for provider default)" />
          </Form.Item>

          <Form.Item name="temperature" label="Temperature">
            <Slider min={0} max={2} step={0.05} marks={{ 0: '0', 0.1: '0.1', 1: '1', 2: '2' }} />
          </Form.Item>

          <Form.Item name="maxTokens" label="Max Tokens">
            <InputNumber min={256} max={16384} step={256} style={{ width: '100%' }} />
          </Form.Item>

          <Form.Item
            name="systemPromptTemplate"
            label="System Prompt Template"
            extra="Use {{criteria_block}}, {{output_format}}, {{additional_context}} as placeholders. Leave empty for built-in default."
          >
            <TextArea
              rows={10}
              placeholder="You are an experienced IT capstone project assessor..."
              style={{ fontFamily: 'monospace', fontSize: 12 }}
            />
          </Form.Item>

          <Form.Item
            name="additionalContext"
            label="Additional Context (Tutor Notes)"
            extra="Optional instructions injected into the prompt. E.g., 'This semester focuses on mobile development.'"
          >
            <TextArea rows={3} placeholder="Optional tutor-provided context..." />
          </Form.Item>

          <Form.Item
            name="outputFormatTemplate"
            label="Output Format Template"
            extra="Custom JSON output schema. Leave empty for built-in default with section-linked evidence."
          >
            <TextArea
              rows={6}
              placeholder="Leave empty to use default format"
              style={{ fontFamily: 'monospace', fontSize: 12 }}
            />
          </Form.Item>

          <Form.Item name="isDefault" label="Set as Default" valuePropName="checked">
            <Switch />
          </Form.Item>

          <Form.Item>
            <Space>
              <Button type="primary" htmlType="submit" loading={saving}>
                {editingId ? 'Update' : 'Create'}
              </Button>
              <Button onClick={() => setModalOpen(false)}>Cancel</Button>
            </Space>
          </Form.Item>
        </Form>
      </Modal>

      <Modal
        title="Prompt Preview"
        open={previewOpen}
        onCancel={() => setPreviewOpen(false)}
        footer={null}
        width={900}
      >
        {preview && (
          <pre style={{ maxHeight: 600, overflow: 'auto', fontSize: 12, whiteSpace: 'pre-wrap' }}>
            {preview.systemPrompt}
          </pre>
        )}
      </Modal>
    </div>
  );
}
