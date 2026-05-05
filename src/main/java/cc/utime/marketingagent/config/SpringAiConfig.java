package cc.utime.marketingagent.config;

import com.alibaba.cloud.ai.dashscope.api.DashScopeApi;
import com.alibaba.cloud.ai.dashscope.chat.DashScopeChatModel;
import com.alibaba.cloud.ai.dashscope.chat.DashScopeChatOptions;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.StringUtils;

@Configuration
@ConditionalOnProperty(prefix = "agent.llm", name = "enabled", havingValue = "true")
public class SpringAiConfig {

  private static final String DEFAULT_BASE_URL = "https://dashscope.aliyuncs.com";

  @Bean
  DashScopeApi dashScopeApi(@Value("${agent.llm.dashscope.api-key:}") String apiKey) {
    if (!StringUtils.hasText(apiKey)) {
      throw new IllegalStateException("agent.llm.enabled=true 时必须配置 agent.llm.dashscope.api-key");
    }
    return DashScopeApi.builder()
        .apiKey(apiKey)
        .baseUrl(DEFAULT_BASE_URL)
        .build();
  }

  @Bean
  ChatModel dashScopeChatModel(
      DashScopeApi dashScopeApi,
      @Value("${agent.llm.dashscope.model:qwen-plus}") String model) {
    return DashScopeChatModel.builder()
        .dashScopeApi(dashScopeApi)
        .defaultOptions(DashScopeChatOptions.builder().model(model).build())
        .build();
  }

  @Bean
  ChatClient chatClient(ChatModel chatModel) {
    return ChatClient.builder(chatModel).build();
  }
}
