package cc.utime.marketingagent.domain;

import java.util.List;

public record CreateDraftResponse(
    String traceId,
    String status,
    CampaignDraft draft,
    List<ValidationIssue> validationIssues,
    List<ToolCall> toolCalls) {}
