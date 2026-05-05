package cc.utime.marketingagent.domain;

import java.time.LocalDateTime;
import java.util.List;

public record AgentTrace(
    String traceId,
    String userInput,
    CampaignIntent parsedIntent,
    List<CampaignSample> retrievedSamples,
    List<ToolCall> toolCalls,
    CampaignDraft finalDraft,
    List<ValidationIssue> validationIssues,
    DraftResult draftResult,
    String status,
    LocalDateTime createdAt) {}
