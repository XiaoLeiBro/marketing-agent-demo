package cc.utime.marketingagent.validation;

import cc.utime.marketingagent.domain.CampaignDraft;
import cc.utime.marketingagent.domain.ValidationIssue;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
public class CampaignSchemaValidator {

  /*
   * JSON Schema 等价校验层。
   *
   * 这个 Demo 没有引入额外 JSON Schema 依赖，而是用确定性 Java 代码模拟第一层结构校验。
   * 它只判断“活动草稿像不像一个合法 JSON 配置”：必填字段、正数、时间范围、审批状态。
   * 预算是否合理、库存是否充足、人群是否冲突则交给 CampaignPolicyValidator。
   */
  public List<ValidationIssue> validate(CampaignDraft draft) {
    List<ValidationIssue> issues = new ArrayList<>();
    if (!StringUtils.hasText(draft.campaignName())) {
      issues.add(ValidationIssue.blocker("SCHEMA_CAMPAIGN_NAME_REQUIRED", "活动名称不能为空"));
    }
    if (!StringUtils.hasText(draft.region())) {
      issues.add(ValidationIssue.blocker("SCHEMA_REGION_REQUIRED", "活动区域不能为空"));
    }
    if (!StringUtils.hasText(draft.audienceCode())) {
      issues.add(ValidationIssue.blocker("SCHEMA_AUDIENCE_REQUIRED", "人群包编码不能为空"));
    }
    if (!StringUtils.hasText(draft.couponRule())) {
      issues.add(ValidationIssue.blocker("SCHEMA_COUPON_RULE_REQUIRED", "券规则不能为空"));
    }
    if (draft.budgetFen() <= 0) {
      issues.add(ValidationIssue.blocker("SCHEMA_BUDGET_POSITIVE", "预算必须大于 0"));
    }
    if (draft.perUserLimit() <= 0) {
      issues.add(ValidationIssue.blocker("SCHEMA_PER_USER_LIMIT_POSITIVE", "单用户限领数量必须大于 0"));
    }
    if (draft.startTime() == null || draft.endTime() == null) {
      issues.add(ValidationIssue.blocker("SCHEMA_TIME_REQUIRED", "活动开始和结束时间不能为空"));
    }
    else if (!draft.endTime().isAfter(draft.startTime())) {
      issues.add(ValidationIssue.blocker("SCHEMA_TIME_RANGE_INVALID", "活动结束时间必须晚于开始时间"));
    }
    if (!"PENDING_REVIEW".equals(draft.approvalStatus())) {
      issues.add(ValidationIssue.blocker("SCHEMA_APPROVAL_STATUS_INVALID", "Agent 只能生成 PENDING_REVIEW 草稿"));
    }
    return issues;
  }
}
