package io.github.yashringe.exceptionregister.web;

import io.github.yashringe.exceptionregister.app.ExceptionNotFoundException;
import io.github.yashringe.exceptionregister.domain.DomainException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/** Business rule violations come back as RFC 9457 problem details with the rule's own message. */
@RestControllerAdvice
public class ApiErrorHandler {

    @ExceptionHandler(DomainException.class)
    ProblemDetail ruleViolated(DomainException ex) {
        ProblemDetail p = ProblemDetail.forStatusAndDetail(HttpStatus.UNPROCESSABLE_ENTITY, ex.getMessage());
        p.setTitle("Rule violated");
        return p;
    }

    @ExceptionHandler(ExceptionNotFoundException.class)
    ProblemDetail notFound(ExceptionNotFoundException ex) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, ex.getMessage());
    }

    @ExceptionHandler(OptimisticLockingFailureException.class)
    ProblemDetail conflict(OptimisticLockingFailureException ex) {
        ProblemDetail p = ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, ex.getMessage());
        p.setTitle("Concurrent change");
        return p;
    }
}
