package org.me.newsky.exceptions;

/**
 * The message is the comma-separated list of biomes the island's biomes upgrade level allows,
 * so the command can show the player what they may use instead.
 */
public class BiomeNotUnlockedException extends RuntimeException {

    public BiomeNotUnlockedException(String allowed) {
        super(allowed);
    }
}
