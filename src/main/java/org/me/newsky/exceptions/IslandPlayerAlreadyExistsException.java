package org.me.newsky.exceptions;

public class IslandPlayerAlreadyExistsException extends RuntimeException {

    @SuppressWarnings("unused")
    public IslandPlayerAlreadyExistsException() {
        super();
    }

    @SuppressWarnings("unused")
    public IslandPlayerAlreadyExistsException(String message) {
        super(message);
    }

    @SuppressWarnings("unused")
    public IslandPlayerAlreadyExistsException(String message, Throwable cause) {
        super(message, cause);
    }

    @SuppressWarnings("unused")
    public IslandPlayerAlreadyExistsException(Throwable cause) {
        super(cause);
    }
}
