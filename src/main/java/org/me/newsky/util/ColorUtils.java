package org.me.newsky.util;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;

public class ColorUtils {

    private static final MiniMessage MINI = MiniMessage.miniMessage();
    private static final LegacyComponentSerializer LEGACY = LegacyComponentSerializer.builder().character('&').hexColors().build();

    /**
     * Parses a string into a Component, supporting both MiniMessage and legacy '&' color codes.
     * Automatically attempts to parse as MiniMessage first. If that fails (due to malformed tags or
     * accidental use of angle brackets like "<warpName>"), it falls back to legacy formatting.
     * <p>
     * Example:
     * - "<green>Welcome</green>" will be parsed using MiniMessage.
     * - "&aHello &cWorld" will be parsed using legacy color codes.
     * - "Use /is warp <warpName>" will be correctly parsed as legacy even with angle brackets.
     *
     * @param message The input string
     * @return The parsed Component
     */
    public static Component colorize(String message) {
        if (message == null || message.isEmpty()) {
            return Component.empty();
        }

        try {
            return MINI.deserialize(message);
        } catch (Exception e) {
            return LEGACY.deserialize(message);
        }
    }
}
