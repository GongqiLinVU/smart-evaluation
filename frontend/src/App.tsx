import { Routes, Route, Link, useLocation, Navigate } from 'react-router-dom';
import { Layout, Menu, Typography, Button, Space, Tag, Dropdown } from 'antd';
import {
  UploadOutlined,
  UnorderedListOutlined,
  DashboardOutlined,
  UserOutlined,
  TeamOutlined,
  LogoutOutlined,
  ProjectOutlined,
  SettingOutlined,
  ExperimentOutlined,
  RobotOutlined,
  ThunderboltOutlined,
} from '@ant-design/icons';
import type { MenuProps } from 'antd';
import { useAuth } from './context/AuthContext';
import LoginPage from './pages/LoginPage';
import RegisterPage from './pages/RegisterPage';
import DashboardPage from './pages/DashboardPage';
import TutorDashboardPage from './pages/TutorDashboardPage';
import AdminDashboardPage from './pages/AdminDashboardPage';
import UploadPage from './pages/UploadPage';
import SubmissionListPage from './pages/SubmissionListPage';
import ResultPage from './pages/ResultPage';
import ProfilePage from './pages/ProfilePage';
import AdminUsersPage from './pages/AdminUsersPage';
import ProjectsPage from './pages/ProjectsPage';
import ScoringSettingsPage from './pages/ScoringSettingsPage';
import RulePackagesPage from './pages/RulePackagesPage';
import LlmConfigPage from './pages/LlmConfigPage';
import BatchAssessmentPage from './pages/BatchAssessmentPage';

const { Header, Content } = Layout;
const { Title } = Typography;

function getRoleColor(role: string): string {
  const upper = role.toUpperCase();
  if (upper === 'ADMIN') return 'red';
  if (upper === 'TUTOR') return 'purple';
  return 'blue';
}

function AuthenticatedApp() {
  const location = useLocation();
  const { user, logout, isAdmin, isTutor, isStudent } = useAuth();

  const selectedKey = location.pathname.startsWith('/batch')
    ? '/batch'
    : location.pathname.startsWith('/submissions')
      ? '/submissions'
      : location.pathname.startsWith('/upload')
        ? '/upload'
        : location.pathname.startsWith('/projects')
          ? '/projects'
          : location.pathname.startsWith('/admin/rules')
            ? '/admin/rules'
            : location.pathname.startsWith('/admin/llm-config')
              ? '/admin/llm-config'
              : location.pathname.startsWith('/admin/scoring')
                ? '/admin/scoring'
                : location.pathname.startsWith('/admin')
                  ? '/admin/users'
                  : '/';

  const menuItems: MenuProps['items'] = [
    {
      key: '/',
      icon: <DashboardOutlined />,
      label: <Link to="/">Dashboard</Link>,
    },
  ];

  if (isStudent) {
    menuItems.push({
      key: '/upload',
      icon: <UploadOutlined />,
      label: <Link to="/upload">Upload</Link>,
    });
  }

  if (isAdmin || isTutor) {
    menuItems.push({
      key: '/projects',
      icon: <ProjectOutlined />,
      label: <Link to="/projects">Projects</Link>,
    });
    menuItems.push({
      key: '/submissions',
      icon: <UnorderedListOutlined />,
      label: <Link to="/submissions">All Submissions</Link>,
    });
    menuItems.push({
      key: '/admin/rules',
      icon: <ExperimentOutlined />,
      label: <Link to="/admin/rules">Rules</Link>,
    });
    menuItems.push({
      key: '/admin/llm-config',
      icon: <RobotOutlined />,
      label: <Link to="/admin/llm-config">LLM Config</Link>,
    });
    menuItems.push({
      key: '/batch',
      icon: <ThunderboltOutlined />,
      label: <Link to="/batch">Batch</Link>,
    });
  }

  if (isAdmin) {
    menuItems.push({
      key: '/admin/users',
      icon: <TeamOutlined />,
      label: <Link to="/admin/users">Users</Link>,
    });
    menuItems.push({
      key: '/admin/scoring',
      icon: <SettingOutlined />,
      label: <Link to="/admin/scoring">Scoring</Link>,
    });
  }

  const userMenuItems: MenuProps['items'] = [
    {
      key: 'profile',
      icon: <UserOutlined />,
      label: <Link to="/profile">My Profile</Link>,
    },
    { type: 'divider' },
    {
      key: 'logout',
      icon: <LogoutOutlined />,
      label: 'Sign Out',
      onClick: logout,
    },
  ];

  return (
    <Layout style={{ minHeight: '100vh' }}>
      <Header
        style={{
          display: 'flex',
          alignItems: 'center',
          background: '#001529',
          padding: '0 24px',
        }}
      >
        <Link to="/" style={{ textDecoration: 'none' }}>
          <Title
            level={4}
            style={{
              color: '#fff',
              margin: 0,
              marginRight: 40,
              whiteSpace: 'nowrap',
            }}
          >
            Capstone Evaluator
          </Title>
        </Link>
        <Menu
          theme="dark"
          mode="horizontal"
          selectedKeys={[selectedKey]}
          style={{ flex: 1, minWidth: 0 }}
          items={menuItems}
        />
        <Space>
          <Tag color={getRoleColor(user?.role ?? '')}>{user?.role}</Tag>
          <Dropdown menu={{ items: userMenuItems }} placement="bottomRight">
            <Button type="text" style={{ color: '#fff' }}>
              <UserOutlined /> {user?.fullName}
            </Button>
          </Dropdown>
        </Space>
      </Header>
      <Content style={{ padding: '24px 48px', background: '#f5f5f5' }}>
        <Routes>
          <Route
            path="/"
            element={
              isAdmin ? (
                <AdminDashboardPage />
              ) : isTutor ? (
                <TutorDashboardPage />
              ) : (
                <DashboardPage />
              )
            }
          />
          {isStudent && (
            <Route path="/upload" element={<UploadPage />} />
          )}
          {(isAdmin || isTutor) && (
            <Route path="/projects" element={<ProjectsPage />} />
          )}
          {(isAdmin || isTutor) && (
            <Route path="/submissions" element={<SubmissionListPage />} />
          )}
          {(isAdmin || isTutor) && (
            <Route path="/admin/rules" element={<RulePackagesPage />} />
          )}
          {(isAdmin || isTutor) && (
            <Route path="/admin/llm-config" element={<LlmConfigPage />} />
          )}
          {(isAdmin || isTutor) && (
            <Route path="/batch" element={<BatchAssessmentPage />} />
          )}
          <Route path="/submissions/:id" element={<ResultPage />} />
          <Route path="/profile" element={<ProfilePage />} />
          {isAdmin && (
            <Route path="/admin/users" element={<AdminUsersPage />} />
          )}
          {isAdmin && (
            <Route path="/admin/scoring" element={<ScoringSettingsPage />} />
          )}
          <Route path="*" element={<Navigate to="/" replace />} />
        </Routes>
      </Content>
    </Layout>
  );
}

function App() {
  const { token } = useAuth();

  if (!token) {
    return (
      <Routes>
        <Route path="/login" element={<LoginPage />} />
        <Route path="/register" element={<RegisterPage />} />
        <Route path="*" element={<Navigate to="/login" replace />} />
      </Routes>
    );
  }

  return <AuthenticatedApp />;
}

export default App;
