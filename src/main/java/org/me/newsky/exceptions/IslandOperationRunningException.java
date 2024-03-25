package org.me.newsky.exceptions;

public class IslandOperationRunningException extends RuntimeException {

    @SuppressWarnings("unused")
    public IslandOperationRunningException() {
        super();
    }

    @SuppressWarnings("unused")
    public IslandOperationRunningException(String message) {
        super(message);
    }

    @SuppressWarnings("unused")
    public IslandOperationRunningException(String message, Throwable cause) {
        super(message, cause);
    }

    @SuppressWarnings("unused")
    public IslandOperationRunningException(Throwable cause) {
        super(cause);
    }
}
