package io.github.yashringe.exceptionregister.domain;

/** A business rule was broken (wrong state, missing approval rights, invalid input). Maps to HTTP 409/422. */
public class DomainException extends RuntimeException {
    private static final long serialVersionUID = 1L;

    public DomainException(String message) {
        super(message);
    }
}
