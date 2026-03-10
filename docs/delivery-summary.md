# ZM Skill Collector 交付摘要

## 1. 交付范围

### 1.1 后端：Spring Boot 3.2 + Java 17

**Domain 层（8 类）**
- `SkillMeta` — Skill 元数据模型（含 YAML 序列化、长度校验）
- `Submission` — 投递任务模型
- `DomainCluster` — 领域聚类结果模型
- `Feedback` — 反馈数据模型
- `SkillType` / `Completeness` / `ProcedureCategory` / `AgentReadiness` / `Visibility` / `ProcessingStatus` — 枚举与值对象

**Service 层（14 类）**
- `PipelineService` — 端到端处理编排（状态机驱动）
- `ClassificationService` — AI 文档分类
- `ClusteringService` — 两阶段领域聚类
- `SkillGenerationService` — AI Skill 生成（含长度约束）
- `SkillUpdateService` — 增量更新（knowledge 聚合 / procedure 覆盖）
- `ValidationService` — 质量校验（格式 + AI Q&A + 步骤模拟）
- `DeduplicationService` — 去重检测（三段式：放行 / LLM 复核 / 直接标记）
- `SensitiveInfoFilter` — 敏感信息正则过滤（13 类）
- `StalenessService` — 老化检测与 stale 标记
- `FeedbackService` — 反馈收集与自动降级
- `ReleaseService` — release.json 发布指针管理
- `AuditService` — 结构化审计日志
- `UrlValidator` — SSRF 防护（私网/回环/域名白名单）
- `VisibilityFilter` — 团队可见性过滤

**Controller 层（4 个 + 全局异常处理）**
- `SubmissionController` — 文件上传、语雀导入、状态查询、领域地图、确认
- `SkillController` — Skill 列表、详情、索引
- `FeedbackController` — 反馈提交、统计、明细查询
- `GlossaryController` — 术语映射查询
- `GlobalExceptionHandler` — 统一错误码响应

**DTO 层（7 类）**
- `SubmitRequest` / `SubmitResponse` / `YuqueSubmitRequest` / `ConfirmRequest` / `DomainMapResponse` / `FeedbackRequest` / `ApiResponse` / `ErrorCode`

**AI 层（5 类）**
- `ClaudeClient` — Claude API 客户端
- `AiModelConfig` / `AiConfig` — 模型路由配置
- `ClassificationPrompt` / `KnowledgePrompt` / `ProcedurePrompt` — Prompt 模板

**Parser 层（6 类）**
- `DocumentParser`（接口）/ `MarkdownParser` / `DocxParser` / `PdfParser` / `HtmlParser` / `ParserFactory`

**Storage 层（6 类）**
- `SkillRepository`（接口）/ `FileSkillRepository` — 文件系统存储
- `SkillFileFormat` / `SkillDocument` — YAML front matter 格式
- `GitService` — JGit 自动提交
- `StorageConfig` — 存储路径配置

**后端源文件总计：78 个 Java 文件（含 58 个 main + 20 个 test），~7,882 行代码**

### 1.2 前端：React 18 + Ant Design 5 + Vite + TypeScript

| 页面 | 文件 | 功能 |
|------|------|------|
| 文档投递 | `SubmitPage.tsx` | 拖拽上传 + URL 粘贴 + 种子领域 |
| 领域地图确认 | `DomainMapPage.tsx` | 卡片视图 + 置信度标识 + 确认调整 |
| Skill 列表 | `SkillListPage.tsx` | 搜索 + 按 domain/team/type 过滤 + stale 标识 |
| Skill 详情 | `SkillDetailPage.tsx` | Meta 卡片 + Markdown 正文 + 反馈按钮 |
| 数据看板 | `DashboardPage.tsx` | 使用统计 + 反馈汇总 + 老化预警 + 最近投递 |

**前端源文件：8 个 TS/TSX 文件，~1,352 行代码**

### 1.3 CLI：Node.js + Commander + Chalk + Ora

| 命令 | 文件 | 功能 |
|------|------|------|
| `zm-skill submit <path\|url>` | `submit.ts` | 投递文档/语雀 URL |
| `zm-skill status [id]` | `status.ts` | 查询处理状态 |
| `zm-skill list [--domain] [--team]` | `list.ts` | 浏览 Skill 列表 |
| `zm-skill feedback <name> <rating>` | `feedback.ts` | 提交反馈 |

**CLI 源文件：6 个 TS 文件，~319 行代码**

### 1.4 Plugin：Claude Code Plugin

- `plugin.json` — 插件清单（全量索引加载 + 智能匹配触发 + 按需加载正文）
- `scripts/build-plugin.sh` — 打包脚本
- `templates/knowledge-prompt.md` — 知识类生成模板
- `templates/procedure-prompt.md` — 技能类生成模板

---

## 2. 功能清单

### 2.1 投递层

| 功能 | 状态 | 说明 |
|------|------|------|
| 本地文件上传（md/docx/pdf/html） | 已实现 | 单文件快速路径 + 多文件批量路径 |
| 语雀 URL 导入 | 部分实现 | URL 校验 + SSRF 防护已实现，实际导入返回 501 |
| 代码仓库持续监听 | 未实现 | 设计中有，当前版本 deferred |
| 批量导入冷启动（两阶段） | 已实现 | 快速扫描 → 领域地图确认 → 逐领域生成 |
| 幂等控制 | 已实现 | SHA-256 内容摘要 + ConcurrentHashMap 原子操作 |
| 文件限制校验 | 已实现 | 单文件 ≤ 20MB，单批 ≤ 50 个，总量 ≤ 100MB |

### 2.2 处理层

| 功能 | 状态 | 说明 |
|------|------|------|
| 文档解析（md/docx/pdf/html） | 已实现 | ParserFactory 按扩展名路由 |
| AI 文档分类 | 已实现 | type/domain/category/confidence 判定 |
| 两阶段领域聚类 | 已实现 | 快速扫描 + 深度聚类，支持种子领域 |
| AI Skill 生成 | 已实现 | knowledge 聚合 + procedure 单文档生成 |
| 敏感信息过滤 | 已实现 | 13 类正则（IP/JDBC/JWT/手机号/身份证/银行卡等） |
| 质量校验 | 已实现 | 格式校验 + AI Q&A + 步骤模拟 + 完整度分级 |
| 去重检测 | 已实现 | 三段式（<0.6 放行 / 0.6-0.85 LLM 复核 / >0.85 标记） |
| 术语统一 | 已实现 | AI 自动识别 + 时间优先 + aliases 生成 |
| Prompt Injection 防护 | 已实现 | `<user_document>` 包裹 + 系统指令忽略文档内指令 |
| 并发控制 | 已实现 | domain 级 ReentrantLock |

### 2.3 存储层

| 功能 | 状态 | 说明 |
|------|------|------|
| 文件系统存储（YAML front matter + Markdown body） | 已实现 | raw/ + skills/ + tests/ + glossary/ |
| Git 自动提交 | 已实现 | JGit 每次 skill save/update 自动 commit |
| release.json 发布指针 | 已实现 | 生成后自动更新，支持回滚 |
| Glossary 自动生成 | 已实现 | pipeline 和更新路径均接入 |
| 审计日志 | 已实现 | JSON Lines 格式，覆盖 submit/confirm/feedback/staleness_scan |

### 2.4 消费层

| 功能 | 状态 | 说明 |
|------|------|------|
| Plugin 全量索引加载 | 已实现 | plugin.json + build-plugin.sh |
| 智能匹配触发 + 按需加载 | 已实现 | trigger/aliases 匹配 |
| 多团队可见性过滤 | 已实现 | public + team:{name} |
| 反馈收集 hooks | 未实现 | Plugin 侧 hooks 当前版本 deferred |

### 2.5 生命周期

| 功能 | 状态 | 说明 |
|------|------|------|
| Knowledge 增量聚合更新 | 已实现 | 重新读取全部 raw 聚合重生成 |
| Procedure 覆盖式更新 | 已实现 | 精确删除旧 raw 后重新生成 |
| 老化标记（stale） | 已实现 | 定时扫描 + stale 注入过时提示 |
| 反馈回流 + 自动降级 | 已实现 | misleading ≥ 3 且 > useful 时标记 needs_review |
| 确认超时自动处理 | 未实现 | 设计中有（3天无人回复自动处理），当前版本 deferred |

---

## 3. 技术指标

| 指标 | 数值 |
|------|------|
| 后端代码行数 | ~7,882 行（Java） |
| 前端代码行数 | ~1,352 行（TypeScript/TSX） |
| CLI 代码行数 | ~319 行（TypeScript） |
| 总代码行数 | ~9,700+ 行（不含配置/模板/文档） |
| 源文件数 | 92 个（78 Java + 8 TS/TSX + 6 CLI TS） |
| 测试文件 | 20 个 Java 测试类 |
| 测试用例 | 163 个 @Test 方法 |
| Git 提交 | 37 次 |
| 缺陷统计 | 15 发现，15 修复，0 遗留 |
| 主要依赖 | Spring Boot 3.2.3, Apache POI 5.2.5, PDFBox 3.0.1, flexmark 0.64.8, JGit 6.8.0, Lombok 1.18.38 |

---

## 4. 已知限制

| 限制 | 说明 |
|------|------|
| 语雀导入返回 501 | URL 校验和 SSRF 防护已就绪，实际 API 对接未实现 |
| 代码仓库持续监听未实现 | 设计中有 Git webhook + 防抖机制，当前版本 deferred |
| Plugin 反馈收集 hooks 未实现 | Plugin 侧的使用反馈回传 hooks 未开发 |
| 确认超时自动处理未实现 | 领域地图确认 3 天无响应自动处理的逻辑未开发 |
| release.json 回滚无 Controller endpoint | `ReleaseService.rollback()` 存在但未暴露 REST 接口 |
| 幂等键依赖文件拼接顺序 | 同文件集不同上传顺序可能生成不同 submissionId |
| AI 模型 preset 切换未暴露 API | balanced/quality/economy 预设切换仅通过配置文件 |
| 前端未做用户认证 | 当前无登录/权限体系，依赖部署环境网络隔离 |

---

## 5. 测试报告摘要

### 5.1 测试用例覆盖

| 优先级 | 用例数 | 自动化 | 手动 | 通过情况 |
|--------|--------|--------|------|----------|
| P0 | 30 | 28 (auto) | 2 (manual: REL-003, E2E-002 部分) | 全部通过 |
| P1 | ~30 | 全部 auto | — | 全部通过 |
| P2 | ~5 | 全部 auto | — | 全部通过 |

### 5.2 测试覆盖维度

- 接口测试：12 个 REST API 端点，含正常/异常/边界
- 端到端：单文档、批量冷启动、增量更新、procedure 覆盖更新
- 质量验证：敏感信息过滤、去重三段式、格式/长度约束、完整度分级
- 生命周期：老化标记、反馈回流、自动降级、stale 清除
- 安全：Prompt Injection、SSRF 防护、团队隔离、敏感输出泄漏
- 并发幂等：同内容重复提交、domain lock 串行化、状态单调推进
- 发布指针：release.json 生成、回滚
- Schema 版本：默认值、序列化稳定性
- 审计日志：submit/confirm/feedback/staleness_scan 全覆盖

### 5.3 缺陷闭环

- **Round 1**：Codex 全量测试发现 11 条缺陷（QA-001 ~ QA-011）
  - 2 BLOCKER + 2 CRITICAL + 6 MAJOR + 1 MINOR
  - 全部修复
- **Round 2**：Codex 回归测试发现 4 条残留（QA-004b, QA-006b, QA-007b, QA-008b）
  - 2 CRITICAL + 2 MAJOR
  - 全部修复
- **最终状态**：15 defects found, 15 fixed, 0 open

---

## 6. 部署说明

### 6.1 环境要求

| 组件 | 要求 |
|------|------|
| Java | 17+ |
| Maven | 3.8+ |
| Node.js | 18+ |
| npm | 9+ |

### 6.2 环境变量

| 变量 | 说明 | 默认值 |
|------|------|--------|
| `ANTHROPIC_API_KEY` | Claude API 密钥 | 无（必填） |
| `SKILL_STORAGE_PATH` | Skill 仓库存储路径 | `./skill-repo` |

### 6.3 启动命令

**后端**
```bash
cd server
mvn spring-boot:run
# 或
mvn package -DskipTests && java -jar target/skill-collector-0.1.0-SNAPSHOT.jar
```

**前端**
```bash
cd web
npm install
npm run dev        # 开发模式
npm run build      # 生产构建
```

**CLI**
```bash
cd cli
npm install
npm run build
npm link           # 全局注册 zm-skill 命令
```

**Plugin 打包**
```bash
./scripts/build-plugin.sh
```

### 6.4 服务端口

- 后端 API：`http://localhost:8080`
- 前端开发服务器：`http://localhost:5173`（Vite 默认）
