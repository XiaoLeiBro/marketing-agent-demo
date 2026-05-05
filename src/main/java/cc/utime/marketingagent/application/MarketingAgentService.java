package cc.utime.marketingagent.application;

import cc.utime.marketingagent.domain.AgentTrace;
import cc.utime.marketingagent.domain.AudienceInfo;
import cc.utime.marketingagent.domain.CampaignDraft;
import cc.utime.marketingagent.domain.CampaignIntent;
import cc.utime.marketingagent.domain.CampaignSample;
import cc.utime.marketingagent.domain.ConflictInfo;
import cc.utime.marketingagent.domain.CreateDraftResponse;
import cc.utime.marketingagent.domain.DraftResult;
import cc.utime.marketingagent.domain.StockInfo;
import cc.utime.marketingagent.domain.ToolCall;
import cc.utime.marketingagent.domain.ValidationIssue;
import cc.utime.marketingagent.parser.CampaignIntentParser;
import cc.utime.marketingagent.rag.KnowledgeBase;
import cc.utime.marketingagent.tool.MarketingToolbox;
import cc.utime.marketingagent.trace.TraceRepository;
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

  private final CampaignIntentParser requirementParser;
  private final KnowledgeBase knowledgeBase;
  private final MarketingToolbox marketingToolbox;
  private final CampaignPolicyValidator policyValidator;
  private final TraceRepository traceRepository;
  private final ConcurrentMap<String, DraftResult> campaignDrafts = new ConcurrentHashMap<>();

  public MarketingAgentService(
      CampaignIntentParser requirementParser,
      KnowledgeBase knowledgeBase,
      MarketingToolbox marketingToolbox,
      CampaignPolicyValidator policyValidator,
      TraceRepository traceRepository) {
    this.requirementParser = requirementParser;
    this.knowledgeBase = knowledgeBase;
    this.marketingToolbox = marketingToolbox;
    this.policyValidator = policyValidator;
    this.traceRepository = traceRepository;
  }

  public CreateDraftResponse createDraft(String requirement) {
    if (!StringUtils.hasText(requirement)) {
      throw new IllegalArgumentException("活动需求不能为空");
    }

    List<ToolCall> toolCalls = new ArrayList<>();
    CampaignIntent intent = this.requirementParser.parse(requirement);

    List<CampaignSample> samples = this.knowledgeBase.searchSimilarCampaigns(requirement, 3);
    toolCalls.add(ToolCall.of("searchSimilarCampaigns", "topK=3", "matched=" + samples.size()));

    AudienceInfo audienceInfo = this.marketingToolbox.queryAudience(intent.audienceTags(), intent.region());
    toolCalls.add(ToolCall.of("queryAudience", intent.region(), audienceInfo.audienceCode()));

    StockInfo stockInfo = this.marketingToolbox.queryStock(intent.couponRule());
    toolCalls.add(ToolCall.of("queryStock", intent.couponRule(), "available=" + stockInfo.availableQuantity()));

    ConflictInfo conflictInfo =
        this.marketingToolbox.checkTimeConflict(intent.startTime(), intent.endTime(), intent.region());
    toolCalls.add(ToolCall.of("checkTimeConflict", intent.region(), conflictInfo.reason()));

    CampaignDraft draft = CampaignDraft.from(intent, audienceInfo);
    List<ValidationIssue> issues =
        this.policyValidator.validate(draft, audienceInfo, stockInfo, conflictInfo);

    String status = issues.stream().anyMatch(ValidationIssue::blocker)
        ? "VALIDATION_FAILED"
        : "PENDING_REVIEW";

    String traceId = UUID.randomUUID().toString();
    AgentTrace trace =
        new AgentTrace(
            traceId,
            requirement,
            intent,
            samples,
            toolCalls,
            draft,
            issues,
            null,
            status,
            LocalDateTime.now());
    this.traceRepository.save(trace);
    return new CreateDraftResponse(traceId, status, draft, issues, toolCalls);
  }

  public DraftResult approveDraft(String traceId) {
    AgentTrace trace = this.getTrace(traceId);
    if (!"PENDING_REVIEW".equals(trace.status())) {
      throw new IllegalStateException("只有 PENDING_REVIEW 状态的 Agent 草稿可以审批创建活动");
    }
    DraftResult draftResult = this.marketingToolbox.createCampaignDraft(trace.finalDraft());
    this.campaignDrafts.put(draftResult.draftId(), draftResult);

    List<ToolCall> toolCalls = new ArrayList<>(trace.toolCalls());
    toolCalls.add(ToolCall.of("createCampaignDraft", trace.finalDraft().campaignName(), draftResult.draftId()));
    AgentTrace updatedTrace =
        new AgentTrace(
            trace.traceId(),
            trace.userInput(),
            trace.parsedIntent(),
            trace.retrievedSamples(),
            toolCalls,
            trace.finalDraft(),
            trace.validationIssues(),
            draftResult,
            draftResult.status(),
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
}
