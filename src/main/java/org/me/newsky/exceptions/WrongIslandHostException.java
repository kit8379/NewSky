package org.me.newsky.exceptions;

/**
 * This server is not the island's claim holder, so it must not execute the write: doing so would
 * commit a change the real host's in-memory copy never hears about. The caller re-resolves the
 * claim and routes the write to whoever holds it now. Crosses the wire by class name, so the
 * no-arg constructor is required.
 */
public class WrongIslandHostException extends RuntimeException {

    public WrongIslandHostException() {
        super();
    }
}
