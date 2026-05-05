package cc.utime.marketingagent.rag;

import cc.utime.marketingagent.domain.CampaignSample;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import org.springframework.stereotype.Repository;

@Repository
public class InMemoryKnowledgeBase implements KnowledgeBase {

  private final List<CampaignSample> samples =
      List.of(
          new CampaignSample(
              "ACT-2026-001",
              "福建新客首单满减活动",
              "coupon",
              "满30减5",
              "福建",
              10_000_000,
              "新客首单券，每人限领 1 张，活动上线前需要校验同渠道时间冲突。"),
          new CampaignSample(
              "ACT-2026-002",
              "浙江周末分享助力活动",
              "share_coupon",
              "满50减8",
              "浙江",
              5_000_000,
              "分享助力后发券，分享奖励必须进入审批，禁止自动上线。"),
          new CampaignSample(
              "ACT-2026-003",
              "全国新用户无门槛券活动",
              "coupon",
              "无门槛2元",
              "全国",
              2_000_000,
              "低额无门槛券活动，重点关注预算、人群规模与库存。"));

  @Override
  public List<CampaignSample> searchSimilarCampaigns(String query, int topK) {
    return this.samples.stream()
        .sorted(Comparator.comparing(sample -> score(sample, query)))
        .limit(Math.max(1, topK))
        .toList();
  }

  private int score(CampaignSample sample, String query) {
    List<String> hitWords = new ArrayList<>();
    hitWords.add(sample.region());
    hitWords.add(sample.couponRule());
    hitWords.add(sample.playType());
    hitWords.add(sample.title());
    return (int) hitWords.stream().filter(query::contains).count() * -1;
  }
}
