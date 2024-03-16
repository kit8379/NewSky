package org.me.newsky.exceptions;

public class MemberAlreadyExistsException extends RuntimeException {

    @SuppressWarnings("unused")
    public MemberAlreadyExistsException() {
        super();
    }

    @SuppressWarnings("unused")
    public MemberAlreadyExistsException(String message) {
        super(message);
    }

    @SuppressWarnings("unused")
    public MemberAlreadyExistsException(String message, Throwable cause) {
        super(message, cause);
    }

    @SuppressWarnings("unused")
    public MemberAlreadyExistsException(Throwable cause) {
        super(cause);
    }
}
