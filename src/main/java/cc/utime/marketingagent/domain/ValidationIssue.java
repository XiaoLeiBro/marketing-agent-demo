package cc.utime.marketingagent.domain;

public record ValidationIssue(String code, String message, boolean blocker) {

  public static ValidationIssue warning(String code, String message) {
    return new ValidationIssue(code, message, false);
  }

  public static ValidationIssue blocker(String code, String message) {
    return new ValidationIssue(code, message, true);
  }
}
