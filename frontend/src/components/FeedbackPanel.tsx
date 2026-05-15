import { List, Typography, Alert } from 'antd';
import { CheckCircleOutlined, ExclamationCircleOutlined } from '@ant-design/icons';

const { Text } = Typography;

interface FeedbackPanelProps {
  strengths: string[];
  improvements: string[];
  overallFeedback: string;
}

export default function FeedbackPanel({
  strengths,
  improvements,
  overallFeedback,
}: FeedbackPanelProps) {
  return (
    <div>
      {strengths.length > 0 && (
        <div style={{ marginBottom: 24 }}>
          <Typography.Title level={5} style={{ color: '#52c41a' }}>
            <CheckCircleOutlined /> Strengths
          </Typography.Title>
          <List
            size="small"
            dataSource={strengths}
            renderItem={(item) => (
              <List.Item>
                <Text>
                  <CheckCircleOutlined style={{ color: '#52c41a', marginRight: 8 }} />
                  {item}
                </Text>
              </List.Item>
            )}
          />
        </div>
      )}

      {improvements.length > 0 && (
        <div style={{ marginBottom: 24 }}>
          <Typography.Title level={5} style={{ color: '#fa8c16' }}>
            <ExclamationCircleOutlined /> Areas for Improvement
          </Typography.Title>
          <List
            size="small"
            dataSource={improvements}
            renderItem={(item) => (
              <List.Item>
                <Text>
                  <ExclamationCircleOutlined style={{ color: '#fa8c16', marginRight: 8 }} />
                  {item}
                </Text>
              </List.Item>
            )}
          />
        </div>
      )}

      {overallFeedback && (
        <Alert
          message="Overall Feedback"
          description={overallFeedback}
          type="info"
          showIcon
          style={{ marginTop: 16 }}
        />
      )}
    </div>
  );
}
