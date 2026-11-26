package io.github.yashringe.exceptionregister.domain;

import java.util.Objects;

/**
 * A measure that reduces the risk while the policy is not met, e.g. "extra network segmentation" or
 * "weekly manual log review".
 */
public record CompensatingControl(String description, Effectiveness effectiveness) {

    public enum Effectiveness {
        /** Documented but not tested. Does not lower the score. */
        UNTESTED(0),
        /** Tested, covers part of the gap. */
        PARTIAL(1),
        /** Tested, covers the gap well. */
        EFFECTIVE(2);

        private final int likelihoodReduction;

        Effectiveness(int likelihoodReduction) {
            this.likelihoodReduction = likelihoodReduction;
        }

        public int likelihoodReduction() {
            return likelihoodReduction;
        }
    }

    public CompensatingControl {
        Objects.requireNonNull(effectiveness, "effectiveness");
        if (description == null || description.isBlank()) {
            throw new DomainException("A compensating control needs a description");
        }
        description = description.strip();
    }
}
