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
        Component legacyComponent = LEGACY.deserialize(message);

        String miniFormat = MINI.serialize(legacyComponent);

        return MINI.deserialize(miniFormat);
    }
}
