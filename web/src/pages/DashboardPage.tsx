import { useEffect, useState } from 'react';
import {
  Card,
  Row,
  Col,
  Statistic,
  Typography,
  List,
  Tag,
  Button,
  Spin,
} from 'antd';
import {
  BookOutlined,
  WarningOutlined,
  FileAddOutlined,
  RightOutlined,
} from '@ant-design/icons';
import { useNavigate } from 'react-router-dom';
import apiClient from '../api/client';

const { Title, Text } = Typography;

interface SkillMeta {
  name: string;
  type: string;
  domain: string;
  summary: string;
  completeness: string;
}

export default function DashboardPage() {
  const navigate = useNavigate();
  const [skills, setSkills] = useState<SkillMeta[]>([]);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    fetchData();
  }, []);

  const fetchData = async () => {
    setLoading(true);
    try {
      const res = await apiClient.get<{ success: boolean; data: SkillMeta[] }>('/skills');
      setSkills(res.data.data || []);
    } catch {
      // Dashboard gracefully handles errors
    } finally {
      setLoading(false);
    }
  };

  const totalSkills = skills.length;
  const staleSkills = skills.filter(
    (s) => s.completeness === 'stub' || s.completeness === 'STUB'
  ).length;
  const recentSkills = skills.slice(0, 10);

  if (loading) {
    return (
      <div style={{ textAlign: 'center', padding: 80 }}>
        <Spin size="large" />
      </div>
    );
  }

  return (
    <div>
      <Title level={3}>仪表板</Title>

      <Row gutter={[16, 16]} style={{ marginBottom: 24 }}>
        <Col xs={24} sm={8}>
          <Card hoverable onClick={() => navigate('/skills')}>
            <Statistic
              title="技能总数"
              value={totalSkills}
              prefix={<BookOutlined />}
              valueStyle={{ color: '#1677ff' }}
            />
          </Card>
        </Col>
        <Col xs={24} sm={8}>
          <Card hoverable onClick={() => navigate('/skills')}>
            <Statistic
              title="待完善技能"
              value={staleSkills}
              prefix={<WarningOutlined />}
              valueStyle={{ color: staleSkills > 0 ? '#ff4d4f' : '#52c41a' }}
            />
          </Card>
        </Col>
        <Col xs={24} sm={8}>
          <Card hoverable onClick={() => navigate('/submit')}>
            <Statistic
              title="提交文档"
              value="+"
              prefix={<FileAddOutlined />}
              valueStyle={{ color: '#1677ff' }}
            />
            <Text type="secondary">点击提交新文档</Text>
          </Card>
        </Col>
      </Row>

      <Card
        title="技能概览"
        extra={
          <Button type="link" onClick={() => navigate('/skills')}>
            查看全部 <RightOutlined />
          </Button>
        }
      >
        <List
          dataSource={recentSkills}
          renderItem={(skill) => (
            <List.Item
              style={{ cursor: 'pointer' }}
              onClick={() => navigate(`/skills/${encodeURIComponent(skill.name)}`)}
              actions={[
                skill.completeness && (
                  <Tag
                    key="comp"
                    color={
                      skill.completeness === 'complete' || skill.completeness === 'COMPLETE'
                        ? 'green'
                        : skill.completeness === 'partial' || skill.completeness === 'PARTIAL'
                        ? 'orange'
                        : 'red'
                    }
                  >
                    {skill.completeness === 'complete' || skill.completeness === 'COMPLETE'
                      ? '完整'
                      : skill.completeness === 'partial' || skill.completeness === 'PARTIAL'
                      ? '部分'
                      : '存根'}
                  </Tag>
                ),
              ]}
            >
              <List.Item.Meta
                title={
                  <span>
                    {skill.name}
                    {skill.domain && (
                      <Tag color="blue" style={{ marginLeft: 8 }}>
                        {skill.domain}
                      </Tag>
                    )}
                    {skill.type && (
                      <Tag
                        color={skill.type === 'knowledge' || skill.type === 'KNOWLEDGE' ? 'geekblue' : 'purple'}
                        style={{ marginLeft: 4 }}
                      >
                        {skill.type === 'knowledge' || skill.type === 'KNOWLEDGE' ? '知识型' : '流程型'}
                      </Tag>
                    )}
                  </span>
                }
                description={skill.summary || '-'}
              />
            </List.Item>
          )}
          locale={{ emptyText: '暂无技能数据，请先提交文档' }}
        />
      </Card>
    </div>
  );
}
