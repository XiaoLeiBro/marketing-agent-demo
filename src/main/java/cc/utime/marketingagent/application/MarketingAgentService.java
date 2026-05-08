package cc.utime.marketingagent.application;

import cc.utime.marketingagent.domain.ApprovalCheckReport;
import cc.utime.marketingagent.domain.AgentTrace;
import cc.utime.marketingagent.domain.AudienceInfo;
import cc.utime.marketingagent.domain.CampaignDraft;
import cc.utime.marketingagent.domain.CampaignIntent;
import cc.utime.marketingagent.domain.CampaignSample;
import cc.utime.marketingagent.domain.ConflictInfo;
import cc.utime.marketingagent.domain.CreateDraftResponse;
import cc.utime.marketingagent.domain.DraftResult;
import cc.utime.marketingagent.domain.IntentType;
import cc.utime.marketingagent.domain.StockInfo;
import cc.utime.marketingagent.domain.ToolCall;
import cc.utime.marketingagent.domain.ValidationIssue;
import cc.utime.marketingagent.parser.CampaignIntentParser;
import cc.utime.marketingagent.rag.KnowledgeBase;
import cc.utime.marketingagent.tool.MarketingToolbox;
import cc.utime.marketingagent.trace.TraceRepository;
import cc.utime.marketingagent.validation.CampaignSchemaValidator;
import cc.utime.marketingagent.validation.CampaignPolicyValidator;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class MarketingAgentService {

  /*
   * 这是整个 Demo 的 Agent 编排层，对应面试文档里的“Agent 服务 / Orchestrator”。
   *
   * 这里故意不把逻辑放到 Controller：
   * 1. Controller 只接收 HTTP 请求；
   * 2. Service 负责任务分流、RAG 检索、工具调用、结构化草稿生成、确定性校验和 trace 记录；
   * 3. 写操作只出现在 approveDraft 之后，用代码边界体现“模型不能直接创建正式活动”。
   */
  private static final String PROMPT_TEMPLATE_VERSION = "marketing-agent-demo-v1";
  private static final String MODEL_VERSION = "mock-rule-parser-or-spring-ai";
  private static final String KNOWLEDGE_BASE_VERSION = "in-memory-samples-v1";

  private final CampaignIntentParser requirementParser;
  private final KnowledgeBase knowledgeBase;
  private final MarketingToolbox marketingToolbox;
  private final CampaignSchemaValidator schemaValidator;
  private final CampaignPolicyValidator policyValidator;
  private final TraceRepository traceRepository;
  private final ConcurrentMap<String, DraftResult> campaignDrafts = new ConcurrentHashMap<>();

  public MarketingAgentService(
      CampaignIntentParser requirementParser,
      KnowledgeBase knowledgeBase,
      MarketingToolbox marketingToolbox,
      CampaignSchemaValidator schemaValidator,
      CampaignPolicyValidator policyValidator,
      TraceRepository traceRepository) {
    this.requirementParser = requirementParser;
    this.knowledgeBase = knowledgeBase;
    this.marketingToolbox = marketingToolbox;
    this.schemaValidator = schemaValidator;
    this.policyValidator = policyValidator;
    this.traceRepository = traceRepository;
  }

  public CreateDraftResponse createDraft(String requirement) {
    if (!StringUtils.hasText(requirement)) {
      throw new IllegalArgumentException("活动需求不能为空");
    }

    List<ToolCall> toolCalls = new ArrayList<>();
    CampaignIntent intent = this.requirementParser.parse(requirement);

    // 第一步：意图识别。不是所有输入都应该走“生成活动草稿”链路。
    // 查询类请求只调用只读工具；规则问答只走知识检索；创建/检查类请求才进入草稿生成与校验。
    if (intent.intentType() == IntentType.QUERY_ACTIVITY) {
      return answerQuery(requirement, intent, toolCalls);
    }
    if (intent.intentType() == IntentType.RULE_QA) {
      return answerRuleQuestion(requirement, intent, toolCalls);
    }

    // 第二步：RAG 检索。这里用内存样例模拟历史活动/运营手册/规则文档。
    // 面试时可以说明：生产环境可替换为 pgvector、Elasticsearch hybrid search 或公司内部知识库。
    List<CampaignSample> samples = this.knowledgeBase.searchSimilarCampaigns(requirement, 3);
    toolCalls.add(ToolCall.read("searchSimilarCampaigns", "topK=3", "matched=" + samples.size()));

    // 第三步：Tool Calling。这里全部是只读工具，用于查询人群、库存和时间冲突。
    // 模型只“建议查什么”，真正执行、参数校验和权限控制应由后端工具层负责。
    AudienceInfo audienceInfo = this.marketingToolbox.queryAudience(intent.audienceTags(), intent.region());
    toolCalls.add(ToolCall.read("queryAudience", intent.region(), audienceInfo.audienceCode()));

    StockInfo stockInfo = this.marketingToolbox.queryStock(intent.couponRule());
    toolCalls.add(ToolCall.read("queryStock", intent.couponRule(), "available=" + stockInfo.availableQuantity()));

    ConflictInfo conflictInfo =
        this.marketingToolbox.checkTimeConflict(intent.startTime(), intent.endTime(), intent.region());
    toolCalls.add(ToolCall.read("checkTimeConflict", intent.region(), conflictInfo.reason()));

    // 第四步：结构化输出。Demo 中由规则解析器/LLM 解析后的 intent 组装为 CampaignDraft。
    // 真实接 LLM 时，模型输出也必须转成这个结构，而不是让运营复制一段自然语言。
    CampaignDraft draft = CampaignDraft.from(intent, audienceInfo);
    // 第五步：先做 JSON Schema 等价校验，再做业务规则链校验。
    // Schema 关心字段是否完整、类型/枚举/时间范围是否基本合法；业务规则关心预算、库存、人群和冲突。
    List<ValidationIssue> schemaIssues = this.schemaValidator.validate(draft);
    List<ValidationIssue> policyIssues =
        this.policyValidator.validate(draft, audienceInfo, stockInfo, conflictInfo);
    List<ValidationIssue> allIssues = new ArrayList<>(schemaIssues);
    allIssues.addAll(policyIssues);

    String status = allIssues.stream().anyMatch(ValidationIssue::blocker)
        ? "VALIDATION_FAILED"
        : intent.intentType() == IntentType.CHECK_ACTIVITY ? "CHECK_PASSED" : "PENDING_REVIEW";
    // 第六步：审批前检查报告。审批人不应该只看 JSON，还要看到风险项、相似案例和工具查询依据。
    ApprovalCheckReport approvalReport = ApprovalCheckReport.from(draft, samples, toolCalls, allIssues);
    String assistantMessage = buildDraftMessage(status, allIssues);

    String traceId = UUID.randomUUID().toString();
    AgentTrace trace =
        new AgentTrace(
            traceId,
            requirement,
            intent,
            samples,
            toolCalls,
            draft,
            schemaIssues,
            policyIssues,
            approvalReport,
            null,
            status,
            PROMPT_TEMPLATE_VERSION,
            MODEL_VERSION,
            KNOWLEDGE_BASE_VERSION,
            assistantMessage,
            LocalDateTime.now());
    this.traceRepository.save(trace);
    return new CreateDraftResponse(
        traceId,
        intent.intentType(),
        status,
        assistantMessage,
        draft,
        approvalReport,
        schemaIssues,
        policyIssues,
        toolCalls);
  }

  public DraftResult approveDraft(String traceId) {
    AgentTrace trace = this.getTrace(traceId);
    if (!"PENDING_REVIEW".equals(trace.status())) {
      throw new IllegalStateException("只有 PENDING_REVIEW 状态的 Agent 草稿可以审批创建活动");
    }
    // 只有人工审批通过后，才允许调用受控写接口创建营销系统 DRAFT 活动。
    // 这对应文档里的“Agent 生成候选配置，最终决策交给人工审批”。
    DraftResult draftResult = this.marketingToolbox.createCampaignDraft(trace.finalDraft());
    this.campaignDrafts.put(draftResult.draftId(), draftResult);

    List<ToolCall> toolCalls = new ArrayList<>(trace.toolCalls());
    toolCalls.add(ToolCall.write("createCampaignDraft", trace.finalDraft().campaignName(), draftResult.draftId()));
    AgentTrace updatedTrace =
        new AgentTrace(
            trace.traceId(),
            trace.userInput(),
            trace.parsedIntent(),
            trace.retrievedSamples(),
            toolCalls,
            trace.finalDraft(),
            trace.schemaValidationIssues(),
            trace.validationIssues(),
            trace.approvalReport(),
            draftResult,
            draftResult.status(),
            trace.promptTemplateVersion(),
            trace.modelVersion(),
            trace.knowledgeBaseVersion(),
            "人工审批通过，已调用受控写接口创建营销系统 DRAFT 活动。",
            trace.createdAt());
    this.traceRepository.save(updatedTrace);
    return draftResult;
  }

  public DraftResult confirmCampaign(String draftId) {
    DraftResult current = this.findCampaignDraft(draftId);
    if (!"DRAFT".equals(current.status())) {
      throw new IllegalStateException("只有 DRAFT 状态的活动可以二次确认");
    }
    DraftResult confirmed = this.marketingToolbox.confirmCampaign(draftId);
    this.campaignDrafts.put(draftId, confirmed);
    return confirmed;
  }

  public DraftResult activateCampaign(String draftId) {
    DraftResult current = this.findCampaignDraft(draftId);
    if (!"PENDING_EFFECTIVE".equals(current.status())) {
      throw new IllegalStateException("只有 PENDING_EFFECTIVE 状态的活动可以到点生效");
    }
    DraftResult activated = this.marketingToolbox.activateCampaign(draftId);
    this.campaignDrafts.put(draftId, activated);
    return activated;
  }

  public AgentTrace getTrace(String traceId) {
    return this.traceRepository.findById(traceId)
        .orElseThrow(() -> new IllegalArgumentException("trace 不存在：" + traceId));
  }

  public List<CampaignSample> searchSamples(String query, int topK) {
    return this.knowledgeBase.searchSimilarCampaigns(query, topK);
  }

  private DraftResult findCampaignDraft(String draftId) {
    DraftResult result = this.campaignDrafts.get(draftId);
    if (result == null) {
      throw new IllegalArgumentException("活动草稿不存在：" + draftId);
    }
    return result;
  }

  private CreateDraftResponse answerQuery(
      String requirement,
      CampaignIntent intent,
      List<ToolCall> toolCalls) {
    // 查询意图只允许访问只读工具；即使用户输入里出现“创建/发布”等词，也不在这里执行写操作。
    StockInfo stockInfo = this.marketingToolbox.queryStock(intent.couponRule());
    toolCalls.add(ToolCall.read("queryStock", intent.couponRule(), "available=" + stockInfo.availableQuantity()));
    AudienceInfo audienceInfo = this.marketingToolbox.queryAudience(intent.audienceTags(), intent.region());
    toolCalls.add(ToolCall.read("queryAudience", intent.region(), audienceInfo.audienceCode()));
    List<CampaignSample> samples = this.knowledgeBase.searchSimilarCampaigns(requirement, 2);
    toolCalls.add(ToolCall.read("searchSimilarCampaigns", "topK=2", "matched=" + samples.size()));
    String message = "已完成只读查询：券库存 " + stockInfo.availableQuantity() + "，人群包 "
        + audienceInfo.audienceCode() + "，未创建活动草稿。";
    return saveReadonlyTrace(requirement, intent, samples, toolCalls, "QUERY_ANSWERED", message);
  }

  private CreateDraftResponse answerRuleQuestion(
      String requirement,
      CampaignIntent intent,
      List<ToolCall> toolCalls) {
    List<CampaignSample> samples = this.knowledgeBase.searchSimilarCampaigns(requirement, 3);
    toolCalls.add(ToolCall.read("searchSimilarCampaigns", "topK=3", "matched=" + samples.size()));
    String message = "规则解释：Agent 可以参考历史活动和规则文档给出建议，但最终必须经过 Schema 校验、业务规则链和人工审批。";
    return saveReadonlyTrace(requirement, intent, samples, toolCalls, "RULE_QA_ANSWERED", message);
  }

  private CreateDraftResponse saveReadonlyTrace(
      String requirement,
      CampaignIntent intent,
      List<CampaignSample> samples,
      List<ToolCall> toolCalls,
      String status,
      String message) {
    String traceId = UUID.randomUUID().toString();
    ApprovalCheckReport report = ApprovalCheckReport.readonlyAnswer(message, samples, toolCalls);
    AgentTrace trace = new AgentTrace(
        traceId,
        requirement,
        intent,
        samples,
        toolCalls,
        null,
        List.of(),
        List.of(),
        report,
        null,
        status,
        PROMPT_TEMPLATE_VERSION,
        MODEL_VERSION,
        KNOWLEDGE_BASE_VERSION,
        message,
        LocalDateTime.now());
    this.traceRepository.save(trace);
    return new CreateDraftResponse(
        traceId,
        intent.intentType(),
        status,
        message,
        null,
        report,
        List.of(),
        List.of(),
        toolCalls);
  }

  private String buildDraftMessage(String status, List<ValidationIssue> issues) {
    if ("VALIDATION_FAILED".equals(status)) {
      return "活动草稿存在阻断问题，需运营补充或修改后重新生成。";
    }
    if ("CHECK_PASSED".equals(status)) {
      return "配置检查通过，当前结果仅用于审批前风险复核，不会直接创建正式活动。";
    }
    boolean hasWarnings = issues.stream().anyMatch(issue -> !issue.blocker());
    return hasWarnings
        ? "活动草稿已生成，包含需要人工审批关注的风险提醒。"
        : "活动草稿已生成，可进入人工审批。";
  }
}
