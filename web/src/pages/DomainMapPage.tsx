import { useEffect, useState } from 'react';
import {
  Card,
  Tag,
  Button,
  Typography,
  Row,
  Col,
  Spin,
  Result,
  message,
  Progress,
  List,
  Badge,
} from 'antd';
import {
  CheckOutlined,
  ExclamationCircleOutlined,
  LoadingOutlined,
} from '@ant-design/icons';
import { useParams, useNavigate } from 'react-router-dom';
import apiClient from '../api/client';

const { Title, Text, Paragraph } = Typography;

interface DomainCluster {
  domain: string;
  confidence: number;
  documents: string[];
  suggestedType: string;
  summaryPreview: string;
}

interface DomainMapResponse {
  submissionId: string;
  clusters: DomainCluster[];
}

function confidenceTag(confidence: number) {
  if (confidence >= 0.8) {
    return <Tag color="green">高置信度</Tag>;
  }
  if (confidence >= 0.5) {
    return <Tag color="orange">中置信度</Tag>;
  }
  return <Tag color="red">低置信度</Tag>;
}

function typeLabel(type: string) {
  switch (type?.toLowerCase()) {
    case 'knowledge':
      return <Tag color="blue">知识型</Tag>;
    case 'procedure':
      return <Tag color="purple">流程型</Tag>;
    default:
      return <Tag>{type || '未知'}</Tag>;
  }
}

export default function DomainMapPage() {
  const { id } = useParams<{ id: string }>();
  const navigate = useNavigate();
  const [loading, setLoading] = useState(true);
  const [clusters, setClusters] = useState<DomainCluster[]>([]);
  const [error, setError] = useState<string | null>(null);
  const [confirming, setConfirming] = useState(false);
  const [confirmed, setConfirmed] = useState(false);
  const [generateProgress, setGenerateProgress] = useState(0);

  useEffect(() => {
    if (!id) return;
    fetchDomainMap();
  }, [id]);

  const fetchDomainMap = async () => {
    setLoading(true);
    try {
      const res = await apiClient.get<{ success: boolean; data: DomainMapResponse }>(
        `/submissions/${id}/domain-map`
      );
      setClusters(res.data.data.clusters || []);
    } catch (err: unknown) {
      const msg = err instanceof Error ? err.message : '获取领域映射失败';
      setError(msg);
    } finally {
      setLoading(false);
    }
  };

  const handleConfirm = async () => {
    if (!id) return;
    setConfirming(true);
    setGenerateProgress(10);

    try {
      const progressInterval = setInterval(() => {
        setGenerateProgress((prev) => {
          if (prev >= 90) {
            clearInterval(progressInterval);
            return 90;
          }
          return prev + Math.random() * 15;
        });
      }, 1000);

      await apiClient.post(`/submissions/${id}/confirm`, {
        clusters,
      });

      clearInterval(progressInterval);
      setGenerateProgress(100);
      setConfirmed(true);
      message.success('技能生成完成');
    } catch (err: unknown) {
      const msg = err instanceof Error ? err.message : '确认失败';
      message.error(msg);
    } finally {
      setConfirming(false);
    }
  };

  if (loading) {
    return (
      <div style={{ textAlign: 'center', padding: 80 }}>
        <Spin size="large" tip="加载领域映射..." />
      </div>
    );
  }

  if (error) {
    return (
      <Result
        status="error"
        title="加载失败"
        subTitle={error}
        extra={
          <Button onClick={() => navigate('/submit')}>返回提交页</Button>
        }
      />
    );
  }

  if (confirmed) {
    return (
      <Result
        status="success"
        title="技能生成完成"
        subTitle={`共处理 ${clusters.length} 个领域分组`}
        extra={[
          <Button key="skills" type="primary" onClick={() => navigate('/skills')}>
            查看技能列表
          </Button>,
          <Button key="submit" onClick={() => navigate('/submit')}>
            继续提交
          </Button>,
        ]}
      />
    );
  }

  const lowConfidenceCount = clusters.filter((c) => c.confidence < 0.5).length;

  return (
    <div style={{ maxWidth: 1000, margin: '0 auto' }}>
      <div style={{ marginBottom: 24, display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
        <div>
          <Title level={3} style={{ margin: 0 }}>领域映射确认</Title>
          <Text type="secondary">
            共 {clusters.length} 个领域分组
            {lowConfidenceCount > 0 && (
              <span style={{ marginLeft: 8 }}>
                <Badge count={lowConfidenceCount} style={{ backgroundColor: '#ff4d4f' }} />
                <Text type="danger" style={{ marginLeft: 4 }}>个低置信度项</Text>
              </span>
            )}
          </Text>
        </div>
        <Button
          type="primary"
          size="large"
          icon={confirming ? <LoadingOutlined /> : <CheckOutlined />}
          loading={confirming}
          onClick={handleConfirm}
          disabled={clusters.length === 0}
        >
          确认并生成技能
        </Button>
      </div>

      {confirming && (
        <Card style={{ marginBottom: 24 }}>
          <Title level={5}>技能生成中...</Title>
          <Progress percent={Math.round(generateProgress)} status="active" />
        </Card>
      )}

      <Row gutter={[16, 16]}>
        {clusters.map((cluster, index) => (
          <Col xs={24} sm={12} lg={8} key={index}>
            <Card
              title={
                <span>
                  {cluster.domain}
                </span>
              }
              extra={confidenceTag(cluster.confidence)}
              style={{
                height: '100%',
                borderColor: cluster.confidence < 0.5 ? '#ff4d4f' : undefined,
              }}
              hoverable
            >
              <div style={{ marginBottom: 8 }}>
                {typeLabel(cluster.suggestedType)}
                <Tag>{cluster.documents?.length || 0} 篇文档</Tag>
              </div>

              {cluster.confidence < 0.5 && (
                <div style={{ marginBottom: 8 }}>
                  <Text type="danger">
                    <ExclamationCircleOutlined style={{ marginRight: 4 }} />
                    低置信度，请关注
                  </Text>
                </div>
              )}

              {cluster.summaryPreview && (
                <Paragraph
                  ellipsis={{ rows: 3 }}
                  style={{ color: '#666', marginBottom: 8 }}
                >
                  {cluster.summaryPreview}
                </Paragraph>
              )}

              <List
                size="small"
                dataSource={cluster.documents || []}
                renderItem={(doc) => (
                  <List.Item style={{ padding: '4px 0', border: 'none' }}>
                    <Text ellipsis style={{ fontSize: 12, color: '#999' }}>
                      {doc}
                    </Text>
                  </List.Item>
                )}
              />
            </Card>
          </Col>
        ))}
      </Row>
    </div>
  );
}
