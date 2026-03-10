# ZM Skill Collector — 业务知识与技能仓库系统设计

## Context

团队需要将分散在语雀、代码仓库、各类文档中的业务知识和技能沉淀为 Claude Code 可消费的 skill,实现**投递 → 转换 → 验证 → 分发**的完整闭环。目标是让混合团队(开发、测试、产品、架构师)以极低门槛投递,AI 全自动完成转换,团队成员通过 Claude Code 插件即时获取业务 skill。

## 系统架构

```
┌─────────────────────────────────────────────────────────┐
│                     投递层                                │
│  Web UI (文件上传/语雀URL导入)  |  CLI (本地文档/语雀skill) │
│  代码仓库 Git 集成              |  PR 提交原始文档          │
└──────────────────────┬──────────────────────────────────┘
                       ▼
┌─────────────────────────────────────────────────────────┐
│                   处理层 (Spring Boot)                    │
│                                                         │
│  1. 文档解析 — 支持 md/docx/pdf/html                     │
│  2. AI 理解 — 领域聚类 + 类型判断 + 术语统一              │
│  3. 领域地图生成 — 输出给投递者确认                        │
│  4. Skill 生成 — 按内容模型生成完整 skill                 │
│  5. 质量验证 — 格式校验 + 可执行性验证 + 敏感信息过滤      │
│  6. 去重检测 — 与已有 skill 比对,提示合并建议             │
│  7. 打包发布 — 生成 Claude Code plugin 包                 │
└──────────────────────┬──────────────────────────────────┘
                       ▼
┌─────────────────────────────────────────────────────────┐
│                   存储层 (Git 仓库)                       │
│  raw/        — 原始文档                                   │
│  skills/     — 生成的 skill 产物                          │
│  tests/      — 验证用例                                   │
│  glossary/   — AI 自动生成的术语映射(按领域)              │
└──────────────────────┬──────────────────────────────────┘
                       ▼
┌─────────────────────────────────────────────────────────┐
│                   消费层                                  │
│  Claude Code Plugin — 全量加载索引 + 智能匹配触发          │
│  公共 skill 自动加载 + 团队专属 skill 按订阅加载           │
└─────────────────────────────────────────────────────────┘
```

## Skill 分类体系

### 一、业务知识 (knowledge)

领域内完整的信息、规则、上下文。知识是累积性的,随文档增量追加,AI 聚合重生成。

### 二、业务技能 (procedure)

可执行的操作流程。按执行主体和自动化程度进一步分类:

| category | 说明 | Agent 就绪度 |
|---|---|---|
| dev | 建表规范、发布流程、代码生成模板 | ready |
| ops | 故障排查 SOP、扩缩容流程 | ready |
| test | 测试用例设计、回归测试流程 | ready |
| biz-operation | 退款处理、工单流转、配置变更 | future |
| analysis | 数据分析方法、指标计算规则 | future |
| collaboration | 需求评审、事故复盘、变更审批流程 | partial |

## Skill 内容模型

### 业务知识 skill

```yaml
name: payment-clearing           # 知识标识
type: knowledge
domain: payment                  # 业务领域
trigger: "用户提到清算、对账相关的业务逻辑"
aliases: [清算, 结算, 清分]       # AI 自动生成的术语别名
summary: "支付清算的核心规则和对账逻辑"  # ≤50字,用于全量索引
completeness: L2                 # L1基础/L2标准/L3完整
visibility: public               # public | team:{name}
sources:                         # 溯源
  - 2024-01-arch-design.md
  - 2025-06-requirement.md
related_skills: [risk-rules]     # 关联 skill
---
# 清算业务知识
...(正文)...
```

### 业务技能 skill

```yaml
name: refund-flow
type: procedure
category: biz-operation
domain: payment
trigger: "用户需要处理退款、发起逆向交易"
summary: "退款操作的完整流程和注意事项"  # ≤50字
completeness: L2
visibility: team:payment
agent_readiness: future
sources:
  - refund-sop-v3.md
related_knowledge: [payment-clearing, risk-rules]  # 依赖的知识 skill

# 稳定层
intent: "发起退款并确保清算逆向正确完成"
implementations:
  - type: human-operation        # 当前:指导人操作
    status: active
  # 未来预留:
  # - type: agent-executable
  #   status: active
---
# 退款操作流程

## 前置条件
- ...

## 执行步骤
1. ...
2. ...
   - 如果 X → ...
   - 如果 Y → ...

## 输入/输出
- 输入: ...
- 输出: ...

## 验证标准
- ...
```

### 完整度分级

| 等级 | 知识类要求 | 技能类要求 | 入库 |
|---|---|---|---|
| L1 基础 | summary + body | summary + steps | 可入库 |
| L2 标准 | L1 + trigger + aliases | L1 + trigger + preconditions + verification | 可入库 |
| L3 完整 | 全字段 + AI 理解性测试通过 | 全字段 + 沙箱可执行验证通过 | 可入库 |

Skill 完整度随文档增量投递自动生长,不要求一次到位。

### Skill 描述长度约束(生成流程硬规则)

- `summary`: ≤ 50 字（用于全量加载时的索引,直接影响上下文窗口消耗）
- `trigger`: ≤ 100 字
- `aliases`: ≤ 10 个
- `body`/步骤正文: 不限,但仅在被触发后才加载

生成 prompt 中明确约束这些长度,CI 验证时校验不通过则打回重新生成。

## 核心逻辑模型

### Job 模型（处理任务）
```yaml
job_id: string              # UUID
status: ProcessingStatus    # SUBMITTED → PARSING → CLASSIFYING → CLUSTERING → AWAITING_CONFIRMATION → GENERATING → VALIDATING → DEDUP_CHECK → REVIEW_REQUIRED → COMPLETED / PARTIALLY_COMPLETED / FAILED
idempotency_key: string     # SHA-256(file_contents + source_uri)
retry_count: int            # 当前重试次数，上限 3
error_code: string          # 失败错误码
error_message: string       # 失败详情
created_at: timestamp
updated_at: timestamp
```

### Source 模型（来源文档）
```yaml
source_uri: string          # 文件路径 / 语雀 URL / Git 仓库地址
source_type: enum           # LOCAL_FILE / YUQUE / GIT_REPO
content_hash: string        # SHA-256 内容摘要，用于幂等和变更检测
imported_at: timestamp      # 导入时间
```

### Skill 模型（增强）
在现有 SkillMeta 基础上新增：
```yaml
schema_version: int         # 当前版本 1，用于未来字段迁移
status: enum                # draft / active / deprecated / stale / needs_review
```

> Note: 实现层仍使用 Submission + SkillMeta + Feedback 三类实体，逻辑模型用于系分文档对齐和测试设计。

## 投递流程

### 最低投递要求

投递者只需提供:
1. **原始文档**（md/docx/pdf/html/语雀URL/代码仓库地址）
2. **一句话说明**（可选,帮助 AI 定向理解）
3. **种子领域**（可选,帮助 AI 做领域划分,批量导入时推荐提供）

meta.yaml 都不需要手动写,AI 自动生成。

### 文档来源集成

| 来源 | Web UI | CLI | 触发方式 |
|---|---|---|---|
| 本地文件 | 拖拽上传（md/docx/pdf） | `zm-skill submit ./docs/` | 手动 |
| 语雀 | 粘贴知识库/文档 URL | `zm-skill submit --yuque <url>` | 手动 |
| 代码仓库 | 粘贴仓库地址 + 配置监听目录 | `zm-skill watch --repo <url> --paths "docs/**,**/README.md"` | 持续监听 |

**代码仓库持续监听规则:**
- 只监听指定目录/文件模式（如 `docs/**`, `**/README.md`, `deploy/`, `Makefile`）,不监听代码文件本身
- 防抖机制:1 小时时间窗口内的多次 commit 只触发一次处理
- 差异检测:只有被监听的文件实际内容有变化时才触发,避免无效重生成
- Web UI 可配置每个代码仓库的监听规则（目录、防抖时间窗口）

### 批量导入冷启动流程

首次大批量导入时,分两阶段处理以降低确认成本:

```
阶段一:快速扫描（低成本）
  - AI 只读取每份文档的前 500 字
  - 如有种子领域 → 优先往里分拣,新发现的领域单独列出
  - 无种子领域 → AI 完全自主聚类
  - 产出:领域地图草稿

阶段二:投递者确认领域地图
  - UI 展示按领域分组的卡片视图,每个卡片显示:
    - 领域名称 + AI 置信度（高/中/低）
    - 归属文档数量 + 文档摘要预览
    - 类型判断（knowledge / procedure）
  - 投递者只需对低置信度的做决策,高置信度一眼跳过
  - 确认后进入深度处理,逐领域生成 skill
```

### 完整 Pipeline

```
Step 1: 投递
  Web UI: 上传文件 / 粘贴语雀知识库 URL / 粘贴代码仓库地址
  CLI:   zm-skill submit ./docs/ 或 zm-skill submit --yuque <url>

Step 2: AI 文档解析
  - 解析所有文档内容
  - 识别文档类型（需求/架构/系分/测分/SOP/etc.）
  - 自动判断 knowledge vs procedure
  - 自动推断 domain、category

Step 3: AI 领域聚类 → 生成领域地图
  ┌──────────────────────────────────┐
  │ AI 识别到以下领域:                │
  │                                  │
  │ ✅ 支付清算 (3份文档)             │
  │ ✅ 风控审核 (2份文档)             │
  │ ⚠️ 退款 — 不确定是否独立领域      │
  │    （与"支付清算"高度相关）       │
  │ ❌ 无法归类 (1份文档)             │
  └──────────────────────────────────┘
  投递者在 Web UI / CLI 交互中确认或调整

Step 4: Skill 生成
  - 知识类:同领域多文档聚合 → 生成一份完整 knowledge skill
  - 技能类:单文档 → 生成一份 procedure skill
  - 术语统一:参照该领域已有 skill 的术语,时间优先原则
  - 敏感信息过滤:自动移除 IP/密钥/内部地址/具体客户数据
  - 长度约束:summary ≤ 50字、trigger ≤ 100字

Step 5: 质量验证
  - 格式完整性:必填字段校验
  - 知识类:AI 对 skill 提问 → 验证能否基于 skill 正确回答
  - 技能类:AI 模拟执行步骤 → 验证步骤无缺失/无死循环/有明确终止
  - 去重检测:与已有 skill 语义比对,>70% 相似则提示合并建议

Step 6: 入库
  - 写入 skills/ 目录
  - 生成/更新 tests/ 下的验证用例
  - 更新 glossary/ 下的术语映射
  - 打包更新 Claude Code plugin

Step 7: 分发
  - Plugin 包自动发布
  - 消费者 claude plugins update 获取最新 skill
```

## 术语治理

**核心原则:无人工维护,AI 自治 + 时间权重**

- 不维护人工 glossary 文件,不指定领域负责人
- AI 聚合同领域文档时自动识别术语冲突
- **时间优先**:同一概念的不同称呼,以最近投递的文档为准
- 术语映射作为生成产物写入 skill 的 `aliases` 字段
- 如果 AI 不确定两个术语是否指同一概念,在投递确认阶段标记 warning,投递者确认
- 3天无人回复则按时间优先原则自动处理

## Skill 更新策略

| 类型 | 更新方式 | 触发 |
|---|---|---|
| 业务知识 | 增量追加:新文档加入后,同领域全部文档重新聚合生成 | 新文档投递到已有领域 |
| 业务技能 | 覆盖式:新文档直接替换旧版本重新生成 | 投递者更新文档 |

版本管理完全依赖 Git 历史,不做显式版本号。

## Skill 生命周期

### 自动老化标记

- Skill 超过 **N 个月**（可配置,默认 6 个月）未被更新（无新文档投递到该领域）
- 自动标记为 `stale: true`
- Claude Code 使用该 skill 时,在 skill 头部注入提示:"此 skill 最后更新于 X 个月前,内容可能已过时,请注意验证"
- 系统定期推送老化 skill 列表通知到相关团队

### 使用反馈闭环

- Claude Code plugin 收集使用反馈:
  - 用户可标记 skill 为"有用"/"误导"/"过时"
  - 自动收集:skill 被触发但用户中途放弃/忽略的情况
- 反馈数据回流到系统:
  - "误导"/"过时"标记多的 skill 自动降级或标记待审
  - "有用"标记多的 skill 提升推荐权重
  - 反馈数据在 Web UI 管理后台可视化展示

## Skill 去重与冲突

- 新 skill 入库时,AI 自动与已有 skill 做语义相似度比对
- 相似度 > 70%: 提示投递者"已有类似 skill [{name}],建议合并或标注差异"
- 投递者可选择:合并到已有 skill / 保持独立（标注差异原因）
- 保持独立的 skill 在消费端通过 team 订阅区分

## 敏感信息过滤

生成 prompt 中硬编码过滤规则:
- IP 地址、数据库连接串、密钥/token
- 具体客户名称和数据
- 内部系统 URL（替换为占位符 `{internal_system_url}`）
- CI 验证阶段二次扫描,匹配正则兜底

## Skill 依赖关系

- `related_knowledge`: 技能 skill 声明依赖的知识 skill
- `related_skills`: 知识 skill 声明关联的其他 skill
- AI 生成时自动推断依赖关系（基于内容引用和术语重叠）
- 消费端:当触发一个 procedure skill 时,如果其 `related_knowledge` 中的 skill 未加载（不在订阅范围内）,Claude Code 提示用户"此技能关联了 [{knowledge_name}],建议同时加载以获得完整上下文"

## AI Native 预留

Skill meta 中的 `implementations` 字段支持多种执行类型:
- `human-operation`（当前）:指导人操作
- `agent-executable`（未来）:Agent 直接执行（API 调用/脚本）

当前只实现 `human-operation`,`agent_readiness` 字段标记每个 skill 的 Agent 就绪程度,用于未来批量筛选可优先改造的 skill。

## 多团队模型

- `visibility: public` — 公共 skill,所有消费者自动加载
- `visibility: team:{name}` — 团队专属 skill

消费端配置:
```yaml
teams: ["payment", "risk"]   # 加载 public + 这两个团队的 skill
```

## Skill 可信度展示

Skill 正文头部嵌入精简版元信息,Claude Code 可读取并告知用户:

```yaml
completeness: L2              # 完整度等级
sources_count: 3              # 聚合文档数量
last_updated: 2026-02-15      # 最后更新时间
stale: false                  # 是否已老化
feedback_score: 4.2/5 (12)    # 反馈评分（评价次数）
```

Web UI 管理后台展示完整详情:来源文档列表、反馈明细、使用频次、版本变更历史。

## AI 模型与成本控制

各环节 AI 模型可配置,提供 preset 降低门槛:

```yaml
ai:
  models:
    summarize: claude-haiku-4-5-20251001    # 文档摘要/分类 — 轻量任务
    cluster: claude-sonnet-4-6              # 领域聚类 — 中等任务
    generate: claude-sonnet-4-6             # skill 生成 — 核心任务
    validate: claude-sonnet-4-6             # 质量验证
    dedup: claude-haiku-4-5-20251001        # 去重比对 — 嵌入向量即可
  defaults:
    preset: balanced          # balanced（推荐） | quality（全 opus） | economy（全 haiku）
```

批量导入时额外优化:
- 阶段一（快速扫描）统一用轻量模型,控制冷启动成本
- 相同领域的文档聚合为一次 API 调用,减少调用次数
- 去重检测优先用嵌入向量比对,仅高相似度时才调用大模型做语义确认

## 技术方案

### 后端: Spring Boot

- 文档解析服务:Apache POI（docx）、PDFBox（pdf）、flexmark（markdown）
- AI 调用:Claude API（文档理解、skill 生成、验证）
- Git 操作:JGit
- 语雀集成:语雀 OpenAPI
- 任务队列:批量文档处理异步化

### 前端: Web UI

- 文档投递:拖拽上传 + URL 粘贴（语雀/Git仓库）
- 领域地图确认:交互式领域归属确认页
- Skill 管理:浏览、搜索、查看详情、标记反馈
- 团队管理:团队订阅配置
- 数据看板:skill 使用统计、反馈汇总、老化预警

### CLI

- `zm-skill submit <path|url>` — 投递文档
- `zm-skill status` — 查看处理状态
- `zm-skill list [--domain] [--team]` — 浏览 skill
- `zm-skill feedback <skill-name> <useful|misleading|outdated>` — 反馈

### Claude Code Plugin

- 全量加载 skill 索引（name + trigger + summary + aliases）
- 智能匹配触发后按需加载完整 skill 内容
- 反馈收集 hooks
- `teams` 配置控制加载范围

## 处理层模块划分

处理层虽然部署为 Spring Boot 单体，但内部按职责划分为四类模块：

| 模块 | 职责 | 对应 Service |
|------|------|-------------|
| Ingestion（接入） | 文档解析、格式标准化、敏感信息预过滤 | ParserFactory, SensitiveInfoFilter |
| Orchestration（编排） | 流程状态机、任务调度、并发控制、重试 | PipelineService |
| Generation（生成） | AI 分类、聚类、skill 生成、术语统一 | ClassificationService, ClusteringService, SkillGenerationService |
| Validation & Publishing（校验与发布） | 格式校验、质量验证、去重检测、入库、Git commit、Plugin 打包 | ValidationService, DeduplicationService, FileSkillRepository, GitService |

## 仓库结构

```
zm-skill-collector/
├── raw/                              # 原始文档区
│   ├── knowledge/
│   │   └── {domain}/                 # 按领域组织
│   │       ├── {doc-name}.md         # 原始文档
│   │       └── ...
│   └── procedure/
│       └── {domain}/
│           └── {skill-name}/
│               └── source.md
│
├── skills/                           # 生成产物（CI 生成,不手动编辑）
│   ├── knowledge/
│   │   └── {domain}.md
│   └── procedure/
│       └── {skill-name}.md
│
├── tests/                            # 验证用例（AI 生成 + 可人工补充）
│   ├── knowledge/
│   │   └── {domain}.test.yaml
│   └── procedure/
│       └── {skill-name}.test.yaml
│
├── glossary/                         # AI 自动生成的术语映射
│   └── {domain}.yaml
│
├── templates/                        # 生成 prompt 模板
│   ├── knowledge-prompt.md
│   └── procedure-prompt.md
│
├── scripts/                          # 构建脚本
│   └── build-plugin.sh
│
├── plugin.json                       # Claude Code plugin 描述
├── server/                           # Spring Boot 后端
├── web/                              # 前端
└── cli/                              # CLI 工具
```

## 验证方案

### 验证流程

验证按以下顺序推进：

1. **梳理用户场景执行路径** — 基于常见场景走一遍系统，生成执行路径文档 `docs/execution-path.md`
2. **构造测试资产与测分** — 基于执行路径，产出测分文档 `docs/test-analysis.md`，包含：
   - 接口测试用例（测试执行方法 + 预期输入 + 预期输出）
   - 端到端全链路测试用例（测试执行方法 + 预期输入 + 预期输出）
3. **Review 测分** — 检查覆盖度、合理性、边界条件，优化后形成终版
4. **自测** — 基于测分用例走完整自测流程，自测通过后提测
5. **提测 → 缺陷管理** — 参考完整需求交付流程：提测报告 → 缺陷记录 → 修复 → 回归

### 测试场景

#### 一、端到端全链路

1. **单文档投递 → Skill 生成**: 上传一份 markdown 文档 → 确认领域地图 → 验证生成的 skill 格式和内容正确
2. **批量导入冷启动**: 上传多份文档(含种子领域) → 验证两阶段流程(快速扫描 → 领域地图确认 → 逐领域生成)
3. **术语统一**: 投递两份使用不同术语的同领域文档 → 验证生成的 skill 术语已统一且 aliases 正确
4. **增量更新**: 已有 knowledge skill 后再投递同领域新文档 → 验证聚合重生成正确
5. **Procedure 覆盖更新**: 更新已有 procedure 的源文档 → 验证覆盖式重生成正确

#### 二、质量验证

6. **敏感信息过滤**: 投递包含 IP/密钥/内部 URL 的文档 → 验证生成的 skill 已移除敏感信息
7. **去重检测**: 投递与已有 skill 高度相似的文档 → 验证系统正确提示合并建议
8. **格式校验**: 验证生成的 skill 满足长度约束（summary ≤ 50字、trigger ≤ 100字、aliases ≤ 10个）
9. **完整度分级**: 验证不同信息量的文档生成对应 L1/L2/L3 等级的 skill

#### 三、消费层

10. **Plugin 加载**: `claude plugins add` 安装 → 验证 skill 索引加载 + 智能匹配触发 + 按需加载完整内容
11. **多团队隔离**: 配置不同 teams 订阅 → 验证只加载 public + 订阅团队的 skill
12. **依赖提示**: 触发一个有 `related_knowledge` 的 procedure skill → 验证未加载依赖时的提示

#### 四、生命周期

13. **老化标记**: 模拟 skill 超期（>6个月未更新）→ 验证标记 `stale: true` 和使用时的提示
14. **反馈回流**: 在 Claude Code 中标记 skill 反馈 → 验证反馈数据在管理后台可见
15. **反馈降级**: 多次"误导"标记 → 验证 skill 自动降级

#### 五、接口测试（后端 API）

每个 REST API 端点的正常/异常/边界用例，具体用例在测分文档中展开，覆盖：
- 文档上传接口（单文件/批量/格式校验/大小限制）
- 语雀 URL 导入接口（有效/无效/权限不足）
- 领域地图查询与确认接口
- Skill CRUD 接口
- 处理状态查询接口
- 反馈提交接口
- 团队/订阅管理接口
