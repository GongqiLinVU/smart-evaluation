import { Progress, Tag, Typography, Space } from 'antd';

const { Title } = Typography;

interface ScoreSummaryProps {
  score: number;
  level: string;
  method: string;
}

function getLevelColor(level: string): string {
  const upper = level.toUpperCase();
  if (upper === 'EXCELLENT') return '#52c41a';
  if (upper === 'PROFICIENT') return '#1677ff';
  if (upper === 'COMPETENT') return '#fa8c16';
  if (upper === 'DEVELOPING') return '#faad14';
  if (upper === 'INADEQUATE') return '#ff4d4f';
  return '#d9d9d9';
}

function getLevelTagColor(level: string): string {
  const upper = level.toUpperCase();
  if (upper === 'EXCELLENT') return 'green';
  if (upper === 'PROFICIENT') return 'blue';
  if (upper === 'COMPETENT') return 'orange';
  if (upper === 'DEVELOPING') return 'gold';
  if (upper === 'INADEQUATE') return 'red';
  return 'default';
}

function getMethodTagColor(method: string): string {
  return method === 'RULE_BASED' ? 'purple' : 'cyan';
}

export default function ScoreSummary({ score, level, method }: ScoreSummaryProps) {
  const percent = Math.round((score / 30) * 100);

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
            <span style={{ fontSize: 14, color: '#999' }}>/ 30</span>
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
