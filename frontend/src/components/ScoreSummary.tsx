import { Progress, Tag, Typography, Space } from 'antd';

const { Title } = Typography;

interface ScoreSummaryProps {
  score: number;
  maxScore?: number;
  level: string;
  method: string;
}

function getLevelColor(level: string): string {
  const upper = level.toUpperCase();
  if (upper === 'EXCELLENT' || upper === 'HD') return '#52c41a';
  if (upper === 'PROFICIENT' || upper === 'D') return '#1677ff';
  if (upper === 'COMPETENT' || upper === 'C') return '#fa8c16';
  if (upper === 'DEVELOPING' || upper === 'P') return '#faad14';
  if (upper === 'INADEQUATE' || upper === 'F') return '#ff4d4f';
  return '#d9d9d9';
}

function getLevelTagColor(level: string): string {
  const upper = level.toUpperCase();
  if (upper === 'EXCELLENT' || upper === 'HD') return 'green';
  if (upper === 'PROFICIENT' || upper === 'D') return 'blue';
  if (upper === 'COMPETENT' || upper === 'C') return 'orange';
  if (upper === 'DEVELOPING' || upper === 'P') return 'gold';
  if (upper === 'INADEQUATE' || upper === 'F') return 'red';
  return 'default';
}

function getMethodTagColor(method: string): string {
  return method === 'RULE_BASED' ? 'purple' : 'cyan';
}

export default function ScoreSummary({ score, maxScore = 30, level, method }: ScoreSummaryProps) {
  const percent = Math.round((score / maxScore) * 100);

  return (
    <div style={{ textAlign: 'center', padding: '16px 0' }}>
      <Progress
        type="circle"
        percent={percent}
        size={140}
        strokeColor={getLevelColor(level)}
        format={() => (
          <div>
            <Title level={2} style={{ margin: 0, lineHeight: 1 }}>
              {score}
            </Title>
            <span style={{ fontSize: 14, color: '#999' }}>/ {maxScore}</span>
          </div>
        )}
      />
      <div style={{ marginTop: 16 }}>
        <Space>
          <Tag color={getLevelTagColor(level)} style={{ fontSize: 14, padding: '4px 12px' }}>
            {level}
          </Tag>
          <Tag color={getMethodTagColor(method)} style={{ fontSize: 14, padding: '4px 12px' }}>
            {method === 'RULE_BASED' ? 'Rule-Based' : 'LLM'}
          </Tag>
        </Space>
      </div>
    </div>
  );
}
