package io.github.yashringe.exceptionregister.app;

import io.github.yashringe.exceptionregister.domain.Actor;
import io.github.yashringe.exceptionregister.domain.AuditChain;
import io.github.yashringe.exceptionregister.domain.AuditEntry;
import io.github.yashringe.exceptionregister.domain.ExceptionRequest;
import io.github.yashringe.exceptionregister.domain.ExceptionStatus;
import io.github.yashringe.exceptionregister.domain.KriCalculator;
import io.github.yashringe.exceptionregister.domain.RiskScore;
import io.github.yashringe.exceptionregister.persistence.AuditRepository;
import io.github.yashringe.exceptionregister.persistence.ExceptionRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * Use cases. Each change loads the request, lets the domain object apply its rules, saves it and writes one
 * audit entry, all in one transaction: either the change and its audit line are both stored, or neither.
 */
@Service
public class ExceptionService {

    private static final Logger log = LoggerFactory.getLogger(ExceptionService.class);
    private static final String SYSTEM = "system:expiry-job";

    private final ExceptionRepository exceptions;
    private final AuditRepository audit;
    private final KriCalculator kriCalculator;
    private final Clock clock;

    public ExceptionService(ExceptionRepository exceptions, AuditRepository audit, KriCalculator kriCalculator,
                            Clock clock) {
        this.exceptions = exceptions;
        this.audit = audit;
        this.kriCalculator = kriCalculator;
        this.clock = clock;
    }

    @Transactional
    public ExceptionRequest create(Actor actor, NewExceptionCommand c) {
        Instant now = clock.instant();
        ExceptionRequest e = ExceptionRequest.draft(actor, c.title(), c.assetId(), c.policyReference(),
                c.justification(), c.owner(), new RiskScore(c.likelihood(), c.impact()),
                c.controls() == null ? List.of() : c.controls(), c.requestedExpiry(), now);
        exceptions.insert(e);
        audit.append(e.id(), "CREATED", actor.name(), now,
                "inherent=" + e.inherentRisk().value() + " residual=" + e.residualRisk().value()
                        + " (" + e.residualRating() + ") expiry=" + e.requestedExpiry());
        return e;
    }

    @Transactional
    public ExceptionRequest submit(UUID id, Actor actor) {
        return change(id, actor, "SUBMITTED", e -> e.submit(actor, clock.instant()), "");
    }

    @Transactional
    public ExceptionRequest approve(UUID id, Actor actor, String comment) {
        return change(id, actor, "APPROVAL_RECORDED", e -> e.approve(actor, comment, clock.instant()),
                comment);
    }

    @Transactional
    public ExceptionRequest reject(UUID id, Actor actor, String reason) {
        return change(id, actor, "REJECTED", e -> e.reject(actor, reason, clock.instant()), reason);
    }

    @Transactional
    public ExceptionRequest close(UUID id, Actor actor, String note) {
        return change(id, actor, "CLOSED", e -> e.close(actor, note, clock.instant()), note);
    }

    @Transactional
    public ExceptionRequest renew(UUID id, Actor actor, LocalDate newExpiry) {
        return change(id, actor, "RENEWAL_REQUESTED", e -> e.requestRenewal(actor, newExpiry, clock.instant()),
                "new expiry " + newExpiry);
    }

    /** Marks every approved exception past its date as EXPIRED. Returns how many changed. */
    @Transactional
    public int expireDue() {
        Instant now = clock.instant();
        LocalDate today = LocalDate.ofInstant(now, ZoneOffset.UTC);
        int changed = 0;
        for (ExceptionRequest e : exceptions.findApprovedExpiringBefore(today)) {
            if (e.expireIfDue(today)) {
                exceptions.update(e);
                audit.append(e.id(), "EXPIRED", SYSTEM, now, "expiry date " + e.requestedExpiry() + " passed");
                changed++;
            }
        }
        if (changed > 0) {
            log.info("Marked {} exception(s) as expired", changed);
        }
        return changed;
    }

    @Transactional(readOnly = true)
    public ExceptionRequest get(UUID id) {
        return exceptions.findById(id).orElseThrow(() -> new ExceptionNotFoundException(id));
    }

    @Transactional(readOnly = true)
    public List<ExceptionRequest> list(Optional<ExceptionStatus> status) {
        return exceptions.findAll(status);
    }

    @Transactional(readOnly = true)
    public KriCalculator.Snapshot kri() {
        return kriCalculator.calculate(exceptions.findAll(Optional.empty()), clock.instant());
    }

    @Transactional(readOnly = true)
    public List<AuditEntry> auditTrail(UUID id) {
        get(id);
        return audit.findByException(id);
    }

    /** Recomputes the whole hash chain. Empty result = intact. */
    @Transactional(readOnly = true)
    public Optional<Long> verifyAuditChain() {
        return AuditChain.firstBrokenEntry(audit.findAllInOrder());
    }

    private ExceptionRequest change(UUID id, Actor actor, String action, Consumer<ExceptionRequest> command,
                                    String details) {
        ExceptionRequest e = get(id);
        ExceptionStatus before = e.status();
        command.accept(e);
        exceptions.update(e);
        audit.append(e.id(), action, actor.name(), clock.instant(),
                before + " -> " + e.status() + (details == null || details.isBlank() ? "" : ": " + details));
        return e;
    }
}
