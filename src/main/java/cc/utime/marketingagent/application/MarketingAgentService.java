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
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class MarketingAgentService {

  private final CampaignIntentParser requirementParser;
  private final KnowledgeBase knowledgeBase;
  private final MarketingToolbox marketingToolbox;
  private final CampaignPolicyValidator policyValidator;
  private final TraceRepository traceRepository;

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
        : "PENDING_APPROVAL";
    DraftResult draftResult = null;
    if ("PENDING_APPROVAL".equals(status)) {
      draftResult = this.marketingToolbox.createCampaignDraft(draft);
      toolCalls.add(ToolCall.of("createCampaignDraft", draft.campaignName(), draftResult.draftId()));
    }

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
            draftResult,
            status,
            LocalDateTime.now());
    this.traceRepository.save(trace);
    return new CreateDraftResponse(traceId, status, draft, issues, toolCalls);
  }

  public AgentTrace getTrace(String traceId) {
    return this.traceRepository.findById(traceId)
        .orElseThrow(() -> new IllegalArgumentException("trace 不存在：" + traceId));
  }

  public List<CampaignSample> searchSamples(String query, int topK) {
    return this.knowledgeBase.searchSimilarCampaigns(query, topK);
  }
}
