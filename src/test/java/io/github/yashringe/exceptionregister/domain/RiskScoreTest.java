package io.github.yashringe.exceptionregister.domain;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class RiskScoreTest {

    @Test
    void bandsFollowTheFiveByFiveMatrix() {
        assertEquals(RiskRating.LOW, new RiskScore(1, 4).rating());
        assertEquals(RiskRating.LOW, new RiskScore(2, 2).rating());
        assertEquals(RiskRating.MEDIUM, new RiskScore(1, 5).rating());
        assertEquals(RiskRating.MEDIUM, new RiskScore(3, 3).rating());
        assertEquals(RiskRating.HIGH, new RiskScore(2, 5).rating());
        assertEquals(RiskRating.HIGH, new RiskScore(4, 4).rating());
        assertEquals(RiskRating.CRITICAL, new RiskScore(4, 5).rating());
        assertEquals(RiskRating.CRITICAL, new RiskScore(5, 5).rating());
    }

    @Test
    void rejectsValuesOutsideTheScale() {
        assertThrows(DomainException.class, () -> new RiskScore(0, 3));
        assertThrows(DomainException.class, () -> new RiskScore(3, 6));
    }

    @Test
    void criticalNeedsTwoApproversAndShortestValidity() {
        assertEquals(2, RiskRating.CRITICAL.requiredApprovals());
        assertEquals(30, RiskRating.CRITICAL.maxValidityDays());
        assertEquals(1, RiskRating.HIGH.requiredApprovals());
        assertEquals(365, RiskRating.LOW.maxValidityDays());
    }
}
