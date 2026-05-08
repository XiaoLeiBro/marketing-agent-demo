package cc.utime.marketingagent.domain;

import java.util.List;

/*
 * 审批前检查报告。
 *
 * 这个对象对应文档里的“审批前检查报告应该包含什么”：
 * 活动摘要、关键字段、风险项、相似历史活动、工具查询结果、置信度和修改建议。
 * 它的作用是让审批人理解“为什么生成这个草稿、风险在哪里”，而不是只面对一段 JSON。
 */
public record ApprovalCheckReport(
    String activitySummary,
    List<String> keyFields,
    List<String> riskItems,
    List<String> similarReferences,
    List<String> toolResultSummaries,
    String confidence,
    List<String> suggestions) {

  public static ApprovalCheckReport from(
      CampaignDraft draft,
      List<CampaignSample> samples,
      List<ToolCall> toolCalls,
      List<ValidationIssue> issues) {
    List<String> risks = issues.stream()
        .map(issue -> (issue.blocker() ? "阻断：" : "提醒：") + issue.code() + " - " + issue.message())
        .toList();
    List<String> suggestions = issues.isEmpty()
        ? List.of("当前草稿可进入人工审批，审批通过后再创建营销系统 DRAFT 活动。")
        : issues.stream().map(ValidationIssue::message).toList();
    return new ApprovalCheckReport(
        draft.campaignName() + "，区域：" + draft.region() + "，券规则：" + draft.couponRule(),
        List.of(
            "活动时间：" + draft.startTime() + " ~ " + draft.endTime(),
            "人群包：" + draft.audienceCode(),
            "预算：" + draft.budgetFen() + " 分",
            "单用户限领：" + draft.perUserLimit()),
        risks,
        samples.stream().map(sample -> sample.campaignCode() + " " + sample.title()).toList(),
        toolCalls.stream().map(call -> call.name() + " => " + call.outputSummary()).toList(),
        samples.isEmpty() ? "LOW" : "MEDIUM",
        suggestions);
  }

  public static ApprovalCheckReport readonlyAnswer(
      String summary,
      List<CampaignSample> samples,
      List<ToolCall> toolCalls) {
    return new ApprovalCheckReport(
        summary,
        List.of(),
        List.of(),
        samples.stream().map(sample -> sample.campaignCode() + " " + sample.title()).toList(),
        toolCalls.stream().map(call -> call.name() + " => " + call.outputSummary()).toList(),
        samples.isEmpty() ? "LOW" : "MEDIUM",
        List.of("这是只读查询/规则解释结果，不会创建活动草稿，也不会触发写操作。"));
  }
}
