package cc.utime.marketingagent.domain;

import java.time.LocalDateTime;
import java.util.List;

public record CampaignIntent(
    IntentType intentType,
    String region,
    String audienceLabel,
    List<String> audienceTags,
    String couponRule,
    int budgetFen,
    int perUserLimit,
    LocalDateTime startTime,
    LocalDateTime endTime,
    int shareRewardFen) {}
