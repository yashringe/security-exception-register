package io.github.yashringe.exceptionregister.web;

import io.github.yashringe.exceptionregister.app.ExceptionService;
import io.github.yashringe.exceptionregister.domain.ExceptionRequest;
import io.github.yashringe.exceptionregister.domain.Kri;
import io.github.yashringe.exceptionregister.domain.KriCalculator;
import io.github.yashringe.exceptionregister.web.ApiModels.ChainCheck;
import io.github.yashringe.exceptionregister.web.ApiModels.KriSnapshotView;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Optional;

/**
 * Read-only endpoints for dashboards. The CSV files are meant for Power BI or Excel ("Get data > Web" with
 * basic auth), the JSON for the Python report in tools/kri_report.
 */
@RestController
@RequestMapping("/api")
public class ReportingController {

    private static final MediaType CSV = new MediaType("text", "csv", java.nio.charset.StandardCharsets.UTF_8);

    private final ExceptionService service;

    public ReportingController(ExceptionService service) {
        this.service = service;
    }

    @GetMapping("/kri")
    public KriSnapshotView kri() {
        return KriSnapshotView.of(service.kri());
    }

    @GetMapping("/kri.csv")
    public ResponseEntity<String> kriCsv() {
        KriCalculator.Snapshot s = service.kri();
        StringBuilder csv = new StringBuilder("as_of,id,name,value,unit,status,amber_from,red_from\n");
        for (Kri k : s.indicators()) {
            csv.append(Csv.row(List.of(s.asOf().toString(), k.id(), k.name(), Double.toString(k.value()), k.unit(),
                    k.status().name(), Double.toString(k.amberFrom()), Double.toString(k.redFrom()))));
        }
        return csv(csv.toString(), "kri.csv");
    }

    @GetMapping("/export/exceptions.csv")
    public ResponseEntity<String> exceptionsCsv() {
        StringBuilder csv = new StringBuilder("id,title,asset_id,policy_reference,requester,owner,status,"
                + "inherent_score,residual_score,residual_rating,requested_expiry,renewal_count,created_at\n");
        for (ExceptionRequest e : service.list(Optional.empty())) {
            csv.append(Csv.row(List.of(e.id().toString(), e.title(), e.assetId(), e.policyReference(),
                    e.requester(), e.owner(), e.status().name(), Integer.toString(e.inherentRisk().value()),
                    Integer.toString(e.residualRisk().value()), e.residualRating().name(),
                    e.requestedExpiry().toString(), Integer.toString(e.renewalCount()),
                    e.createdAt().toString())));
        }
        return csv(csv.toString(), "exceptions.csv");
    }

    @GetMapping("/audit/verify")
    public ChainCheck verifyAudit() {
        Optional<Long> broken = service.verifyAuditChain();
        return new ChainCheck(broken.isEmpty(), broken.orElse(null));
    }

    private static ResponseEntity<String> csv(String body, String filename) {
        return ResponseEntity.ok()
                .contentType(CSV)
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"" + filename + "\"")
                .body(body);
    }
}
