package cc.utime.marketingagent.domain;

import java.time.LocalDateTime;

public record ToolCall(String name, String inputSummary, String outputSummary, LocalDateTime calledAt) {

  public static ToolCall of(String name, String inputSummary, String outputSummary) {
    return new ToolCall(name, inputSummary, outputSummary, LocalDateTime.now());
  }
}
