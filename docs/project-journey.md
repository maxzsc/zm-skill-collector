# 从想法到落地 — ZM Skill Collector 项目历程

## 1. 起点：一个想法

团队长期面临一个痛点：业务知识散落在语雀文档、代码仓库 README、架构设计文档、SOP 手册等各个角落。新人上手需要大量时间翻阅历史文档，老人也经常在不同系统间反复搜索。更关键的是，这些知识无法直接被 AI 工具消费——Claude Code 作为日常开发的核心助手，却对团队的业务上下文一无所知。

目标很明确：**让 AI 自动将散落的业务文档转化为 Claude Code 可消费的 skill，实现投递 → 转换 → 验证 → 分发的完整闭环**。投递门槛要极低（拖个文件、贴个 URL），AI 全自动完成转换，团队成员通过 Claude Code 插件即时获取。

---

## 2. 系统设计（Phase 1）

### 架构决策

系统采用四层架构：

```
投递层 → 处理层 → 存储层 → 消费层
```

- **投递层**：Web UI（拖拽上传/语雀 URL）+ CLI（本地文档/命令行操作）+ 代码仓库 Git 集成
- **处理层**：Spring Boot 单体，内部按职责划分为 Ingestion / Orchestration / Generation / Validation & Publishing 四个模块
- **存储层**：Git 仓库（raw/ + skills/ + tests/ + glossary/），版本管理完全依赖 Git 历史
- **消费层**：Claude Code Plugin，全量加载索引 + 智能匹配触发 + 按需加载正文

### Skill 分类体系

将业务知识分为两大类：
- **knowledge**（业务知识）：领域内完整的信息、规则、上下文，累积性增长，多文档聚合生成
- **procedure**（业务技能）：可执行的操作流程，按 dev/ops/test/biz-operation/analysis/collaboration 细分

### 内容模型设计

Skill 文件采用 YAML front matter + Markdown body 的格式。元数据包含 name、type、domain、trigger、aliases、summary、completeness、visibility 等字段，正文为结构化的知识或步骤描述。

关键约束写入设计：summary ≤ 50 字、trigger ≤ 100 字、aliases ≤ 10 个——这些直接影响 Plugin 全量加载时的上下文窗口消耗。

### 设计文档先落盘再开工

这是整个项目的关键原则。`docs/design.md` 作为系统设计的单一真相来源，所有后续的实现计划、代码审查、测试设计都以此为基准。设计文档不是事后补的，而是先写清楚再动手。

---

## 3. 方案 Review（Phase 2）

### 引入 Codex 做独立审查

设计完成后，没有直接开工，而是引入 OpenAI Codex 作为独立审查者。这是一个有意的选择——用不同的 AI 模型来审查 Claude 产出的设计，避免思维惯性。

审查采用**多轮 battle 模式**，共进行 3 轮，保持 session 上下文。每轮 Codex 提出问题和建议，Claude 逐条回应：采纳、不采纳（给出理由）、或有条件同意。

### 8 个维度的 Review

审查覆盖 8 个维度：
1. 架构合理性
2. 接口设计
3. 数据模型
4. 处理流程
5. 异常处理
6. 安全性
7. 可扩展性
8. 可测试性

### 核心共识

3 轮 battle 后达成共识：
- **16 条 P0 问题**：必须在开发前修复（含逻辑模型补充、模块职责划分、验证方案等）
- **10 条 P1 问题**：开发中修复
- **若干 deferred 项**：记录但不阻塞当前版本

### 代码级改动清单

Review 结论不是笼统的"建议改进"，而是精确到代码级的改动清单，每条包含：
- 模块
- 现状
- 目标
- 原因
- 置信度

这种精确度确保了后续修复不会遗漏或误改。

---

## 4. 并行开发（Phase 3）

### 实现计划

基于设计文档和 Review 结论，制定了 24 个 Task、10 个 Phase 的实现计划。

计划的核心思路是识别**解锁点**：Task 14（REST API Controllers）是关键分水岭——前 13 个 Task 构建后端核心能力（domain model → parsers → storage → AI integration → pipeline），Task 14 定义 API 契约后，前端和 CLI 可以并行启动。

### 三路并行 Agent

开发阶段采用三路并行 Agent 模式：

```
Agent A (Backend Core)     Agent B (Web UI)          Agent C (CLI + Plugin)
─────────────────────      ──────────────────        ─────────────────────
Task 1-14: 后端核心         Task 15-18: 前端          Task 19-20: CLI + Plugin
  ↓ (API contract ready)    ↓                         ↓
Task 21-24: 高级功能
```

Agent A 完成 Task 14 后，Agent B 和 Agent C 同时启动。三个 Agent 使用 **worktree 隔离开发**，各自在独立的 Git worktree 中工作，避免代码冲突。

### 开发成果

并行开发阶段产出：
- **109 个文件**
- **~13,500 行代码**
- **134 个测试用例**
- **32 次 Git 提交**

---

## 5. 方案 Review 修复

并行开发完成后，回头处理 Phase 2 审查中识别的问题。

### P0 修复（16 条）

包含代码和文档两个层面的修复：
- 逻辑模型定义补充到设计文档（Job/Source/Skill 增强模型）
- 处理层模块划分写入设计文档
- `schema_version` 字段实现
- `status` 字段生命周期实现
- `GlobalExceptionHandler` 统一错误处理
- `UrlValidator` SSRF 防护
- `VisibilityFilter` 团队隔离
- `AuditService` 审计日志
- Prompt Injection 防护
- 并发安全增强
- 其他代码健壮性修复

### P1 修复（9 条）

- 去重阈值三段式细化
- 反馈降级阈值明确化
- 老化检测边界处理
- 错误码枚举统一
- 其他

### 测试增长

修复过程中补充了新的测试用例，测试从 **134 → 153** 个。

---

## 6. 测试执行循环（Phase 4）

### 测试计划产出

测试计划不是一个人闭门造车，而是 Claude 和 Codex 协作产出。

先由 Claude 基于设计文档和实际代码产出 TEST-PLAN.md 初稿，然后交给 Codex 审查。**经过 2 轮对齐**，Codex 补充了多项遗漏（幂等边界、审计日志覆盖、release.json 测试、schema_version 测试等），最终形成覆盖 9 个维度、65+ 用例的测试方案。

关键的测试基线确认：Codex 阅读实际代码后，纠正了测试计划中对当前实现的假设偏差（比如 yuque 接口实际返回 501、幂等键依赖文件顺序等）。

### P0 自测

30 个 P0 用例：
- 28 个 auto 通过
- 2 个 manual（REL-003 Plugin 加载验证、E2E-002 批量冷启动部分步骤）

### Codex 全量测试

将 TEST-PLAN.md 交给 Codex 执行全量测试（阅读代码 + 对照用例逐条验证）。

**Round 1 发现 11 条缺陷**（QA-001 ~ QA-011）：

| 严重程度 | 数量 | 典型问题 |
|----------|------|----------|
| BLOCKER | 2 | release.json 未实现、skill 详情接口团队隔离被绕过 |
| CRITICAL | 2 | 幂等判断非原子、校验失败仍落盘 |
| MAJOR | 6 | 字段解析丢失、glossary 未接入、错误码不统一、procedure raw 不清理、AI 校验未接入主流程、P0 测试反模式 |
| MINOR | 1 | stale 提示未注入读取路径 |

### 修复循环

**Round 1 修复**：逐条修复 11 个缺陷。修复内容包括：
- 实现 `ReleaseService` 完整的发布指针能力
- `SkillController` 增加可见性过滤
- 幂等检查改为 `computeIfAbsent` 原子操作
- `PipelineService` 校验失败不落盘
- `SkillGenerationService` 解析全部字段
- `PipelineService` 和 `SkillUpdateService` 接入 glossary 生成
- `GlobalExceptionHandler` 统一错误码
- `SkillUpdateService` 精确清理 raw
- AI 校验接入主流程
- P0 集成测试重写

**Round 2 回归**：Codex 回归测试发现 4 条残留问题（QA-004b, QA-006b, QA-007b, QA-008b）——主要是 Round 1 修复不彻底导致的路径遗漏（更新路径未接入校验、更新路径未接入 glossary、404 响应缺 errorCode、raw 清理误删同 domain 其他 procedure）。

**Round 2 修复**：全部修复完成。

### 最终状态

- **163 个测试用例**
- **15 个缺陷全部 closed**
- **0 个 open defect**

---

## 7. 交付

### 交付产物

- 交付摘要文档（`docs/delivery-summary.md`）
- 项目历程文档（`docs/project-journey.md`）
- 完整代码仓库（37 次提交）
- TEST-PLAN.md + TEST-DEFECTS.md

### Phase 5

交付文档整理完成后上传，项目进入维护阶段。

---

## 8. 关键方法论沉淀

### 8.1 delivery-workflow skill：5 个 Phase 的标准化流程

本项目实践了一套完整的 AI 协作交付流程，可沉淀为可复用的方法论：

| Phase | 内容 | 产出 |
|-------|------|------|
| Phase 1 | 系统设计 | `docs/design.md` |
| Phase 2 | 方案 Review（Codex 多轮 battle） | P0/P1 改动清单 |
| Phase 3 | 并行开发（多 Agent + worktree） | 代码 + 测试 |
| Phase 4 | 测试执行（Claude-Codex 协作） | TEST-PLAN.md + TEST-DEFECTS.md |
| Phase 5 | 交付文档 + 上传 | 交付摘要 + 项目历程 |

### 8.2 Codex 保持上下文的多轮 battle 模式

Codex 的 Review 不是一次性的，而是保持 session 上下文的多轮对话。每轮 Codex 都能引用之前的讨论，这使得审查深度远超单次 prompt。

关键实践：
- 第 1 轮：全局扫描，识别架构和设计层面的问题
- 第 2 轮：深入代码层面，逐模块审查
- 第 3 轮：聚焦边界和异常路径

### 8.3 代码审查只看实际调用链，不看注释

Codex 测试执行时的一个重要发现：代码中有方法存在（如 `validateWithAi()`、`saveGlossary()`、`decorateBody()`），注释也写得正确，但**实际调用链中根本没有被调用**。

教训：代码审查必须追踪实际调用链，而不是看到方法存在就认为功能已实现。

### 8.4 改动清单精确到代码级

Phase 2 的 Review 结论不是"建议增加错误处理"这种笼统描述，而是：

> 模块：PipelineService
> 现状：confirm 路径校验失败仍 save
> 目标：校验失败时跳过 save，返回 success=false
> 原因：QA-004
> 置信度：高

这种精确度让修复执行零歧义。

### 8.5 设计方案先落盘再开工

`docs/design.md` 在第一次 Git 提交时就存在。所有后续工作——实现计划、代码审查、测试设计——都以这份文档为基准。当 Review 发现设计缺陷时，先修改设计文档，再修改代码。

### 8.6 验证要求写入设计文档

设计文档不仅描述"要做什么"，还描述"怎么验证"。`docs/design.md` 的验证方案章节明确列出了 15 个测试场景和 5 类接口测试，这些在 Phase 4 成为 TEST-PLAN.md 的基础。

---

## 9. 数据统计

| 指标 | 数值 |
|------|------|
| 代码行数（开发完成时） | ~13,500 行 |
| 代码行数（Review 修复后） | ~9,700+ 行（重构精简 + 测试代码增长） |
| 源文件总数 | 92 个 |
| 测试用例（开发完成时） | 134 个 |
| 测试用例（P0/P1 修复后） | 153 个 |
| 测试用例（最终） | 163 个 |
| 缺陷发现 | 15 个（Round 1: 11, Round 2: 4） |
| 缺陷修复 | 15 个（100% closed） |
| 缺陷严重程度 | 2 BLOCKER + 4 CRITICAL + 8 MAJOR + 1 MINOR |
| Git 提交 | 37 次 |
| 实现计划 Task 数 | 24 个，10 个 Phase |
| REST API 端点 | 12 个 |
| Web 页面 | 5 个 |
| CLI 命令 | 4 个 |
| Codex 协作 sessions | 4 次（Review 3 轮 + 测试计划 2 轮 + 测试执行 + 回归） |
| 并行开发 Agent | 3 路（Backend Core / Web UI / CLI+Plugin） |
| 设计 Review 维度 | 8 个 |
| Review 共识条目 | 16 P0 + 10 P1 + deferred |
| 测试覆盖维度 | 9 个（接口/E2E/质量/生命周期/安全/并发/发布/Schema/审计） |
| 测试用例总数（TEST-PLAN.md） | 65+ 个 |
| 项目周期 | 1 天（从设计到交付） |
