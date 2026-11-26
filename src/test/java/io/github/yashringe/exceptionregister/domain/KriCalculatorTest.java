package io.github.yashringe.exceptionregister.domain;

import io.github.yashringe.exceptionregister.domain.CompensatingControl.Effectiveness;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import static io.github.yashringe.exceptionregister.domain.Fixtures.*;
import static org.junit.jupiter.api.Assertions.assertEquals;

class KriCalculatorTest {

    private final KriCalculator calculator = new KriCalculator(KriCalculator.Thresholds.defaults());

    @Test
    void emptyRegisterIsAllGreen() {
        KriCalculator.Snapshot s = calculator.calculate(List.of(), NOW);
        assertEquals(0, s.totalOpen());
        s.indicators().forEach(k -> assertEquals(Kri.Status.GREEN, k.status(), k.id()));
    }

    @Test
    void countsAndStatusesForAMixedRegister() {
        // 1) approved MEDIUM, no effective control, expires in 20 days
        ExceptionRequest a = draft(new RiskScore(3, 3), List.of(control(Effectiveness.PARTIAL)), TODAY.plusDays(20));
        a.submit(ALICE, NOW);
        a.approve(BOB, "", NOW);

        // 2) approved CRITICAL with two approvals and an untested control
        ExceptionRequest b = draft(new RiskScore(5, 5), List.of(control(Effectiveness.UNTESTED)), TODAY.plusDays(25));
        b.submit(ALICE, NOW);
        b.approve(BOB, "", NOW);
        b.approve(CAROL, "", NOW);

        // 3) approved LOW with an effective control, then expired
        ExceptionRequest c = draft(new RiskScore(2, 2), List.of(control(Effectiveness.EFFECTIVE)), TODAY.plusDays(5));
        c.submit(ALICE, NOW);
        c.approve(BOB, "", NOW);
        c.expireIfDue(TODAY.plusDays(6));

        // 4) submitted 15 days before "now" and still waiting
        ExceptionRequest d = mediumDraft();
        d.submit(ALICE, NOW.minus(Duration.ofDays(15)));

        // 5) closed: must not count anywhere
        ExceptionRequest e = mediumDraft();
        e.submit(ALICE, NOW);
        e.approve(BOB, "", NOW);
        e.close(ALICE, "fixed", NOW);

        KriCalculator.Snapshot s = calculator.calculate(List.of(a, b, c, d, e), NOW);
        Map<String, Kri> byId = s.indicators().stream().collect(Collectors.toMap(Kri::id, Function.identity()));

        assertEquals(4, s.totalOpen());
        assertEquals(2, s.openByResidualRating().get(RiskRating.MEDIUM)); // a, d
        assertEquals(1, s.openByResidualRating().get(RiskRating.CRITICAL)); // b
        assertEquals(1, s.openByResidualRating().get(RiskRating.LOW)); // c
        assertEquals(2, s.expiringSoon()); // a (20 d), b (25 d)

        assertEquals(1, byId.get("expired_not_remediated").value());
        assertEquals(Kri.Status.AMBER, byId.get("expired_not_remediated").status());
        assertEquals(1, byId.get("critical_accepted").value());
        assertEquals(100.0, byId.get("weak_controls_pct").value()); // a and b, neither has an effective control
        assertEquals(Kri.Status.RED, byId.get("weak_controls_pct").status());
        assertEquals(1, byId.get("stale_approvals").value());
        assertEquals(Kri.Status.GREEN, byId.get("stale_approvals").status());
    }
}
