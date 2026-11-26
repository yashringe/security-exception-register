package io.github.yashringe.exceptionregister.app;

import io.github.yashringe.exceptionregister.domain.CompensatingControl;

import java.time.LocalDate;
import java.util.List;

public record NewExceptionCommand(String title, String assetId, String policyReference, String justification,
                                  String owner, int likelihood, int impact, List<CompensatingControl> controls,
                                  LocalDate requestedExpiry) {
}
