import { useEffect, useState, useMemo } from 'react';
import {
  Table,
  Input,
  Select,
  Tag,
  Space,
  Typography,
  Card,
  message,
} from 'antd';
import { SearchOutlined } from '@ant-design/icons';
import { useNavigate } from 'react-router-dom';
import type { ColumnsType } from 'antd/es/table';
import apiClient from '../api/client';

const { Title } = Typography;

interface SkillMeta {
  name: string;
  type: string;
  domain: string;
  trigger: string;
  summary: string;
  completeness: string;
  sources: string[];
  related_skills: string[];
  visibility: string;
}

export default function SkillListPage() {
  const navigate = useNavigate();
  const [skills, setSkills] = useState<SkillMeta[]>([]);
  const [loading, setLoading] = useState(true);
  const [search, setSearch] = useState('');
  const [domainFilter, setDomainFilter] = useState<string | undefined>();
  const [typeFilter, setTypeFilter] = useState<string | undefined>();

  useEffect(() => {
    fetchSkills();
  }, []);

  const fetchSkills = async () => {
    setLoading(true);
    try {
      const res = await apiClient.get<{ success: boolean; data: SkillMeta[] }>('/skills');
      setSkills(res.data.data || []);
    } catch (err: unknown) {
      const msg = err instanceof Error ? err.message : '加载技能列表失败';
      message.error(msg);
    } finally {
      setLoading(false);
    }
  };

  const domains = useMemo(() => {
    const set = new Set(skills.map((s) => s.domain).filter(Boolean));
    return Array.from(set).sort();
  }, [skills]);

  const filtered = useMemo(() => {
    return skills.filter((s) => {
      if (search && !s.name.toLowerCase().includes(search.toLowerCase()) &&
          !s.summary?.toLowerCase().includes(search.toLowerCase()) &&
          !s.domain?.toLowerCase().includes(search.toLowerCase())) {
        return false;
      }
      if (domainFilter && s.domain !== domainFilter) return false;
      if (typeFilter && s.type !== typeFilter) return false;
      return true;
    });
  }, [skills, search, domainFilter, typeFilter]);

  const isStale = (skill: SkillMeta) => {
    return skill.completeness === 'stub' || skill.completeness === 'STUB';
  };

  const columns: ColumnsType<SkillMeta> = [
    {
      title: '技能名称',
      dataIndex: 'name',
      key: 'name',
      render: (name: string, record: SkillMeta) => (
        <Space>
          <span style={{ fontWeight: 500 }}>{name}</span>
          {isStale(record) && <Tag color="red">待完善</Tag>}
        </Space>
      ),
    },
    {
      title: '领域',
      dataIndex: 'domain',
      key: 'domain',
      render: (domain: string) => domain ? <Tag color="blue">{domain}</Tag> : '-',
    },
    {
      title: '类型',
      dataIndex: 'type',
      key: 'type',
      render: (type: string) => {
        switch (type?.toLowerCase()) {
          case 'knowledge':
            return <Tag color="geekblue">知识型</Tag>;
          case 'procedure':
            return <Tag color="purple">流程型</Tag>;
          default:
            return <Tag>{type || '-'}</Tag>;
        }
      },
    },
    {
      title: '完整度',
      dataIndex: 'completeness',
      key: 'completeness',
      render: (val: string) => {
        const colorMap: Record<string, string> = {
          complete: 'green',
          COMPLETE: 'green',
          partial: 'orange',
          PARTIAL: 'orange',
          stub: 'red',
          STUB: 'red',
        };
        const labelMap: Record<string, string> = {
          complete: '完整',
          COMPLETE: '完整',
          partial: '部分',
          PARTIAL: '部分',
          stub: '存根',
          STUB: '存根',
        };
        return <Tag color={colorMap[val] || 'default'}>{labelMap[val] || val || '-'}</Tag>;
      },
    },
    {
      title: '摘要',
      dataIndex: 'summary',
      key: 'summary',
      ellipsis: true,
    },
  ];

  return (
    <div>
      <Title level={3}>技能列表</Title>

      <Card style={{ marginBottom: 16 }}>
        <Space wrap>
          <Input
            placeholder="搜索技能名称、摘要、领域"
            prefix={<SearchOutlined />}
            value={search}
            onChange={(e) => setSearch(e.target.value)}
            style={{ width: 280 }}
            allowClear
          />
          <Select
            placeholder="按领域筛选"
            value={domainFilter}
            onChange={setDomainFilter}
            allowClear
            style={{ width: 180 }}
            options={domains.map((d) => ({ label: d, value: d }))}
          />
          <Select
            placeholder="按类型筛选"
            value={typeFilter}
            onChange={setTypeFilter}
            allowClear
            style={{ width: 140 }}
            options={[
              { label: '知识型', value: 'knowledge' },
              { label: '流程型', value: 'procedure' },
            ]}
          />
        </Space>
      </Card>

      <Table
        columns={columns}
        dataSource={filtered}
        rowKey="name"
        loading={loading}
        pagination={{ pageSize: 20, showSizeChanger: true, showTotal: (total) => `共 ${total} 条` }}
        onRow={(record) => ({
          onClick: () => navigate(`/skills/${encodeURIComponent(record.name)}`),
          style: { cursor: 'pointer' },
        })}
      />
    </div>
  );
}
