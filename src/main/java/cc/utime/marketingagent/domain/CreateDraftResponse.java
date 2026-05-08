package cc.utime.marketingagent.domain;

import java.util.List;

public record CreateDraftResponse(
    String traceId,
    IntentType intentType,
    String status,
    String assistantMessage,
    CampaignDraft draft,
    ApprovalCheckReport approvalReport,
    List<ValidationIssue> schemaValidationIssues,
    List<ValidationIssue> validationIssues,
    List<ToolCall> toolCalls) {}
