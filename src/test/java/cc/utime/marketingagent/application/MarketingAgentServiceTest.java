package cc.utime.marketingagent.application;

import static org.assertj.core.api.Assertions.assertThat;

import cc.utime.marketingagent.parser.CampaignIntentParser;
import cc.utime.marketingagent.parser.RequirementParser;
import cc.utime.marketingagent.rag.InMemoryKnowledgeBase;
import cc.utime.marketingagent.tool.MarketingToolbox;
import cc.utime.marketingagent.trace.InMemoryTraceRepository;
import cc.utime.marketingagent.validation.CampaignPolicyValidator;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import org.junit.jupiter.api.Test;

class MarketingAgentServiceTest {

  private final InMemoryTraceRepository traceRepository = new InMemoryTraceRepository();
  private final CampaignIntentParser parser =
      new RequirementParser(Clock.fixed(Instant.parse("2026-05-05T00:00:00Z"), ZoneId.of("Asia/Shanghai")));
  private final MarketingAgentService service =
      new MarketingAgentService(
          this.parser,
          new InMemoryKnowledgeBase(),
          new MarketingToolbox(),
          new CampaignPolicyValidator(),
          this.traceRepository);

  @Test
  void shouldCreatePendingApprovalDraft() {
    var response =
        this.service.createDraft(
            "下周五到下下周一，针对福建地区新用户，做一个满30减5的首单优惠券活动，预算10万，每人限领1张，分享给好友再送2元无门槛券");

    assertThat(response.status()).isEqualTo("PENDING_APPROVAL");
    assertThat(response.draft().region()).isEqualTo("福建");
    assertThat(response.draft().couponRule()).isEqualTo("满30减5");
    assertThat(response.toolCalls()).extracting("name")
        .contains("searchSimilarCampaigns", "queryAudience", "queryStock", "checkTimeConflict", "createCampaignDraft");
    assertThat(response.validationIssues()).anyMatch(issue -> issue.code().equals("MANUAL_APPROVAL_REQUIRED"));
  }

  @Test
  void shouldRecordTrace() {
    var response = this.service.createDraft("福建新用户满30减5预算10万");

    assertThat(this.service.getTrace(response.traceId()).userInput()).contains("福建新用户");
  }

  @Test
  void shouldBlockWhenCouponRuleMissing() {
    var response = this.service.createDraft("福建新用户活动，预算10万");

    assertThat(response.status()).isEqualTo("VALIDATION_FAILED");
    assertThat(response.validationIssues()).anyMatch(issue -> issue.code().equals("COUPON_RULE_MISSING"));
  }
}
