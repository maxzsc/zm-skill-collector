import { useEffect, useState } from 'react';
import {
  Card,
  Tag,
  Button,
  Space,
  Typography,
  Descriptions,
  Divider,
  Spin,
  Result,
  message,
} from 'antd';
import {
  LikeOutlined,
  WarningOutlined,
  ClockCircleOutlined,
  ArrowLeftOutlined,
} from '@ant-design/icons';
import { useParams, useNavigate } from 'react-router-dom';
import apiClient from '../api/client';

const { Title, Paragraph } = Typography;

interface SkillMeta {
  name: string;
  type: string;
  domain: string;
  trigger: string;
  summary: string;
  completeness: string;
  visibility: string;
  sources: string[];
  related_skills: string[];
  aliases: string[];
  category: string;
  agent_readiness: string;
  related_knowledge: string[];
}

interface SkillDocument {
  meta: SkillMeta;
  body: string;
}

export default function SkillDetailPage() {
  const { name } = useParams<{ name: string }>();
  const navigate = useNavigate();
  const [skill, setSkill] = useState<SkillDocument | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [feedbackSending, setFeedbackSending] = useState<string | null>(null);

  useEffect(() => {
    if (!name) return;
    fetchSkill();
  }, [name]);

  const fetchSkill = async () => {
    setLoading(true);
    setError(null);
    try {
      const res = await apiClient.get<{ success: boolean; data: SkillDocument }>(
        `/skills/${encodeURIComponent(name!)}`
      );
      setSkill(res.data.data);
    } catch (err: unknown) {
      const msg = err instanceof Error ? err.message : '加载技能详情失败';
      setError(msg);
    } finally {
      setLoading(false);
    }
  };

  const sendFeedback = async (rating: string) => {
    if (!name) return;
    setFeedbackSending(rating);
    try {
      await apiClient.post('/feedback', {
        skillName: name,
        rating,
      });
      message.success('反馈已提交');
    } catch (err: unknown) {
      const msg = err instanceof Error ? err.message : '反馈提交失败';
      message.error(msg);
    } finally {
      setFeedbackSending(null);
    }
  };

  if (loading) {
    return (
      <div style={{ textAlign: 'center', padding: 80 }}>
        <Spin size="large" />
      </div>
    );
  }

  if (error || !skill) {
    return (
      <Result
        status="error"
        title="加载失败"
        subTitle={error || '技能未找到'}
        extra={
          <Button onClick={() => navigate('/skills')}>返回列表</Button>
        }
      />
    );
  }

  const { meta, body } = skill;

  const completenessColor: Record<string, string> = {
    complete: 'green',
    COMPLETE: 'green',
    partial: 'orange',
    PARTIAL: 'orange',
    stub: 'red',
    STUB: 'red',
  };

  const completenessLabel: Record<string, string> = {
    complete: '完整',
    COMPLETE: '完整',
    partial: '部分',
    PARTIAL: '部分',
    stub: '存根',
    STUB: '存根',
  };

  return (
    <div style={{ maxWidth: 900, margin: '0 auto' }}>
      <Button
        icon={<ArrowLeftOutlined />}
        onClick={() => navigate('/skills')}
        style={{ marginBottom: 16 }}
      >
        返回列表
      </Button>

      <Card>
        <Title level={3}>{meta.name}</Title>

        <Descriptions column={{ xs: 1, sm: 2, md: 3 }} bordered size="small">
          <Descriptions.Item label="领域">
            {meta.domain ? <Tag color="blue">{meta.domain}</Tag> : '-'}
          </Descriptions.Item>
          <Descriptions.Item label="类型">
            {meta.type === 'knowledge' || meta.type === 'KNOWLEDGE'
              ? <Tag color="geekblue">知识型</Tag>
              : meta.type === 'procedure' || meta.type === 'PROCEDURE'
              ? <Tag color="purple">流程型</Tag>
              : <Tag>{meta.type || '-'}</Tag>}
          </Descriptions.Item>
          <Descriptions.Item label="完整度">
            <Tag color={completenessColor[meta.completeness] || 'default'}>
              {completenessLabel[meta.completeness] || meta.completeness || '-'}
            </Tag>
          </Descriptions.Item>
          <Descriptions.Item label="可见性">
            {meta.visibility || '-'}
          </Descriptions.Item>
          <Descriptions.Item label="触发条件" span={2}>
            {meta.trigger || '-'}
          </Descriptions.Item>
          <Descriptions.Item label="摘要" span={3}>
            {meta.summary || '-'}
          </Descriptions.Item>
          {meta.sources && meta.sources.length > 0 && (
            <Descriptions.Item label="来源" span={3}>
              <Space wrap>
                {meta.sources.map((src, i) => (
                  <Tag key={i}>{src}</Tag>
                ))}
              </Space>
            </Descriptions.Item>
          )}
          {meta.aliases && meta.aliases.length > 0 && (
            <Descriptions.Item label="别名" span={3}>
              <Space wrap>
                {meta.aliases.map((a, i) => (
                  <Tag key={i} color="cyan">{a}</Tag>
                ))}
              </Space>
            </Descriptions.Item>
          )}
          {meta.related_skills && meta.related_skills.length > 0 && (
            <Descriptions.Item label="关联技能" span={3}>
              <Space wrap>
                {meta.related_skills.map((rs, i) => (
                  <Tag
                    key={i}
                    color="geekblue"
                    style={{ cursor: 'pointer' }}
                    onClick={() => navigate(`/skills/${encodeURIComponent(rs)}`)}
                  >
                    {rs}
                  </Tag>
                ))}
              </Space>
            </Descriptions.Item>
          )}
        </Descriptions>

        <Divider />

        <Title level={5}>技能内容</Title>
        <Card
          type="inner"
          style={{
            backgroundColor: '#fafafa',
            maxHeight: 600,
            overflow: 'auto',
          }}
        >
          <pre style={{
            whiteSpace: 'pre-wrap',
            wordBreak: 'break-word',
            fontFamily: '-apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, monospace',
            fontSize: 14,
            lineHeight: 1.6,
            margin: 0,
          }}>
            {body || '(无内容)'}
          </pre>
        </Card>

        <Divider />

        <Title level={5}>反馈</Title>
        <Paragraph type="secondary">
          这个技能对你有帮助吗？你的反馈将帮助我们改进技能质量。
        </Paragraph>
        <Space>
          <Button
            icon={<LikeOutlined />}
            loading={feedbackSending === 'useful'}
            onClick={() => sendFeedback('useful')}
          >
            有用
          </Button>
          <Button
            icon={<WarningOutlined />}
            loading={feedbackSending === 'misleading'}
            onClick={() => sendFeedback('misleading')}
            danger
          >
            误导
          </Button>
          <Button
            icon={<ClockCircleOutlined />}
            loading={feedbackSending === 'outdated'}
            onClick={() => sendFeedback('outdated')}
          >
            过时
          </Button>
        </Space>
      </Card>
    </div>
  );
}
