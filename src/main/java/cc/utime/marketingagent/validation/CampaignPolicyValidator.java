package cc.utime.marketingagent.validation;

import cc.utime.marketingagent.domain.AudienceInfo;
import cc.utime.marketingagent.domain.CampaignDraft;
import cc.utime.marketingagent.domain.ConflictInfo;
import cc.utime.marketingagent.domain.StockInfo;
import cc.utime.marketingagent.domain.ValidationIssue;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class CampaignPolicyValidator {

  private static final int APPROVAL_THRESHOLD_FEN = 50_000 * 100;

  public List<ValidationIssue> validate(
      CampaignDraft draft,
      AudienceInfo audienceInfo,
      StockInfo stockInfo,
      ConflictInfo conflictInfo) {
    List<ValidationIssue> issues = new ArrayList<>();
    if ("待补充券规则".equals(draft.couponRule())) {
      issues.add(ValidationIssue.blocker("COUPON_RULE_MISSING", "券规则缺失，请补充满减或无门槛规则"));
    }
    if (!audienceInfo.exists()) {
      issues.add(ValidationIssue.blocker("AUDIENCE_NOT_FOUND", "人群包不存在"));
    }
    if (conflictInfo.conflict()) {
      issues.add(ValidationIssue.blocker("TIME_CONFLICT", conflictInfo.reason()));
    }
    if (draft.budgetFen() > APPROVAL_THRESHOLD_FEN) {
      issues.add(ValidationIssue.warning("MANUAL_APPROVAL_REQUIRED", "预算超过 5 万，必须进入人工审批"));
    }
    int maxCoupons = draft.budgetFen() / Math.max(1, stockInfo.unitPriceFen());
    long maxAudienceCoupons = audienceInfo.estimatedUsers() * draft.perUserLimit();
    if (maxCoupons > maxAudienceCoupons) {
      issues.add(ValidationIssue.warning("BUDGET_OVER_AUDIENCE", "预算可覆盖券数超过人群规模，请确认预算或人群范围"));
    }
    if (stockInfo.availableQuantity() < maxCoupons) {
      issues.add(ValidationIssue.blocker("STOCK_NOT_ENOUGH", "券库存不足，无法覆盖预算"));
    }
    if (draft.shareRewardFen() >= stockInfo.unitPriceFen()) {
      issues.add(ValidationIssue.warning("SHARE_REWARD_REVIEW", "分享奖励金额偏高，请运营负责人复核"));
    }
    return issues;
  }
}
