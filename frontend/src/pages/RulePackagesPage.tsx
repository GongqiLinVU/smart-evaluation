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
  Spin,
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
  ThunderboltOutlined,
  SyncOutlined,
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
  generateEvidenceQuestions,
  getEvidenceQuestions,
  getScoringRules,
  updateEvidenceQuestions,
  updateScoringRules,
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

// Parse llmCriterionPrompt text into rubricLevels array.
// Expects lines like: "- EXCELLENT (4): description" or "- HD (30): description"
function parseLevelsFromPrompt(prompt: string): { level: string; points: number; description: string }[] {
  const levels: { level: string; points: number; description: string }[] = [];
  const lineRe = /^[-*]\s*(EXCELLENT|PROFICIENT|COMPETENT|DEVELOPING|INADEQUATE|PARTIAL|HD|D|C|P|F)\s*\((\d+)(?:[–-]\d+)?\)\s*:?\s*(.+)/i;
  for (const line of prompt.split('\n')) {
    const m = line.match(lineRe);
    if (m) {
      levels.push({ level: m[1].toUpperCase(), points: parseInt(m[2], 10), description: m[3].trim() });
    }
  }
  return levels;
}

const RULE_METADATA: Record<string, RuleMetadata> = {
  fr_problem_definition: {
    color: '#fa8c16',
    icon: '🎯',
    tagColor: 'orange',
    rubricCriteria: 'Does the report clearly identify the real-world problem, define scope, and specify requirements with evidence of engineering rigour?',
    rubricLevels: [
      { level: 'EXCELLENT', points: 4, description: 'Problem grounded in real-world context; all stakeholders identified; functional + non-functional requirements explicitly stated; constraints and scope defined' },
      { level: 'PROFICIENT', points: 3, description: 'Good problem framing with identifiable stakeholders and mostly complete requirements; minor gaps in constraints' },
      { level: 'COMPETENT', points: 2, description: 'Problem described at surface level; requirements are feature lists rather than engineering requirements; stakeholder analysis superficial' },
      { level: 'DEVELOPING', points: 1, description: 'Generic problem statement applicable to any project; no meaningful requirements analysis' },
      { level: 'INADEQUATE', points: 0, description: 'No discernible problem definition or requirements analysis' },
    ],
    signals: [
      { name: 'Problem Context', weight: 30, description: 'Real-world grounding with specific stakeholders and needs', keywords: ['problem', 'stakeholder', 'user', 'context', 'challenge'] },
      { name: 'Requirements Specification', weight: 40, description: 'Explicit functional and non-functional requirements', keywords: ['requirement', 'functional', 'non-functional', 'constraint', 'scope'] },
      { name: 'Scope Definition', weight: 30, description: 'Clear boundaries and what the project does/does not cover', keywords: ['scope', 'boundary', 'limitation', 'in-scope', 'out-of-scope'] },
    ],
  },
  fr_architecture: {
    color: '#1890ff',
    icon: '🏗️',
    tagColor: 'blue',
    rubricCriteria: 'Does the report demonstrate deliberate architectural thinking, justified technology choices, and a coherent development methodology?',
    rubricLevels: [
      { level: 'EXCELLENT', points: 6, description: 'Well-designed architecture with justified technology choices (alternatives considered); methodology consistently applied; informative diagrams' },
      { level: 'PROFICIENT', points: 5, description: 'Good architectural coverage with reasonable justifications; methodology described; diagrams present but gaps' },
      { level: 'COMPETENT', points: 4, description: 'Architecture and methodology mentioned but lack depth; technology choices stated without justification' },
      { level: 'DEVELOPING', points: 2, description: 'Superficial mention of tools/frameworks; no architectural thinking; methodology reads as generic template' },
      { level: 'INADEQUATE', points: 0, description: 'No architecture or methodology content' },
    ],
    signals: [
      { name: 'Architecture Design', weight: 25, description: 'System architecture with separation of concerns', keywords: ['architecture', 'component', 'layer', 'service', 'module'] },
      { name: 'Technology Justification', weight: 25, description: 'Technology choices with explicit rationale and trade-offs', keywords: ['chose', 'because', 'alternative', 'trade-off', 'compared'] },
      { name: 'Methodology', weight: 25, description: 'Development methodology explained and applied', keywords: ['agile', 'iterative', 'sprint', 'methodology', 'process'] },
      { name: 'Diagrams', weight: 25, description: 'System diagrams (architecture, component, data flow) present and explained', keywords: ['diagram', 'figure', 'ERD', 'flowchart', 'architecture'] },
    ],
  },
  fr_engineering_design: {
    color: '#52c41a',
    icon: '⚙️',
    tagColor: 'green',
    rubricCriteria: 'Does the report explain engineering design decisions and document system workflows with sufficient technical depth?',
    rubricLevels: [
      { level: 'EXCELLENT', points: 5, description: 'Design decisions explained with reasoning; workflows thoroughly documented; iterative design or trade-off analysis present' },
      { level: 'PROFICIENT', points: 4, description: 'Good design explanation with workflows documented; most decisions justified; minor gaps' },
      { level: 'COMPETENT', points: 3, description: 'Design and workflow present but surface-level; decisions described without reasoning; screenshots dominate' },
      { level: 'DEVELOPING', points: 2, description: 'Feature descriptions only; workflow absent or trivial; reads as user manual' },
      { level: 'INADEQUATE', points: 0, description: 'No engineering design or workflow explanation' },
    ],
    signals: [
      { name: 'Design Rationale', weight: 35, description: 'WHY each design decision was made, not just WHAT was built', keywords: ['because', 'reason', 'chose', 'decided', 'trade-off'] },
      { name: 'Workflow Documentation', weight: 35, description: 'User journey, data flow, or process flow documented', keywords: ['workflow', 'process', 'flow', 'sequence', 'step'] },
      { name: 'Technical Credibility', weight: 30, description: 'Implementation details specific and technically accurate', keywords: ['algorithm', 'API', 'database', 'endpoint', 'function'] },
    ],
  },
  fr_testing: {
    color: '#722ed1',
    icon: '🧪',
    tagColor: 'purple',
    rubricCriteria: 'Does the report demonstrate a systematic testing approach and evaluate the solution against the original problem?',
    rubricLevels: [
      { level: 'EXCELLENT', points: 5, description: 'Comprehensive testing documented with test cases and results; evaluation closes loop back to requirements; quantitative or qualitative evidence provided' },
      { level: 'PROFICIENT', points: 4, description: 'Good testing coverage with results; evaluation present but may not fully map back to all requirements' },
      { level: 'COMPETENT', points: 3, description: 'Some testing described but limited scope; results vague; system shown to "work" but effectiveness not analysed' },
      { level: 'DEVELOPING', points: 1, description: 'Only mentions testing was done; no specific test cases or results; no evaluation of problem solving' },
      { level: 'INADEQUATE', points: 0, description: 'No testing or evaluation content' },
    ],
    signals: [
      { name: 'Test Coverage', weight: 35, description: 'Unit, integration, UAT, or usability testing documented', keywords: ['test', 'unit test', 'integration', 'UAT', 'usability'] },
      { name: 'Test Results', weight: 30, description: 'Specific test cases with outcomes reported', keywords: ['result', 'pass', 'fail', 'output', 'screenshot'] },
      { name: 'Requirements Traceability', weight: 35, description: 'Evaluation explicitly maps back to original requirements', keywords: ['requirement', 'objective', 'goal', 'achieved', 'validated'] },
    ],
  },
  fr_discussion: {
    color: '#eb2f96',
    icon: '💬',
    tagColor: 'magenta',
    rubricCriteria: 'Does the report demonstrate honest critical reflection and meaningful forward thinking?',
    rubricLevels: [
      { level: 'EXCELLENT', points: 3, description: 'Discussion connects results to problem; limitations specifically identified (technical/scope/data); future work concrete and grounded in findings' },
      { level: 'COMPETENT', points: 2, description: 'Discussion present but lacks depth; limitations generic (e.g. "more time needed"); future work listed but not motivated' },
      { level: 'DEVELOPING', points: 1, description: 'Superficial reflection; limitations or future work missing; no critical thinking about outcomes' },
      { level: 'INADEQUATE', points: 0, description: 'No discussion, limitations, or future work' },
    ],
    signals: [
      { name: 'Critical Reflection', weight: 40, description: 'Connects outcomes back to the original problem statement', keywords: ['limitation', 'constraint', 'challenge', 'however', 'despite'] },
      { name: 'Specific Limitations', weight: 30, description: 'Concrete technical or scope limitations (not generic)', keywords: ['accuracy', 'dataset', 'performance', 'scalability', 'security'] },
      { name: 'Future Work', weight: 30, description: 'Concrete future directions grounded in current findings', keywords: ['future', 'improve', 'extend', 'next step', 'recommendation'] },
    ],
  },
  fr_report_structure: {
    color: '#13c2c2',
    icon: '📝',
    tagColor: 'cyan',
    rubricCriteria: 'Is the report professionally structured, clearly written, and appropriately referenced?',
    rubricLevels: [
      { level: 'EXCELLENT', points: 2, description: 'Well-organised with logical engineering flow; professional academic writing; complete references; figures/tables labelled and cited' },
      { level: 'PARTIAL', points: 1, description: 'Mostly readable but structural issues; inconsistent writing quality; incomplete or improperly formatted references' },
      { level: 'INADEQUATE', points: 0, description: 'Poor structure, significant writing issues, or no references; dominated by code listings or screenshots' },
    ],
    signals: [
      { name: 'Structure & Flow', weight: 40, description: 'Logical section ordering from problem to reflection', keywords: ['introduction', 'conclusion', 'abstract', 'methodology', 'evaluation'] },
      { name: 'Professional Writing', weight: 30, description: 'Academic writing style without excessive code/screenshots', keywords: ['therefore', 'demonstrates', 'analysis', 'indicates', 'suggests'] },
      { name: 'References', weight: 30, description: 'Complete citations and reference list', keywords: ['reference', 'citation', 'bibliography', 'figure', 'table'] },
    ],
  },
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

function levelTagColor(level: string): string {
  const l = level.toUpperCase();
  if (l === 'HD' || l === 'EXCELLENT') return 'gold';
  if (l === 'D' || l === 'PROFICIENT') return 'blue';
  if (l === 'C' || l === 'COMPETENT') return 'green';
  if (l === 'P' || l === 'DEVELOPING') return 'orange';
  if (l === 'PARTIAL') return 'orange';
  return 'red';
}

function RuleCard({ rule, onEdit, onDelete }: { rule: RuleResponse; onEdit: () => void; onDelete: () => void }) {
  const meta = getMetadata(rule.ruleKey);
  // For rules not in RULE_METADATA, parse levels from llmCriterionPrompt
  const promptLevels = !meta.rubricCriteria && rule.llmCriterionPrompt
    ? parseLevelsFromPrompt(rule.llmCriterionPrompt)
    : [];
  // Extract framing question (first non-heading line after the ### heading)
  const promptQuestion = !meta.rubricCriteria && rule.llmCriterionPrompt
    ? (() => {
        const lines = rule.llmCriterionPrompt.split('\n').map(l => l.trim()).filter(Boolean);
        const q = lines.find(l => !l.startsWith('#') && !l.startsWith('-') && !l.startsWith('NOTE'));
        return q || '';
      })()
    : '';

  const levelsToShow = meta.rubricLevels.length > 0 ? meta.rubricLevels : promptLevels;
  const criteriaText = meta.rubricCriteria || promptQuestion;

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

      {criteriaText && (
        <>
          <Divider style={{ margin: '12px 0' }} />
          <div style={{ marginBottom: 12 }}>
            <Text strong style={{ fontSize: 12, textTransform: 'uppercase', color: '#8c8c8c' }}>
              Rubric Criteria
            </Text>
            <Paragraph
              style={{ fontSize: 13, fontStyle: 'italic', margin: '4px 0 0 0', color: '#262626' }}
            >
              &ldquo;{criteriaText}&rdquo;
            </Paragraph>
          </div>

          <Collapse
            ghost
            size="small"
            items={[
              {
                key: 'levels',
                label: <Text style={{ fontSize: 12, color: '#8c8c8c' }}>Performance Levels ({levelsToShow.length})</Text>,
                children: (
                  <div style={{ display: 'flex', flexDirection: 'column', gap: 6 }}>
                    {levelsToShow.map((lvl) => (
                      <div key={lvl.level} style={{ display: 'flex', gap: 8, alignItems: 'flex-start' }}>
                        <Tag
                          color={levelTagColor(lvl.level)}
                          style={{ minWidth: 80, textAlign: 'center', flexShrink: 0 }}
                        >
                          {lvl.level} ({lvl.points})
                        </Tag>
                        <Text style={{ fontSize: 12 }}>{lvl.description}</Text>
                      </div>
                    ))}
                  </div>
                ),
              },
              ...(meta.signals.length > 0 ? [{
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
              }] : []),
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
                  {item.maxPoints != null ? (
                    <Tooltip title={`Fixed marks: ${item.maxPoints}`}>
                      <Tag color={meta.tagColor} style={{ margin: 0 }}>{item.maxPoints}pts</Tag>
                    </Tooltip>
                  ) : (
                    <Tooltip title={`Weight: ${item.weight.toFixed(1)} (normalized: ${normalizedPct.toFixed(0)}%)`}>
                      <Tag color={meta.tagColor} style={{ margin: 0 }}>{normalizedPct.toFixed(0)}%</Tag>
                    </Tooltip>
                  )}
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

function HybridConfigTab({ packages, onRefresh }: { packages: RulePackageResponse[]; onRefresh: () => void }) {
  const [selectedPackageId, setSelectedPackageId] = useState<number | undefined>();
  const [generating, setGenerating] = useState<Record<number, boolean>>({});
  const [viewItem, setViewItem] = useState<{ packageId: number; itemId: number; ruleName: string } | null>(null);
  const [questionsJson, setQuestionsJson] = useState<string>('');
  const [rulesJson, setRulesJson] = useState<string>('');
  const [detailLoading, setDetailLoading] = useState(false);
  const [saving, setSaving] = useState(false);

  const selectedPackage = packages.find((p) => p.id === selectedPackageId);
  const enabledItems = selectedPackage?.items.filter((i) => i.enabled) || [];

  const handleGenerate = async (packageId: number, itemId: number) => {
    setGenerating((prev) => ({ ...prev, [itemId]: true }));
    try {
      await generateEvidenceQuestions(packageId, itemId);
      message.success('Evidence questions generated and saved');
      onRefresh();
    } catch {
      message.error('Failed to generate evidence questions');
    } finally {
      setGenerating((prev) => ({ ...prev, [itemId]: false }));
    }
  };

  const handleGenerateAll = async () => {
    if (!selectedPackage) return;
    for (const item of enabledItems) {
      await handleGenerate(selectedPackage.id, item.id);
    }
  };

  const openDetail = async (packageId: number, itemId: number, ruleName: string) => {
    setViewItem({ packageId, itemId, ruleName });
    setDetailLoading(true);
    try {
      const [q, r] = await Promise.all([
        getEvidenceQuestions(packageId, itemId).catch(() => null),
        getScoringRules(packageId, itemId).catch(() => null),
      ]);
      setQuestionsJson(q ? JSON.stringify(q, null, 2) : '');
      setRulesJson(r ? JSON.stringify(r, null, 2) : '');
    } catch {
      setQuestionsJson('');
      setRulesJson('');
    } finally {
      setDetailLoading(false);
    }
  };

  const handleSave = async () => {
    if (!viewItem) return;
    setSaving(true);
    try {
      if (questionsJson.trim()) {
        await updateEvidenceQuestions(viewItem.packageId, viewItem.itemId, JSON.parse(questionsJson));
      }
      if (rulesJson.trim()) {
        await updateScoringRules(viewItem.packageId, viewItem.itemId, JSON.parse(rulesJson));
      }
      message.success('Saved');
      setViewItem(null);
      onRefresh();
    } catch (e) {
      message.error('Invalid JSON or save failed');
    } finally {
      setSaving(false);
    }
  };

  return (
    <div>
      <div style={{ marginBottom: 16 }}>
        <Text type="secondary">
          Configure evidence questions and scoring rules for the Hybrid evaluation method.
          Select a rule package, then generate or edit the questions for each criterion.
        </Text>
      </div>

      <Card size="small" style={{ marginBottom: 16, background: '#f0f5ff', border: '1px solid #adc6ff' }}>
        <Space>
          <InfoCircleOutlined style={{ color: '#1890ff' }} />
          <Text style={{ fontSize: 13 }}>
            Hybrid evaluation uses LLM to extract factual evidence, then applies deterministic scoring rules.
            Each enabled rule needs evidence questions before Hybrid eval can run.
          </Text>
        </Space>
      </Card>

      <Space style={{ marginBottom: 16 }}>
        <Select
          placeholder="Select a rule package"
          style={{ width: 300 }}
          value={selectedPackageId}
          onChange={setSelectedPackageId}
          options={packages.map((p) => ({
            label: `${p.name}${p.isDefault ? ' (Default)' : ''}`,
            value: p.id,
          }))}
        />
        {selectedPackage && enabledItems.length > 0 && (
          <Button
            icon={<ThunderboltOutlined />}
            onClick={handleGenerateAll}
            loading={Object.values(generating).some(Boolean)}
          >
            Generate All
          </Button>
        )}
      </Space>

      {selectedPackage && (
        <Table
          dataSource={enabledItems}
          rowKey="id"
          pagination={false}
          columns={[
            {
              title: 'Rule',
              key: 'rule',
              render: (_, item) => (
                <Space>
                  <Text strong>{item.ruleName}</Text>
                  <Tag>{item.ruleKey}</Tag>
                </Space>
              ),
            },
            {
              title: 'Evidence Questions',
              key: 'questions',
              width: 160,
              render: (_, item) =>
                item.hasEvidenceQuestions ? (
                  <Tag color="green" icon={<CheckCircleOutlined />}>Configured</Tag>
                ) : (
                  <Tag color="orange">Not set</Tag>
                ),
            },
            {
              title: 'Scoring Rules',
              key: 'rules',
              width: 160,
              render: (_, item) =>
                item.hasScoringRules ? (
                  <Tag color="green" icon={<CheckCircleOutlined />}>Configured</Tag>
                ) : (
                  <Tag color="orange">Not set</Tag>
                ),
            },
            {
              title: 'Actions',
              key: 'actions',
              width: 220,
              render: (_, item) => (
                <Space>
                  <Button
                    size="small"
                    icon={<SyncOutlined spin={generating[item.id]} />}
                    onClick={() => handleGenerate(selectedPackage.id, item.id)}
                    loading={generating[item.id]}
                  >
                    Generate
                  </Button>
                  <Button
                    size="small"
                    type="link"
                    icon={<EditOutlined />}
                    onClick={() => openDetail(selectedPackage.id, item.id, item.ruleName)}
                  >
                    View/Edit
                  </Button>
                </Space>
              ),
            },
          ]}
        />
      )}

      {!selectedPackage && (
        <Empty description="Select a rule package above to configure hybrid evaluation" image={Empty.PRESENTED_IMAGE_SIMPLE} />
      )}

      <Modal
        title={`Hybrid Config: ${viewItem?.ruleName || ''}`}
        open={viewItem !== null}
        onCancel={() => setViewItem(null)}
        width={800}
        footer={[
          <Button key="cancel" onClick={() => setViewItem(null)}>Cancel</Button>,
          <Button key="save" type="primary" onClick={handleSave} loading={saving}>Save Changes</Button>,
        ]}
      >
        {detailLoading ? (
          <div style={{ textAlign: 'center', padding: 40 }}><Spin /></div>
        ) : (
          <Tabs
            items={[
              {
                key: 'questions',
                label: 'Evidence Questions',
                children: (
                  <Input.TextArea
                    value={questionsJson}
                    onChange={(e) => setQuestionsJson(e.target.value)}
                    rows={16}
                    style={{ fontFamily: 'monospace', fontSize: 12 }}
                    placeholder="No evidence questions configured yet. Click Generate to create them."
                  />
                ),
              },
              {
                key: 'rules',
                label: 'Scoring Rules',
                children: (
                  <Input.TextArea
                    value={rulesJson}
                    onChange={(e) => setRulesJson(e.target.value)}
                    rows={16}
                    style={{ fontFamily: 'monospace', fontSize: 12 }}
                    placeholder="No scoring rules configured yet. Click Generate to create them."
                  />
                ),
              },
            ]}
          />
        )}
      </Modal>
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

  const [packageItems, setPackageItems] = useState<{ ruleId: number; enabled: boolean; weight: number; maxPoints: number | null }[]>([]);
  const [scoringScale, setScoringScale] = useState<string | null>(null);

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
  const SCORING_SCALE_PRESETS: Record<string, string> = {
    standard: JSON.stringify([
      { level: 'EXCELLENT', points: 30, description: 'Outstanding, comprehensive work' },
      { level: 'PROFICIENT', points: 24, description: 'Solid, thorough work with minor gaps' },
      { level: 'COMPETENT', points: 18, description: 'Adequate work with some gaps' },
      { level: 'DEVELOPING', points: 12, description: 'Basic work with significant gaps' },
      { level: 'INADEQUATE', points: 6, description: 'Poor or missing work' },
    ]),
    progress: JSON.stringify([
      { level: 'HD', points: 10, description: 'Outstanding, comprehensive progress' },
      { level: 'D', points: 8, description: 'Solid progress with minor gaps' },
      { level: 'C', points: 6, description: 'Adequate progress with some gaps' },
      { level: 'P', points: 4, description: 'Basic progress with significant gaps' },
      { level: 'F', points: 2, description: 'Insufficient progress' },
    ]),
  };

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
    setPackageItems(rules.map((r) => ({ ruleId: r.id, enabled: true, weight: 1.0, maxPoints: null })));
    setScoringScale(null);
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
    setScoringScale(rp.scoringScale || null);
    if (rp.items && rp.items.length > 0) {
      setPackageItems(rp.items.map((item) => ({ ruleId: item.ruleId, enabled: item.enabled, weight: item.weight, maxPoints: item.maxPoints ?? null })));
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
        maxPoints: null,
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
      scoringScale: scoringScale || undefined,
      items: packageItems.filter((item) => item.enabled).map((item) => ({
        ruleId: item.ruleId,
        enabled: item.enabled,
        weight: item.weight,
        maxPoints: item.maxPoints ?? undefined,
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

  const updatePackageItem = (ruleId: number, field: 'enabled' | 'weight' | 'maxPoints', value: boolean | number | null) => {
    setPackageItems((prev) =>
      prev.map((item) =>
        item.ruleId === ruleId ? { ...item, [field]: value } : item
      )
    );
  };

  const addRuleToPackage = (ruleId: number) => {
    if (packageItems.some((item) => item.ruleId === ruleId)) return;
    setPackageItems((prev) => [...prev, { ruleId, enabled: true, weight: 1.0, maxPoints: null }]);
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
                if (item.maxPoints != null) {
                  return (
                    <Tooltip key={item.id} title={`${item.ruleName}: ${item.maxPoints} marks`}>
                      <Tag color={meta.tagColor} icon={<CheckCircleOutlined />}>
                        {item.ruleName} {item.maxPoints}pts
                      </Tag>
                    </Tooltip>
                  );
                }
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
            key: 'hybrid',
            label: <span><ThunderboltOutlined /> Hybrid Config</span>,
            children: <HybridConfigTab packages={packages} onRefresh={fetchPackages} />,
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

          <Form.Item label="Scoring Scale" extra="Determines valid score values for this package. Default is 30/24/18/12/6.">
            <Select
              value={
                scoringScale === null ? 'default' :
                scoringScale === SCORING_SCALE_PRESETS.standard ? 'standard' :
                scoringScale === SCORING_SCALE_PRESETS.progress ? 'progress' : 'custom'
              }
              onChange={(value) => {
                if (value === 'default') setScoringScale(null);
                else if (value === 'standard') setScoringScale(SCORING_SCALE_PRESETS.standard);
                else if (value === 'progress') setScoringScale(SCORING_SCALE_PRESETS.progress);
              }}
              options={[
                { label: 'Default (30/24/18/12/6)', value: 'default' },
                { label: 'Standard — 30pt (EXCELLENT/PROFICIENT/COMPETENT/DEVELOPING/INADEQUATE)', value: 'standard' },
                { label: 'Progress Report — 10pt (HD/D/C/P/F)', value: 'progress' },
                { label: 'Custom (edit JSON below)', value: 'custom' },
              ]}
            />
            {scoringScale !== null && (
              <Input.TextArea
                style={{ marginTop: 8, fontFamily: 'monospace', fontSize: 11 }}
                rows={3}
                value={scoringScale}
                onChange={(e) => setScoringScale(e.target.value)}
                placeholder='[{"level":"HD","points":10,"description":"..."},...]'
              />
            )}
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
                  <Tooltip title="Max marks for this criterion (leave blank to use shared scoring scale)">
                    <InputNumber
                      min={0}
                      max={200}
                      value={item.maxPoints ?? undefined}
                      placeholder="pts"
                      onChange={(val) => updatePackageItem(item.ruleId, 'maxPoints', val ?? null)}
                      addonBefore="max"
                      addonAfter="pts"
                      size="small"
                      style={{ width: 130 }}
                      disabled={!item.enabled}
                    />
                  </Tooltip>
                  <Tooltip title="Relative weight (used when max marks are not set)">
                    <InputNumber
                      min={0}
                      max={100}
                      value={item.weight}
                      onChange={(val) => updatePackageItem(item.ruleId, 'weight', val || 0)}
                      addonAfter="%"
                      size="small"
                      style={{ width: 100 }}
                      disabled={!item.enabled || item.maxPoints != null}
                    />
                  </Tooltip>
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
                { label: 'Diary', value: 'Diary' },
                { label: 'Demo', value: 'Demo' },
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
