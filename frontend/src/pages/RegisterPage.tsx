import { useState } from 'react';
import { useNavigate, Link } from 'react-router-dom';
import { Card, Form, Input, Button, Typography, Select, message } from 'antd';
import { UserOutlined, LockOutlined, MailOutlined } from '@ant-design/icons';
import { register } from '../api/client';
import { useAuth } from '../context/AuthContext';

const { Title, Text } = Typography;

export default function RegisterPage() {
  const [loading, setLoading] = useState(false);
  const navigate = useNavigate();
  const { setAuth } = useAuth();

  const handleSubmit = async (values: {
    email: string;
    password: string;
    fullName: string;
    academicYear?: string;
    semester?: string;
  }) => {
    setLoading(true);
    try {
      const res = await register(
        values.email,
        values.password,
        values.fullName,
        values.academicYear,
        values.semester,
      );
      setAuth(res.token, res.user);
      message.success('Registration successful!');
      navigate('/');
    } catch (err: any) {
      if (err?.response?.status === 409) {
        message.error('Email already registered');
      } else {
        message.error('Registration failed. Please try again.');
      }
    } finally {
      setLoading(false);
    }
  };

  return (
    <div
      style={{
        minHeight: '100vh',
        display: 'flex',
        alignItems: 'center',
        justifyContent: 'center',
        background: '#f0f2f5',
      }}
    >
      <Card style={{ width: 400 }}>
        <Title level={3} style={{ textAlign: 'center', marginBottom: 8 }}>
          Create Account
        </Title>
        <Text
          type="secondary"
          style={{ display: 'block', textAlign: 'center', marginBottom: 32 }}
        >
          Register as a student
        </Text>

        <Form layout="vertical" onFinish={handleSubmit} autoComplete="off">
          <Form.Item
            name="fullName"
            rules={[{ required: true, message: 'Please enter your full name' }]}
          >
            <Input
              prefix={<UserOutlined />}
              placeholder="Full Name"
              size="large"
            />
          </Form.Item>

          <Form.Item
            name="email"
            rules={[
              { required: true, message: 'Please enter your email' },
              { type: 'email', message: 'Please enter a valid email' },
            ]}
          >
            <Input
              prefix={<MailOutlined />}
              placeholder="Email"
              size="large"
            />
          </Form.Item>

          <Form.Item
            name="password"
            rules={[
              { required: true, message: 'Please enter a password' },
              { min: 6, message: 'Password must be at least 6 characters' },
            ]}
          >
            <Input.Password
              prefix={<LockOutlined />}
              placeholder="Password"
              size="large"
            />
          </Form.Item>

          <Form.Item name="academicYear" label="Academic Year">
            <Input placeholder="e.g. 2025/2026 (optional)" size="large" />
          </Form.Item>

          <Form.Item name="semester" label="Semester">
            <Select
              placeholder="Select semester (optional)"
              size="large"
              allowClear
              options={[
                { label: 'Semester 1', value: 'S1' },
                { label: 'Semester 2', value: 'S2' },
                { label: 'Summer', value: 'Summer' },
              ]}
            />
          </Form.Item>

          <Form.Item>
            <Button
              type="primary"
              htmlType="submit"
              size="large"
              block
              loading={loading}
            >
              Register
            </Button>
          </Form.Item>
        </Form>

        <div style={{ textAlign: 'center' }}>
          <Text>
            Already have an account? <Link to="/login">Sign In</Link>
          </Text>
        </div>
      </Card>
    </div>
  );
}
