package org.me.newsky.exceptions;

public class WarpDoesNotExistException extends RuntimeException {

    @SuppressWarnings("unused")
    public WarpDoesNotExistException() {
        super();
    }

    @SuppressWarnings("unused")
    public WarpDoesNotExistException(String message) {
        super(message);
    }

    @SuppressWarnings("unused")
    public WarpDoesNotExistException(String message, Throwable cause) {
        super(message, cause);
    }

    @SuppressWarnings("unused")
    public WarpDoesNotExistException(Throwable cause) {
        super(cause);
    }
}
