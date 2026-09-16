package org.me.newsky.exceptions;

/**
 * The message is the limit that was hit: a String-only constructor is what the cross-server
 * exception path restores, so the value survives a remote failure.
 */
public class TeamLimitReachedException extends RuntimeException {

    public TeamLimitReachedException(String limit) {
        super(limit);
    }
}
