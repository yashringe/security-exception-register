package io.github.yashringe.exceptionregister.domain;

import io.github.yashringe.exceptionregister.domain.CompensatingControl.Effectiveness;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;

import static io.github.yashringe.exceptionregister.domain.Fixtures.*;
import static org.junit.jupiter.api.Assertions.*;

class ExceptionRequestTest {

    // ------------------------------------------------------------ residual risk

    @Test
    void onlyTheBestControlLowersLikelihood() {
        ExceptionRequest e = draft(new RiskScore(4, 5),
                List.of(control(Effectiveness.PARTIAL), control(Effectiveness.PARTIAL),
                        control(Effectiveness.PARTIAL)),
                TODAY.plusDays(20));
        // Three partial controls do not add up to -3: residual is (4-1) x 5 = 15, HIGH.
        assertEquals(new RiskScore(3, 5), e.residualRisk());
        assertEquals(RiskRating.HIGH, e.residualRating());
    }

    @Test
    void effectiveControlLowersByTwoButNeverBelowOne() {
        ExceptionRequest e = draft(new RiskScore(2, 5), List.of(control(Effectiveness.EFFECTIVE)),
                TODAY.plusDays(30));
        assertEquals(new RiskScore(1, 5), e.residualRisk());
    }

    @Test
    void untestedControlDoesNotCount() {
        ExceptionRequest e = draft(new RiskScore(4, 4), List.of(control(Effectiveness.UNTESTED)),
                TODAY.plusDays(30));
        assertEquals(e.inherentRisk(), e.residualRisk());
    }

    // ------------------------------------------------------------ submit

    @Test
    void submitMovesDraftToSubmitted() {
        ExceptionRequest e = mediumDraft();
        e.submit(ALICE, NOW);
        assertEquals(ExceptionStatus.SUBMITTED, e.status());
        assertEquals(NOW, e.submittedAt());
    }

    @Test
    void highRiskWithoutControlCannotBeSubmitted() {
        ExceptionRequest e = draft(new RiskScore(4, 4), List.of(), TODAY.plusDays(30));
        DomainException ex = assertThrows(DomainException.class, () -> e.submit(ALICE, NOW));
        assertTrue(ex.getMessage().contains("compensating control"));
    }

    @Test
    void expiryLongerThanPolicyAllowsIsRejected() {
        // HIGH residual may run 90 days at most.
        ExceptionRequest e = draft(new RiskScore(4, 4), List.of(control(Effectiveness.UNTESTED)),
                TODAY.plusDays(91));
        DomainException ex = assertThrows(DomainException.class, () -> e.submit(ALICE, NOW));
        assertTrue(ex.getMessage().contains("at most 90 days"), ex.getMessage());
    }

    @Test
    void expiryInThePastIsRejected() {
        ExceptionRequest e = draft(new RiskScore(1, 1), List.of(), TODAY);
        assertThrows(DomainException.class, () -> e.submit(ALICE, NOW));
    }

    @Test
    void strangerCannotSubmit() {
        ExceptionRequest e = mediumDraft();
        assertThrows(DomainException.class, () -> e.submit(BOB, NOW));
    }

    @Test
    void auditorCannotFileRequests() {
        assertThrows(DomainException.class, () -> ExceptionRequest.draft(DAVE, "t", "a", "p", "j", "o",
                new RiskScore(1, 1), List.of(), TODAY.plusDays(10), NOW));
    }

    // ------------------------------------------------------------ approve / reject

    @Test
    void riskOfficerApprovesMediumAlone() {
        ExceptionRequest e = mediumDraft();
        e.submit(ALICE, NOW);
        e.approve(BOB, "Accepted until vendor upgrade", NOW.plus(Duration.ofDays(1)));
        assertEquals(ExceptionStatus.APPROVED, e.status());
        assertEquals(1, e.approvals().size());
    }

    @Test
    void fourEyesBlocksRequesterAndOwnerFromApproving() {
        Actor aliceAsOfficer = new Actor("alice", java.util.Set.of(Actor.Role.REQUESTER, Actor.Role.RISK_OFFICER));
        Actor ownerAsOfficer = new Actor("olivia", java.util.Set.of(Actor.Role.RISK_OFFICER));
        ExceptionRequest e = mediumDraft();
        e.submit(ALICE, NOW);
        DomainException ex = assertThrows(DomainException.class, () -> e.approve(aliceAsOfficer, "", NOW));
        assertTrue(ex.getMessage().startsWith("Four-eyes"));
        assertThrows(DomainException.class, () -> e.approve(ownerAsOfficer, "", NOW));
        assertEquals(ExceptionStatus.SUBMITTED, e.status());
    }

    @Test
    void nonOfficerCannotApprove() {
        ExceptionRequest e = mediumDraft();
        e.submit(ALICE, NOW);
        assertThrows(DomainException.class, () -> e.approve(DAVE, "", NOW));
    }

    @Test
    void criticalNeedsTwoDifferentOfficers() {
        ExceptionRequest e = draft(new RiskScore(5, 5), List.of(control(Effectiveness.UNTESTED)),
                TODAY.plusDays(14));
        e.submit(ALICE, NOW);
        e.approve(BOB, "ok", NOW);
        assertEquals(ExceptionStatus.SUBMITTED, e.status(), "one approval is not enough for CRITICAL");
        assertThrows(DomainException.class, () -> e.approve(BOB, "again", NOW), "same officer twice");
        e.approve(CAROL, "ok", NOW);
        assertEquals(ExceptionStatus.APPROVED, e.status());
    }

    @Test
    void rejectionNeedsAReason() {
        ExceptionRequest e = mediumDraft();
        e.submit(ALICE, NOW);
        assertThrows(DomainException.class, () -> e.reject(BOB, "  ", NOW));
        e.reject(BOB, "Patch is available, apply it", NOW);
        assertEquals(ExceptionStatus.REJECTED, e.status());
        assertEquals("Patch is available, apply it", e.decisionNote());
    }

    @Test
    void cannotApproveADraft() {
        ExceptionRequest e = mediumDraft();
        assertThrows(DomainException.class, () -> e.approve(BOB, "", NOW));
    }

    // ------------------------------------------------------------ expiry, renewal, close

    @Test
    void approvedExceptionExpiresTheDayAfterItsExpiryDate() {
        ExceptionRequest e = approvedMedium();
        assertFalse(e.expireIfDue(e.requestedExpiry()));
        assertTrue(e.expireIfDue(e.requestedExpiry().plusDays(1)));
        assertEquals(ExceptionStatus.EXPIRED, e.status());
        assertTrue(e.status().isOpen(), "an expired exception is still an open risk");
    }

    @Test
    void renewalClearsApprovalsAndCountsUp() {
        ExceptionRequest e = approvedMedium();
        e.expireIfDue(e.requestedExpiry().plusDays(1));
        e.requestRenewal(ALICE, TODAY.plusDays(150), NOW.plus(Duration.ofDays(91)));
        assertEquals(ExceptionStatus.SUBMITTED, e.status());
        assertTrue(e.approvals().isEmpty());
        assertEquals(1, e.renewalCount());
    }

    @Test
    void closeOnlyFromApprovedOrExpired() {
        ExceptionRequest e = mediumDraft();
        assertThrows(DomainException.class, () -> e.close(ALICE, "fixed", NOW));
        ExceptionRequest approved = approvedMedium();
        approved.close(OWNER, "Patched on 2027-01-10", NOW);
        assertEquals(ExceptionStatus.CLOSED, approved.status());
        assertFalse(approved.status().isOpen());
    }

    @Test
    void unrelatedRequesterCannotClose() {
        Actor mallory = new Actor("mallory", java.util.Set.of(Actor.Role.REQUESTER));
        ExceptionRequest e = approvedMedium();
        assertThrows(DomainException.class, () -> e.close(mallory, "", NOW));
    }

    private static ExceptionRequest approvedMedium() {
        ExceptionRequest e = mediumDraft();
        e.submit(ALICE, NOW);
        e.approve(BOB, "ok", NOW);
        return e;
    }
}
