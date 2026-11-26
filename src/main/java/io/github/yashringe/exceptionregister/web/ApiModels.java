package io.github.yashringe.exceptionregister.web;

import io.github.yashringe.exceptionregister.domain.Approval;
import io.github.yashringe.exceptionregister.domain.AuditEntry;
import io.github.yashringe.exceptionregister.domain.CompensatingControl;
import io.github.yashringe.exceptionregister.domain.ExceptionRequest;
import io.github.yashringe.exceptionregister.domain.ExceptionStatus;
import io.github.yashringe.exceptionregister.domain.Kri;
import io.github.yashringe.exceptionregister.domain.KriCalculator;
import io.github.yashringe.exceptionregister.domain.RiskRating;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Request and response bodies. Kept apart from the domain so the API can change without touching the rules. */
public final class ApiModels {

    private ApiModels() {
    }

    public record ControlBody(@NotBlank @Size(max = 500) String description,
                              @NotNull CompensatingControl.Effectiveness effectiveness) {
        CompensatingControl toDomain() {
            return new CompensatingControl(description, effectiveness);
        }
    }

    public record CreateBody(@NotBlank @Size(max = 200) String title,
                             @NotBlank @Size(max = 100) String assetId,
                             @NotBlank @Size(max = 100) String policyReference,
                             @Size(max = 4000) String justification,
                             @NotBlank @Size(max = 100) String owner,
                             @Min(1) @Max(5) int likelihood,
                             @Min(1) @Max(5) int impact,
                             @Valid List<ControlBody> controls,
                             @NotNull LocalDate requestedExpiry) {
    }

    public record DecisionBody(@Size(max = 2000) String comment) {
    }

    public record RenewBody(@NotNull LocalDate newExpiry) {
    }

    public record ExceptionView(UUID id, String title, String assetId, String policyReference,
                                String justification, String requester, String owner, ExceptionStatus status,
                                int inherentScore, RiskRating inherentRating, int residualScore,
                                RiskRating residualRating, int requiredApprovals, List<CompensatingControl> controls,
                                List<Approval> approvals, LocalDate requestedExpiry, LocalDate maxAllowedExpiry,
                                Instant createdAt, Instant submittedAt, Instant decidedAt, String decisionNote,
                                int renewalCount) {

        static ExceptionView of(ExceptionRequest e, LocalDate today) {
            return new ExceptionView(e.id(), e.title(), e.assetId(), e.policyReference(), e.justification(),
                    e.requester(), e.owner(), e.status(), e.inherentRisk().value(), e.inherentRisk().rating(),
                    e.residualRisk().value(), e.residualRating(), e.residualRating().requiredApprovals(),
                    e.controls(), e.approvals(), e.requestedExpiry(), e.maxAllowedExpiry(today), e.createdAt(),
                    e.submittedAt(), e.decidedAt(), e.decisionNote(), e.renewalCount());
        }
    }

    public record AuditView(long sequence, UUID exceptionId, String action, String actor, Instant at,
                            String details, String hash) {
        static AuditView of(AuditEntry a) {
            return new AuditView(a.sequence(), a.exceptionId(), a.action(), a.actor(), a.at(), a.details(),
                    a.hash());
        }
    }

    public record ChainCheck(boolean intact, Long firstBrokenSequence) {
    }

    public record KriView(String id, String name, double value, String unit, Kri.Status status, double amberFrom,
                          double redFrom) {
        static KriView of(Kri k) {
            return new KriView(k.id(), k.name(), k.value(), k.unit(), k.status(), k.amberFrom(), k.redFrom());
        }
    }

    public record KriSnapshotView(LocalDate asOf, Map<RiskRating, Integer> openByResidualRating, int totalOpen,
                                  int expiringSoon, List<KriView> indicators) {
        static KriSnapshotView of(KriCalculator.Snapshot s) {
            return new KriSnapshotView(s.asOf(), s.openByResidualRating(), s.totalOpen(), s.expiringSoon(),
                    s.indicators().stream().map(KriView::of).toList());
        }
    }
}
