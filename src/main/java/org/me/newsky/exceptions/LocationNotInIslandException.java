package org.me.newsky.exceptions;

public class LocationNotInIslandException extends RuntimeException {

    @SuppressWarnings("unused")
    public LocationNotInIslandException() {
        super();
    }

    @SuppressWarnings("unused")
    public LocationNotInIslandException(String message) {
        super(message);
    }

    @SuppressWarnings("unused")
    public LocationNotInIslandException(String message, Throwable cause) {
        super(message, cause);
    }

    @SuppressWarnings("unused")
    public LocationNotInIslandException(Throwable cause) {
        super(cause);
    }
}
