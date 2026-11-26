package io.github.yashringe.exceptionregister.domain;

/**
 * Rating derived from a 5x5 likelihood x impact matrix.
 *
 * <p>Bands: 1-4 LOW, 5-9 MEDIUM, 10-16 HIGH, 20-25 CRITICAL. (Scores 17-19 cannot occur on a 5x5 grid.)
 * Each rating also caps how long an exception may stay valid, see {@link #maxValidityDays()}.
 */
public enum RiskRating {
    LOW(365, 1),
    MEDIUM(180, 1),
    HIGH(90, 1),
    CRITICAL(30, 2);

    private final int maxValidityDays;
    private final int requiredApprovals;

    RiskRating(int maxValidityDays, int requiredApprovals) {
        this.maxValidityDays = maxValidityDays;
        this.requiredApprovals = requiredApprovals;
    }

    public static RiskRating fromScore(int score) {
        if (score < 1 || score > 25) {
            throw new IllegalArgumentException("Risk score must be between 1 and 25, was " + score);
        }
        if (score <= 4) return LOW;
        if (score <= 9) return MEDIUM;
        if (score <= 16) return HIGH;
        return CRITICAL;
    }

    /** Longest period an exception with this residual rating may be granted for. */
    public int maxValidityDays() {
        return maxValidityDays;
    }

    /** Number of distinct risk officers who must approve. CRITICAL needs two (four-eyes on the approval itself). */
    public int requiredApprovals() {
        return requiredApprovals;
    }
}
