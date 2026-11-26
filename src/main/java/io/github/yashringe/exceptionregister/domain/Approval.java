package io.github.yashringe.exceptionregister.domain;

import java.time.Instant;

public record Approval(String approver, Instant at, String comment) {
}
