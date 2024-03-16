package org.me.newsky.exceptions;

public class IslandDoesNotExistException extends RuntimeException {

    @SuppressWarnings("unused")
    public IslandDoesNotExistException() {
        super();
    }

    @SuppressWarnings("unused")
    public IslandDoesNotExistException(String message) {
        super(message);
    }

    @SuppressWarnings("unused")
    public IslandDoesNotExistException(String message, Throwable cause) {
        super(message, cause);
    }

    @SuppressWarnings("unused")
    public IslandDoesNotExistException(Throwable cause) {
        super(cause);
    }
}
