package cc.utime.marketingagent.tool;

import cc.utime.marketingagent.domain.AudienceInfo;
import cc.utime.marketingagent.domain.CampaignDraft;
import cc.utime.marketingagent.domain.ConflictInfo;
import cc.utime.marketingagent.domain.DraftResult;
import cc.utime.marketingagent.domain.StockInfo;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
public class MarketingToolbox {

  public AudienceInfo queryAudience(List<String> tags, String region) {
    boolean newUser = tags.stream().anyMatch(tag -> tag.contains("新用户") || tag.contains("新客"));
    long estimatedUsers = newUser ? 180_000L : 500_000L;
    String regionCode = switch (region) {
      case "福建" -> "FJ";
      case "浙江" -> "ZJ";
      case "上海" -> "SH";
      default -> "CN";
    };
    return new AudienceInfo("AUD-" + regionCode + "-" + (newUser ? "NEW" : "ALL"), region, estimatedUsers, true);
  }

  public StockInfo queryStock(String couponRule) {
    int unitPriceFen = couponRule.contains("减5") ? 500 : 200;
    return new StockInfo("COUPON-" + couponRule, 200_000, unitPriceFen);
  }

  public ConflictInfo checkTimeConflict(LocalDateTime start, LocalDateTime end, String channel) {
    if (!end.isAfter(start)) {
      return new ConflictInfo(true, "活动结束时间必须晚于开始时间");
    }
    if ("福建".equals(channel) && start.getHour() < 8) {
      return new ConflictInfo(true, "福建渠道活动不允许在 8 点前开始");
    }
    return new ConflictInfo(false, "未发现同渠道活动时间冲突");
  }

  public DraftResult createCampaignDraft(CampaignDraft draft) {
    return new DraftResult("DRAFT-" + UUID.randomUUID().toString().substring(0, 8), "DRAFT");
  }

  public DraftResult confirmCampaign(String draftId) {
    return new DraftResult(draftId, "PENDING_EFFECTIVE");
  }

  public DraftResult activateCampaign(String draftId) {
    return new DraftResult(draftId, "EFFECTIVE");
  }
}
