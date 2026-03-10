# ZM Skill Collector Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Build a complete skill collection system that converts business documents into Claude Code consumable skills, with Web UI, CLI, and plugin distribution.

**Architecture:** Spring Boot backend handles document parsing → AI processing → skill generation → git storage. React + Ant Design frontend for submission and management. Node.js CLI for command-line operations. Claude Code plugin for skill consumption.

**Tech Stack:** Java 17 + Spring Boot 3.2, React 18 + Ant Design 5, Node.js CLI, Apache POI, PDFBox, flexmark-java, JGit, Claude API (Anthropic Java SDK)

**Execution model:** New session + worktree + parallel agents. Codex for code review and test coverage.

---

## Phase 1: Project Scaffolding & Domain Model

### Task 1: Initialize Spring Boot backend

**Files:**
- Create: `server/pom.xml`
- Create: `server/src/main/java/com/zm/skill/SkillCollectorApplication.java`
- Create: `server/src/main/resources/application.yml`
- Create: `server/.gitignore`

**Step 1: Create pom.xml**

```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>
    <parent>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-parent</artifactId>
        <version>3.2.3</version>
    </parent>
    <groupId>com.zm</groupId>
    <artifactId>skill-collector</artifactId>
    <version>0.1.0-SNAPSHOT</version>

    <properties>
        <java.version>17</java.version>
    </properties>

    <dependencies>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-web</artifactId>
        </dependency>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-validation</artifactId>
        </dependency>
        <dependency>
            <groupId>org.apache.poi</groupId>
            <artifactId>poi-ooxml</artifactId>
            <version>5.2.5</version>
        </dependency>
        <dependency>
            <groupId>org.apache.pdfbox</groupId>
            <artifactId>pdfbox</artifactId>
            <version>3.0.1</version>
        </dependency>
        <dependency>
            <groupId>com.vladsch.flexmark</groupId>
            <artifactId>flexmark-all</artifactId>
            <version>0.64.8</version>
        </dependency>
        <dependency>
            <groupId>org.eclipse.jgit</groupId>
            <artifactId>org.eclipse.jgit</artifactId>
            <version>6.8.0.202311291450-r</version>
        </dependency>
        <dependency>
            <groupId>com.fasterxml.jackson.dataformat</groupId>
            <artifactId>jackson-dataformat-yaml</artifactId>
        </dependency>
        <dependency>
            <groupId>org.projectlombok</groupId>
            <artifactId>lombok</artifactId>
            <optional>true</optional>
        </dependency>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-test</artifactId>
            <scope>test</scope>
        </dependency>
    </dependencies>

    <build>
        <plugins>
            <plugin>
                <groupId>org.springframework.boot</groupId>
                <artifactId>spring-boot-maven-plugin</artifactId>
                <configuration>
                    <excludes>
                        <exclude>
                            <groupId>org.projectlombok</groupId>
                            <artifactId>lombok</artifactId>
                        </exclude>
                    </excludes>
                </configuration>
            </plugin>
        </plugins>
    </build>
</project>
```

**Step 2: Create application entry point and config**

```java
// server/src/main/java/com/zm/skill/SkillCollectorApplication.java
package com.zm.skill;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class SkillCollectorApplication {
    public static void main(String[] args) {
        SpringApplication.run(SkillCollectorApplication.class, args);
    }
}
```

```yaml
# server/src/main/resources/application.yml
server:
  port: 8080

spring:
  servlet:
    multipart:
      max-file-size: 50MB
      max-request-size: 100MB

skill-collector:
  storage:
    base-path: ${SKILL_STORAGE_PATH:./skill-repo}
  ai:
    api-key: ${ANTHROPIC_API_KEY:}
    models:
      summarize: claude-haiku-4-5-20251001
      cluster: claude-sonnet-4-6
      generate: claude-sonnet-4-6
      validate: claude-sonnet-4-6
      dedup: claude-haiku-4-5-20251001
    preset: balanced
  staleness:
    threshold-months: 6
```

**Step 3: Verify build**

Run: `cd server && mvn compile -q`
Expected: BUILD SUCCESS

**Step 4: Commit**

```bash
git add server/
git commit -m "feat: initialize Spring Boot backend project"
```

---

### Task 2: Define domain model

**Files:**
- Create: `server/src/main/java/com/zm/skill/domain/SkillType.java`
- Create: `server/src/main/java/com/zm/skill/domain/Completeness.java`
- Create: `server/src/main/java/com/zm/skill/domain/ProcedureCategory.java`
- Create: `server/src/main/java/com/zm/skill/domain/AgentReadiness.java`
- Create: `server/src/main/java/com/zm/skill/domain/Visibility.java`
- Create: `server/src/main/java/com/zm/skill/domain/SkillMeta.java`
- Create: `server/src/main/java/com/zm/skill/domain/Submission.java`
- Create: `server/src/main/java/com/zm/skill/domain/ProcessingStatus.java`
- Create: `server/src/main/java/com/zm/skill/domain/DomainCluster.java`
- Test: `server/src/test/java/com/zm/skill/domain/SkillMetaTest.java`

**Step 1: Write failing test**

```java
package com.zm.skill.domain;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.assertj.core.api.Assertions.assertThat;

class SkillMetaTest {

    private final ObjectMapper yaml = new ObjectMapper(new YAMLFactory());

    @Test
    void shouldRoundTripKnowledgeMeta() throws Exception {
        SkillMeta meta = SkillMeta.builder()
            .name("payment-clearing").type(SkillType.KNOWLEDGE)
            .domain("payment").trigger("用户提到清算、对账")
            .aliases(List.of("清算", "结算", "清分"))
            .summary("支付清算核心规则").completeness(Completeness.L2)
            .visibility(Visibility.parse("public"))
            .sources(List.of("arch.md")).relatedSkills(List.of("risk-rules"))
            .build();

        String s = yaml.writeValueAsString(meta);
        SkillMeta d = yaml.readValue(s, SkillMeta.class);
        assertThat(d.getName()).isEqualTo("payment-clearing");
        assertThat(d.getType()).isEqualTo(SkillType.KNOWLEDGE);
        assertThat(d.getAliases()).containsExactly("清算", "结算", "清分");
    }

    @Test
    void shouldRoundTripProcedureMeta() throws Exception {
        SkillMeta meta = SkillMeta.builder()
            .name("refund-flow").type(SkillType.PROCEDURE)
            .category(ProcedureCategory.BIZ_OPERATION).domain("payment")
            .visibility(Visibility.parse("team:payment"))
            .agentReadiness(AgentReadiness.FUTURE).summary("退款流程")
            .relatedKnowledge(List.of("payment-clearing"))
            .build();

        String s = yaml.writeValueAsString(meta);
        SkillMeta d = yaml.readValue(s, SkillMeta.class);
        assertThat(d.getVisibility().isTeam()).isTrue();
        assertThat(d.getVisibility().getTeamName()).isEqualTo("payment");
    }

    @Test
    void shouldValidateLengthConstraints() {
        assertThat(SkillMeta.isValidSummary("短摘要")).isTrue();
        assertThat(SkillMeta.isValidSummary("x".repeat(51))).isFalse();
        assertThat(SkillMeta.isValidTrigger("x".repeat(100))).isTrue();
        assertThat(SkillMeta.isValidTrigger("x".repeat(101))).isFalse();
        assertThat(SkillMeta.isValidAliases(List.of("a","b","c"))).isTrue();
        assertThat(SkillMeta.isValidAliases(
            List.of("1","2","3","4","5","6","7","8","9","10","11")
        )).isFalse();
    }
}
```

**Step 2: Run test — expect FAIL**

**Step 3: Implement all domain classes**

Enums (`SkillType`, `Completeness`, `ProcedureCategory`, `AgentReadiness`) use `@JsonValue` for serialization. `Visibility` has `parse(String)` / `toValue()`. `SkillMeta` is `@Builder` with static validation methods. `Submission` tracks upload state. `DomainCluster` represents the domain map. `ProcessingStatus` is the state machine enum.

See design doc `docs/design.md` "Skill 内容模型" section for exact field definitions.

**Step 4: Run test — expect PASS**

**Step 5: Commit**

```bash
git commit -m "feat: define domain model with YAML serialization"
```

---

## Phase 2: Document Parsing

### Task 3: Implement document parsers

**Files:**
- Create: `server/src/main/java/com/zm/skill/parser/DocumentParser.java` (interface)
- Create: `server/src/main/java/com/zm/skill/parser/MarkdownParser.java`
- Create: `server/src/main/java/com/zm/skill/parser/DocxParser.java`
- Create: `server/src/main/java/com/zm/skill/parser/PdfParser.java`
- Create: `server/src/main/java/com/zm/skill/parser/HtmlParser.java`
- Create: `server/src/main/java/com/zm/skill/parser/ParserFactory.java`
- Test: `server/src/test/java/com/zm/skill/parser/DocumentParserTest.java`
- Fixtures: `server/src/test/resources/fixtures/sample.md`, etc.

**Step 1: Write failing test**

```java
package com.zm.skill.parser;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class DocumentParserTest {
    @Test
    void shouldParseMarkdown() {
        String content = new MarkdownParser().parse("# Title\n\nBody text\n\n- item");
        assertThat(content).contains("Title").contains("Body text").contains("item");
    }

    @Test
    void shouldParseHtml() {
        String content = new HtmlParser().parse("<h1>Title</h1><p>Body</p>");
        assertThat(content).contains("Title").contains("Body");
    }

    @Test
    void shouldSelectParserByExtension() {
        ParserFactory f = new ParserFactory();
        assertThat(f.getParser("a.md")).isInstanceOf(MarkdownParser.class);
        assertThat(f.getParser("b.docx")).isInstanceOf(DocxParser.class);
        assertThat(f.getParser("c.pdf")).isInstanceOf(PdfParser.class);
        assertThat(f.getParser("d.html")).isInstanceOf(HtmlParser.class);
    }
}
```

**Step 2: Implement**

- `DocumentParser`: interface with `parse(String)` and `parseFile(InputStream)`
- `MarkdownParser`: strip and return (already text)
- `DocxParser`: Apache POI `XWPFDocument` → extract paragraphs
- `PdfParser`: PDFBox `PDDocument` → `PDFTextStripper`
- `HtmlParser`: flexmark `FlexmarkHtmlConverter` → markdown
- `ParserFactory`: extension → parser mapping

**Step 3: Run test — PASS → Commit**

```bash
git commit -m "feat: implement document parsers (md/docx/pdf/html)"
```

---

## Phase 3: Storage Layer

### Task 4: Implement file-based skill repository

**Files:**
- Create: `server/src/main/java/com/zm/skill/storage/SkillRepository.java` (interface)
- Create: `server/src/main/java/com/zm/skill/storage/FileSkillRepository.java`
- Create: `server/src/main/java/com/zm/skill/storage/SkillFileFormat.java`
- Test: `server/src/test/java/com/zm/skill/storage/FileSkillRepositoryTest.java`

Skill file format = YAML front matter + `---` + markdown body.

**Step 1: Write failing test**

```java
package com.zm.skill.storage;

import com.zm.skill.domain.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Path;
import java.util.List;
import static org.assertj.core.api.Assertions.assertThat;

class FileSkillRepositoryTest {
    @TempDir Path tempDir;

    @Test
    void shouldSaveAndLoadSkill() {
        var repo = new FileSkillRepository(tempDir);
        SkillMeta meta = SkillMeta.builder()
            .name("payment-clearing").type(SkillType.KNOWLEDGE)
            .domain("payment").summary("清算规则")
            .visibility(Visibility.parse("public")).build();
        repo.save(meta, "# 清算知识\n\n正文内容");

        assertThat(tempDir.resolve("skills/knowledge/payment.md")).exists();
        var loaded = repo.findByName("payment-clearing");
        assertThat(loaded).isPresent();
        assertThat(loaded.get().getBody()).contains("正文内容");
    }

    @Test
    void shouldSaveRawDocument() {
        var repo = new FileSkillRepository(tempDir);
        repo.saveRaw("payment", SkillType.KNOWLEDGE, "doc1.md", "raw content");
        assertThat(tempDir.resolve("raw/knowledge/payment/doc1.md")).exists();
    }

    @Test
    void shouldLoadIndex() {
        var repo = new FileSkillRepository(tempDir);
        repo.save(SkillMeta.builder().name("s1").type(SkillType.KNOWLEDGE)
            .domain("d1").summary("s1").visibility(Visibility.parse("public")).build(), "b1");
        repo.save(SkillMeta.builder().name("s2").type(SkillType.PROCEDURE)
            .domain("d2").summary("s2").visibility(Visibility.parse("team:t")).build(), "b2");

        List<SkillMeta> index = repo.loadIndex();
        assertThat(index).hasSize(2);
    }

    @Test
    void shouldSaveGlossary() {
        var repo = new FileSkillRepository(tempDir);
        repo.saveGlossary("payment", java.util.Map.of("清算", List.of("结算", "清分")));
        assertThat(tempDir.resolve("glossary/payment.yaml")).exists();
    }
}
```

**Step 2: Implement → Step 3: PASS → Commit**

```bash
git commit -m "feat: implement file-based skill repository with YAML front matter format"
```

---

### Task 5: Implement Git auto-commit service

**Files:**
- Create: `server/src/main/java/com/zm/skill/storage/GitService.java`
- Test: `server/src/test/java/com/zm/skill/storage/GitServiceTest.java`

JGit: init if needed, add, commit on every skill save/update.

```bash
git commit -m "feat: add Git auto-commit service via JGit"
```

---

## Phase 4: AI Integration

### Task 6: Implement Claude API client

**Files:**
- Create: `server/src/main/java/com/zm/skill/ai/ClaudeClient.java`
- Create: `server/src/main/java/com/zm/skill/ai/AiModelConfig.java`
- Test: `server/src/test/java/com/zm/skill/ai/ClaudeClientTest.java` (WireMock)

Wraps Anthropic Messages API. `AiModelConfig` reads `skill-collector.ai.*` from application.yml. Exposes `call(model, systemPrompt, userMessage)` → returns assistant text.

```bash
git commit -m "feat: implement Claude API client with configurable model routing"
```

---

### Task 7: Implement document classification

**Files:**
- Create: `server/src/main/java/com/zm/skill/service/ClassificationService.java`
- Create: `server/src/main/java/com/zm/skill/ai/prompts/ClassificationPrompt.java`
- Test: `server/src/test/java/com/zm/skill/service/ClassificationServiceTest.java`

Input: document text → Output: `{type, domain, category, doc_type, confidence, summary_preview}`

Prompt enforces JSON output. Test uses mocked ClaudeClient.

```bash
git commit -m "feat: implement AI document classification"
```

---

### Task 8: Implement domain clustering

**Files:**
- Create: `server/src/main/java/com/zm/skill/service/ClusteringService.java`
- Test: `server/src/test/java/com/zm/skill/service/ClusteringServiceTest.java`

Two-phase: phase 1 reads first 500 chars (haiku), phase 2 full clustering (sonnet). Supports seed domain. Outputs `List<DomainCluster>`.

```bash
git commit -m "feat: implement two-phase domain clustering"
```

---

### Task 9: Implement sensitive info filter

**Files:**
- Create: `server/src/main/java/com/zm/skill/service/SensitiveInfoFilter.java`
- Test: `server/src/test/java/com/zm/skill/service/SensitiveInfoFilterTest.java`

Regex patterns for: IP addresses, DB connection strings, credentials/tokens, internal URLs, cloud access keys. Replaces with `{FILTERED}`.

```java
// Test cases
@Test void shouldFilterIpAddresses()    { assertFiltered("连接 10.0.1.5:3306 数据库"); }
@Test void shouldFilterDbUrls()          { assertFiltered("jdbc:mysql://10.0.1.5/db"); }
@Test void shouldFilterTokens()          { assertFiltered("api_key=sk-abc123def456"); }
@Test void shouldFilterInternalUrls()    { assertFiltered("访问 http://192.168.1.100/admin"); }
@Test void shouldPreserveNormalContent() { assertNotFiltered("清算规则是 T+1"); }
```

```bash
git commit -m "feat: implement sensitive info regex filter"
```

---

### Task 10: Implement skill generation

**Files:**
- Create: `server/src/main/java/com/zm/skill/service/SkillGenerationService.java`
- Create: `server/src/main/java/com/zm/skill/ai/prompts/KnowledgePrompt.java`
- Create: `server/src/main/java/com/zm/skill/ai/prompts/ProcedurePrompt.java`
- Test: `server/src/test/java/com/zm/skill/service/SkillGenerationServiceTest.java`

Key behaviors:
- Knowledge: aggregate multiple docs → one skill, AI merges and unifies terminology
- Procedure: single doc → one skill with steps/preconditions/verification
- Enforce: summary ≤ 50 chars, trigger ≤ 100 chars, aliases ≤ 10
- Run SensitiveInfoFilter on output
- Generate prompt references `templates/knowledge-prompt.md` and `templates/procedure-prompt.md`

```bash
git commit -m "feat: implement AI skill generation with length constraints"
```

---

### Task 11: Implement quality validation

**Files:**
- Create: `server/src/main/java/com/zm/skill/service/ValidationService.java`
- Test: `server/src/test/java/com/zm/skill/service/ValidationServiceTest.java`

Validates:
1. Required fields per completeness level (L1/L2/L3)
2. Length constraints
3. Knowledge: AI Q&A test (ask 3 questions → verify answers reference skill content)
4. Procedure: AI step simulation (verify no dead ends, has termination)
5. Auto-determine completeness level

```bash
git commit -m "feat: implement skill quality validation"
```

---

### Task 12: Implement deduplication

**Files:**
- Create: `server/src/main/java/com/zm/skill/service/DeduplicationService.java`
- Test: `server/src/test/java/com/zm/skill/service/DeduplicationServiceTest.java`

Compares new skill vs existing skills in same domain. Lightweight keyword similarity first (haiku), flag if > 70% similar. Returns merge suggestions.

```bash
git commit -m "feat: implement skill dedup detection"
```

---

## Phase 5: Processing Pipeline

### Task 13: Orchestrate end-to-end pipeline

**Files:**
- Create: `server/src/main/java/com/zm/skill/service/PipelineService.java`
- Create: `server/src/main/java/com/zm/skill/service/PipelineResult.java`
- Test: `server/src/test/java/com/zm/skill/service/PipelineServiceTest.java`

Orchestrates: submit → parse → classify → cluster → (await confirm) → generate → validate → dedup → save → git commit.

`@Async` for background processing. `Submission.status` tracks state machine.

```java
public class PipelineService {
    // Phase 1: submit + scan → return DomainMap for confirmation
    public DomainMap submitAndScan(Submission submission) { ... }

    // Phase 2: user confirms → generate + validate + save
    public List<PipelineResult> confirmAndGenerate(String submissionId, DomainMap confirmed) { ... }

    // Quick path for single document
    public PipelineResult submitSingle(Submission submission) { ... }
}
```

```bash
git commit -m "feat: implement end-to-end processing pipeline"
```

---

## Phase 6: REST API

### Task 14: Implement REST controllers

**Files:**
- Create: `server/src/main/java/com/zm/skill/controller/SubmissionController.java`
- Create: `server/src/main/java/com/zm/skill/controller/SkillController.java`
- Create: `server/src/main/java/com/zm/skill/controller/FeedbackController.java`
- Create: `server/src/main/java/com/zm/skill/controller/dto/SubmitRequest.java`
- Create: `server/src/main/java/com/zm/skill/controller/dto/SubmitResponse.java`
- Create: `server/src/main/java/com/zm/skill/controller/dto/DomainMapResponse.java`
- Create: `server/src/main/java/com/zm/skill/controller/dto/ConfirmRequest.java`
- Test: `server/src/test/java/com/zm/skill/controller/SubmissionControllerTest.java`
- Test: `server/src/test/java/com/zm/skill/controller/SkillControllerTest.java`

**Endpoints:**

```
POST   /api/submissions              — 上传文件 (multipart)
POST   /api/submissions/yuque        — 语雀 URL 导入
GET    /api/submissions/{id}/status   — 处理状态
GET    /api/submissions/{id}/domain-map — 领域地图
POST   /api/submissions/{id}/confirm  — 确认领域地图

GET    /api/skills                    — 列表 (?domain=&team=&type=)
GET    /api/skills/{name}             — 详情 (meta + body)
GET    /api/skills/index              — 全量索引 (plugin 用)

POST   /api/feedback                  — 提交反馈
GET    /api/feedback/stats            — 反馈统计

GET    /api/glossary/{domain}         — 术语映射
```

MockMvc tests for each endpoint.

```bash
git commit -m "feat: implement REST API controllers"
```

---

## Phase 7: Web UI (React + Ant Design)

### Task 15: Initialize frontend project

**Files:**
- Create: `web/` (Vite + React + TypeScript scaffold)
- Create: `web/src/api/client.ts` (axios instance)

```bash
cd web && npm create vite@latest . -- --template react-ts
npm install antd @ant-design/icons axios react-router-dom
```

```bash
git commit -m "feat: initialize React + Ant Design frontend"
```

---

### Task 16: Implement document submission page

**Files:**
- Create: `web/src/pages/SubmitPage.tsx`

Features:
- `Upload.Dragger` for file drag-drop (md/docx/pdf)
- Input for URL paste (语雀/Git repo)
- Optional description and seed domain fields
- Submit → show progress → redirect to domain map

```bash
git commit -m "feat: implement document submission page"
```

---

### Task 17: Implement domain map confirmation page

**Files:**
- Create: `web/src/pages/DomainMapPage.tsx`

Features:
- Card grid grouped by domain
- Confidence badge (high=green, medium=yellow, low=red)
- Each card: domain name, doc count, type, preview
- Adjust/confirm actions → triggers generation

```bash
git commit -m "feat: implement domain map confirmation page"
```

---

### Task 18: Implement skill management & dashboard

**Files:**
- Create: `web/src/pages/SkillListPage.tsx`
- Create: `web/src/pages/SkillDetailPage.tsx`
- Create: `web/src/pages/DashboardPage.tsx`
- Create: `web/src/App.tsx` (routing)

Features:
- Skill list: search, filter by domain/team/type, stale badge
- Detail: meta card + markdown body render + feedback buttons
- Dashboard: usage stats charts, feedback summary, stale alerts, recent submissions

```bash
git commit -m "feat: implement skill management pages and dashboard"
```

---

## Phase 8: CLI Tool

### Task 19: Implement CLI

**Files:**
- Create: `cli/package.json`
- Create: `cli/src/index.ts`
- Create: `cli/src/commands/submit.ts`
- Create: `cli/src/commands/status.ts`
- Create: `cli/src/commands/list.ts`
- Create: `cli/src/commands/feedback.ts`
- Create: `cli/tsconfig.json`

Tech: commander + chalk + ora + axios

```
zm-skill submit <path|url>           → POST /api/submissions
zm-skill submit --yuque <url>        → POST /api/submissions/yuque
zm-skill status [id]                 → GET /api/submissions/{id}/status
zm-skill list [--domain] [--team]    → GET /api/skills
zm-skill feedback <name> <rating>    → POST /api/feedback
```

```bash
git commit -m "feat: implement CLI tool"
```

---

## Phase 9: Claude Code Plugin

### Task 20: Implement plugin packaging

**Files:**
- Create: `plugin.json`
- Create: `scripts/build-plugin.sh`
- Create: `templates/knowledge-prompt.md`
- Create: `templates/procedure-prompt.md`

`plugin.json` = Claude Code plugin manifest. `build-plugin.sh` fetches skill index from server API, packages skills + templates into distributable plugin.

Plugin behavior:
- Load all skill index (name + trigger + summary + aliases) at startup
- On user message: match against triggers/aliases → load full skill body on demand
- `teams` config filters visibility

```bash
git commit -m "feat: implement Claude Code plugin packaging"
```

---

## Phase 10: Advanced Features

### Task 21: Staleness detection

Scheduled `@Scheduled` task: scan skills, mark `stale: true` if > N months without update. Inject warning header into stale skill body.

```bash
git commit -m "feat: implement staleness detection"
```

### Task 22: Feedback system

Store feedback per skill (JSON). Aggregate scores. Auto-degrade on high "misleading" count.

```bash
git commit -m "feat: implement feedback collection and auto-degradation"
```

### Task 23: Knowledge aggregation update

On new doc submission to existing domain: re-aggregate all domain docs → regenerate knowledge skill. Procedure: overwrite and regenerate.

```bash
git commit -m "feat: implement skill update strategies (aggregate/overwrite)"
```

### Task 24: Multi-team visibility

Filter skills in API and plugin by `visibility` + team subscription config.

```bash
git commit -m "feat: implement multi-team visibility filtering"
```

---

## Parallel Execution Strategy

```
Agent A (Backend Core)     Agent B (Web UI)          Agent C (CLI + Plugin)
─────────────────────      ──────────────────        ─────────────────────
Task 1: Scaffolding        (blocked)                 (blocked)
Task 2: Domain model       (blocked)                 (blocked)
Task 3: Parsers            (blocked)                 (blocked)
Task 4: Storage            (blocked)                 (blocked)
Task 5: Git service        (blocked)                 (blocked)
Task 6: Claude client      (blocked)                 (blocked)
Task 7: Classification     (blocked)                 (blocked)
Task 8: Clustering         (blocked)                 (blocked)
Task 9: Sensitive filter   (blocked)                 (blocked)
Task 10: Skill generation  (blocked)                 (blocked)
Task 11: Validation        (blocked)                 (blocked)
Task 12: Dedup             (blocked)                 (blocked)
Task 13: Pipeline          (blocked)                 (blocked)
Task 14: REST API          Task 15: Init frontend    Task 19: CLI
  ↓ (API contract ready)  Task 16: Submit page      Task 20: Plugin
                           Task 17: Domain map
                           Task 18: Skill mgmt

Task 21-24: Advanced features (after core is stable)
```

**Unblock point:** Once Task 14 (REST API) commits, Agent B and C can start in parallel — they only depend on the API contract.

**Codex integration:** After each phase, Codex reviews code and generates test coverage for untested paths. Specifically:
- Phase 1-5 完成后: Codex review domain model + services, supplement edge case tests
- Phase 6 完成后: Codex review API layer, generate integration test suite
- Phase 7-9 完成后: Codex review frontend/CLI/plugin, add E2E test scaffolding
