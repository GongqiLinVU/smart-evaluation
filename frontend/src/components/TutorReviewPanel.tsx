import { useState } from 'react';
import {
  Card,
  Form,
  Input,
  InputNumber,
  Button,
  Space,
  Typography,
  Tag,
  Progress,
  Divider,
  Popconfirm,
  message,
} from 'antd';
import {
  PlusOutlined,
  DeleteOutlined,
  EditOutlined,
  UserOutlined,
} from '@ant-design/icons';
import dayjs from 'dayjs';
import {
  saveTutorReview,
  addTutorReviewDimension,
  removeTutorReviewDimension,
} from '../api/client';
import type { TutorReviewResponse } from '../types';

const { Title, Text } = Typography;
const { TextArea } = Input;

const DEFAULT_DIMENSIONS = [
  { dimensionName: 'Presentation & Demo', maxScore: 10 },
  { dimensionName: 'Iterative Improvement', maxScore: 10 },
  { dimensionName: 'Group Contribution', maxScore: 10 },
];

interface Props {
  submissionId: number;
  review: TutorReviewResponse | null;
  canEdit: boolean;
  onRefresh: () => void;
}

export default function TutorReviewPanel({
  submissionId,
  review,
  canEdit,
  onRefresh,
}: Props) {
  const [editing, setEditing] = useState(false);
  const [addingDimension, setAddingDimension] = useState(false);
  const [form] = Form.useForm();
  const [dimForm] = Form.useForm();
  const [loading, setLoading] = useState(false);

  const handleSave = async (values: {
    overallScore: number;
    overallComment: string;
    dimensions: {
      dimensionName: string;
      score: number;
      maxScore: number;
      justification: string;
    }[];
  }) => {
    setLoading(true);
    try {
      await saveTutorReview(submissionId, {
        overallScore: values.overallScore,
        overallComment: values.overallComment,
        dimensions: values.dimensions,
      });
      message.success('Tutor review saved');
      setEditing(false);
      onRefresh();
    } catch {
      message.error('Failed to save review');
    } finally {
      setLoading(false);
    }
  };

  const handleAddDimension = async (values: {
    dimensionName: string;
    score: number;
    maxScore: number;
    justification: string;
  }) => {
    setLoading(true);
    try {
      await addTutorReviewDimension(submissionId, values);
      message.success('Dimension added');
      dimForm.resetFields();
      setAddingDimension(false);
      onRefresh();
    } catch {
      message.error('Failed to add dimension');
    } finally {
      setLoading(false);
    }
  };

  const handleRemoveDimension = async (dimensionId: number) => {
    try {
      await removeTutorReviewDimension(submissionId, dimensionId);
      message.success('Dimension removed');
      onRefresh();
    } catch {
      message.error('Failed to remove dimension');
    }
  };

  const startEditing = () => {
    if (review) {
      form.setFieldsValue({
        overallScore: review.overallScore,
        overallComment: review.overallComment,
        dimensions: review.dimensions.map((d) => ({
          dimensionName: d.dimensionName,
          score: d.score,
          maxScore: d.maxScore,
          justification: d.justification,
        })),
      });
    } else {
      form.setFieldsValue({
        overallScore: undefined,
        overallComment: '',
        dimensions: DEFAULT_DIMENSIONS.map((d) => ({
          dimensionName: d.dimensionName,
          score: 0,
          maxScore: d.maxScore,
          justification: '',
        })),
      });
    }
    setEditing(true);
  };

  return (
    <Card
      title={
        <Space>
          <UserOutlined />
          <span>Tutor Review</span>
          {review && <Tag color="gold">{review.overallScore}/30</Tag>}
        </Space>
      }
      style={{ marginBottom: 24 }}
      extra={
        canEdit && !editing ? (
          <Button
            type="primary"
            icon={review ? <EditOutlined /> : <PlusOutlined />}
            onClick={startEditing}
          >
            {review ? 'Edit Review' : 'Create Review'}
          </Button>
        ) : undefined
      }
    >
      {/* Display mode */}
      {review && !editing && (
        <div>
          <Space style={{ marginBottom: 12 }}>
            <Text type="secondary">
              Reviewed by <Text strong>{review.tutorName}</Text> on{' '}
              {dayjs(review.reviewedAt).format('YYYY-MM-DD HH:mm')}
            </Text>
          </Space>

          {review.overallComment && (
            <div style={{ marginBottom: 16 }}>
              <Text>{review.overallComment}</Text>
            </div>
          )}

          <div style={{ display: 'flex', gap: 12, flexWrap: 'wrap', marginBottom: 16 }}>
            {review.dimensions.map((dim) => (
              <div
                key={dim.id}
                style={{
                  background: '#fafafa',
                  border: '1px solid #f0f0f0',
                  borderRadius: 8,
                  padding: '12px 16px',
                  minWidth: 200,
                  flex: '1 1 200px',
                  maxWidth: 300,
                }}
              >
                <div
                  style={{
                    display: 'flex',
                    justifyContent: 'space-between',
                    alignItems: 'center',
                  }}
                >
                  <Text strong style={{ fontSize: 13 }}>
                    {dim.dimensionName}
                  </Text>
                  {canEdit && (
                    <Popconfirm
                      title="Remove this dimension?"
                      onConfirm={() => handleRemoveDimension(dim.id)}
                    >
                      <Button
                        type="text"
                        size="small"
                        danger
                        icon={<DeleteOutlined />}
                      />
                    </Popconfirm>
                  )}
                </div>
                <Progress
                  percent={Math.round((dim.score / dim.maxScore) * 100)}
                  size="small"
                  format={() => `${dim.score}/${dim.maxScore}`}
                />
                {dim.justification && (
                  <Text type="secondary" style={{ fontSize: 12 }}>
                    {dim.justification}
                  </Text>
                )}
              </div>
            ))}
          </div>

          {/* Add custom dimension */}
          {canEdit && !addingDimension && (
            <Button
              type="dashed"
              icon={<PlusOutlined />}
              onClick={() => setAddingDimension(true)}
              size="small"
            >
              Add Dimension
            </Button>
          )}

          {canEdit && addingDimension && (
            <>
              <Divider style={{ margin: '12px 0' }} />
              <Form
                form={dimForm}
                layout="inline"
                onFinish={handleAddDimension}
                style={{ flexWrap: 'wrap', gap: 8 }}
                initialValues={{ maxScore: 10 }}
              >
                <Form.Item
                  name="dimensionName"
                  rules={[{ required: true, message: 'Name required' }]}
                >
                  <Input placeholder="Dimension name" style={{ width: 180 }} />
                </Form.Item>
                <Form.Item
                  name="score"
                  rules={[{ required: true, message: 'Score required' }]}
                >
                  <InputNumber min={0} max={10} placeholder="Score" />
                </Form.Item>
                <Form.Item
                  name="maxScore"
                  rules={[{ required: true }]}
                >
                  <InputNumber min={1} max={10} placeholder="Max" />
                </Form.Item>
                <Form.Item name="justification">
                  <Input placeholder="Justification" style={{ width: 200 }} />
                </Form.Item>
                <Form.Item>
                  <Space>
                    <Button
                      type="primary"
                      htmlType="submit"
                      size="small"
                      loading={loading}
                    >
                      Add
                    </Button>
                    <Button size="small" onClick={() => setAddingDimension(false)}>
                      Cancel
                    </Button>
                  </Space>
                </Form.Item>
              </Form>
            </>
          )}
        </div>
      )}

      {/* Empty state */}
      {!review && !editing && (
        <Text type="secondary">
          No tutor review yet.
          {canEdit && ' Click "Create Review" to provide structured human assessment.'}
        </Text>
      )}

      {/* Edit/Create form */}
      {editing && (
        <>
          <Title level={5}>{review ? 'Edit' : 'Create'} Tutor Review</Title>
          <Form form={form} layout="vertical" onFinish={handleSave}>
            <Form.List name="dimensions">
              {(fields, { add, remove }) => (
                <div style={{ marginBottom: 16 }}>
                  {fields.map((field) => (
                    <Card
                      key={field.key}
                      size="small"
                      style={{ marginBottom: 8 }}
                      extra={
                        fields.length > 1 && (
                          <Button
                            type="text"
                            danger
                            size="small"
                            icon={<DeleteOutlined />}
                            onClick={() => remove(field.name)}
                          />
                        )
                      }
                    >
                      <Space align="start" style={{ width: '100%' }} wrap>
                        <Form.Item
                          name={[field.name, 'dimensionName']}
                          label="Dimension"
                          rules={[{ required: true, message: 'Required' }]}
                          style={{ marginBottom: 0 }}
                        >
                          <Input style={{ width: 180 }} />
                        </Form.Item>
                        <Form.Item
                          name={[field.name, 'score']}
                          label="Score"
                          rules={[{ required: true, message: 'Required' }]}
                          style={{ marginBottom: 0 }}
                        >
                          <InputNumber min={0} max={10} />
                        </Form.Item>
                        <Form.Item
                          name={[field.name, 'maxScore']}
                          label="Max"
                          rules={[{ required: true, message: 'Required' }]}
                          style={{ marginBottom: 0 }}
                        >
                          <InputNumber min={1} max={10} />
                        </Form.Item>
                        <Form.Item
                          name={[field.name, 'justification']}
                          label="Justification"
                          style={{ marginBottom: 0, flex: 1, minWidth: 200 }}
                        >
                          <Input placeholder="Brief reasoning..." />
                        </Form.Item>
                      </Space>
                    </Card>
                  ))}
                  <Button
                    type="dashed"
                    onClick={() =>
                      add({ dimensionName: '', score: 0, maxScore: 10, justification: '' })
                    }
                    icon={<PlusOutlined />}
                    style={{ width: '100%' }}
                  >
                    Add Dimension
                  </Button>
                </div>
              )}
            </Form.List>

            <Form.Item
              name="overallScore"
              label="Overall Score (0-30)"
              rules={[{ required: true, message: 'Please provide an overall score' }]}
            >
              <InputNumber min={0} max={30} style={{ width: 120 }} />
            </Form.Item>

            <Form.Item name="overallComment" label="Overall Comment">
              <TextArea
                rows={3}
                placeholder="Overall assessment: demo quality, effort, group dynamics, growth over the semester..."
              />
            </Form.Item>

            <Space>
              <Button type="primary" htmlType="submit" loading={loading}>
                Save Review
              </Button>
              <Button onClick={() => setEditing(false)}>Cancel</Button>
            </Space>
          </Form>
        </>
      )}
    </Card>
  );
}
