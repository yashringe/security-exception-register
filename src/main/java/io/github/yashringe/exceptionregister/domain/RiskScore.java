package io.github.yashringe.exceptionregister.domain;

/** Likelihood and impact on a 1-5 scale. */
public record RiskScore(int likelihood, int impact) {

    public RiskScore {
        requireScale("likelihood", likelihood);
        requireScale("impact", impact);
    }

    private static void requireScale(String name, int value) {
        if (value < 1 || value > 5) {
            throw new DomainException(name + " must be between 1 and 5, was " + value);
        }
    }

    public int value() {
        return likelihood * impact;
    }

    public RiskRating rating() {
        return RiskRating.fromScore(value());
    }
}
