package org.me.newsky.exceptions;

public class MemberDoesNotExistException extends RuntimeException {

    @SuppressWarnings("unused")
    public MemberDoesNotExistException() {
        super();
    }

    @SuppressWarnings("unused")
    public MemberDoesNotExistException(String message) {
        super(message);
    }

    @SuppressWarnings("unused")
    public MemberDoesNotExistException(String message, Throwable cause) {
        super(message, cause);
    }

    @SuppressWarnings("unused")
    public MemberDoesNotExistException(Throwable cause) {
        super(cause);
    }
}
