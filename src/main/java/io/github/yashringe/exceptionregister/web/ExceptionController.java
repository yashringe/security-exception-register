package io.github.yashringe.exceptionregister.web;

import io.github.yashringe.exceptionregister.app.ExceptionService;
import io.github.yashringe.exceptionregister.app.NewExceptionCommand;
import io.github.yashringe.exceptionregister.domain.CompensatingControl;
import io.github.yashringe.exceptionregister.domain.ExceptionRequest;
import io.github.yashringe.exceptionregister.domain.ExceptionStatus;
import io.github.yashringe.exceptionregister.web.ApiModels.AuditView;
import io.github.yashringe.exceptionregister.web.ApiModels.CreateBody;
import io.github.yashringe.exceptionregister.web.ApiModels.DecisionBody;
import io.github.yashringe.exceptionregister.web.ApiModels.ExceptionView;
import io.github.yashringe.exceptionregister.web.ApiModels.RenewBody;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@RestController
@RequestMapping("/api/exceptions")
public class ExceptionController {

    private final ExceptionService service;
    private final Clock clock;

    public ExceptionController(ExceptionService service, Clock clock) {
        this.service = service;
        this.clock = clock;
    }

    @PostMapping
    public ResponseEntity<ExceptionView> create(@Valid @RequestBody CreateBody body, Authentication auth) {
        List<CompensatingControl> controls = body.controls() == null ? List.of()
                : body.controls().stream().map(ApiModels.ControlBody::toDomain).toList();
        ExceptionRequest e = service.create(CurrentActor.from(auth), new NewExceptionCommand(body.title(),
                body.assetId(), body.policyReference(), body.justification(), body.owner(), body.likelihood(),
                body.impact(), controls, body.requestedExpiry()));
        var location = ServletUriComponentsBuilder.fromCurrentRequest().path("/{id}").buildAndExpand(e.id()).toUri();
        return ResponseEntity.created(location).body(view(e));
    }

    @GetMapping
    public List<ExceptionView> list(@RequestParam Optional<ExceptionStatus> status) {
        return service.list(status).stream().map(this::view).toList();
    }

    @GetMapping("/{id}")
    public ExceptionView get(@PathVariable UUID id) {
        return view(service.get(id));
    }

    @PostMapping("/{id}/submit")
    public ExceptionView submit(@PathVariable UUID id, Authentication auth) {
        return view(service.submit(id, CurrentActor.from(auth)));
    }

    @PostMapping("/{id}/approve")
    public ExceptionView approve(@PathVariable UUID id, @Valid @RequestBody(required = false) DecisionBody body,
                                 Authentication auth) {
        return view(service.approve(id, CurrentActor.from(auth), body == null ? null : body.comment()));
    }

    @PostMapping("/{id}/reject")
    public ExceptionView reject(@PathVariable UUID id, @Valid @RequestBody DecisionBody body, Authentication auth) {
        return view(service.reject(id, CurrentActor.from(auth), body.comment()));
    }

    @PostMapping("/{id}/close")
    public ExceptionView close(@PathVariable UUID id, @Valid @RequestBody(required = false) DecisionBody body,
                               Authentication auth) {
        return view(service.close(id, CurrentActor.from(auth), body == null ? null : body.comment()));
    }

    @PostMapping("/{id}/renew")
    public ExceptionView renew(@PathVariable UUID id, @Valid @RequestBody RenewBody body, Authentication auth) {
        return view(service.renew(id, CurrentActor.from(auth), body.newExpiry()));
    }

    @GetMapping("/{id}/audit")
    public List<AuditView> audit(@PathVariable UUID id) {
        return service.auditTrail(id).stream().map(AuditView::of).toList();
    }

    private ExceptionView view(ExceptionRequest e) {
        return ExceptionView.of(e, LocalDate.now(clock));
    }
}
