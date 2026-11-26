package io.github.yashringe.exceptionregister.domain;

import io.github.yashringe.exceptionregister.domain.CompensatingControl.Effectiveness;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;

final class Fixtures {

    static final Instant NOW = Instant.parse("2026-12-01T09:00:00Z");
    static final LocalDate TODAY = LocalDate.parse("2026-12-01");

    static final Actor ALICE = new Actor("alice", Set.of(Actor.Role.REQUESTER));
    static final Actor OWNER = new Actor("olivia", Set.of(Actor.Role.REQUESTER));
    static final Actor BOB = new Actor("bob", Set.of(Actor.Role.RISK_OFFICER));
    static final Actor CAROL = new Actor("carol", Set.of(Actor.Role.RISK_OFFICER));
    static final Actor DAVE = new Actor("dave", Set.of(Actor.Role.AUDITOR));

    private Fixtures() {
    }

    static CompensatingControl control(Effectiveness effectiveness) {
        return new CompensatingControl("Network segmentation of the legacy host", effectiveness);
    }

    static ExceptionRequest draft(RiskScore inherent, List<CompensatingControl> controls, LocalDate expiry) {
        return ExceptionRequest.draft(ALICE, "Legacy settlement server misses OS patch", "SRV-LEG-042",
                "POL-PATCH-01 §3.2", "Vendor upgrade only certified for Q2", OWNER.name(), inherent, controls,
                expiry, NOW);
    }

    /** MEDIUM residual (3x3, no controls), 90 days. */
    static ExceptionRequest mediumDraft() {
        return draft(new RiskScore(3, 3), List.of(), TODAY.plusDays(90));
    }
}
