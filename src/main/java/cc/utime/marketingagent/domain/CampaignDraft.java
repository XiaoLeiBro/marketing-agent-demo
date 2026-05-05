package cc.utime.marketingagent.domain;

import java.time.LocalDateTime;

public record CampaignDraft(
    String campaignName,
    String region,
    String audienceCode,
    String couponRule,
    int budgetFen,
    int perUserLimit,
    LocalDateTime startTime,
    LocalDateTime endTime,
    boolean shareRewardEnabled,
    int shareRewardFen,
    String approvalStatus) {

  public static CampaignDraft from(CampaignIntent intent, AudienceInfo audienceInfo) {
    return new CampaignDraft(
        intent.region() + intent.audienceLabel() + intent.couponRule() + "活动",
        intent.region(),
        audienceInfo.audienceCode(),
        intent.couponRule(),
        intent.budgetFen(),
        intent.perUserLimit(),
        intent.startTime(),
        intent.endTime(),
        intent.shareRewardFen() > 0,
        intent.shareRewardFen(),
        "PENDING_REVIEW");
  }
}
