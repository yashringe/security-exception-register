package io.github.yashringe.exceptionregister.domain;

public enum ExceptionStatus {
    DRAFT,
    SUBMITTED,
    APPROVED,
    REJECTED,
    /** The approval ran out and nobody closed or renewed it: the gap is still there, the permission is not. */
    EXPIRED,
    /** The underlying gap was fixed, the exception is no longer needed. */
    CLOSED;

    /** Exceptions that still represent an accepted or pending risk. */
    public boolean isOpen() {
        return this == SUBMITTED || this == APPROVED || this == EXPIRED;
    }
}
