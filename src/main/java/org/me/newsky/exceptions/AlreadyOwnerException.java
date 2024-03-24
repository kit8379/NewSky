package org.me.newsky.exceptions;

public class AlreadyOwnerException extends RuntimeException {

    @SuppressWarnings("unused")
    public AlreadyOwnerException() {
        super();
    }

    @SuppressWarnings("unused")
    public AlreadyOwnerException(String message) {
        super(message);
    }

    @SuppressWarnings("unused")
    public AlreadyOwnerException(String message, Throwable cause) {
        super(message, cause);
    }

    @SuppressWarnings("unused")
    public AlreadyOwnerException(Throwable cause) {
        super(cause);
    }
}
