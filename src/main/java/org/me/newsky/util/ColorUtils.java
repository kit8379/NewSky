package org.me.newsky.util;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;

public class ColorUtils {

    private static final MiniMessage MINI = MiniMessage.miniMessage();
    private static final LegacyComponentSerializer LEGACY = LegacyComponentSerializer.builder().character('&').hexColors().build();

    /**
     * Supports BOTH MiniMessage syntax and legacy '&' and '&#RRGGBB' color codes.
     */
    public static Component colorize(String message) {
        // Step 1: Convert legacy color codes into Component
        Component legacyComponent = LEGACY.deserialize(message);

        // Step 2: Serialize legacy component to MiniMessage-compatible string
        String miniFormat = MINI.serialize(legacyComponent);

        // Step 3: Deserialize MiniMessage (to handle things like <bold>, <hover>, etc.)
        return MINI.deserialize(miniFormat);
    }
}
