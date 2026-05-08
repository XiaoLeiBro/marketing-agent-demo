# 营销活动配置 Agent 代码讲解

这份说明用于把面试准备文档里的架构，映射到当前可运行 Demo 的代码。

## 结论

当前工程是按《营销活动配置 Agent（运营辅助工具）》文档的主线开发的，但它是面试展示 Demo，不是生产级营销中台。已经覆盖：

- 意图识别：`CampaignIntentParser` 输出 `IntentType`
- RAG 检索模拟：`KnowledgeBase` / `InMemoryKnowledgeBase`
- Tool Calling 模拟：`MarketingToolbox`
- 结构化草稿：`CampaignIntent` -> `CampaignDraft`
- JSON Schema 等价校验：`CampaignSchemaValidator`
- 业务规则链：`CampaignPolicyValidator`
- 审批前检查报告：`ApprovalCheckReport`
- trace 审计：`AgentTrace` / `TraceRepository`
- 人工审批后写操作：`approveDraft` 后才调用 `createCampaignDraft`

## 主链路怎么讲

入口是 `MarketingAgentController#createDraft`，Controller 不拼 Prompt，也不写业务规则，只把请求交给 `MarketingAgentService`。

`MarketingAgentService#createDraft` 是核心编排：

1. 调用 `CampaignIntentParser` 识别意图。
2. 如果是查询意图，只调用只读工具，不生成草稿。
3. 如果是规则问答，只检索知识库并返回解释。
4. 如果是创建或检查活动，先检索历史活动样例。
5. 调用 `MarketingToolbox` 查询人群、库存和时间冲突。
6. 把解析结果组装成 `CampaignDraft`。
7. 先执行 `CampaignSchemaValidator`，再执行 `CampaignPolicyValidator`。
8. 生成 `ApprovalCheckReport`，把风险项、工具结果和相似案例展示给审批人。
9. 保存 `AgentTrace`，用于回放和审计。

面试中可以概括为：

> 模型负责理解和生成，RAG 提供业务上下文，Tool Calling 查询实时数据，后端规则链做确定性校验，人工审批控制写操作，trace 保证可回放。

## 为什么不是自动投放

`createDraft` 阶段只会返回 `PENDING_REVIEW` 或 `VALIDATION_FAILED`，不会创建营销系统活动。

真正的写操作在 `MarketingAgentService#approveDraft`：

- 只有 trace 状态是 `PENDING_REVIEW` 才能审批。
- 审批后调用 `MarketingToolbox#createCampaignDraft`。
- 返回状态是 `DRAFT`，仍然不是线上生效活动。
- 后续还要 `confirmCampaign` 进入 `PENDING_EFFECTIVE`，再由 `activateCampaign` 模拟定时生效。

这体现文档里的读写隔离：Agent 可以辅助生成草稿，但不能直接发布活动。

## Schema 校验和业务规则校验的区别

`CampaignSchemaValidator` 对应文档里的 JSON Schema 校验，关心字段结构：

- 活动名称是否为空
- 区域、人群包、券规则是否存在
- 预算和限领数量是否为正数
- 开始结束时间是否合理
- Agent 生成结果是否只能是 `PENDING_REVIEW`

`CampaignPolicyValidator` 对应业务规则链，关心业务风险：

- 券规则缺失
- 人群包不存在
- 时间冲突
- 预算超过人工审批阈值
- 预算覆盖券数超过人群规模
- 券库存不足
- 分享奖励偏高

面试时要强调：Schema 只能兜字段格式，资损风险必须靠确定性业务规则。

## Tool Calling 怎么防风险

`MarketingToolbox` 里有两类方法：

- 只读工具：`queryAudience`、`queryStock`、`checkTimeConflict`
- 写工具：`createCampaignDraft`、`confirmCampaign`、`activateCampaign`

`ToolCall` 记录了 `accessMode` 和 `writeOperation`。创建草稿时，trace 里的工具调用都是 `READ_ONLY`；只有人工审批后，才会出现 `APPROVAL_GATED_WRITE`。

这就是面试里要讲的工具白名单和写操作审批边界。

## Trace 里为什么要记录版本

`AgentTrace` 里记录：

- `promptTemplateVersion`
- `modelVersion`
- `knowledgeBaseVersion`
- `schemaValidationIssues`
- `validationIssues`
- `approvalReport`
- `assistantMessage`

原因是 Agent 结果会受 Prompt、模型和知识库影响。生产排查时，要能回答“这次结果是哪个模型、哪个 Prompt、哪些知识命中、哪些工具返回导致的”。

## 本地验证命令

```bash
mvn test
```

创建活动草稿：

```bash
curl -X POST http://localhost:8080/api/marketing-agent/drafts \
  -H 'Content-Type: application/json' \
  -d '{"requirement":"福建新用户满30减5预算10万"}'
```

规则问答分流：

```bash
curl -X POST http://localhost:8080/api/marketing-agent/drafts \
  -H 'Content-Type: application/json' \
  -d '{"requirement":"为什么新用户活动必须走人工审批规则？"}'
```

只读查询分流：

```bash
curl -X POST http://localhost:8080/api/marketing-agent/drafts \
  -H 'Content-Type: application/json' \
  -d '{"requirement":"查询福建新用户满30减5库存多少"}'
```

## 面试防守口径

如果被问“这个项目是不是生产上线”，建议这样说：

> 这是结合营销中台场景做的工程化 Demo 复现，核心链路是可运行的，包括 RAG、Tool Calling、结构化草稿、Schema 校验、业务规则链、审批边界和 trace 审计。生产落地时需要把内存知识库替换为向量检索，把 mock 工具替换为真实营销中台只读接口，把 trace 和草稿落到数据库，并接入真实审批流。

如果被问“你具体写了什么代码”，建议这样说：

> 我主要做的是 Java 后端的 Agent 编排链路，不是训练模型。代码上包括自然语言意图解析、RAG 样例检索、只读工具封装、活动草稿结构化建模、Schema 和业务规则校验、审批前报告生成，以及 trace 审计记录。重点是把 LLM 放到可控业务流程里，而不是让模型直接操作生产活动。
