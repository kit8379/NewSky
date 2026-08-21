package org.me.newsky.exceptions;

/**
 * The invitation's issuer is no longer a member of the island at redemption time. An invitation
 * is a member's personal vouch, so it dies with their membership; checked inside the add-member
 * transaction under the island lock.
 */
public class InviterNotMemberException extends RuntimeException {

    public InviterNotMemberException() {
        super();
    }
}
