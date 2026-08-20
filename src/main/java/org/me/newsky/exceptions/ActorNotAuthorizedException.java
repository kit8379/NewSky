package org.me.newsky.exceptions;

/**
 * The actor is not allowed to perform this operation on this subject: a player acting on someone
 * else's homes, warps or membership, or a player attempting an operator-only operation.
 * <p>
 * Distinct from {@link NotIslandOwnerException}, which is about an island role read inside the
 * write transaction. This one is a pure identity mismatch, decided before any I/O.
 */
public class ActorNotAuthorizedException extends RuntimeException {

    public ActorNotAuthorizedException() {
        super();
    }
}
