package io.github.yashringe.exceptionregister.app;

import java.util.UUID;

public class ExceptionNotFoundException extends RuntimeException {
    private static final long serialVersionUID = 1L;

    public ExceptionNotFoundException(UUID id) {
        super("No exception request with id " + id);
    }
}
