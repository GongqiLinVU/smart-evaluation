import { useEffect, useState } from 'react';
import {
  Card,
  Form,
  InputNumber,
  Button,
  Slider,
  Typography,
  Space,
  message,
  Alert,
  Spin,
} from 'antd';
import { SettingOutlined } from '@ant-design/icons';
import { getScoringWeights, updateScoringWeights } from '../api/client';
import type { ScoringWeightsResponse } from '../types';

const { Title, Text } = Typography;

export default function ScoringSettingsPage() {
  const [weights, setWeights] = useState<ScoringWeightsResponse | null>(null);
  const [loading, setLoading] = useState(true);
  const [saving, setSaving] = useState(false);
  const [form] = Form.useForm();

  useEffect(() => {
    getScoringWeights()
      .then((w) => {
        setWeights(w);
        form.setFieldsValue(w);
      })
      .catch(() => message.error('Failed to load scoring weights'))
      .finally(() => setLoading(false));
  }, [form]);

  const handleSave = async (values: {
    ruleBasedWeight: number;
    llmWeight: number;
    tutorWeight: number;
    maxScore: number;
  }) => {
    const total = values.ruleBasedWeight + values.llmWeight + values.tutorWeight;
    if (Math.abs(total - 100) > 0.01) {
      message.error(`Weights must sum to 100%. Current total: ${total}%`);
      return;
    }
    setSaving(true);
    try {
      const updated = await updateScoringWeights(values);
      setWeights(updated);
      message.success('Scoring weights updated');
    } catch (err: unknown) {
      const msg = (err as { response?: { data?: { message?: string } } })?.response?.data?.message;
      message.error(msg || 'Failed to update weights');
    } finally {
      setSaving(false);
    }
  };

  const onValuesChange = () => {
    const values = form.getFieldsValue();
    const total = (values.ruleBasedWeight || 0) + (values.llmWeight || 0) + (values.tutorWeight || 0);
    if (Math.abs(total - 100) > 0.01) {
      form.setFields([
        { name: 'ruleBasedWeight', errors: [`Sum: ${total}% (must be 100%)`] },
      ]);
    } else {
      form.setFields([{ name: 'ruleBasedWeight', errors: [] }]);
    }
  };

  if (loading) {
    return (
      <div style={{ textAlign: 'center', paddingTop: 60 }}>
        <Spin size="large" />
      </div>
    );
  }

  return (
    <div style={{ maxWidth: 700 }}>
      <Title level={3}>
        <SettingOutlined /> Scoring Configuration
      </Title>

      <Alert
        message="Weighted Composite Scoring"
        description="Configure how the final composite score is calculated from three evaluation methods. Weights must sum to 100%. When a component is unavailable, its weight is redistributed among available components."
        type="info"
        showIcon
        style={{ marginBottom: 24 }}
      />

      <Card>
        <Form
          form={form}
          layout="vertical"
          onFinish={handleSave}
          onValuesChange={onValuesChange}
          initialValues={weights || { ruleBasedWeight: 40, llmWeight: 30, tutorWeight: 30, maxScore: 100 }}
        >
          <Form.Item
            name="ruleBasedWeight"
            label="Rule-Based Evaluation Weight (%)"
            rules={[{ required: true }]}
          >
            <Slider min={0} max={100} marks={{ 0: '0%', 50: '50%', 100: '100%' }} />
          </Form.Item>
          <Form.Item style={{ marginTop: -16, marginBottom: 24 }}>
            <Form.Item name="ruleBasedWeight" noStyle>
              <InputNumber min={0} max={100} addonAfter="%" style={{ width: 120 }} />
            </Form.Item>
          </Form.Item>

          <Form.Item
            name="llmWeight"
            label="LLM Evaluation Weight (%)"
            rules={[{ required: true }]}
          >
            <Slider min={0} max={100} marks={{ 0: '0%', 50: '50%', 100: '100%' }} />
          </Form.Item>
          <Form.Item style={{ marginTop: -16, marginBottom: 24 }}>
            <Form.Item name="llmWeight" noStyle>
              <InputNumber min={0} max={100} addonAfter="%" style={{ width: 120 }} />
            </Form.Item>
          </Form.Item>

          <Form.Item
            name="tutorWeight"
            label="Tutor Review Weight (%)"
            rules={[{ required: true }]}
          >
            <Slider min={0} max={100} marks={{ 0: '0%', 50: '50%', 100: '100%' }} />
          </Form.Item>
          <Form.Item style={{ marginTop: -16, marginBottom: 24 }}>
            <Form.Item name="tutorWeight" noStyle>
              <InputNumber min={0} max={100} addonAfter="%" style={{ width: 120 }} />
            </Form.Item>
          </Form.Item>

          <Form.Item
            name="maxScore"
            label="Maximum Composite Score"
            rules={[{ required: true }]}
            extra="The composite percentage will be scaled to this maximum (e.g., 100 = percentage-based, 30 = rubric-based)"
          >
            <InputNumber min={1} max={200} style={{ width: 120 }} />
          </Form.Item>

          <Space>
            <Button type="primary" htmlType="submit" loading={saving}>
              Save Configuration
            </Button>
            <Text type="secondary">
              Current total: {
                ((form.getFieldValue('ruleBasedWeight') || 0) +
                 (form.getFieldValue('llmWeight') || 0) +
                 (form.getFieldValue('tutorWeight') || 0))
              }%
            </Text>
          </Space>
        </Form>
      </Card>
    </div>
  );
}
