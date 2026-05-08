package cc.utime.marketingagent.domain;

import java.time.LocalDateTime;
import java.util.List;

/*
 * Trace 审计记录。
 *
 * 生产级 Agent 出问题时，不能只看最终回答，需要回放完整链路：
 * 用户输入、意图识别、RAG 命中、工具调用、模型/模板版本、Schema 校验、业务规则校验和审批结果。
 * 这里用 record 保持 Demo 轻量；真实项目可以落 MySQL/PostgreSQL，并按 requestId 建索引。
 */
public record AgentTrace(
    String traceId,
    String userInput,
    CampaignIntent parsedIntent,
    List<CampaignSample> retrievedSamples,
    List<ToolCall> toolCalls,
    CampaignDraft finalDraft,
    List<ValidationIssue> schemaValidationIssues,
    List<ValidationIssue> validationIssues,
    ApprovalCheckReport approvalReport,
    DraftResult draftResult,
    String status,
    String promptTemplateVersion,
    String modelVersion,
    String knowledgeBaseVersion,
    String assistantMessage,
    LocalDateTime createdAt) {}
