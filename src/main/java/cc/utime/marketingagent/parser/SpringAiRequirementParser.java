package cc.utime.marketingagent.parser;

import cc.utime.marketingagent.domain.CampaignIntent;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnBean(ChatClient.class)
@ConditionalOnProperty(prefix = "agent.llm", name = "enabled", havingValue = "true")
public class SpringAiRequirementParser implements CampaignIntentParser {

  private final ChatClient chatClient;
  private final ObjectMapper objectMapper;
  private final Clock clock;

  public SpringAiRequirementParser(ChatClient chatClient, ObjectMapper objectMapper, Clock clock) {
    this.chatClient = chatClient;
    this.objectMapper = objectMapper;
    this.clock = clock;
  }

  @Override
  public CampaignIntent parse(String requirement) {
    String json = this.chatClient.prompt()
        .system(systemPrompt())
        .user(requirement)
        .call()
        .content();
    try {
      IntentJson intentJson = this.objectMapper.readValue(cleanJson(json), IntentJson.class);
      return toIntent(intentJson);
    }
    catch (Exception e) {
      throw new IllegalArgumentException("Spring AI 解析活动需求失败，请检查模型输出：" + json, e);
    }
  }

  private CampaignIntent toIntent(IntentJson intentJson) {
    LocalDate startDate = LocalDate.now(this.clock).plusDays(Math.max(1, intentJson.startAfterDays()));
    LocalDateTime startTime = LocalDateTime.of(startDate, LocalTime.of(10, 0));
    LocalDateTime endTime = startTime.plusDays(Math.max(1, intentJson.durationDays()));
    String audienceLabel = intentJson.audienceLabel() == null ? "目标用户" : intentJson.audienceLabel();
    return new CampaignIntent(
        defaultString(intentJson.region(), "全国"),
        audienceLabel,
        List.of(audienceLabel),
        defaultString(intentJson.couponRule(), "待补充券规则"),
        Math.max(1, intentJson.budgetFen()),
        Math.max(1, intentJson.perUserLimit()),
        startTime,
        endTime,
        Math.max(0, intentJson.shareRewardFen()));
  }

  private String systemPrompt() {
    return """
        你是营销中台活动配置助手。请把用户自然语言需求解析成严格 JSON。
        不要输出 Markdown，不要解释，只输出 JSON。
        字段：
        - region: 地区，例如 福建、浙江、上海、全国
        - audienceLabel: 人群标签，例如 新用户、目标用户
        - couponRule: 券规则，例如 满30减5、无门槛2元；不确定时填 待补充券规则
        - budgetFen: 预算，单位分；10万=10000000
        - perUserLimit: 单用户限领数量
        - startAfterDays: 从今天起几天后开始，不确定填 1
        - durationDays: 活动持续天数，不确定填 4
        - shareRewardFen: 分享奖励金额，单位分；没有则填 0
        """;
  }

  private String cleanJson(String raw) {
    if (raw == null) {
      return "{}";
    }
    return raw.replace("```json", "").replace("```", "").trim();
  }

  private String defaultString(String value, String defaultValue) {
    return value == null || value.isBlank() ? defaultValue : value;
  }

  private record IntentJson(
      String region,
      String audienceLabel,
      String couponRule,
      int budgetFen,
      int perUserLimit,
      int startAfterDays,
      int durationDays,
      int shareRewardFen) {}
}
