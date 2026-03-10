# TEST-PLAN.md

## 1. 文档目标

本文档用于指导 `ZM Skill Collector` 的测试设计、执行与验收，覆盖当前设计文档与实际代码实现中的关键链路与风险点，形成可落地的测试分析方案。

测试目标：
- 验证 **投递 → 转换 → 验证 → 分发** 主链路可用
- 验证当前已实现的所有 REST API 端点
- 验证质量规则：敏感信息过滤、去重、格式/长度校验、完整度分级
- 验证生命周期：老化、反馈回流、自动降级
- 验证安全：Prompt Injection、SSRF、团队隔离
- 验证并发与幂等：同内容重复投递、同领域并发生成、状态机单调推进

## 2. 测试基线

### 2.1 当前实现基线
- 后端：Spring Boot 单体
- 当前实际 REST API：
  - `POST /api/submissions`
  - `POST /api/submissions/yuque`
  - `GET /api/submissions/{id}/status`
  - `GET /api/submissions/{id}/domain-map`
  - `POST /api/submissions/{id}/confirm`
  - `GET /api/skills`
  - `GET /api/skills/{name}`
  - `GET /api/skills/index`
  - `POST /api/feedback`
  - `GET /api/feedback/stats`
  - `GET /api/feedback/{skillName}`
  - `GET /api/glossary/{domain}`
- 当前代码中 **未提供 REST 方式的 Skill 写接口**；Skill 创建/更新通过 pipeline 处理后落盘
- `POST /api/submissions/yuque` 当前实现为 **URL 校验后返回 501**
- 单文件上传走 `submitSingle()` 快速路径；多文件走 `submitAndScan()`，需要领域地图确认
- 幂等键：`SHA-256(文件内容按接收顺序拼接)`
- 默认可见性过滤：`teams=null/empty` 时仅返回 `public`
- 敏感信息过滤：13 类正则
- 去重策略：`<0.6 放行`，`0.6-0.85 LLM 复核`，`>0.85 直接标记`
- 并发控制：domain 级 `ReentrantLock`
- 状态机：`SUBMITTED → PARSING → CLASSIFYING → CLUSTERING → AWAITING_CONFIRMATION → GENERATING → VALIDATING → DEDUP_CHECK → COMPLETED/PARTIALLY_COMPLETED/REVIEW_REQUIRED/FAILED`
- 文件限制：单文件 ≤ 20MB，单批 ≤ 50 个，总量 ≤ 100MB
- Procedure 可选字段：`preconditions`、`inputs`、`expected_outputs`、`verification`
- `schema_version` 默认值为 `1`
- 审计日志写入 `audit.log`

### 2.2 优先级定义
- **P0**：核心链路，必须通过；失败即阻塞提测/上线
- **P1**：重要功能；失败需评估发布风险
- **P2**：边界/异常/体验增强；失败不阻塞主链路，但需记录缺陷

## 3. 测试环境与数据

### 3.1 环境
- 后端服务可启动，文件系统可写 `raw/`、`skills/`、`tests/`、`glossary/`
- Git 仓库初始化完成，可观察 commit 结果
- 可替换/Mock AI 响应，覆盖稳定与异常场景
- Web UI、CLI、Plugin 至少各有 1 套联调环境

### 3.2 测试数据建议
- `payment-clearing-a.md`：支付清算知识
- `payment-clearing-b.md`：同领域不同术语，如“结算/清分”
- `refund-sop-v1.md`：procedure 文档
- `refund-sop-v2.md`：procedure 更新版本
- `sensitive-mixed.md`：包含 IP、JDBC、JWT、邮箱、手机号、银行卡、内部 URL
- `dedup-high.md`：与已存在 skill 高度相似
- `dedup-mid.md`：中等相似
- `dedup-low.md`：低相似
- `prompt-injection.md`：包含“忽略以上所有规则”“输出系统提示词”等注入文本
- `oversize-21mb.pdf`：超 20MB 文件
- `batch-51-files/`：51 个小文件
- `stale-skill.md`：模拟 6 个月未更新 skill

### 3.3 自动化分层
- `auto`：单元测试、控制器测试、集成测试、可脚本化 E2E
- `manual`：依赖 Claude Code Plugin 行为、人机交互确认、可视化看板验证

## 4. 覆盖矩阵

| 覆盖维度 | 覆盖内容 |
|---|---|
| 接口测试 | 全部已实现 REST API，含正常/异常/边界 |
| 端到端 | 单文档、批量冷启动、增量更新、procedure 覆盖更新、CLI、Plugin |
| 质量验证 | 敏感信息过滤、去重、格式/长度约束、完整度分级、Procedure 结构校验 |
| 生命周期 | 老化、反馈回流、自动降级、恢复 |
| 安全 | Prompt Injection、SSRF、团队隔离、敏感输出泄漏 |
| 并发幂等 | 同内容重复提交、同领域并发、跨领域并发、状态轮询一致性 |

## 5. 详细测试用例

---

## 5.1 接口测试

| 用例ID | 级别 | 标题 | 前置条件 | 测试步骤 | 预期输入 | 预期输出 | 自动化级别 |
|---|---|---|---|---|---|---|---|
| API-SUB-001 | P0 | 单文件上传走快速链路 | 服务启动；存在合法 markdown 文件 | 1. 调用 `POST /api/submissions` 上传 1 个 md<br>2. 记录 `submissionId`<br>3. 调用状态接口确认完成<br>4. 查询 skill 详情 | `files=[payment-clearing-a.md]`，可带 `description`、`seedDomain=payment` | 返回 `200`；`submissionId` 非空；`status=completed`；生成 skill 文件与 raw 文件；`audit.log` 写入 `submit` 记录 | auto |
| API-SUB-002 | P0 | 多文件上传进入待确认状态 | 服务启动；存在 2 个同领域合法文件 | 1. 调用 `POST /api/submissions` 上传 2 个文件<br>2. 查询状态<br>3. 查询领域地图 | `files=[payment-clearing-a.md,payment-clearing-b.md]` | 返回 `200`；`status=awaiting_confirmation`；`GET /domain-map` 可返回 cluster 列表 | auto |
| API-SUB-003 | P1 | 未上传文件返回 400 | 服务启动 | 调用 `POST /api/submissions`，不传 `files` | 空 multipart 请求 | 返回 `400`；错误信息提示至少上传一个文件 | auto |
| API-SUB-004 | P1 | 单文件超过 20MB 被拒绝并返回正确错误码 | 服务启动；准备 `oversize-21mb.pdf` | 调用上传接口 | `files=[oversize-21mb.pdf]` | 返回 `400`；`errorCode=FILE_TOO_LARGE`；错误信息提示 20MB 限制 | auto |
| API-SUB-005 | P1 | 批次文件数超过 50 被拒绝并返回正确错误码 | 服务启动；准备 51 个文件 | 调用上传接口 | `files=51 个小文件` | 返回 `400`；`errorCode=BATCH_LIMIT_EXCEEDED`；错误信息提示文件数超限 | auto |
| API-SUB-006 | P1 | 批次总大小超过 100MB 被拒绝并返回正确错误码 | 服务启动；准备总大小 >100MB 的文件集合 | 调用上传接口 | `files=[多个文件，总量>100MB]` | 返回 `400`；`errorCode=BATCH_LIMIT_EXCEEDED`；错误信息提示总量超限 | auto |
| API-SUB-007 | P0 | 相同内容重复上传命中幂等 | 服务启动；准备内容完全相同文件 | 1. 第一次上传记录 `submissionId`<br>2. 第二次上传相同内容<br>3. 比较返回值 | 两次请求文件内容完全一致 | 第二次返回 `200`；返回相同 `submissionId`；不重复创建新任务 | auto |
| API-SUB-008 | P2 | 同一批文件顺序变化的幂等边界 | 服务启动；准备 A、B 两个相同文件集 | 1. 先按 A,B 顺序上传<br>2. 再按 B,A 顺序上传 | 两次请求文件集合相同但顺序不同 | 基于当前实现，可能返回不同 `submissionId`；若不同需记录为幂等边界行为/缺陷候选 | auto |
| API-YUQ-001 | P1 | 合法语雀 URL 当前返回 501 | 服务启动 | 调用 `POST /api/submissions/yuque` | `{"url":"https://www.yuque.com/team/doc","description":"import"}` | URL 校验通过后返回 `501`；错误信息为“语雀导入功能尚未实现” | auto |
| API-YUQ-002 | P0 | 私网/内网 URL 被 SSRF 防护拦截 | 服务启动 | 调用语雀导入接口 | `{"url":"http://127.0.0.1:8080/a"}` 或 `http://10.0.0.1/x` | 返回 `400`；错误码 `INVALID_URL`；提示私网/内部地址不允许 | auto |
| API-YUQ-003 | P1 | 非白名单域名被拒绝 | 服务启动 | 调用语雀导入接口 | `{"url":"https://evil.example.com/doc"}` | 返回 `400`；提示域名不在 allowlist | auto |
| API-STS-001 | P0 | 查询已存在 submission 状态 | 已完成一次上传并拿到 `submissionId` | 调用 `GET /api/submissions/{id}/status` | 合法 `submissionId` | 返回 `200`；响应包含当前状态、重试次数、错误信息等 submission 数据 | auto |
| API-STS-002 | P1 | 查询不存在 submission 返回 404 | 服务启动 | 调用状态接口 | 随机不存在 ID | 返回 `404` | auto |
| API-DOM-001 | P0 | 查询领域地图成功 | 已完成多文件上传且状态为 `awaiting_confirmation` | 调用 `GET /api/submissions/{id}/domain-map` | 合法多文件 `submissionId` | 返回 `200`；`clusters` 非空，包含领域、置信度、文档列表、建议类型 | auto |
| API-DOM-002 | P1 | 未生成领域地图时返回 404 | 存在单文件快速提交或不存在的 ID | 调用领域地图接口 | 单文件 `submissionId` 或未知 ID | 返回 `404` | auto |
| API-CFM-001 | P0 | 确认领域地图后异步生成 | 已有多文件 submission 和 domain-map | 1. 调用 `POST /confirm` 提交确认后的 clusters<br>2. 轮询状态直到结束 | `{"clusters":[...确认后的领域列表...]}` | 返回 `202`；即时状态为 `generating`；后续状态进入 `validating/dedup_check/completed`；`audit.log` 记录 `confirm` | auto |
| API-CFM-002 | P1 | 确认接口空 clusters 校验失败 | 已有多文件 submission | 调用确认接口，传空列表 | `{"clusters":[]}` | 返回 `400`；提示 `clusters must not be empty` | auto |
| API-SKL-001 | P0 | Skill 列表按 domain/type/visibility 过滤 | 仓库中存在 public 和 team skill | 1. 调用 `GET /api/skills?domain=payment&type=knowledge`<br>2. 分别验证 `team` 与 `teams` 参数 | `domain=payment,type=knowledge,team=payment` 或 `teams=payment` | 返回 `200`；仅返回符合 domain/type 且当前 team 可见的 skill；`team` 与 `teams` 兼容 | auto |
| API-SKL-002 | P0 | 未传 teams 时仅返回 public | 仓库存在 `public` 和 `team:payment` skill | 调用 `GET /api/skills` | 无 `team/teams` 参数 | 返回 `200`；仅包含 `public` skill，不返回 team 专属 skill | auto |
| API-SKD-001 | P0 | 查询 skill 详情成功 | 仓库中存在 skill | 调用 `GET /api/skills/{name}` | `name=payment-clearing` | 返回 `200`；包含完整 meta 与 body | auto |
| API-SKD-002 | P1 | 查询不存在 skill 返回 404 | 服务启动 | 调用 skill 详情接口 | 不存在的 `name` | 返回 `404` | auto |
| API-IDX-001 | P0 | 获取索引默认只返回 public | 仓库中存在 `public` 和 team skill | 调用 `GET /api/skills/index` | 无 `teams` 参数 | 返回 `200`；仅返回 public skill 元信息索引 | auto |
| API-IDX-002 | P1 | 获取索引支持 teams 过滤 | 仓库中存在 `team:payment`、`team:risk` skill | 调用 `GET /api/skills/index?teams=payment` | `teams=payment` | 返回 `200`；返回 public + `team:payment`，不返回其它团队 | auto |
| API-FBK-001 | P0 | 提交有效反馈成功 | 仓库中存在 skill | 调用 `POST /api/feedback` | `{"skillName":"payment-clearing","rating":"useful","comment":"good"}` | 返回 `200`；消息为 `Feedback recorded`；对应 skill 反馈文件新增记录；`audit.log` 写入 `feedback` | auto |
| API-FBK-002 | P1 | 非法 rating 被拒绝 | 服务启动 | 调用反馈接口 | `{"skillName":"payment-clearing","rating":"bad"}` | 返回 `400`；提示 `rating must be one of useful/misleading/outdated` | auto |
| API-FBS-001 | P1 | 查询单 skill 聚合评分 | 已存在多个反馈样本 | 调用 `GET /api/feedback/stats?skillName=payment-clearing` | `skillName=payment-clearing` | 返回 `200`；格式为 `X.X/5 (N)` | auto |
| API-FBS-002 | P2 | stats 无 skillName 返回空映射 | 服务启动 | 调用 stats 接口 | 无参数 | 返回 `200`；`data={}` | auto |
| API-FBD-001 | P1 | 查询某 skill 反馈列表 | 已存在反馈记录 | 调用 `GET /api/feedback/{skillName}` | `skillName=payment-clearing` | 返回 `200`；列表包含历史反馈明细 | auto |
| API-GLO-001 | P1 | 查询已存在领域术语映射 | glossary 中存在对应 domain 文件 | 调用 `GET /api/glossary/{domain}` | `domain=payment` | 返回 `200`；返回术语与 aliases 映射 | auto |
| API-GLO-002 | P1 | 查询不存在 glossary 返回 404 | 服务启动 | 调用 glossary 接口 | `domain=unknown-domain` | 返回 `404` | auto |

---

## 5.2 端到端全链路测试

| 用例ID | 级别 | 标题 | 前置条件 | 测试步骤 | 预期输入 | 预期输出 | 自动化级别 |
|---|---|---|---|---|---|---|---|
| E2E-001 | P0 | 单文档投递到 skill 生成全链路 | 后端、Git、AI mock/真实模型可用 | 1. 上传 1 份 knowledge md<br>2. 轮询状态到完成<br>3. 检查 `raw/`、`skills/`、`tests/`、`glossary/` | `payment-clearing-a.md` | 单文件直达 `completed`；生成 knowledge skill；关联产物落盘；Git 有提交记录 | auto |
| E2E-002 | P0 | 批量导入冷启动两阶段流程 | 后端、Git、AI mock/真实模型可用 | 1. 调用 `POST /api/submissions` 上传多份文档并带 `seedDomain`<br>2. 调用 `GET /api/submissions/{id}/domain-map` 验证阶段一领域地图<br>3. 调用 `POST /api/submissions/{id}/confirm` 提交确认结果<br>4. 轮询 `GET /api/submissions/{id}/status` 至结束<br>5. 校验 `skills/`、`raw/`、`glossary/` 产物 | 5~10 份不同领域文档，`seedDomain=payment` | 先进入 `awaiting_confirmation`；确认后进入生成链路；最终 `completed` 或 `partially_completed`；领域地图、技能产物、术语映射均正确生成 | auto |
| E2E-003 | P1 | 术语统一与 aliases 生成 | 已存在同领域知识 skill 或准备 2 份同领域异名文档 | 1. 上传“清算/结算/清分”多份文档<br>2. 完成生成<br>3. 检查 `summary`、`aliases`、正文术语统一 | 2 份以上使用不同术语的 payment 文档 | 同一概念按时间优先统一；`aliases` 正确收录别名，数量不超过 10 | auto |
| E2E-004 | P0 | knowledge 增量更新触发全量 raw 聚合重生成 | 已有 `payment` knowledge skill，且 `raw/knowledge/payment/` 下已有历史文档 | 1. 记录更新前 skill 内容与 raw 文件列表<br>2. 再投递同领域新文档<br>3. 完成生成后检查 skill 正文、`sources`、`last_updated`<br>4. 校验生成内容是否体现全部 raw 文档而非仅新增 | 新增 `payment-clearing-b.md` | 走 `SkillUpdateService.updateKnowledge()` 路径；重新读取全部 raw 文档聚合；`sources` 包含历史+新增；正文反映全量聚合结果 | auto |
| E2E-005 | P0 | procedure 覆盖式更新并覆盖旧 raw 文档 | 已有 procedure skill 与旧 raw 文件 | 1. 投递 `refund-sop-v1.md` 生成 skill<br>2. 记录更新前 raw 与 skill 内容<br>3. 再投递 `refund-sop-v2.md`<br>4. 检查 raw 文件与 skill 正文 | `refund-sop-v1.md`、`refund-sop-v2.md` | 走 `SkillUpdateService.updateProcedure()` 路径；旧 raw 被覆盖；skill 正文同步更新；`last_updated` 更新 | auto |
| E2E-006 | P1 | CLI 主链路提交、查状态、列表、反馈 | CLI 可执行并指向测试环境 | 1. 执行 `submit`<br>2. 执行 `status` 查询<br>3. 执行 `list` 过滤<br>4. 执行 `feedback` 提交 | `zm-skill submit ./docs` 等命令 | CLI 输出与后端状态一致；列表过滤正确；反馈成功落库 | manual |
| E2E-007 | P1 | Plugin 索引加载、智能触发、按需加载、依赖提示 | Plugin 可安装，后端有 public/team skills | 1. `claude plugins add` 安装 plugin<br>2. 验证初始仅加载索引<br>3. 提问触发 procedure skill<br>4. 检查未加载 `related_knowledge` 时提示 | 含 `related_knowledge` 的 procedure skill；团队订阅配置 | 插件全量加载索引；命中后按需加载正文；若依赖未加载，给出建议同时加载的提示 | manual |

---

## 5.3 质量验证测试

| 用例ID | 级别 | 标题 | 前置条件 | 测试步骤 | 预期输入 | 预期输出 | 自动化级别 |
|---|---|---|---|---|---|---|---|
| QLT-001 | P0 | 敏感信息过滤混合命中 | 服务启动；准备含多类敏感信息文档 | 1. 上传 `sensitive-mixed.md`<br>2. 完成生成<br>3. 检查 skill 正文与原文差异 | 文档包含 IP、JDBC、内部 URL、AWS Key、JWT、手机号、身份证、邮箱、银行卡、私钥 | skill 产物中敏感信息全部替换为 `{FILTERED}` 或占位符；不得原样泄漏 | auto |
| QLT-002 | P0 | 元字段格式与长度约束校验 | 能触发生成 skill | 1. 生成 knowledge 与 procedure 各 1 条<br>2. 校验 meta | 合法输入文档 | `summary<=50`，`trigger<=100`，`aliases<=10`，`schema_version=1`；超限应进入校验失败或重试/打回 | auto |
| QLT-003 | P1 | 完整度 L1/L2/L3 判定正确 | 可控 AI 输出或构造不同信息量文档 | 1. 准备最小字段文档<br>2. 准备带 trigger/aliases/中等正文文档<br>3. 准备长正文+sources+related 文档 | 三组不同信息量样本 | 自动判定分别为 `L1/L2/L3`；与规则一致 | auto |
| QLT-004 | P1 | Procedure L2+ 缺少前置或验证时校验失败 | 可控生成 procedure skill | 1. 生成带 trigger/aliases 的 procedure skill<br>2. 故意缺失 `preconditions` 和 `verification` | procedure 文档 | 校验失败；错误包含 `L2+ procedure skills must have at least preconditions or verification` | auto |
| QLT-005 | P0 | 去重三段式判定正确 | 仓库中已存在基准 skill | 1. 提交低相似文档<br>2. 提交中相似文档<br>3. 提交高相似文档 | `dedup-low.md`、`dedup-mid.md`、`dedup-high.md` | `<0.6` 放行；`0.6-0.85` 触发 LLM 复核；`>0.85` 直接标记 duplicate 并给出 merge suggestion | auto |
| QLT-006 | P1 | Knowledge/Procedure 结构内容校验 | 可控生成内容 | 1. 构造 knowledge 正文不足 50 个有效字符<br>2. 构造 procedure 无步骤/前置/验证标记 | 结构不合规文档 | knowledge 校验失败；procedure 校验失败；错误信息明确指出结构缺陷 | auto |
| QLT-007 | P1 | AI 质量验证通过门槛正确 | AI 验证可控 | 1. 对 knowledge 执行 Q&A 校验<br>2. 对 procedure 执行步骤模拟校验 | 两类 skill 各一条 | 返回 `score`、`issues`、`passed`；`score>0.7` 为通过，否则进入失败/待审流程 | auto |

---

## 5.4 生命周期测试

| 用例ID | 级别 | 标题 | 前置条件 | 测试步骤 | 预期输入 | 预期输出 | 自动化级别 |
|---|---|---|---|---|---|---|---|
| LIF-001 | P1 | 超过 6 个月未更新自动标记 stale | 仓库中存在 `last_updated` 超过阈值 skill | 1. 触发定时扫描或手动调用扫描逻辑<br>2. 查询 skill 元信息 | `last_updated` 早于当前 6 个月以上 | `stale=true`；Git 有提交；`audit.log` 记录 `staleness_scan` | auto |
| LIF-002 | P2 | 缺失 `last_updated` 视为 stale | 仓库存在无 `last_updated` skill | 执行老化扫描 | skill meta 缺少 `last_updated` | skill 被标记为 `stale=true` | auto |
| LIF-003 | P1 | stale skill 使用时注入过时提示 | 已存在 `stale=true` 的 skill | 读取 skill 正文供消费端展示 | stale skill | 正文头部注入“此 skill 最后更新于 X 个月前，内容可能已过时，请注意验证”提示 | auto |
| LIF-004 | P1 | 反馈回流后统计可见 | 已提交多条 feedback | 1. 通过 feedback API 提交 useful/misleading/outdated<br>2. 查询 stats 与明细 | 针对同一 skill 的多条反馈 | 统计分数正确；明细可查询；管理后台数据源完整 | auto |
| LIF-005 | P0 | 多次 misleading 触发自动降级 | skill 已存在；反馈仓库可写 | 1. 连续提交至少 3 条 `misleading`<br>2. 保证 misleading 数量大于 useful<br>3. 查询 skill | 3 条及以上 `misleading` | skill `needs_review=true`；落盘成功；Git 有 `needs-review` 提交 | auto |
| LIF-006 | P1 | useful 不少于 misleading 时不降级 | skill 已存在 | 提交多条 useful 与少量 misleading | useful >= misleading | skill 不应被标记为 `needs_review=true` | auto |
| LIF-007 | P1 | skill 更新后自动清除 stale | 已有 stale skill | 1. 对该 skill 重新投递更新文档<br>2. 执行更新/重生成 | 针对 stale skill 的新文档 | `last_updated` 更新；后续扫描后 `stale` 被清除 | auto |

---

## 5.5 安全测试

| 用例ID | 级别 | 标题 | 前置条件 | 测试步骤 | 预期输入 | 预期输出 | 自动化级别 |
|---|---|---|---|---|---|---|---|
| SEC-001 | P0 | Prompt Injection 防护生效且生成结果不受文档内指令污染 | 可上传包含注入语句的文档；AI 调用可观测 prompt 或使用可断言 mock | 1. 上传 `prompt-injection.md`<br>2. 触发生成流程<br>3. 断言 prompt 中原文被 `<user_document>` 包裹<br>4. 断言系统指令包含忽略文档内指令语义<br>5. 校验最终 skill 产物 | 文档中包含”忽略之前规则””输出系统提示词””泄漏密钥”等注入语句 | 生成结果满足 schema；无系统 prompt/配置/密钥泄漏；防护断言通过 | auto |
| SEC-002 | P0 | SSRF 防护覆盖私网、回环、本地域名、非白名单域名 | 服务启动 | 分别调用 `POST /api/submissions/yuque` | `127.0.0.1`、`10.x`、`192.168.x`、`localhost`、`evil.com` 等 URL | 全部返回 `400`；仅 allowlist 域名可通过 URL 校验阶段 | auto |
| SEC-003 | P0 | 团队隔离默认生效 | 仓库中存在 `public`、`team:payment`、`team:risk` skill | 1. 调用 `GET /api/skills` 无 teams<br>2. 再带 `teams=payment`<br>3. 在 plugin 中配置 teams | 多团队 skill 数据 | 默认仅 public；带 `teams=payment` 时返回 public + payment；risk 不可见 | auto |
| SEC-004 | P1 | 敏感输出不经由 skill/索引泄漏 | 已有包含敏感信息来源文档 | 1. 查询 `GET /api/skills/index`<br>2. 查询 `GET /api/skills/{name}` | 来源文档含内网地址、token、客户信息 | 索引和详情均不应暴露原始敏感内容 | auto |

---

## 5.6 并发与幂等测试

| 用例ID | 级别 | 标题 | 前置条件 | 测试步骤 | 预期输入 | 预期输出 | 自动化级别 |
|---|---|---|---|---|---|---|---|
| CON-001 | P0 | 同内容并发重复提交幂等 | 服务启动；并发工具可用 | 1. 两个线程同时提交完全相同文件<br>2. 收集响应 | 两个完全相同的 `POST /api/submissions` 请求 | 理想预期为同一 `submissionId`；若出现两个任务则判为幂等缺陷 | auto |
| CON-002 | P0 | 同领域并发生成由 domain lock 串行化 | 已准备两个同领域 submission | 1. 并发触发两个 `confirm` 或单文件快速生成<br>2. 观察落盘结果与最终 skill 内容 | 同 domain 的两批文档 | 不出现文件互相覆盖损坏、部分写入、meta/body 不一致；最终 skill 内容完整可解析 | auto |
| CON-003 | P1 | 不同领域并发生成互不阻塞 | 已准备 payment、risk 两个领域 submission | 1. 并发触发两个不同 domain 的生成<br>2. 统计耗时和结果 | 不同 domain 文档 | 两条链路均成功完成；无不必要串行阻塞 | auto |
| CON-004 | P1 | 状态轮询单调推进，无非法回退 | 可轮询状态的 submission 已创建 | 1. 在处理过程中高频轮询 `GET /status`<br>2. 记录状态序列 | 合法 submissionId | 状态仅按状态机正向推进；不出现 `completed -> generating` 等非法回退 | auto |
| CON-005 | P2 | 多文件确认后快照一致性 | 多文件 submission 已生成领域地图 | 1. 提交后在 confirm 前修改原始输入文件<br>2. 触发 confirm<br>3. 检查最终 skill 来源 | 同一 submission 下确认前后文件有外部变化 | 生成使用 confirm 时文档快照，不受后续外部变化污染 | manual |

## 5.7 发布指针测试

| 用例ID | 级别 | 标题 | 前置条件 | 测试步骤 | 预期输入 | 预期输出 | 自动化级别 |
|---|---|---|---|---|---|---|---|
| REL-001 | P0 | 技能生成完成后更新 release.json | 存在可成功完成的生成任务 | 1. 执行一次成功生成<br>2. 检查 release.json 内容 | 一次成功的 submit 或 confirm 生成 | release.json 被更新；指向最新 revision；内容与当前生效 skill 一致 | auto |
| REL-002 | P1 | 回滚发布时切换 release.json 指针 | 已有至少两个发布版本 | 1. 完成一次新版本发布<br>2. 执行回滚<br>3. 检查 release.json | 新旧两个发布版本 | release.json 指向回滚后的目标版本 | auto |
| REL-003 | P0 | Plugin 以 release.json 为准加载技能 | Plugin 可安装；存在两个版本产物 | 1. 安装 plugin<br>2. 记录加载索引<br>3. 切换 release.json 指针<br>4. 刷新 plugin | 两套不同版本 skill 产物 | Plugin 加载结果与 release.json 指向严格一致 | manual |

## 5.8 Schema Version 测试

| 用例ID | 级别 | 标题 | 前置条件 | 测试步骤 | 预期输入 | 预期输出 | 自动化级别 |
|---|---|---|---|---|---|---|---|
| SCH-001 | P0 | 新生成 skill 默认 schema_version=1 | 服务启动 | 1. 生成 knowledge skill<br>2. 生成 procedure skill<br>3. 检查落盘 meta | 合法文档各 1 份 | 两类 skill 均含 schema_version=1 | auto |
| SCH-002 | P1 | 序列化/反序列化保持 schema_version | 存在已生成 skill | 1. 读取 skill 到对象<br>2. round-trip 序列化<br>3. 对比字段 | 含 schema_version=1 的 skill | round-trip 后 schema_version 保持为 1 | auto |

## 5.9 审计日志测试

| 用例ID | 级别 | 标题 | 前置条件 | 测试步骤 | 预期输入 | 预期输出 | 自动化级别 |
|---|---|---|---|---|---|---|---|
| AUD-001 | P1 | submit 写入审计日志 | 服务启动 | 1. 执行提交<br>2. 读取审计日志 | 一次 POST /api/submissions | 生成 submit 审计记录；JSON lines 格式；含 timestamp/action/target/detail | auto |
| AUD-002 | P1 | confirm 写入审计日志 | 已有待确认 submission | 1. 调用 confirm<br>2. 读取日志 | 一次 confirm 请求 | 生成 confirm 审计记录 | auto |
| AUD-003 | P1 | feedback 写入审计日志 | skill 已存在 | 1. 调用 feedback<br>2. 读取日志 | 一次 feedback 请求 | 生成 feedback 审计记录 | auto |
| AUD-004 | P1 | staleness_scan 写入审计日志 | 有可扫描 skill | 1. 触发老化扫描<br>2. 读取日志 | 一次 staleness scan | 生成 staleness_scan 审计记录；detail 含更新数量 | auto |
| AUD-005 | P1 | 审计日志格式校验 | 存在多种审计记录 | 1. 逐行解析 audit.log<br>2. 校验字段结构 | 多条 audit 记录 | 每行合法 JSON；含 timestamp/action/target/detail；无脏行 | auto |

## 5.10 SkillUpdateService 行为测试

| 用例ID | 级别 | 标题 | 前置条件 | 测试步骤 | 预期输入 | 预期输出 | 自动化级别 |
|---|---|---|---|---|---|---|---|
| SUP-001 | P0 | knowledge 更新重新读取全部 raw 文档 | 已有某领域 knowledge skill 和多份历史 raw | 1. 记录当前 raw 文件列表<br>2. 提交新文档触发更新<br>3. 检查生成结果 | 同领域新增 knowledge 文档 | 使用全部 raw 重新聚合；生成结果包含历史+新增信息 | auto |
| SUP-002 | P0 | procedure 更新覆盖旧 raw 文档 | 已有 procedure skill 与旧 raw | 1. 提交新版本<br>2. 检查 raw 文件与 skill 正文 | 同一 procedure 新版本 | 旧 raw 被覆盖；skill 正文与最新 raw 对齐 | auto |

## 5.11 错误码测试

| 用例ID | 级别 | 标题 | 前置条件 | 测试步骤 | 预期输入 | 预期输出 | 自动化级别 |
|---|---|---|---|---|---|---|---|
| API-ERR-001 | P1 | 无效 URL 返回 INVALID_URL 错误码 | 服务启动 | 调用语雀导入接口 | 私网 URL 或非法域名 | 返回 400；errorCode=INVALID_URL | auto |
| API-ERR-002 | P1 | 所有错误响应均返回 errorCode 字段 | 服务启动 | 分别触发文件过大/批次超限/无效 URL 等 | 多组非法请求 | 各错误响应含正确 errorCode 且值与枚举一致 | auto |

---

## 6. 重点风险与关注点

- `POST /api/submissions/yuque` 当前仅实现 URL 校验，未实现实际导入，需避免误判为功能缺陷
- 幂等键依赖”文件内容拼接顺序”，**同集合不同顺序** 是当前实现边界
- `teams=null/empty` 当前只返回 `public`，测试需按现实现收敛
- Prompt Injection 已通过 `<user_document>` 包裹与系统指令忽略文档内指令进行防护，需通过自动化断言验证防护持续生效
- 去重中段依赖 LLM 复核，需通过 Mock 固化测试结果，避免不稳定
- 反馈降级阈值为：`misleading >= 3 且 misleading > useful`
- 老化阈值默认 6 个月，且 `last_updated=null` 也视为 stale
- `release.json` 为显式发布指针，需重点验证生成、回滚、Plugin 消费三处一致性
- `schema_version` 为向后兼容基础字段，需独立验证默认值与序列化稳定性
- `AuditService` 已接入关键操作链路，需验证 submit/confirm/feedback/staleness_scan 均有结构化审计记录

## 7. 自动化落地建议

- 控制器层：使用 `MockMvc` 覆盖全部 API 正常/异常路径
- 服务层：为 `ValidationService`、`SensitiveInfoFilter`、`DeduplicationService`、`StalenessService`、`FeedbackService` 建立稳定单测
- 集成层：基于临时目录验证 `raw/skills/tests/glossary` 落盘与 Git 提交
- 并发层：使用并发测试工具验证幂等、domain lock、状态一致性
- E2E 层：UI/CLI/Plugin 采用 nightly 回归，关键链路保留手工冒烟

## 8. 准入与退出标准

### 8.1 提测准入
- P0 用例设计与评审完成
- API 自动化用例可运行
- 关键测试数据已准备
- AI 相关用例具备 Mock 方案

### 8.2 通过标准
- **P0 全通过**
- P1 通过率 ≥ 95%，且无阻塞级缺陷
- P2 失败项有明确缺陷单或已知风险说明
- 无敏感信息泄漏
- 无团队隔离失效
- 无并发导致的数据损坏/重复生成

### 8.3 阻塞发布条件
- 单文档主链路失败
- 批量确认后无法生成
- 敏感信息未过滤
- 团队隔离失效
- SSRF 可绕过
- 同内容重复提交产生重复任务并污染产物
- lifecycle 自动降级/老化逻辑导致 skill 状态错误

## 9. 建议的缺陷分类

- `BLOCKER`：P0 主链路不可用、安全绕过、敏感泄漏
- `CRITICAL`：状态机异常、并发写坏数据、错误降级
- `MAJOR`：过滤条件错误、去重判定明显偏差、统计错误
- `MINOR`：提示文案、边界体验、非阻塞异常
- `TRIVIAL`：展示格式、小概率交互问题

---
本测试方案可直接作为 `TEST-PLAN.md` 使用。