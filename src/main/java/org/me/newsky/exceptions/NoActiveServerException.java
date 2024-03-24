package org.me.newsky.exceptions;

public class NoActiveServerException extends RuntimeException {

    @SuppressWarnings("unused")
    public NoActiveServerException() {
        super();
    }

    @SuppressWarnings("unused")
    public NoActiveServerException(String message) {
        super(message);
    }

    @SuppressWarnings("unused")
    public NoActiveServerException(String message, Throwable cause) {
        super(message, cause);
    }

    @SuppressWarnings("unused")
    public NoActiveServerException(Throwable cause) {
        super(cause);
    }
}
