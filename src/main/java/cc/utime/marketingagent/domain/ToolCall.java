package cc.utime.marketingagent.domain;

import java.time.LocalDateTime;

public record ToolCall(
    String name,
    String inputSummary,
    String outputSummary,
    String accessMode,
    boolean writeOperation,
    LocalDateTime calledAt) {

  public static ToolCall of(String name, String inputSummary, String outputSummary) {
    return read(name, inputSummary, outputSummary);
  }

  public static ToolCall read(String name, String inputSummary, String outputSummary) {
    return new ToolCall(name, inputSummary, outputSummary, "READ_ONLY", false, LocalDateTime.now());
  }

  public static ToolCall write(String name, String inputSummary, String outputSummary) {
    return new ToolCall(name, inputSummary, outputSummary, "APPROVAL_GATED_WRITE", true, LocalDateTime.now());
  }
}
