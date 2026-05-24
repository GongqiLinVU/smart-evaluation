import { useEffect, useState } from 'react';
import {
  Card,
  Table,
  Button,
  Modal,
  Form,
  Input,
  Switch,
  InputNumber,
  Tag,
  Space,
  Typography,
  message,
  Popconfirm,
  Tabs,
  Progress,
  Collapse,
  Tooltip,
  Divider,
  Row,
  Col,
  Badge,
  Select,
  Empty,
} from 'antd';
import {
  PlusOutlined,
  EditOutlined,
  DeleteOutlined,
  ExperimentOutlined,
  BulbOutlined,
  ApartmentOutlined,
  InfoCircleOutlined,
  CheckCircleOutlined,
  ArrowRightOutlined,
  LockOutlined,
} from '@ant-design/icons';
import {
  listRulePackages,
  createRulePackage,
  updateRulePackage,
  deleteRulePackage,
  listRules,
  createRule,
  updateRule,
  deleteRule,
} from '../api/client';
import type { RulePackageResponse, RuleResponse } from '../types';

const { Title, Text, Paragraph } = Typography;

interface Signal {
  name: string;
  weight: number;
  description: string;
  keywords?: string[];
}

interface RuleMetadata {
  rubricCriteria: string;
  rubricLevels: { level: string; points: number; description: string }[];
  signals: Signal[];
  color: string;
  icon: string;
  tagColor: string;
}

const RULE_METADATA: Record<string, RuleMetadata> = {
  logic_explanation: {
    color: '#1890ff',
    icon: '🧠',
    tagColor: 'blue',
    rubricCriteria:
      'Provides a clear, comprehensive, and insightful explanation of the logic behind each main function, demonstrating in-depth understanding.',
    rubricLevels: [
      { level: 'HD', points: 30, description: 'Clear, comprehensive, insightful explanation with in-depth understanding' },
      { level: 'D', points: 24, description: 'Thorough explanation with good clarity and solid understanding' },
      { level: 'C', points: 18, description: 'Sufficient explanation with some minor gaps or lack of detail' },
      { level: 'P', points: 12, description: 'Basic explanation with limited depth, overall understanding evident' },
      { level: 'F', points: 6, description: 'Fails to explain or presents disorganized, unclear understanding' },
    ],
    signals: [
      { name: 'Section Coverage', weight: 20, description: 'Logic-related sections with substantial content (>100 words)', keywords: ['implementation', 'logic', 'function', 'module', 'component'] },
      { name: 'Explanation Depth', weight: 20, description: 'Average word count per logic section' },
      { name: 'Technical Vocabulary', weight: 15, description: 'Density of technical keywords across logic sections' },
      { name: 'Data Flow Indicators', weight: 15, description: 'Data flow and process descriptions', keywords: ['flow', 'sequence', 'passes', 'returns', 'triggers', 'processes'] },
      { name: 'Decision Rationale', weight: 15, description: 'Explaining WHY design decisions were made', keywords: ['because', 'reason', 'chose', 'trade-off', 'decided'] },
      { name: 'Diagram Support', weight: 15, description: 'Images/diagrams in logic or architecture sections' },
    ],
  },
  methodology: {
    color: '#52c41a',
    icon: '📐',
    tagColor: 'green',
    rubricCriteria:
      'The methodology is explained in a highly structured, clear, and concise manner, with strong justification for choices made.',
    rubricLevels: [
      { level: 'HD', points: 30, description: 'Highly structured, clear methodology with strong justification for choices' },
      { level: 'D', points: 24, description: 'Clear explanation with appropriate reasoning for most decisions' },
      { level: 'C', points: 18, description: 'Described but lacks clarity or depth; reasoning partially justified' },
      { level: 'P', points: 12, description: 'Basic description with little justification or reflection on choices' },
      { level: 'F', points: 6, description: 'No clear explanation or inadequate/poorly justified methodology' },
    ],
    signals: [
      { name: 'Architecture Section', weight: 20, description: 'Presence and depth of architecture/design section', keywords: ['architecture', 'system design', 'structure', 'overview'] },
      { name: 'Technology Justification', weight: 20, description: 'Technology names paired with justification keywords', keywords: ['React', 'Spring', 'chose', 'because', 'suitable'] },
      { name: 'Development Process', weight: 20, description: 'Evidence of structured development methodology', keywords: ['agile', 'sprint', 'version control', 'CI/CD', 'scrum'] },
      { name: 'Design Patterns', weight: 20, description: 'Recognition and use of design patterns', keywords: ['MVC', 'factory', 'singleton', 'microservice', 'repository pattern'] },
      { name: 'Testing & Validation', weight: 20, description: 'Testing methodology and validation strategy', keywords: ['unit test', 'integration test', 'validation', 'coverage'] },
    ],
  },
  implementation_detail: {
    color: '#722ed1',
    icon: '⚙️',
    tagColor: 'purple',
    rubricCriteria:
      'Detailed and precise implementation steps are provided, showing a complete understanding of how each main function was implemented.',
    rubricLevels: [
      { level: 'HD', points: 30, description: 'Detailed, precise implementation steps with complete understanding' },
      { level: 'D', points: 24, description: 'Clear, mostly detailed description demonstrating solid understanding' },
      { level: 'C', points: 18, description: 'Adequate explanation with some minor gaps or lack of technical detail' },
      { level: 'P', points: 12, description: 'Basic steps mentioned but lacking clarity or thoroughness' },
      { level: 'F', points: 6, description: 'Poorly explained with little to no detail on implementation' },
    ],
    signals: [
      { name: 'Code Snippets', weight: 25, description: 'Presence and quantity of code blocks' },
      { name: 'File/Function References', weight: 20, description: 'References to specific files and methods', keywords: ['*.java', '*.ts', 'camelCase()', 'file paths'] },
      { name: 'Schema Documentation', weight: 20, description: 'Database tables, ERD, data model docs', keywords: ['schema', 'ERD', 'database', 'entity', 'foreign key'] },
      { name: 'Configuration Detail', weight: 15, description: 'Deployment and infrastructure configuration', keywords: ['config', '.env', 'Dockerfile', 'port', 'endpoint'] },
      { name: 'Section Completeness', weight: 20, description: 'Percentage of sections with meaningful content (>50 words)' },
    ],
  },
};

function getMetadata(ruleKey: string): RuleMetadata {
  return RULE_METADATA[ruleKey] || {
    color: '#595959',
    icon: '📋',
    tagColor: 'default',
    rubricCriteria: '',
    rubricLevels: [],
    signals: [],
  };
}

function RuleCard({ rule, onEdit, onDelete }: { rule: RuleResponse; onEdit: () => void; onDelete: () => void }) {
  const meta = getMetadata(rule.ruleKey);

  return (
    <Card
      style={{ height: '100%', borderTop: `3px solid ${meta.color}` }}
      hoverable
      actions={[
        <Tooltip title="Edit" key="edit">
          <Button type="text" icon={<EditOutlined />} onClick={onEdit} />
        </Tooltip>,
        rule.builtIn ? (
          <Tooltip title="Built-in rules cannot be deleted" key="lock">
            <Button type="text" icon={<LockOutlined />} disabled />
          </Tooltip>
        ) : (
          <Popconfirm title="Delete this rule?" onConfirm={onDelete} key="delete">
            <Button type="text" danger icon={<DeleteOutlined />} />
          </Popconfirm>
        ),
      ]}
    >
      <div style={{ marginBottom: 12 }}>
        <Space align="start">
          <span style={{ fontSize: 24 }}>{meta.icon}</span>
          <div>
            <Title level={5} style={{ margin: 0 }}>
              {rule.name}
            </Title>
            <Space size={4}>
              {rule.category && <Tag color={meta.tagColor}>{rule.category}</Tag>}
              {rule.builtIn && <Tag color="default">Built-in</Tag>}
              <Text type="secondary" style={{ fontSize: 11 }}>
                {rule.ruleKey}
              </Text>
            </Space>
          </div>
        </Space>
      </div>

      <Paragraph style={{ fontSize: 13, color: '#595959' }}>
        {rule.description || 'No description provided.'}
      </Paragraph>

      {meta.rubricCriteria && (
        <>
          <Divider style={{ margin: '12px 0' }} />
          <div style={{ marginBottom: 12 }}>
            <Text strong style={{ fontSize: 12, textTransform: 'uppercase', color: '#8c8c8c' }}>
              Rubric Criteria
            </Text>
            <Paragraph
              style={{ fontSize: 13, fontStyle: 'italic', margin: '4px 0 0 0', color: '#262626' }}
            >
              &ldquo;{meta.rubricCriteria}&rdquo;
            </Paragraph>
          </div>

          <Collapse
            ghost
            size="small"
            items={[
              {
                key: 'levels',
                label: <Text style={{ fontSize: 12, color: '#8c8c8c' }}>Performance Levels</Text>,
                children: (
                  <div style={{ display: 'flex', flexDirection: 'column', gap: 4 }}>
                    {meta.rubricLevels.map((lvl) => (
                      <div key={lvl.level} style={{ display: 'flex', gap: 8, alignItems: 'flex-start' }}>
                        <Tag
                          color={
                            lvl.level === 'HD' ? 'gold' :
                            lvl.level === 'D' ? 'blue' :
                            lvl.level === 'C' ? 'green' :
                            lvl.level === 'P' ? 'orange' : 'red'
                          }
                          style={{ minWidth: 32, textAlign: 'center' }}
                        >
                          {lvl.level}
                        </Tag>
                        <Text style={{ fontSize: 12 }}>{lvl.description}</Text>
                      </div>
                    ))}
                  </div>
                ),
              },
              {
                key: 'signals',
                label: <Text style={{ fontSize: 12, color: '#8c8c8c' }}>Detection Signals ({meta.signals.length})</Text>,
                children: (
                  <div style={{ display: 'flex', flexDirection: 'column', gap: 8 }}>
                    {meta.signals.map((signal) => (
                      <div key={signal.name}>
                        <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
                          <Text style={{ fontSize: 12 }} strong>{signal.name}</Text>
                          <Tag style={{ fontSize: 11 }}>{signal.weight}%</Tag>
                        </div>
                        <Progress
                          percent={signal.weight}
                          size="small"
                          showInfo={false}
                          strokeColor={meta.color}
                          style={{ margin: '2px 0' }}
                        />
                        <Text style={{ fontSize: 11, color: '#8c8c8c' }}>{signal.description}</Text>
                        {signal.keywords && (
                          <div style={{ marginTop: 4 }}>
                            {signal.keywords.slice(0, 5).map((kw) => (
                              <Tag key={kw} style={{ fontSize: 10, marginBottom: 2 }} color="default">{kw}</Tag>
                            ))}
                          </div>
                        )}
                      </div>
                    ))}
                  </div>
                ),
              },
            ]}
          />
        </>
      )}
    </Card>
  );
}

function PackageFlowDiagram({ pkg, rules }: { pkg: RulePackageResponse; rules: RuleResponse[] }) {
  const hasItems = pkg.items && pkg.items.length > 0;

  const enabledItems = hasItems
    ? pkg.items.filter((item) => item.enabled)
    : [];

  const totalWeight = hasItems
    ? enabledItems.reduce((sum, item) => sum + item.weight, 0)
    : (pkg.logicEnabled ? pkg.logicWeight : 0) +
      (pkg.methodologyEnabled ? pkg.methodologyWeight : 0) +
      (pkg.implementationEnabled ? pkg.implementationWeight : 0);

  return (
    <div style={{ background: '#fafafa', borderRadius: 8, padding: 16, border: '1px solid #f0f0f0' }}>
      <div style={{ display: 'flex', alignItems: 'center', gap: 8, marginBottom: 12 }}>
        <ApartmentOutlined style={{ color: '#1890ff' }} />
        <Text strong>{pkg.name}</Text>
        {pkg.isDefault && <Tag color="gold">Default</Tag>}
      </div>

      <div style={{ display: 'flex', alignItems: 'center', gap: 12, flexWrap: 'wrap' }}>
        <div style={{ background: '#fff', border: '1px solid #d9d9d9', borderRadius: 6, padding: '8px 12px', textAlign: 'center', minWidth: 80 }}>
          <Text style={{ fontSize: 11, color: '#8c8c8c', display: 'block' }}>INPUT</Text>
          <Text strong style={{ fontSize: 12 }}>Document</Text>
        </div>

        <ArrowRightOutlined style={{ color: '#bfbfbf' }} />

        <div style={{ display: 'flex', flexDirection: 'column', gap: 6 }}>
          {hasItems ? (
            enabledItems.length > 0 ? enabledItems.map((item) => {
              const meta = getMetadata(item.ruleKey);
              const normalizedPct = totalWeight > 0 ? (item.weight / totalWeight) * 100 : 0;
              return (
                <div
                  key={item.id}
                  style={{
                    display: 'flex', alignItems: 'center', gap: 8,
                    background: '#fff', border: `1px solid ${meta.color}40`,
                    borderLeft: `3px solid ${meta.color}`, borderRadius: 6, padding: '6px 12px',
                  }}
                >
                  <span>{meta.icon}</span>
                  <Text style={{ fontSize: 12, flex: 1 }}>{item.ruleName}</Text>
                  <Tooltip title={`Weight: ${item.weight.toFixed(1)} (normalized: ${normalizedPct.toFixed(0)}%)`}>
                    <Tag color={meta.tagColor} style={{ margin: 0 }}>{normalizedPct.toFixed(0)}%</Tag>
                  </Tooltip>
                </div>
              );
            }) : (
              <Text type="secondary" italic style={{ fontSize: 12 }}>No rules enabled</Text>
            )
          ) : (
            // Legacy display for packages without items
            <>
              {pkg.logicEnabled && (
                <div style={{ display: 'flex', alignItems: 'center', gap: 8, background: '#fff', border: '1px solid #1890ff40', borderLeft: '3px solid #1890ff', borderRadius: 6, padding: '6px 12px' }}>
                  <span>🧠</span>
                  <Text style={{ fontSize: 12, flex: 1 }}>Logic & Explanation</Text>
                  <Tag color="blue" style={{ margin: 0 }}>{totalWeight > 0 ? ((pkg.logicWeight / totalWeight) * 100).toFixed(0) : 0}%</Tag>
                </div>
              )}
              {pkg.methodologyEnabled && (
                <div style={{ display: 'flex', alignItems: 'center', gap: 8, background: '#fff', border: '1px solid #52c41a40', borderLeft: '3px solid #52c41a', borderRadius: 6, padding: '6px 12px' }}>
                  <span>📐</span>
                  <Text style={{ fontSize: 12, flex: 1 }}>Methodology</Text>
                  <Tag color="green" style={{ margin: 0 }}>{totalWeight > 0 ? ((pkg.methodologyWeight / totalWeight) * 100).toFixed(0) : 0}%</Tag>
                </div>
              )}
              {pkg.implementationEnabled && (
                <div style={{ display: 'flex', alignItems: 'center', gap: 8, background: '#fff', border: '1px solid #722ed140', borderLeft: '3px solid #722ed1', borderRadius: 6, padding: '6px 12px' }}>
                  <span>⚙️</span>
                  <Text style={{ fontSize: 12, flex: 1 }}>Implementation Detail</Text>
                  <Tag color="purple" style={{ margin: 0 }}>{totalWeight > 0 ? ((pkg.implementationWeight / totalWeight) * 100).toFixed(0) : 0}%</Tag>
                </div>
              )}
            </>
          )}
        </div>

        <ArrowRightOutlined style={{ color: '#bfbfbf' }} />

        <div style={{ background: '#fff', border: '1px solid #d9d9d9', borderRadius: 6, padding: '8px 12px', textAlign: 'center', minWidth: 90 }}>
          <Text style={{ fontSize: 11, color: '#8c8c8c', display: 'block' }}>AGGREGATE</Text>
          <Text strong style={{ fontSize: 12 }}>Weighted Avg</Text>
        </div>

        <ArrowRightOutlined style={{ color: '#bfbfbf' }} />

        <div style={{ background: '#f6ffed', border: '1px solid #b7eb8f', borderRadius: 6, padding: '8px 12px', textAlign: 'center', minWidth: 80 }}>
          <Text style={{ fontSize: 11, color: '#8c8c8c', display: 'block' }}>OUTPUT</Text>
          <Text strong style={{ fontSize: 12, color: '#52c41a' }}>Score / Level</Text>
        </div>
      </div>

      {pkg.description && (
        <Paragraph style={{ fontSize: 12, color: '#8c8c8c', margin: '12px 0 0 0' }}>
          {pkg.description}
        </Paragraph>
      )}
    </div>
  );
}

export default function RulePackagesPage() {
  const [packages, setPackages] = useState<RulePackageResponse[]>([]);
  const [rules, setRules] = useState<RuleResponse[]>([]);
  const [loading, setLoading] = useState(true);
  const [rulesLoading, setRulesLoading] = useState(true);
  const [modalOpen, setModalOpen] = useState(false);
  const [editing, setEditing] = useState<RulePackageResponse | null>(null);
  const [form] = Form.useForm();
  const [saving, setSaving] = useState(false);

  const [ruleModalOpen, setRuleModalOpen] = useState(false);
  const [editingRule, setEditingRule] = useState<RuleResponse | null>(null);
  const [ruleForm] = Form.useForm();
  const [ruleSaving, setRuleSaving] = useState(false);

  const [packageItems, setPackageItems] = useState<{ ruleId: number; enabled: boolean; weight: number }[]>([]);

  const fetchPackages = () => {
    setLoading(true);
    listRulePackages()
      .then(setPackages)
      .catch(() => message.error('Failed to load rule packages'))
      .finally(() => setLoading(false));
  };

  const fetchRules = () => {
    setRulesLoading(true);
    listRules()
      .then(setRules)
      .catch(() => message.error('Failed to load rules'))
      .finally(() => setRulesLoading(false));
  };

  useEffect(() => {
    fetchPackages();
    fetchRules();
  }, []);

  // --- Rule CRUD ---
  const openCreateRule = () => {
    setEditingRule(null);
    ruleForm.resetFields();
    setRuleModalOpen(true);
  };

  const openEditRule = (rule: RuleResponse) => {
    setEditingRule(rule);
    ruleForm.setFieldsValue({
      ruleKey: rule.ruleKey,
      name: rule.name,
      description: rule.description,
      category: rule.category,
      llmCriterionPrompt: rule.llmCriterionPrompt,
    });
    setRuleModalOpen(true);
  };

  const handleSaveRule = async (values: { ruleKey: string; name: string; description?: string; category?: string; llmCriterionPrompt?: string }) => {
    setRuleSaving(true);
    try {
      if (editingRule) {
        await updateRule(editingRule.id, values);
        message.success('Rule updated');
      } else {
        await createRule(values);
        message.success('Rule created');
      }
      setRuleModalOpen(false);
      fetchRules();
    } catch {
      message.error('Failed to save rule');
    } finally {
      setRuleSaving(false);
    }
  };

  const handleDeleteRule = async (id: number) => {
    try {
      await deleteRule(id);
      message.success('Rule deleted');
      fetchRules();
    } catch {
      message.error('Failed to delete rule (it may be in use by a package)');
    }
  };

  // --- Package CRUD ---
  const openCreate = () => {
    setEditing(null);
    form.resetFields();
    form.setFieldsValue({
      logicEnabled: true,
      methodologyEnabled: true,
      implementationEnabled: true,
      logicWeight: 33.3,
      methodologyWeight: 33.3,
      implementationWeight: 33.4,
      isDefault: false,
    });
    setPackageItems(rules.map((r) => ({ ruleId: r.id, enabled: true, weight: 1.0 })));
    setModalOpen(true);
  };

  const openEdit = (rp: RulePackageResponse) => {
    setEditing(rp);
    form.setFieldsValue({
      name: rp.name,
      description: rp.description,
      logicEnabled: rp.logicEnabled,
      methodologyEnabled: rp.methodologyEnabled,
      implementationEnabled: rp.implementationEnabled,
      logicWeight: rp.logicWeight,
      methodologyWeight: rp.methodologyWeight,
      implementationWeight: rp.implementationWeight,
      isDefault: rp.isDefault,
    });
    if (rp.items && rp.items.length > 0) {
      setPackageItems(rp.items.map((item) => ({ ruleId: item.ruleId, enabled: item.enabled, weight: item.weight })));
    } else {
      setPackageItems(rules.map((r) => ({
        ruleId: r.id,
        enabled:
          r.ruleKey === 'logic_explanation' ? rp.logicEnabled :
          r.ruleKey === 'methodology' ? rp.methodologyEnabled :
          r.ruleKey === 'implementation_detail' ? rp.implementationEnabled : true,
        weight:
          r.ruleKey === 'logic_explanation' ? rp.logicWeight :
          r.ruleKey === 'methodology' ? rp.methodologyWeight :
          r.ruleKey === 'implementation_detail' ? rp.implementationWeight : 1.0,
      })));
    }
    setModalOpen(true);
  };

  const handleSave = async (values: Record<string, unknown>) => {
    const payload = {
      name: values.name as string,
      description: (values.description as string) || undefined,
      logicEnabled: values.logicEnabled as boolean,
      methodologyEnabled: values.methodologyEnabled as boolean,
      implementationEnabled: values.implementationEnabled as boolean,
      logicWeight: values.logicWeight as number,
      methodologyWeight: values.methodologyWeight as number,
      implementationWeight: values.implementationWeight as number,
      isDefault: values.isDefault as boolean,
      items: packageItems.filter((item) => item.enabled).map((item) => ({
        ruleId: item.ruleId,
        enabled: item.enabled,
        weight: item.weight,
      })),
    };

    setSaving(true);
    try {
      if (editing) {
        await updateRulePackage(editing.id, payload);
        message.success('Rule package updated');
      } else {
        await createRulePackage(payload);
        message.success('Rule package created');
      }
      setModalOpen(false);
      fetchPackages();
    } catch {
      message.error('Failed to save rule package');
    } finally {
      setSaving(false);
    }
  };

  const handleDelete = async (id: number) => {
    try {
      await deleteRulePackage(id);
      message.success('Rule package deleted');
      fetchPackages();
    } catch {
      message.error('Failed to delete rule package');
    }
  };

  const updatePackageItem = (ruleId: number, field: 'enabled' | 'weight', value: boolean | number) => {
    setPackageItems((prev) =>
      prev.map((item) =>
        item.ruleId === ruleId ? { ...item, [field]: value } : item
      )
    );
  };

  const addRuleToPackage = (ruleId: number) => {
    if (packageItems.some((item) => item.ruleId === ruleId)) return;
    setPackageItems((prev) => [...prev, { ruleId, enabled: true, weight: 1.0 }]);
  };

  const removeRuleFromPackage = (ruleId: number) => {
    setPackageItems((prev) => prev.filter((item) => item.ruleId !== ruleId));
  };

  const columns = [
    {
      title: 'Package',
      dataIndex: 'name',
      key: 'name',
      render: (name: string, record: RulePackageResponse) => (
        <Space direction="vertical" size={0}>
          <Space>
            <Text strong>{name}</Text>
            {record.isDefault && <Tag color="gold">Default</Tag>}
          </Space>
          {record.description && (
            <Text type="secondary" style={{ fontSize: 12 }}>{record.description}</Text>
          )}
        </Space>
      ),
    },
    {
      title: 'Rules',
      key: 'rules',
      width: 350,
      render: (_: unknown, record: RulePackageResponse) => {
        if (record.items && record.items.length > 0) {
          const enabledItems = record.items.filter((i) => i.enabled);
          const totalWeight = enabledItems.reduce((s, i) => s + i.weight, 0);
          return (
            <Space size={4} wrap>
              {enabledItems.map((item) => {
                const meta = getMetadata(item.ruleKey);
                const pct = totalWeight > 0 ? ((item.weight / totalWeight) * 100).toFixed(0) : '0';
                return (
                  <Tooltip key={item.id} title={`${item.ruleName}: ${pct}% normalized`}>
                    <Tag color={meta.tagColor} icon={<CheckCircleOutlined />}>
                      {item.ruleName} {pct}%
                    </Tag>
                  </Tooltip>
                );
              })}
              {enabledItems.length === 0 && <Tag color="red">No rules active</Tag>}
            </Space>
          );
        }
        // Legacy fallback
        const totalWeight = (record.logicEnabled ? record.logicWeight : 0) + (record.methodologyEnabled ? record.methodologyWeight : 0) + (record.implementationEnabled ? record.implementationWeight : 0);
        return (
          <Space size={4} wrap>
            {record.logicEnabled && <Tag color="blue" icon={<CheckCircleOutlined />}>Logic {totalWeight > 0 ? ((record.logicWeight / totalWeight) * 100).toFixed(0) : 0}%</Tag>}
            {record.methodologyEnabled && <Tag color="green" icon={<CheckCircleOutlined />}>Methodology {totalWeight > 0 ? ((record.methodologyWeight / totalWeight) * 100).toFixed(0) : 0}%</Tag>}
            {record.implementationEnabled && <Tag color="purple" icon={<CheckCircleOutlined />}>Implementation {totalWeight > 0 ? ((record.implementationWeight / totalWeight) * 100).toFixed(0) : 0}%</Tag>}
          </Space>
        );
      },
    },
    {
      title: 'Actions',
      key: 'actions',
      width: 150,
      render: (_: unknown, record: RulePackageResponse) => (
        <Space>
          <Button type="link" icon={<EditOutlined />} onClick={() => openEdit(record)}>Edit</Button>
          <Popconfirm title="Delete this rule package?" onConfirm={() => handleDelete(record.id)}>
            <Button type="link" danger icon={<DeleteOutlined />}>Delete</Button>
          </Popconfirm>
        </Space>
      ),
    },
  ];

  return (
    <div>
      <div style={{ marginBottom: 24 }}>
        <Title level={3} style={{ margin: 0 }}>
          <ExperimentOutlined /> Evaluation Rules
        </Title>
        <Text type="secondary">
          Configure how student submissions are automatically evaluated. Rules analyze document structure, content signals, and technical depth.
        </Text>
      </div>

      <Tabs
        defaultActiveKey="rules"
        items={[
          {
            key: 'rules',
            label: <span><BulbOutlined /> Individual Rules <Badge count={rules.length} style={{ marginLeft: 4 }} size="small" /></span>,
            children: (
              <div>
                <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: 16 }}>
                  <Text type="secondary">
                    Each rule analyzes specific aspects of a submitted document using weighted signals.
                    Scores from 0-100 are mapped to performance levels (F/P/C/D/HD) and rubric points.
                  </Text>
                  <Button type="primary" icon={<PlusOutlined />} onClick={openCreateRule}>
                    Add Rule
                  </Button>
                </div>

                <Card size="small" style={{ marginBottom: 16, background: '#fffbe6', border: '1px solid #ffe58f' }}>
                  <Space split={<Divider type="vertical" />} wrap>
                    <Text style={{ fontSize: 12 }}><InfoCircleOutlined /> <strong>Score Scale:</strong></Text>
                    <Text style={{ fontSize: 12 }}><Tag color="red">F</Tag> 0-24 (6pts)</Text>
                    <Text style={{ fontSize: 12 }}><Tag color="orange">P</Tag> 25-44 (12pts)</Text>
                    <Text style={{ fontSize: 12 }}><Tag color="green">C</Tag> 45-64 (18pts)</Text>
                    <Text style={{ fontSize: 12 }}><Tag color="blue">D</Tag> 65-84 (24pts)</Text>
                    <Text style={{ fontSize: 12 }}><Tag color="gold">HD</Tag> 85-100 (30pts)</Text>
                  </Space>
                </Card>

                {rulesLoading ? (
                  <Empty description="Loading rules..." />
                ) : (
                  <Row gutter={[16, 16]}>
                    {rules.map((rule) => (
                      <Col xs={24} lg={8} key={rule.id}>
                        <RuleCard
                          rule={rule}
                          onEdit={() => openEditRule(rule)}
                          onDelete={() => handleDeleteRule(rule.id)}
                        />
                      </Col>
                    ))}
                  </Row>
                )}
              </div>
            ),
          },
          {
            key: 'packages',
            label: <span><ApartmentOutlined /> Rule Packages <Badge count={packages.length} style={{ marginLeft: 8 }} size="small" /></span>,
            children: (
              <div>
                <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: 16 }}>
                  <Text type="secondary">
                    Rule packages combine rules with custom weights. Assign packages to projects or tasks to customize evaluation per assessment type.
                  </Text>
                  <Button type="primary" icon={<PlusOutlined />} onClick={openCreate}>Create Package</Button>
                </div>

                <div style={{ display: 'flex', flexDirection: 'column', gap: 12, marginBottom: 24 }}>
                  {packages.map((pkg) => (
                    <div key={pkg.id} style={{ position: 'relative' }}>
                      <PackageFlowDiagram pkg={pkg} rules={rules} />
                      <div style={{ position: 'absolute', top: 8, right: 8 }}>
                        <Space size={4}>
                          <Tooltip title="Edit">
                            <Button size="small" type="text" icon={<EditOutlined />} onClick={() => openEdit(pkg)} />
                          </Tooltip>
                          <Popconfirm title="Delete this rule package?" onConfirm={() => handleDelete(pkg.id)}>
                            <Tooltip title="Delete">
                              <Button size="small" type="text" danger icon={<DeleteOutlined />} />
                            </Tooltip>
                          </Popconfirm>
                        </Space>
                      </div>
                    </div>
                  ))}
                </div>

                <Divider orientation="left">Package Details</Divider>

                <Card>
                  <Table
                    dataSource={packages}
                    columns={columns}
                    rowKey="id"
                    loading={loading}
                    pagination={false}
                  />
                </Card>
              </div>
            ),
          },
          {
            key: 'how-it-works',
            label: <span><InfoCircleOutlined /> How It Works</span>,
            children: (
              <Card>
                <Title level={4}>Evaluation Pipeline</Title>
                <Paragraph>
                  When a submission is evaluated using the rule-based method, the system follows this process:
                </Paragraph>

                <div style={{ display: 'flex', flexDirection: 'column', gap: 16 }}>
                  <Card size="small" style={{ borderLeft: '3px solid #1890ff' }}>
                    <Text strong>1. Rule Package Resolution</Text>
                    <Paragraph style={{ margin: '4px 0 0 0', fontSize: 13 }}>
                      The system resolves which rule package to use following this priority chain:
                    </Paragraph>
                    <div style={{ display: 'flex', alignItems: 'center', gap: 8, marginTop: 8, flexWrap: 'wrap' }}>
                      <Tag color="blue">Task&apos;s Rule Package</Tag>
                      <ArrowRightOutlined style={{ color: '#bfbfbf' }} />
                      <Tag color="green">Project&apos;s Rule Package</Tag>
                      <ArrowRightOutlined style={{ color: '#bfbfbf' }} />
                      <Tag color="gold">System Default</Tag>
                      <ArrowRightOutlined style={{ color: '#bfbfbf' }} />
                      <Tag>Equal Weights (all rules)</Tag>
                    </div>
                  </Card>

                  <Card size="small" style={{ borderLeft: '3px solid #52c41a' }}>
                    <Text strong>2. Document Parsing</Text>
                    <Paragraph style={{ margin: '4px 0 0 0', fontSize: 13 }}>
                      The submitted document (PDF/DOCX) is parsed into structured sections, extracting headings,
                      paragraphs, code snippets, tables, images, and links. Results are cached for subsequent evaluations.
                    </Paragraph>
                  </Card>

                  <Card size="small" style={{ borderLeft: '3px solid #722ed1' }}>
                    <Text strong>3. Signal Detection</Text>
                    <Paragraph style={{ margin: '4px 0 0 0', fontSize: 13 }}>
                      Each enabled rule scans the parsed document for its specific signals (keywords, structural patterns,
                      content depth metrics). Each signal produces a normalized 0-100 score.
                    </Paragraph>
                  </Card>

                  <Card size="small" style={{ borderLeft: '3px solid #fa8c16' }}>
                    <Text strong>4. Rule Aggregation</Text>
                    <Paragraph style={{ margin: '4px 0 0 0', fontSize: 13 }}>
                      Within each rule, signal scores are combined using their internal weights.
                      Then, rules are aggregated using the package&apos;s rule-level weights (normalized).
                      The final raw score (0-100) maps to a Performance Level and rubric points.
                    </Paragraph>
                  </Card>

                  <Card size="small" style={{ borderLeft: '3px solid #eb2f96' }}>
                    <Text strong>5. Feedback Generation</Text>
                    <Paragraph style={{ margin: '4px 0 0 0', fontSize: 13 }}>
                      The engine identifies strengths (signals above 65) and areas for improvement (signals below 45),
                      then generates tailored feedback with specific evidence from the document.
                    </Paragraph>
                  </Card>
                </div>

                <Divider />

                <Title level={5}>Composite Scoring</Title>
                <Paragraph>
                  The rule-based score is one component of the final composite score, alongside LLM evaluation
                  and tutor reviews. Each component&apos;s contribution is configured via the Scoring Configuration
                  (global weights). If a component isn&apos;t available, its weight is redistributed.
                </Paragraph>
              </Card>
            ),
          },
        ]}
      />

      {/* Rule Package Modal */}
      <Modal
        title={editing ? 'Edit Rule Package' : 'Create Rule Package'}
        open={modalOpen}
        onCancel={() => setModalOpen(false)}
        footer={null}
        width={700}
      >
        <Form form={form} layout="vertical" onFinish={handleSave}>
          <Form.Item name="name" label="Package Name" rules={[{ required: true, message: 'Name is required' }]}>
            <Input placeholder="e.g., NIT3004 - Final Report" />
          </Form.Item>

          <Form.Item name="description" label="Description">
            <Input.TextArea rows={2} placeholder="Describe what this package evaluates and why" />
          </Form.Item>

          <Form.Item name="isDefault" label="Set as Default" valuePropName="checked">
            <Switch />
          </Form.Item>

          <Divider orientation="left">Select Rules</Divider>

          <div style={{ marginBottom: 12 }}>
            <Text type="secondary" style={{ fontSize: 12 }}>
              Select which rules to include and set their relative weights. Weights are normalized during evaluation.
            </Text>
          </div>

          {/* Rule selector */}
          <div style={{ marginBottom: 16 }}>
            <Select
              placeholder="Add a rule to this package..."
              style={{ width: '100%' }}
              value={undefined}
              onChange={(ruleId: number) => addRuleToPackage(ruleId)}
              options={rules
                .filter((r) => !packageItems.some((item) => item.ruleId === r.id))
                .map((r) => ({
                  label: `${getMetadata(r.ruleKey).icon} ${r.name} (${r.category || 'Uncategorized'})`,
                  value: r.id,
                }))}
            />
          </div>

          {/* Selected rules with weights */}
          <div style={{ display: 'flex', flexDirection: 'column', gap: 8 }}>
            {packageItems.map((item) => {
              const rule = rules.find((r) => r.id === item.ruleId);
              if (!rule) return null;
              const meta = getMetadata(rule.ruleKey);
              return (
                <div
                  key={item.ruleId}
                  style={{
                    display: 'flex', alignItems: 'center', gap: 12,
                    padding: '8px 12px', background: '#fafafa', borderRadius: 6,
                    borderLeft: `3px solid ${meta.color}`,
                    opacity: item.enabled ? 1 : 0.5,
                  }}
                >
                  <Switch
                    size="small"
                    checked={item.enabled}
                    onChange={(checked) => updatePackageItem(item.ruleId, 'enabled', checked)}
                  />
                  <div style={{ flex: 1 }}>
                    <Text strong style={{ fontSize: 13 }}>{meta.icon} {rule.name}</Text>
                    <br />
                    <Text style={{ fontSize: 11, color: '#8c8c8c' }}>{rule.category || 'Uncategorized'}</Text>
                  </div>
                  <InputNumber
                    min={0}
                    max={100}
                    value={item.weight}
                    onChange={(val) => updatePackageItem(item.ruleId, 'weight', val || 0)}
                    addonAfter="%"
                    size="small"
                    style={{ width: 100 }}
                    disabled={!item.enabled}
                  />
                  <Button
                    type="text"
                    size="small"
                    danger
                    icon={<DeleteOutlined />}
                    onClick={() => removeRuleFromPackage(item.ruleId)}
                  />
                </div>
              );
            })}
            {packageItems.length === 0 && (
              <Empty description="No rules selected. Add rules from the dropdown above." image={Empty.PRESENTED_IMAGE_SIMPLE} />
            )}
          </div>

          {/* Hidden legacy fields for backwards compatibility */}
          <Form.Item name="logicEnabled" hidden><Switch /></Form.Item>
          <Form.Item name="methodologyEnabled" hidden><Switch /></Form.Item>
          <Form.Item name="implementationEnabled" hidden><Switch /></Form.Item>
          <Form.Item name="logicWeight" hidden><InputNumber /></Form.Item>
          <Form.Item name="methodologyWeight" hidden><InputNumber /></Form.Item>
          <Form.Item name="implementationWeight" hidden><InputNumber /></Form.Item>

          <Form.Item style={{ marginTop: 24 }}>
            <Space>
              <Button type="primary" htmlType="submit" loading={saving}>
                {editing ? 'Update' : 'Create'}
              </Button>
              <Button onClick={() => setModalOpen(false)}>Cancel</Button>
            </Space>
          </Form.Item>
        </Form>
      </Modal>

      {/* Rule Modal */}
      <Modal
        title={editingRule ? 'Edit Rule' : 'Create Rule'}
        open={ruleModalOpen}
        onCancel={() => setRuleModalOpen(false)}
        footer={null}
        width={700}
      >
        <Form form={ruleForm} layout="vertical" onFinish={handleSaveRule}>
          <Form.Item name="ruleKey" label="Rule Key" rules={[{ required: true, message: 'Key is required' }]}
            extra="Unique identifier (e.g., 'logic_explanation', 'methodology')"
          >
            <Input placeholder="e.g., my_custom_rule" disabled={editingRule?.builtIn} />
          </Form.Item>

          <Form.Item name="name" label="Display Name" rules={[{ required: true, message: 'Name is required' }]}>
            <Input placeholder="e.g., Logic & Explanation" />
          </Form.Item>

          <Form.Item name="category" label="Category">
            <Select
              placeholder="Select a category"
              allowClear
              options={[
                { label: 'Analysis', value: 'Analysis' },
                { label: 'Process', value: 'Process' },
                { label: 'Technical', value: 'Technical' },
                { label: 'Communication', value: 'Communication' },
                { label: 'Research', value: 'Research' },
              ]}
            />
          </Form.Item>

          <Form.Item name="description" label="Description">
            <Input.TextArea rows={3} placeholder="Describe what this rule evaluates and how" />
          </Form.Item>

          <Collapse
            ghost
            items={[{
              key: 'llm',
              label: 'LLM Criterion Prompt (for AI evaluation)',
              children: (
                <Form.Item
                  name="llmCriterionPrompt"
                  extra="This instruction is sent to the LLM when evaluating this criterion. Include level descriptors (EXCELLENT through INADEQUATE) and what to look for."
                >
                  <Input.TextArea
                    rows={10}
                    placeholder={'### Criterion Name\nHow well does the student...?\n\n- EXCELLENT (30): ...\n- PROFICIENT (24): ...\n- COMPETENT (18): ...\n- DEVELOPING (12): ...\n- INADEQUATE (6): ...'}
                    style={{ fontFamily: 'monospace', fontSize: 12 }}
                  />
                </Form.Item>
              ),
            }]}
          />

          <Form.Item style={{ marginTop: 16 }}>
            <Space>
              <Button type="primary" htmlType="submit" loading={ruleSaving}>
                {editingRule ? 'Update' : 'Create'}
              </Button>
              <Button onClick={() => setRuleModalOpen(false)}>Cancel</Button>
            </Space>
          </Form.Item>
        </Form>
      </Modal>
    </div>
  );
}
