package org.me.newsky.util;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;

import java.util.regex.Pattern;

public final class ColorUtils {

    private static final MiniMessage MINI = MiniMessage.miniMessage();

    private static final LegacyComponentSerializer LEGACY = LegacyComponentSerializer.builder().character('&').hexColors().build();

    private static final Pattern LEGACY_CODE_PATTERN = Pattern.compile("(?i)&[0-9A-FK-OR]");

    public static Component colorize(String message) {
        if (message == null || message.isEmpty()) {
            return Component.empty();
        }

        if (containsLegacyCodes(message)) {
            return LEGACY.deserialize(message);
        }

        return MINI.deserialize(message);
    }

    private static boolean containsLegacyCodes(String message) {
        return LEGACY_CODE_PATTERN.matcher(message).find();
    }
}