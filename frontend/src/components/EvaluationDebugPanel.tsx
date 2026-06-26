import { useEffect, useState } from 'react';
import { Card, Collapse, Tag, Typography, Button, Space, message } from 'antd';
import { BugOutlined, CopyOutlined } from '@ant-design/icons';
import { getEvaluationRounds, type EvaluationRoundResponse } from '../api/client';

const { Text } = Typography;

interface Props {
  evaluationId: number;
}

function copyToClipboard(text: string, label: string) {
  navigator.clipboard.writeText(text).then(() => {
    message.success(`${label} copied to clipboard`);
  });
}

function PromptBlock({ title, content, label }: { title: string; content: string | null; label: string }) {
  if (!content) return null;
  return (
    <div style={{ marginBottom: 16 }}>
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: 4 }}>
        <Text strong>{title}</Text>
        <Button
          size="small"
          icon={<CopyOutlined />}
          onClick={() => copyToClipboard(content, label)}
        >
          Copy
        </Button>
      </div>
      <pre
        style={{
          background: '#1e1e1e',
          color: '#d4d4d4',
          padding: 12,
          borderRadius: 6,
          maxHeight: 400,
          overflow: 'auto',
          fontSize: 12,
          whiteSpace: 'pre-wrap',
          wordBreak: 'break-word',
        }}
      >
        {content}
      </pre>
    </div>
  );
}

export default function EvaluationDebugPanel({ evaluationId }: Props) {
  const [rounds, setRounds] = useState<EvaluationRoundResponse[] | null>(null);
  const [loading, setLoading] = useState(true);
  const [expanded, setExpanded] = useState(false);

  useEffect(() => {
    setLoading(true);
    getEvaluationRounds(evaluationId)
      .then(setRounds)
      .catch(() => setRounds([]))
      .finally(() => setLoading(false));
  }, [evaluationId]);

  if (loading || !rounds || rounds.length === 0) {
    return null;
  }

  const handleCopyAll = () => {
    const fullTrace = rounds.map((r) => {
      let text = `=== Round ${r.roundNumber} (${r.roundType}) - ${r.status} ===\n\n`;
      if (r.systemPrompt) text += `--- SYSTEM PROMPT ---\n${r.systemPrompt}\n\n`;
      if (r.userPrompt) text += `--- USER PROMPT ---\n${r.userPrompt}\n\n`;
      if (r.rawResponse) text += `--- RAW RESPONSE ---\n${r.rawResponse}\n\n`;
      text += `Tokens: prompt=${r.promptTokens ?? 0}, completion=${r.completionTokens ?? 0}\n`;
      return text;
    }).join('\n\n');
    copyToClipboard(fullTrace, 'Full evaluation trace');
  };

  return (
    <Card
      title={
        <Space>
          <BugOutlined />
          <span>Debug: LLM Evaluation Trace</span>
        </Space>
      }
      style={{ marginBottom: 24 }}
      extra={
        <Space>
          {rounds.length > 0 && (
            <Button size="small" icon={<CopyOutlined />} onClick={handleCopyAll}>
              Copy Full Trace
            </Button>
          )}
          <Button
            type={expanded ? 'default' : 'primary'}
            size="small"
            onClick={() => setExpanded(!expanded)}
          >
            {expanded ? 'Hide' : 'Show Debug Info'}
          </Button>
        </Space>
      }
    >
      {!expanded && (
        <Text type="secondary">
          Click &quot;Show Debug Info&quot; to view the system prompt, user prompt, and raw LLM response for each evaluation round.
        </Text>
      )}
      {expanded && (
        <Collapse
          items={rounds.map((round) => ({
            key: round.id,
            label: (
              <Space>
                <span>Round {round.roundNumber}</span>
                <Tag>{round.roundType}</Tag>
                <Tag color={round.status === 'SUCCESS' ? 'green' : 'red'}>{round.status}</Tag>
                {round.promptTokens != null && (
                  <Text type="secondary" style={{ fontSize: 12 }}>
                    {round.promptTokens} + {round.completionTokens} tokens
                  </Text>
                )}
              </Space>
            ),
            children: (
              <div>
                <PromptBlock
                  title="System Prompt"
                  content={round.systemPrompt}
                  label={`Round ${round.roundNumber} system prompt`}
                />
                <PromptBlock
                  title="User Prompt (Document + Instructions)"
                  content={round.userPrompt}
                  label={`Round ${round.roundNumber} user prompt`}
                />
                <PromptBlock
                  title="Raw LLM Response"
                  content={round.rawResponse}
                  label={`Round ${round.roundNumber} raw response`}
                />
              </div>
            ),
          }))}
        />
      )}
    </Card>
  );
}
