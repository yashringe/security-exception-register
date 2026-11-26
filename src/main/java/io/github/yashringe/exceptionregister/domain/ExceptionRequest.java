package io.github.yashringe.exceptionregister.domain;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * A request to run a system outside a security policy for a limited time, for example
 * "legacy settlement server cannot get the OS patch before the vendor upgrade in Q2".
 *
 * <p>Lifecycle: DRAFT -> SUBMITTED -> APPROVED | REJECTED; APPROVED -> EXPIRED (date passed) or CLOSED (gap
 * fixed); APPROVED | EXPIRED -> SUBMITTED again on renewal. All rules live here, so the web and persistence
 * layers cannot bypass them.
 */
public final class ExceptionRequest {

    private final UUID id;
    private final String title;
    private final String assetId;
    private final String policyReference;
    private final String justification;
    private final String requester;
    private final String owner;
    private final RiskScore inherentRisk;
    private final List<CompensatingControl> controls;
    private final Instant createdAt;

    private LocalDate requestedExpiry;
    private ExceptionStatus status;
    private final List<Approval> approvals;
    private Instant submittedAt;
    private Instant decidedAt;
    private String decisionNote;
    private int renewalCount;
    private long version;

    private ExceptionRequest(UUID id, String title, String assetId, String policyReference, String justification,
                             String requester, String owner, RiskScore inherentRisk,
                             List<CompensatingControl> controls, LocalDate requestedExpiry, Instant createdAt,
                             ExceptionStatus status, List<Approval> approvals, Instant submittedAt,
                             Instant decidedAt, String decisionNote, int renewalCount, long version) {
        this.id = Objects.requireNonNull(id, "id");
        this.title = requireText("title", title);
        this.assetId = requireText("assetId", assetId);
        this.policyReference = requireText("policyReference", policyReference);
        this.justification = justification == null ? "" : justification.strip();
        this.requester = requireText("requester", requester);
        this.owner = requireText("owner", owner);
        this.inherentRisk = Objects.requireNonNull(inherentRisk, "inherentRisk");
        this.controls = new ArrayList<>(controls);
        this.requestedExpiry = Objects.requireNonNull(requestedExpiry, "requestedExpiry");
        this.createdAt = Objects.requireNonNull(createdAt, "createdAt");
        this.status = Objects.requireNonNull(status, "status");
        this.approvals = new ArrayList<>(approvals);
        this.submittedAt = submittedAt;
        this.decidedAt = decidedAt;
        this.decisionNote = decisionNote;
        this.renewalCount = renewalCount;
        this.version = version;
    }

    /** Starts a new request in DRAFT. The requester is the actor filing it. */
    public static ExceptionRequest draft(Actor requester, String title, String assetId, String policyReference,
                                         String justification, String owner, RiskScore inherentRisk,
                                         List<CompensatingControl> controls, LocalDate requestedExpiry,
                                         Instant now) {
        if (!requester.has(Actor.Role.REQUESTER)) {
            throw new DomainException(requester.name() + " is not allowed to file exception requests");
        }
        return new ExceptionRequest(UUID.randomUUID(), title, assetId, policyReference, justification,
                requester.name(), owner, inherentRisk, controls, requestedExpiry, now, ExceptionStatus.DRAFT,
                List.of(), null, null, null, 0, 0);
    }

    /** Rebuilds a stored request. Only the persistence layer should call this. */
    public static ExceptionRequest restore(UUID id, String title, String assetId, String policyReference,
                                           String justification, String requester, String owner,
                                           RiskScore inherentRisk, List<CompensatingControl> controls,
                                           LocalDate requestedExpiry, Instant createdAt, ExceptionStatus status,
                                           List<Approval> approvals, Instant submittedAt, Instant decidedAt,
                                           String decisionNote, int renewalCount, long version) {
        return new ExceptionRequest(id, title, assetId, policyReference, justification, requester, owner,
                inherentRisk, controls, requestedExpiry, createdAt, status, approvals, submittedAt, decidedAt,
                decisionNote, renewalCount, version);
    }

    // ---------------------------------------------------------------- risk

    /**
     * Risk left after compensating controls. Only the single best control counts: controls usually overlap,
     * and adding up "partial" measures would let a pile of weak controls hide a critical gap. Controls lower
     * the likelihood, never the impact, and never below 1.
     */
    public RiskScore residualRisk() {
        int bestReduction = controls.stream()
                .mapToInt(c -> c.effectiveness().likelihoodReduction())
                .max()
                .orElse(0);
        return new RiskScore(Math.max(1, inherentRisk.likelihood() - bestReduction), inherentRisk.impact());
    }

    public RiskRating residualRating() {
        return residualRisk().rating();
    }

    /** Latest expiry date the policy allows if the decision is taken on {@code decisionDate}. */
    public LocalDate maxAllowedExpiry(LocalDate decisionDate) {
        return decisionDate.plusDays(residualRating().maxValidityDays());
    }

    // ---------------------------------------------------------------- commands

    public void submit(Actor actor, Instant now) {
        requireStatus(ExceptionStatus.DRAFT, "submit");
        requireRequesterOrOwner(actor, "submit");
        if (justification.isBlank()) {
            throw new DomainException("A justification is required before submitting");
        }
        RiskRating inherent = inherentRisk.rating();
        if ((inherent == RiskRating.HIGH || inherent == RiskRating.CRITICAL) && controls.isEmpty()) {
            throw new DomainException("A " + inherent + " risk needs at least one compensating control");
        }
        requireExpiryWithinPolicy(today(now));
        status = ExceptionStatus.SUBMITTED;
        submittedAt = now;
    }

    /**
     * Records one approval. The request becomes APPROVED once the number of distinct approvers required by
     * the residual rating is reached (two for CRITICAL).
     */
    public void approve(Actor approver, String comment, Instant now) {
        requireStatus(ExceptionStatus.SUBMITTED, "approve");
        requireIndependentRiskOfficer(approver, "approve");
        if (approvals.stream().anyMatch(a -> a.approver().equals(approver.name()))) {
            throw new DomainException(approver.name() + " has already approved this request");
        }
        requireExpiryWithinPolicy(today(now));
        approvals.add(new Approval(approver.name(), now, comment == null ? "" : comment.strip()));
        if (approvals.size() >= residualRating().requiredApprovals()) {
            status = ExceptionStatus.APPROVED;
            decidedAt = now;
            decisionNote = comment;
        }
    }

    public void reject(Actor officer, String reason, Instant now) {
        requireStatus(ExceptionStatus.SUBMITTED, "reject");
        requireIndependentRiskOfficer(officer, "reject");
        if (reason == null || reason.isBlank()) {
            throw new DomainException("A rejection needs a reason");
        }
        status = ExceptionStatus.REJECTED;
        decidedAt = now;
        decisionNote = reason.strip();
    }

    /** The gap was remediated. Requester, owner or a risk officer may close. */
    public void close(Actor actor, String note, Instant now) {
        if (status != ExceptionStatus.APPROVED && status != ExceptionStatus.EXPIRED) {
            throw new DomainException("Only approved or expired exceptions can be closed, this one is " + status);
        }
        boolean involved = actor.name().equals(requester) || actor.name().equals(owner);
        if (!involved && !actor.has(Actor.Role.RISK_OFFICER)) {
            throw new DomainException(actor.name() + " may not close this exception");
        }
        status = ExceptionStatus.CLOSED;
        decidedAt = now;
        decisionNote = note;
    }

    /** Asks for more time. Previous approvals no longer count: the risk has to be accepted again. */
    public void requestRenewal(Actor actor, LocalDate newExpiry, Instant now) {
        if (status != ExceptionStatus.APPROVED && status != ExceptionStatus.EXPIRED) {
            throw new DomainException("Only approved or expired exceptions can be renewed, this one is " + status);
        }
        requireRequesterOrOwner(actor, "renew");
        this.requestedExpiry = Objects.requireNonNull(newExpiry, "newExpiry");
        requireExpiryWithinPolicy(today(now));
        approvals.clear();
        renewalCount++;
        status = ExceptionStatus.SUBMITTED;
        submittedAt = now;
        decidedAt = null;
        decisionNote = null;
    }

    /** Called by the daily job. Returns true if the status changed. */
    public boolean expireIfDue(LocalDate today) {
        if (status == ExceptionStatus.APPROVED && today.isAfter(requestedExpiry)) {
            status = ExceptionStatus.EXPIRED;
            return true;
        }
        return false;
    }

    // ---------------------------------------------------------------- rules

    private void requireStatus(ExceptionStatus expected, String action) {
        if (status != expected) {
            throw new DomainException("Cannot " + action + " a request in status " + status);
        }
    }

    private void requireRequesterOrOwner(Actor actor, String action) {
        if (!actor.name().equals(requester) && !actor.name().equals(owner)) {
            throw new DomainException("Only the requester or the system owner may " + action + " this request");
        }
    }

    /** Four-eyes: the person who accepts a risk must not be the one asking for it or owning the system. */
    private void requireIndependentRiskOfficer(Actor officer, String action) {
        if (!officer.has(Actor.Role.RISK_OFFICER)) {
            throw new DomainException(officer.name() + " is not a risk officer and may not " + action);
        }
        if (officer.name().equals(requester) || officer.name().equals(owner)) {
            throw new DomainException("Four-eyes rule: " + officer.name()
                    + " filed or owns this request and may not " + action + " it");
        }
    }

    private void requireExpiryWithinPolicy(LocalDate today) {
        if (!requestedExpiry.isAfter(today)) {
            throw new DomainException("Requested expiry " + requestedExpiry + " must be in the future");
        }
        LocalDate max = maxAllowedExpiry(today);
        if (requestedExpiry.isAfter(max)) {
            throw new DomainException("A " + residualRating() + " exception may run at most "
                    + residualRating().maxValidityDays() + " days (until " + max + "), requested "
                    + requestedExpiry);
        }
    }

    private static LocalDate today(Instant now) {
        return LocalDate.ofInstant(now, ZoneOffset.UTC);
    }

    private static String requireText(String name, String value) {
        if (value == null || value.isBlank()) {
            throw new DomainException(name + " is required");
        }
        return value.strip();
    }

    // ---------------------------------------------------------------- getters

    public UUID id() { return id; }
    public String title() { return title; }
    public String assetId() { return assetId; }
    public String policyReference() { return policyReference; }
    public String justification() { return justification; }
    public String requester() { return requester; }
    public String owner() { return owner; }
    public RiskScore inherentRisk() { return inherentRisk; }
    public List<CompensatingControl> controls() { return List.copyOf(controls); }
    public LocalDate requestedExpiry() { return requestedExpiry; }
    public Instant createdAt() { return createdAt; }
    public ExceptionStatus status() { return status; }
    public List<Approval> approvals() { return List.copyOf(approvals); }
    public Instant submittedAt() { return submittedAt; }
    public Instant decidedAt() { return decidedAt; }
    public String decisionNote() { return decisionNote; }
    public int renewalCount() { return renewalCount; }
    public long version() { return version; }
}
