import { useEffect, useState } from 'react';
import { Card, Progress, Tag, Typography, Space, Tooltip, Spin } from 'antd';
import { TrophyOutlined } from '@ant-design/icons';
import { getCompositeScore } from '../api/client';
import type { CompositeScoreResponse } from '../types';

const { Title, Text } = Typography;

function getLevelColor(level: string | null): string {
  switch (level) {
    case 'EXCELLENT': case 'HD': return '#52c41a';
    case 'PROFICIENT': case 'D': return '#1890ff';
    case 'COMPETENT': case 'C': return '#faad14';
    case 'DEVELOPING': case 'P': return '#fa8c16';
    case 'INADEQUATE': case 'F': return '#f5222d';
    default: return '#d9d9d9';
  }
}

function getMethodLabel(method: string): string {
  switch (method) {
    case 'RULE_BASED': return 'Rule-Based';
    case 'LLM': return 'LLM';
    case 'TUTOR': return 'Tutor Review';
    default: return method;
  }
}

function getMethodColor(method: string): string {
  switch (method) {
    case 'RULE_BASED': return '#722ed1';
    case 'LLM': return '#13c2c2';
    case 'TUTOR': return '#faad14';
    default: return '#666';
  }
}

interface Props {
  submissionId: number;
  refreshKey?: number;
}

export default function CompositeScorePanel({ submissionId, refreshKey }: Props) {
  const [data, setData] = useState<CompositeScoreResponse | null>(null);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    setLoading(true);
    getCompositeScore(submissionId)
      .then(setData)
      .catch(() => setData(null))
      .finally(() => setLoading(false));
  }, [submissionId, refreshKey]);

  if (loading) {
    return (
      <Card style={{ marginBottom: 24 }}>
        <Spin size="small" />
      </Card>
    );
  }

  if (!data) return null;

  const hasScore = data.compositeScore !== null;

  return (
    <Card
      title={
        <Space>
          <TrophyOutlined style={{ color: getLevelColor(data.level) }} />
          <span>Weighted Composite Score</span>
        </Space>
      }
      style={{ marginBottom: 24 }}
    >
      {hasScore ? (
        <>
          <div style={{ textAlign: 'center', marginBottom: 20 }}>
            <Title level={2} style={{ margin: 0, color: getLevelColor(data.level) }}>
              {data.compositeScore} / {data.maxScore}
            </Title>
            <Text type="secondary" style={{ fontSize: 16 }}>
              {data.compositePercentage}%
            </Text>
            <div style={{ marginTop: 8 }}>
              <Tag color={getLevelColor(data.level)} style={{ fontSize: 14, padding: '2px 12px' }}>
                {data.level?.replace('_', ' ')}
              </Tag>
            </div>
          </div>

          <div style={{ display: 'flex', gap: 16, flexWrap: 'wrap' }}>
            {data.components.map((comp) => (
              <div
                key={comp.method}
                style={{
                  flex: '1 1 180px',
                  background: comp.available ? '#fafafa' : '#f5f5f5',
                  border: `1px solid ${comp.available ? getMethodColor(comp.method) + '40' : '#e8e8e8'}`,
                  borderRadius: 8,
                  padding: '12px 16px',
                  opacity: comp.available ? 1 : 0.6,
                }}
              >
                <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: 8 }}>
                  <Text strong style={{ color: getMethodColor(comp.method) }}>
                    {getMethodLabel(comp.method)}
                  </Text>
                  <Tooltip title={`Effective weight: ${comp.weight.toFixed(1)}%`}>
                    <Tag style={{ margin: 0 }}>{comp.weight.toFixed(0)}%</Tag>
                  </Tooltip>
                </div>
                {comp.available ? (
                  <>
                    <Progress
                      percent={Math.round(comp.percentage!)}
                      size="small"
                      strokeColor={getMethodColor(comp.method)}
                      format={() => `${comp.rawScore}/${comp.rawMaxScore}`}
                    />
                    <Text type="secondary" style={{ fontSize: 11 }}>
                      Contributes {comp.weightedContribution?.toFixed(1)}% to final
                    </Text>
                  </>
                ) : (
                  <Text type="secondary" style={{ fontStyle: 'italic' }}>
                    Not yet available
                  </Text>
                )}
              </div>
            ))}
          </div>
        </>
      ) : (
        <Text type="secondary">
          No evaluations available yet. Run an evaluation or add a tutor review to see the composite score.
        </Text>
      )}
    </Card>
  );
}
