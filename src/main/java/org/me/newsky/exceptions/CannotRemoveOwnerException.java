package org.me.newsky.exceptions;

public class CannotRemoveOwnerException extends RuntimeException {

    @SuppressWarnings("unused")
    public CannotRemoveOwnerException() {
        super();
    }

    @SuppressWarnings("unused")
    public CannotRemoveOwnerException(String message) {
        super(message);
    }

    @SuppressWarnings("unused")
    public CannotRemoveOwnerException(String message, Throwable cause) {
        super(message, cause);
    }

    @SuppressWarnings("unused")
    public CannotRemoveOwnerException(Throwable cause) {
        super(cause);
    }
}
