# ZM Skill Collector

业务知识与技能仓库系统 — 将分散的文档自动转化为 Claude Code 可消费的 skill。

## 解决什么问题

团队的业务知识散落在语雀、代码仓库、各类文档中，新人上手慢，AI 助手无法利用这些知识。ZM Skill Collector 实现 **投递 → 转换 → 验证 → 分发** 的完整闭环：投递者只需上传文档，AI 全自动完成 skill 生成，团队成员通过 Claude Code 插件即时获取。

## 系统架构

```
投递层          处理层 (Spring Boot)       存储层          消费层
─────────      ──────────────────        ────────       ─────────
Web UI    ──→  文档解析                                  Claude Code
CLI       ──→  AI 分类/聚类       ──→    Git 仓库  ──→  Plugin
Git 集成  ──→  Skill 生成/验证           raw/skills/    (索引+触发)
               去重/敏感过滤             glossary/
```

## 快速开始

### 环境要求

- Java 17+
- Maven 3.8+
- Node.js 18+
- Anthropic API Key

### 启动后端

```bash
cd server
export ANTHROPIC_API_KEY=your-key
mvn spring-boot:run
```

服务启动在 `http://localhost:8080`。

### 启动前端

```bash
cd web
npm install
npm run dev
```

前端启动在 `http://localhost:5173`。

### 使用 CLI

```bash
cd cli
npm install && npx tsc

# 投递文档
npx zm-skill submit ./path/to/docs/

# 查看处理状态
npx zm-skill status <submission-id>

# 浏览 skill
npx zm-skill list --domain payment

# 提交反馈
npx zm-skill feedback payment-clearing useful
```

### 运行测试

```bash
cd server
mvn test    # 163 tests
```

## API 端点

| 方法 | 路径 | 说明 |
|------|------|------|
| POST | `/api/submissions` | 上传文件（multipart） |
| POST | `/api/submissions/yuque` | 语雀 URL 导入（501 未实现） |
| GET | `/api/submissions/{id}/status` | 查看处理状态 |
| GET | `/api/submissions/{id}/domain-map` | 获取领域地图 |
| POST | `/api/submissions/{id}/confirm` | 确认领域地图 |
| GET | `/api/skills` | Skill 列表（支持 domain/type/teams 过滤） |
| GET | `/api/skills/{name}` | Skill 详情 |
| GET | `/api/skills/index` | 全量索引（Plugin 用） |
| POST | `/api/feedback` | 提交反馈 |
| GET | `/api/feedback/stats` | 反馈统计 |
| GET | `/api/glossary/{domain}` | 术语映射 |
| GET | `/api/releases` | 发布指针 |

## Skill 类型

### 业务知识 (knowledge)

领域内的信息、规则、上下文。多文档聚合生成，增量追加时自动重生成。

```yaml
name: payment-clearing
type: knowledge
domain: payment
trigger: "用户提到清算、对账相关的业务逻辑"
aliases: [清算, 结算, 清分]
summary: "支付清算的核心规则和对账逻辑"
schema_version: 1
---
# 清算业务知识
...
```

### 业务技能 (procedure)

可执行的操作流程。单文档生成，更新时覆盖式重生成。

```yaml
name: refund-flow
type: procedure
category: biz-operation
domain: payment
trigger: "用户需要处理退款、发起逆向交易"
summary: "退款操作的完整流程和注意事项"
preconditions: [...]
verification: [...]
schema_version: 1
---
# 退款操作流程
...
```

## 处理流程

```
上传文档 → 解析(md/docx/pdf/html) → AI分类 → 领域聚类
    → 用户确认领域地图 → AI生成Skill → 质量验证
    → 去重检测 → 敏感信息过滤 → 入库 → Git提交
    → 更新release.json → Plugin分发
```

单文件上传走快速路径，跳过聚类确认直接生成。

## 核心特性

- **极低门槛投递** — 只需上传文档，AI 自动完成分类、聚类、生成
- **多格式支持** — Markdown、Word、PDF、HTML
- **领域聚类** — 两阶段扫描：快速预览 + 投递者确认
- **术语统一** — AI 自动识别同义词，时间优先原则
- **敏感信息过滤** — 13 类正则（IP/凭证/手机号/身份证/JWT/私钥等）
- **Prompt Injection 防护** — 源文档隔离标签 + 系统指令防护
- **SSRF 防护** — URL 域名白名单 + 私网 IP 拦截
- **去重检测** — 三段式（Jaccard 初筛 + LLM 语义复核 + 直接标记）
- **幂等提交** — SHA-256 内容哈希，原子防重
- **并发安全** — 领域级 ReentrantLock，防止聚合覆盖
- **多团队隔离** — visibility 控制 + 服务端分发侧过滤
- **生命周期管理** — 自动老化标记 + 反馈驱动降级
- **审计日志** — 关键操作结构化记录
- **显式发布指针** — release.json 管理版本与回滚

## 项目结构

```
zm-skill-collector/
├── server/          # Spring Boot 后端
├── web/             # React + Ant Design 前端
├── cli/             # Node.js CLI 工具
├── templates/       # AI 生成 Prompt 模板
├── scripts/         # Plugin 构建脚本
├── plugin.json      # Claude Code Plugin 描述
├── docs/
│   ├── design.md              # 系统设计文档
│   ├── delivery-summary.md    # 交付摘要
│   ├── project-journey.md     # 项目历程
│   └── plans/                 # 实现计划
├── TEST-PLAN.md     # 测试方案
└── TEST-DEFECTS.md  # 缺陷记录
```

## 技术栈

| 组件 | 技术 |
|------|------|
| 后端 | Java 17, Spring Boot 3.2, Apache POI, PDFBox, flexmark-java, JGit |
| AI | Claude API (Anthropic), 多模型路由 (haiku/sonnet) |
| 前端 | React 18, TypeScript, Ant Design 5, Vite |
| CLI | Node.js, Commander, Chalk, Ora |
| 存储 | 文件系统 + Git 版本管理 |

## 已知限制

- 语雀 URL 导入当前返回 501（需接入语雀 OpenAPI）
- 代码仓库持续监听未实现
- Plugin 运行时 hooks 未实现
- 确认超时自动处理未实现
- Web UI 团队管理页和数据图表未实现
- 老化通知推送未实现

## 文档

- [系统设计](docs/design.md) — 完整架构与数据模型
- [交付摘要](docs/delivery-summary.md) — 功能清单与技术指标
- [项目历程](docs/project-journey.md) — 从想法到落地的完整过程
- [实现计划](docs/plans/2026-03-10-zm-skill-collector.md) — 24 个 Task 的详细计划
- [测试方案](TEST-PLAN.md) — P0/P1/P2 用例与验收标准
- [缺陷记录](TEST-DEFECTS.md) — 15 条缺陷全部关闭

## License

MIT
