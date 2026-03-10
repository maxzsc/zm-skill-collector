import { ConfigProvider } from 'antd';
import zhCN from 'antd/locale/zh_CN';

function App() {
  return (
    <ConfigProvider locale={zhCN}>
      <div style={{ padding: 24 }}>
        <h1>ZM Skill Collector</h1>
        <p>前端初始化完成</p>
      </div>
    </ConfigProvider>
  );
}

export default App;
