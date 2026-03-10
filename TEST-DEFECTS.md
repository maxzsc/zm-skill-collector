# TEST-DEFECTS.md

## Round 1

| ID | 严重程度 | 关联用例 | 描述 | 复现步骤 | 状态 |
|---|---|---|---|---|---|
| QA-001 | BLOCKER | REL-001, REL-002, REL-003 | `release.json` 发布指针能力未实现，P0 测试直接 SKIP | 1. 成功生成链路检查代码 2. 生成完成后仅 git commit，未写 release 指针 3. P0 集成测试 REL-001/REL-003 被跳过 | init |
| QA-002 | BLOCKER | SEC-003, API-SKD-001, SEC-004 | `GET /api/skills/{name}` 未做可见性过滤，知道 name 即可读取 team skill，团队隔离被绕过 | 1. 仓库放入 team:payment skill 2. 不带 teams 调用 GET /api/skills/{name} 3. 直接返回 200 和正文 | init |
| QA-003 | CRITICAL | API-SUB-007, CON-001 | 幂等判断是"先查再建"非原子流程，并发相同内容上传可能同时 miss 幂等检查并创建两个 submission | 1. 两线程同时 POST /api/submissions 上传相同内容 2. 两边都可能 findByIdempotencyKey() 返回空 3. 各自创建不同 submissionId | init |
| QA-004 | CRITICAL | API-CFM-001, QLT-004, QLT-006 | confirm 路径中 generateSkillForCluster() 无论 validationResult 是否失败都继续保存 skill 并返回 success=true，无效 skill 会被落盘 | 1. 让生成结果返回不合法 skill 2. 调用 confirm 3. 代码继续 save()，submission 走到 completed | init |
| QA-005 | MAJOR | E2E-004, E2E-005, QLT-003 | SkillGenerationService 解析 AI JSON 时只读 name/summary/trigger/aliases/body，丢弃 preconditions/inputs/expected_outputs/verification/sources/related_* 等字段 | 1. AI 返回带完整字段的 JSON 2. 调用生成 3. 返回的 SkillMeta 中这些字段为空 | init |
| QA-006 | MAJOR | API-GLO-001, E2E-001, E2E-002 | 生产代码无 glossary 生成链路。saveGlossary() 存在但 pipeline/generation/update 均未调用，GET /api/glossary/{domain} 只能读预置文件 | 1. 走完整生成流程 2. 检查 glossary/ 3. 无自动产出的术语文件 | init |
| QA-007 | MAJOR | API-ERR-002, API-SUB-003 | 错误响应未统一返回 errorCode：控制器部分分支直接 ApiResponse.error(message)，Bean Validation 失败无统一异常处理 | 1. 触发无文件/空 clusters/非法 feedback 等错误 2. 部分返回无 errorCode，部分不是统一 ApiResponse 结构 | init |
| QA-008 | MAJOR | SUP-002, E2E-005 | Procedure 更新只按新文件名写入 raw，若文件名变化旧 raw 不会删除，无法满足"覆盖旧 raw 文档"预期 | 1. 保存旧 raw refund-sop-v1.md 2. 用 refund-sop-v2.md 调 updateProcedure() 3. raw 目录同时保留 v1 和 v2 | init |
| QA-009 | MAJOR | QLT-007 | AI 质量校验 validateWithAi() 存在但未接入主流程，pipeline 只调 validate()，score > 0.7 门槛在生产路径不生效 | 1. 检查 confirm/quick path 2. 只走 validate(skillDoc) 3. AI 校验分数只在单测里使用 | init |
| QA-010 | MAJOR | REL-001, CON-001, SEC-001 | P0 集成测试存在反模式：有问题直接 SKIP；并发幂等只测查 key 不测创建；Prompt Injection 只断言字符串不验证真实生成结果 (Mock ALL CLEAR) | 1. 阅读 P0IntegrationTest 覆盖表与实际断言 2. 多处仅验证 mock/常量/辅助方法而非真实链路 | init |
| QA-011 | MINOR | LIF-003 | StalenessService.decorateBody() 是工具方法但未接入 GET /api/skills/{name} 读取路径，stale skill 返回原始 body 不注入过时提示 | 1. 保存 stale=true skill 2. 调用 GET /api/skills/{name} 3. 正文无"内容可能已过时"提示 | init |
