import { BrowserRouter, Routes, Route, useNavigate, useLocation } from 'react-router-dom';
import { ConfigProvider, Layout, Menu, Typography } from 'antd';
import {
  DashboardOutlined,
  UploadOutlined,
  ReadOutlined,
} from '@ant-design/icons';
import zhCN from 'antd/locale/zh_CN';
import DashboardPage from './pages/DashboardPage';
import SubmitPage from './pages/SubmitPage';
import DomainMapPage from './pages/DomainMapPage';
import SkillListPage from './pages/SkillListPage';
import SkillDetailPage from './pages/SkillDetailPage';

const { Header, Sider, Content } = Layout;
const { Title } = Typography;

function AppLayout() {
  const navigate = useNavigate();
  const location = useLocation();

  const selectedKey = (() => {
    const path = location.pathname;
    if (path === '/') return '/';
    if (path.startsWith('/submit')) return '/submit';
    if (path.startsWith('/skills')) return '/skills';
    if (path.startsWith('/domain-map')) return '/submit';
    return '/';
  })();

  const menuItems = [
    {
      key: '/',
      icon: <DashboardOutlined />,
      label: '仪表板',
    },
    {
      key: '/submit',
      icon: <UploadOutlined />,
      label: '提交文档',
    },
    {
      key: '/skills',
      icon: <ReadOutlined />,
      label: '技能列表',
    },
  ];

  return (
    <Layout style={{ minHeight: '100vh' }}>
      <Sider
        breakpoint="lg"
        collapsedWidth="0"
        style={{ background: '#fff' }}
      >
        <div style={{ padding: '16px 24px', borderBottom: '1px solid #f0f0f0' }}>
          <Title level={4} style={{ margin: 0, whiteSpace: 'nowrap' }}>
            ZM Skill Collector
          </Title>
        </div>
        <Menu
          mode="inline"
          selectedKeys={[selectedKey]}
          items={menuItems}
          onClick={({ key }) => navigate(key)}
          style={{ borderRight: 0 }}
        />
      </Sider>
      <Layout>
        <Header style={{
          background: '#fff',
          padding: '0 24px',
          borderBottom: '1px solid #f0f0f0',
          display: 'flex',
          alignItems: 'center',
        }}>
          <Typography.Text type="secondary">
            团队知识技能管理平台
          </Typography.Text>
        </Header>
        <Content style={{ margin: 24, padding: 24, background: '#fff', borderRadius: 8, minHeight: 360 }}>
          <Routes>
            <Route path="/" element={<DashboardPage />} />
            <Route path="/submit" element={<SubmitPage />} />
            <Route path="/domain-map/:id" element={<DomainMapPage />} />
            <Route path="/skills" element={<SkillListPage />} />
            <Route path="/skills/:name" element={<SkillDetailPage />} />
          </Routes>
        </Content>
      </Layout>
    </Layout>
  );
}

function App() {
  return (
    <ConfigProvider locale={zhCN}>
      <BrowserRouter>
        <AppLayout />
      </BrowserRouter>
    </ConfigProvider>
  );
}

export default App;
