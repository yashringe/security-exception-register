package io.github.yashringe.exceptionregister.domain;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * Turns the register into the indicators a security risk team reports upwards.
 *
 * <p>The thresholds are examples chosen for the demo. A real team sets them in its risk appetite statement,
 * so they are kept in one place ({@link Thresholds}) and passed in.
 */
public final class KriCalculator {

    /** Amber/red thresholds per indicator. */
    public record Thresholds(double expiredAmber, double expiredRed,
                             double criticalAmber, double criticalRed,
                             double weakControlsPctAmber, double weakControlsPctRed,
                             double staleApprovalsAmber, double staleApprovalsRed,
                             double repeatRenewalsAmber, double repeatRenewalsRed,
                             int staleApprovalDays, int expiringSoonDays) {

        public static Thresholds defaults() {
            return new Thresholds(1, 3, 1, 3, 25, 50, 2, 5, 1, 3, 10, 30);
        }
    }

    /** Everything a dashboard needs in one object. */
    public record Snapshot(LocalDate asOf, Map<RiskRating, Integer> openByResidualRating, int totalOpen,
                           int expiringSoon, List<Kri> indicators) {
    }

    private final Thresholds t;

    public KriCalculator(Thresholds thresholds) {
        this.t = thresholds;
    }

    public Snapshot calculate(List<ExceptionRequest> all, Instant now) {
        LocalDate today = LocalDate.ofInstant(now, ZoneOffset.UTC);

        Map<RiskRating, Integer> openByRating = new EnumMap<>(RiskRating.class);
        for (RiskRating r : RiskRating.values()) {
            openByRating.put(r, 0);
        }
        int totalOpen = 0;
        int expired = 0;
        int criticalApproved = 0;
        int approved = 0;
        int approvedWithoutEffectiveControl = 0;
        int staleApprovals = 0;
        int repeatRenewals = 0;
        int expiringSoon = 0;

        for (ExceptionRequest e : all) {
            ExceptionStatus s = e.status();
            if (!s.isOpen()) {
                continue;
            }
            totalOpen++;
            openByRating.merge(e.residualRating(), 1, Integer::sum);

            if (s == ExceptionStatus.EXPIRED) {
                expired++;
            }
            if (s == ExceptionStatus.APPROVED) {
                approved++;
                if (e.residualRating() == RiskRating.CRITICAL) {
                    criticalApproved++;
                }
                boolean hasEffective = e.controls().stream()
                        .anyMatch(c -> c.effectiveness() == CompensatingControl.Effectiveness.EFFECTIVE);
                if (!hasEffective) {
                    approvedWithoutEffectiveControl++;
                }
                long daysLeft = ChronoUnit.DAYS.between(today, e.requestedExpiry());
                if (daysLeft >= 0 && daysLeft <= t.expiringSoonDays()) {
                    expiringSoon++;
                }
            }
            if (s == ExceptionStatus.SUBMITTED && e.submittedAt() != null
                    && Duration.between(e.submittedAt(), now).toDays() > t.staleApprovalDays()) {
                staleApprovals++;
            }
            if (e.renewalCount() >= 2) {
                repeatRenewals++;
            }
        }

        double weakControlsPct = approved == 0 ? 0 : 100.0 * approvedWithoutEffectiveControl / approved;

        List<Kri> indicators = List.of(
                new Kri("expired_not_remediated", "Expired exceptions not closed or renewed",
                        expired, "count", t.expiredAmber(), t.expiredRed()),
                new Kri("critical_accepted", "Approved exceptions with CRITICAL residual risk",
                        criticalApproved, "count", t.criticalAmber(), t.criticalRed()),
                new Kri("weak_controls_pct", "Approved exceptions without an effective (tested) control",
                        round1(weakControlsPct), "percent", t.weakControlsPctAmber(), t.weakControlsPctRed()),
                new Kri("stale_approvals", "Requests waiting for a decision longer than "
                        + t.staleApprovalDays() + " days",
                        staleApprovals, "count", t.staleApprovalsAmber(), t.staleApprovalsRed()),
                new Kri("repeat_renewals", "Open exceptions renewed two or more times",
                        repeatRenewals, "count", t.repeatRenewalsAmber(), t.repeatRenewalsRed()));

        return new Snapshot(today, Map.copyOf(openByRating), totalOpen, expiringSoon, indicators);
    }

    private static double round1(double v) {
        return Math.round(v * 10) / 10.0;
    }
}
