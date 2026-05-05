package cc.utime.marketingagent.domain;

public record CampaignSample(
    String campaignCode,
    String title,
    String playType,
    String couponRule,
    String region,
    int budgetFen,
    String summary) {}
