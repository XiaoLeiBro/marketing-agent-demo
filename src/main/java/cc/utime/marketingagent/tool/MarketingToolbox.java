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

  /*
   * Tool Calling 模拟层。
   *
   * 面试时重点讲：这里不是把业务接口裸露给模型，而是后端包装后的白名单工具。
   * queryAudience/queryStock/checkTimeConflict 是只读工具，可以被 Agent 编排调用；
   * createCampaignDraft/confirmCampaign/activateCampaign 是写操作，只能在人工审批后的应用服务方法里调用。
   */
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
    // 真实生产中这里会调用营销中台“创建草稿”接口，并由营销中台负责事务、权限和审计。
    // Demo 只返回 DRAFT 状态，用来表达“审批通过后仍然不是直接上线”。
    return new DraftResult("DRAFT-" + UUID.randomUUID().toString().substring(0, 8), "DRAFT");
  }

  public DraftResult confirmCampaign(String draftId) {
    return new DraftResult(draftId, "PENDING_EFFECTIVE");
  }

  public DraftResult activateCampaign(String draftId) {
    return new DraftResult(draftId, "EFFECTIVE");
  }
}
