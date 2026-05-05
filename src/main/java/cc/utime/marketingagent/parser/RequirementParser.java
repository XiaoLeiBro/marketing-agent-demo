package cc.utime.marketingagent.parser;

import cc.utime.marketingagent.domain.CampaignIntent;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(prefix = "agent.llm", name = "enabled", havingValue = "false", matchIfMissing = true)
public class RequirementParser implements CampaignIntentParser {

  private static final Pattern BUDGET_PATTERN = Pattern.compile("预算\\s*(\\d+)\\s*万");
  private static final Pattern FULL_REDUCTION_PATTERN = Pattern.compile("满\\s*(\\d+)\\s*减\\s*(\\d+)");

  private final Clock clock;

  public RequirementParser(Clock clock) {
    this.clock = clock;
  }

  @Override
  public CampaignIntent parse(String requirement) {
    String region = resolveRegion(requirement);
    String audienceLabel = requirement.contains("新用户") || requirement.contains("新客") ? "新用户" : "目标用户";
    String couponRule = resolveCouponRule(requirement);
    int budgetFen = resolveBudgetFen(requirement);
    int shareRewardFen = requirement.contains("再送2元") || requirement.contains("送 2 元") ? 200 : 0;
    LocalDate startDate = LocalDate.now(this.clock).plusDays(1);
    LocalDateTime startTime = LocalDateTime.of(startDate, LocalTime.of(10, 0));
    LocalDateTime endTime = startTime.plusDays(4);
    return new CampaignIntent(
        region,
        audienceLabel,
        List.of(audienceLabel),
        couponRule,
        budgetFen,
        1,
        startTime,
        endTime,
        shareRewardFen);
  }

  private String resolveRegion(String requirement) {
    if (requirement.contains("福建")) {
      return "福建";
    }
    if (requirement.contains("浙江")) {
      return "浙江";
    }
    if (requirement.contains("上海")) {
      return "上海";
    }
    return "全国";
  }

  private String resolveCouponRule(String requirement) {
    Matcher matcher = FULL_REDUCTION_PATTERN.matcher(requirement);
    if (matcher.find()) {
      return "满" + matcher.group(1) + "减" + matcher.group(2);
    }
    if (requirement.contains("无门槛")) {
      return "无门槛2元";
    }
    return "待补充券规则";
  }

  private int resolveBudgetFen(String requirement) {
    Matcher matcher = BUDGET_PATTERN.matcher(requirement);
    if (matcher.find()) {
      return Integer.parseInt(matcher.group(1)) * 10_000 * 100;
    }
    return 10_000 * 100;
  }
}
