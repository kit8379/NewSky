package org.me.newsky.exceptions;

public class IslandAlreadyExistException extends RuntimeException {

    @SuppressWarnings("unused")
    public IslandAlreadyExistException() {
        super();
    }

    @SuppressWarnings("unused")
    public IslandAlreadyExistException(String message) {
        super(message);
    }

    @SuppressWarnings("unused")
    public IslandAlreadyExistException(String message, Throwable cause) {
        super(message, cause);
    }

    @SuppressWarnings("unused")
    public IslandAlreadyExistException(Throwable cause) {
        super(cause);
    }
}
