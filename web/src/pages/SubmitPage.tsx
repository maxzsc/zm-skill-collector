import { useState } from 'react';
import {
  Card,
  Upload,
  Input,
  Button,
  Form,
  Steps,
  message,
  Tabs,
  Space,
  Typography,
  Result,
} from 'antd';
import {
  InboxOutlined,
  LinkOutlined,
  SendOutlined,
  LoadingOutlined,
  CheckCircleOutlined,
} from '@ant-design/icons';
import { useNavigate } from 'react-router-dom';
import type { UploadFile } from 'antd';
import apiClient from '../api/client';

const { Dragger } = Upload;
const { TextArea } = Input;
const { Title } = Typography;

interface SubmitResponse {
  submissionId: string;
  status: string;
}

interface SubmissionStatus {
  id: string;
  status: string;
  fileName?: string;
  errorMessage?: string;
}

type SubmitMode = 'file' | 'url';

export default function SubmitPage() {
  const navigate = useNavigate();
  const [form] = Form.useForm();
  const [fileList, setFileList] = useState<UploadFile[]>([]);
  const [submitMode, setSubmitMode] = useState<SubmitMode>('file');
  const [submitting, setSubmitting] = useState(false);
  const [submissionId, setSubmissionId] = useState<string | null>(null);
  const [currentStep, setCurrentStep] = useState(-1);
  const [statusText, setStatusText] = useState('');
  const [error, setError] = useState<string | null>(null);

  const pollStatus = async (id: string) => {
    const maxAttempts = 60;
    let attempt = 0;
    while (attempt < maxAttempts) {
      try {
        const res = await apiClient.get<{ success: boolean; data: SubmissionStatus }>(
          `/submissions/${id}/status`
        );
        const status = res.data.data?.status;
        setStatusText(status || '');

        if (status === 'AWAITING_CONFIRM' || status === 'awaiting_confirm') {
          setCurrentStep(2);
          return 'AWAITING_CONFIRM';
        }
        if (status === 'COMPLETED' || status === 'completed') {
          setCurrentStep(3);
          return 'COMPLETED';
        }
        if (status === 'FAILED' || status === 'failed') {
          setError(res.data.data?.errorMessage || '处理失败');
          return 'FAILED';
        }
        // Still processing
        if (status === 'CLASSIFYING' || status === 'classifying') {
          setCurrentStep(1);
        } else if (status === 'CLUSTERING' || status === 'clustering') {
          setCurrentStep(1);
        }
      } catch {
        // Ignore poll errors, will retry
      }
      attempt++;
      await new Promise((r) => setTimeout(r, 2000));
    }
    return 'TIMEOUT';
  };

  const handleSubmitFile = async () => {
    if (fileList.length === 0) {
      message.warning('请先选择文件');
      return;
    }

    const values = await form.validateFields();
    setSubmitting(true);
    setCurrentStep(0);
    setError(null);

    try {
      const formData = new FormData();
      fileList.forEach((file) => {
        if (file.originFileObj) {
          formData.append('files', file.originFileObj);
        }
      });
      if (values.description) {
        formData.append('description', values.description);
      }
      if (values.seedDomain) {
        formData.append('seedDomain', values.seedDomain);
      }

      const res = await apiClient.post<{ success: boolean; data: SubmitResponse }>(
        '/submissions',
        formData,
        { headers: { 'Content-Type': 'multipart/form-data' } }
      );

      const id = res.data.data.submissionId;
      setSubmissionId(id);
      setCurrentStep(1);
      message.success('文件已提交，正在处理...');

      const finalStatus = await pollStatus(id);
      if (finalStatus === 'AWAITING_CONFIRM') {
        message.success('文档扫描完成，请确认领域分类');
      }
    } catch (err: unknown) {
      const errorMsg = err instanceof Error ? err.message : '提交失败';
      message.error(errorMsg);
      setError(errorMsg);
    } finally {
      setSubmitting(false);
    }
  };

  const handleSubmitUrl = async () => {
    const values = await form.validateFields();
    if (!values.url) {
      message.warning('请输入URL');
      return;
    }

    setSubmitting(true);
    setCurrentStep(0);
    setError(null);

    try {
      const res = await apiClient.post<{ success: boolean; data: SubmitResponse }>(
        '/submissions/yuque',
        {
          url: values.url,
          description: values.description,
          seedDomain: values.seedDomain,
        }
      );

      const id = res.data.data.submissionId;
      setSubmissionId(id);
      setCurrentStep(1);
      message.success('URL已提交，正在处理...');

      const finalStatus = await pollStatus(id);
      if (finalStatus === 'AWAITING_CONFIRM') {
        message.success('文档扫描完成，请确认领域分类');
      }
    } catch (err: unknown) {
      const errorMsg = err instanceof Error ? err.message : '提交失败';
      message.error(errorMsg);
      setError(errorMsg);
    } finally {
      setSubmitting(false);
    }
  };

  const goToDomainMap = () => {
    if (submissionId) {
      navigate(`/domain-map/${submissionId}`);
    }
  };

  const steps = [
    { title: '提交文档', description: '上传文件或输入URL' },
    { title: '文档分析', description: '分类与聚类' },
    { title: '等待确认', description: '确认领域映射' },
    { title: '完成', description: '技能生成完毕' },
  ];

  return (
    <div style={{ maxWidth: 800, margin: '0 auto' }}>
      <Title level={3}>提交文档</Title>

      {currentStep >= 0 && (
        <Card style={{ marginBottom: 24 }}>
          <Steps
            current={currentStep}
            items={steps.map((s, i) => ({
              ...s,
              icon: submitting && i === currentStep ? <LoadingOutlined /> : undefined,
              status:
                error && i === currentStep
                  ? 'error'
                  : i < currentStep
                  ? 'finish'
                  : i === currentStep
                  ? 'process'
                  : 'wait',
            }))}
          />
          {statusText && (
            <div style={{ marginTop: 12, color: '#888', textAlign: 'center' }}>
              当前状态: {statusText}
            </div>
          )}
          {currentStep === 2 && submissionId && (
            <div style={{ textAlign: 'center', marginTop: 16 }}>
              <Button type="primary" icon={<CheckCircleOutlined />} onClick={goToDomainMap}>
                前往确认领域分类
              </Button>
            </div>
          )}
          {currentStep === 3 && (
            <Result
              status="success"
              title="技能生成完成"
              subTitle="可以在技能列表中查看新生成的技能"
              extra={
                <Button type="primary" onClick={() => navigate('/skills')}>
                  查看技能列表
                </Button>
              }
            />
          )}
          {error && (
            <Result
              status="error"
              title="处理失败"
              subTitle={error}
              extra={
                <Button onClick={() => { setCurrentStep(-1); setError(null); }}>
                  重新提交
                </Button>
              }
            />
          )}
        </Card>
      )}

      {currentStep < 0 && (
        <Card>
          <Form form={form} layout="vertical">
            <Tabs
              activeKey={submitMode}
              onChange={(key) => setSubmitMode(key as SubmitMode)}
              items={[
                {
                  key: 'file',
                  label: '文件上传',
                  children: (
                    <Form.Item>
                      <Dragger
                        multiple
                        fileList={fileList}
                        beforeUpload={() => false}
                        onChange={({ fileList: newList }) => setFileList(newList)}
                        accept=".md,.docx,.pdf"
                      >
                        <p className="ant-upload-drag-icon">
                          <InboxOutlined />
                        </p>
                        <p className="ant-upload-text">
                          点击或拖拽文件到此区域上传
                        </p>
                        <p className="ant-upload-hint">
                          支持 Markdown、Word (docx)、PDF 格式
                        </p>
                      </Dragger>
                    </Form.Item>
                  ),
                },
                {
                  key: 'url',
                  label: 'URL导入',
                  children: (
                    <Form.Item
                      name="url"
                      label="文档URL"
                      rules={
                        submitMode === 'url'
                          ? [{ required: true, message: '请输入URL' }]
                          : []
                      }
                    >
                      <Input
                        prefix={<LinkOutlined />}
                        placeholder="语雀/Git 仓库 URL"
                        size="large"
                      />
                    </Form.Item>
                  ),
                },
              ]}
            />

            <Form.Item name="description" label="一句话说明 (可选)">
              <TextArea
                rows={2}
                placeholder="简要描述这份文档的内容"
                maxLength={200}
                showCount
              />
            </Form.Item>

            <Form.Item name="seedDomain" label="种子领域 (可选)">
              <Input placeholder="例如: 前端开发、后端架构、DevOps" />
            </Form.Item>

            <Form.Item>
              <Space>
                <Button
                  type="primary"
                  size="large"
                  icon={<SendOutlined />}
                  loading={submitting}
                  onClick={submitMode === 'file' ? handleSubmitFile : handleSubmitUrl}
                >
                  提交
                </Button>
              </Space>
            </Form.Item>
          </Form>
        </Card>
      )}
    </div>
  );
}
