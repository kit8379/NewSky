package org.me.newsky.exceptions;

public class HomeDoesNotExistException extends RuntimeException {

    @SuppressWarnings("unused")
    public HomeDoesNotExistException() {
        super();
    }

    @SuppressWarnings("unused")
    public HomeDoesNotExistException(String message) {
        super(message);
    }

    @SuppressWarnings("unused")
    public HomeDoesNotExistException(String message, Throwable cause) {
        super(message, cause);
    }

    @SuppressWarnings("unused")
    public HomeDoesNotExistException(Throwable cause) {
        super(cause);
    }
}
