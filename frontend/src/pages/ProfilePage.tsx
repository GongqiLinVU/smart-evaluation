import { useState } from 'react';
import {
  Card,
  Form,
  Input,
  Button,
  Typography,
  Descriptions,
  Tag,
  Divider,
  message,
} from 'antd';
import { useAuth } from '../context/AuthContext';
import { updateProfile } from '../api/client';

const { Title } = Typography;

function getRoleColor(role: string): string {
  const upper = role.toUpperCase();
  if (upper === 'ADMIN') return 'red';
  if (upper === 'TUTOR') return 'purple';
  return 'blue';
}

export default function ProfilePage() {
  const { user, refreshUser } = useAuth();
  const [loading, setLoading] = useState(false);
  const [form] = Form.useForm();

  const handleSubmit = async (values: {
    fullName: string;
    currentPassword?: string;
    newPassword?: string;
  }) => {
    setLoading(true);
    try {
      await updateProfile(
        values.fullName,
        values.currentPassword,
        values.newPassword,
      );
      await refreshUser();
      message.success('Profile updated!');
      form.resetFields(['currentPassword', 'newPassword']);
    } catch {
      message.error('Failed to update profile. Check your current password.');
    } finally {
      setLoading(false);
    }
  };

  if (!user) return null;

  return (
    <div style={{ maxWidth: 600, margin: '0 auto' }}>
      <Title level={3}>My Profile</Title>

      <Card style={{ marginBottom: 24 }}>
        <Descriptions bordered column={1}>
          <Descriptions.Item label="Email">{user.email}</Descriptions.Item>
          <Descriptions.Item label="Full Name">
            {user.fullName}
          </Descriptions.Item>
          <Descriptions.Item label="Role">
            <Tag color={getRoleColor(user.role)}>{user.role}</Tag>
          </Descriptions.Item>
        </Descriptions>
      </Card>

      <Card title="Update Profile">
        <Form
          form={form}
          layout="vertical"
          onFinish={handleSubmit}
          initialValues={{ fullName: user.fullName }}
        >
          <Form.Item
            label="Full Name"
            name="fullName"
            rules={[{ required: true, message: 'Please enter your name' }]}
          >
            <Input size="large" />
          </Form.Item>

          <Divider>Change Password (optional)</Divider>

          <Form.Item label="Current Password" name="currentPassword">
            <Input.Password size="large" placeholder="Enter current password" />
          </Form.Item>

          <Form.Item
            label="New Password"
            name="newPassword"
            rules={[
              {
                min: 6,
                message: 'Password must be at least 6 characters',
              },
            ]}
          >
            <Input.Password size="large" placeholder="Enter new password" />
          </Form.Item>

          <Form.Item>
            <Button
              type="primary"
              htmlType="submit"
              size="large"
              loading={loading}
            >
              Save Changes
            </Button>
          </Form.Item>
        </Form>
      </Card>
    </div>
  );
}
