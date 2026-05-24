import { Card, Tag, Typography, Collapse, Table, Progress, Space } from 'antd';
import {
  CheckCircleOutlined,
  WarningOutlined,
  BulbOutlined,
} from '@ant-design/icons';
import type { CriterionScoreResponse, EvidenceItem } from '../types';

const { Text, Paragraph } = Typography;

interface ScoreCardProps {
  criterion: CriterionScoreResponse;
}

function formatCriterionName(name: string): string {
  return name
    .split('_')
    .map((word) => word.charAt(0).toUpperCase() + word.slice(1).toLowerCase())
    .join(' ');
}

function getScoreColor(score: number): string {
  if (score >= 27) return 'green';
  if (score >= 21) return 'blue';
  if (score >= 15) return 'orange';
  if (score >= 9) return 'gold';
  return 'red';
}

function getLevelColor(level: string): string {
  const upper = level.toUpperCase();
  if (upper === 'EXCELLENT') return 'green';
  if (upper === 'PROFICIENT') return 'blue';
  if (upper === 'COMPETENT') return 'orange';
  if (upper === 'DEVELOPING') return 'gold';
  if (upper === 'INADEQUATE') return 'red';
  return 'default';
}

function isEvidenceItem(item: string | EvidenceItem): item is EvidenceItem {
  return typeof item === 'object' && item !== null && 'quote' in item;
}

export default function ScoreCard({ criterion }: ScoreCardProps) {
  const subScoreEntries = Object.entries(criterion.subScores || {});
  const subScoreColumns = [
    {
      title: 'Sub-criterion',
      dataIndex: 'name',
      key: 'name',
      render: (name: string) => formatCriterionName(name),
    },
    {
      title: 'Score',
      dataIndex: 'score',
      key: 'score',
      width: 80,
      render: (score: number, record: { max: number }) => `${score}/${record.max}`,
    },
    {
      title: 'Note',
      dataIndex: 'note',
      key: 'note',
    },
  ];

  const subScoreData = subScoreEntries.map(([name, value]) => ({
    key: name,
    name,
    score: value.score,
    max: value.max,
    note: value.note,
  }));

  const collapseItems = [];

  if (criterion.evidence && criterion.evidence.length > 0) {
    const hasRichEvidence = criterion.evidence.some(isEvidenceItem);

    collapseItems.push({
      key: 'evidence',
      label: `Evidence (${criterion.evidence.length} items)`,
      children: hasRichEvidence ? (
        <div style={{ display: 'flex', flexDirection: 'column', gap: 8 }}>
          {criterion.evidence.map((item, idx) => {
            if (isEvidenceItem(item)) {
              return (
                <div
                  key={idx}
                  style={{
                    padding: '8px 12px',
                    borderRadius: 6,
                    border: `1px solid ${item.sentiment === 'positive' ? '#b7eb8f' : '#ffd591'}`,
                    background: item.sentiment === 'positive' ? '#f6ffed' : '#fff7e6',
                  }}
                >
                  <Space size={4} style={{ marginBottom: 4 }}>
                    {item.sectionName && (
                      <Tag color="blue" style={{ fontSize: 11 }}>{item.sectionName}</Tag>
                    )}
                    {item.sentiment === 'positive' ? (
                      <Tag color="success" icon={<CheckCircleOutlined />}>Positive</Tag>
                    ) : (
                      <Tag color="warning" icon={<WarningOutlined />}>Negative</Tag>
                    )}
                  </Space>
                  <div style={{ marginTop: 4, fontStyle: 'italic', color: '#595959' }}>
                    &ldquo;{item.quote}&rdquo;
                  </div>
                  {item.note && (
                    <Text type="secondary" style={{ fontSize: 12 }}>{item.note}</Text>
                  )}
                </div>
              );
            }
            return (
              <li key={idx} style={{ listStyle: 'disc', marginLeft: 16 }}>
                <Text>{item}</Text>
              </li>
            );
          })}
        </div>
      ) : (
        <ul style={{ margin: 0, paddingLeft: 20 }}>
          {criterion.evidence.map((item, idx) => (
            <li key={idx}>
              <Text>{typeof item === 'string' ? item : (item as EvidenceItem).quote}</Text>
            </li>
          ))}
        </ul>
      ),
    });
  }

  if (criterion.suggestions && criterion.suggestions.length > 0) {
    collapseItems.push({
      key: 'suggestions',
      label: `Suggestions (${criterion.suggestions.length})`,
      children: (
        <ul style={{ margin: 0, paddingLeft: 20 }}>
          {criterion.suggestions.map((s, idx) => (
            <li key={idx}>
              <Space size={4}>
                <BulbOutlined style={{ color: '#faad14' }} />
                <Text>{s}</Text>
              </Space>
            </li>
          ))}
        </ul>
      ),
    });
  }

  if (subScoreData.length > 0) {
    collapseItems.push({
      key: 'subscores',
      label: 'Sub-scores',
      children: (
        <Table
          columns={subScoreColumns}
          dataSource={subScoreData}
          pagination={false}
          size="small"
        />
      ),
    });
  }

  return (
    <Card
      title={
        <span>
          {formatCriterionName(criterion.criterionName)}{' '}
          <Tag color={getScoreColor(criterion.score)}>{criterion.score}</Tag>
          <Tag color={getLevelColor(criterion.level)}>{criterion.level}</Tag>
        </span>
      }
      style={{ height: '100%' }}
    >
      <div style={{ marginBottom: 16 }}>
        <Progress
          percent={Math.round((criterion.score / 30) * 100)}
          strokeColor={getScoreColor(criterion.score)}
          format={() => `${criterion.score}/30`}
        />
      </div>
      {criterion.confidence != null && (
        <div style={{ marginBottom: 8 }}>
          <Text type="secondary" style={{ fontSize: 12 }}>
            Confidence: {Math.round(criterion.confidence * 100)}%
          </Text>
          <Progress
            percent={Math.round(criterion.confidence * 100)}
            size="small"
            showInfo={false}
            strokeColor={criterion.confidence >= 0.7 ? '#52c41a' : '#faad14'}
          />
        </div>
      )}
      <Paragraph>{criterion.justification}</Paragraph>
      {collapseItems.length > 0 && (
        <Collapse items={collapseItems} size="small" />
      )}
    </Card>
  );
}
