import { Card, Tag, Typography, Collapse, Table, Progress } from 'antd';
import type { CriterionScoreResponse } from '../types';

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
    collapseItems.push({
      key: 'evidence',
      label: `Evidence (${criterion.evidence.length} items)`,
      children: (
        <ul style={{ margin: 0, paddingLeft: 20 }}>
          {criterion.evidence.map((item, idx) => (
            <li key={idx}>
              <Text>{item}</Text>
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
          percent={Math.round((criterion.score / 10) * 100)}
          strokeColor={getScoreColor(criterion.score)}
          format={() => `${criterion.score}/10`}
        />
      </div>
      <Paragraph>{criterion.justification}</Paragraph>
      {collapseItems.length > 0 && (
        <Collapse items={collapseItems} size="small" />
      )}
    </Card>
  );
}
