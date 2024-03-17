package org.me.newsky.exceptions;

public class IslandPlayerDoesNotExistException extends RuntimeException {

    @SuppressWarnings("unused")
    public IslandPlayerDoesNotExistException() {
        super();
    }

    @SuppressWarnings("unused")
    public IslandPlayerDoesNotExistException(String message) {
        super(message);
    }

    @SuppressWarnings("unused")
    public IslandPlayerDoesNotExistException(String message, Throwable cause) {
        super(message, cause);
    }

    @SuppressWarnings("unused")
    public IslandPlayerDoesNotExistException(Throwable cause) {
        super(cause);
    }
}
