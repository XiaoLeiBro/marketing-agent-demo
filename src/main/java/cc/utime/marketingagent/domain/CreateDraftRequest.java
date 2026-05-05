package cc.utime.marketingagent.domain;

import jakarta.validation.constraints.NotBlank;

public record CreateDraftRequest(@NotBlank String requirement) {}
