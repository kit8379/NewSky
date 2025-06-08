package org.me.newsky.util;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.gson.GsonComponentSerializer;

public class ComponentUtils {
    public static String serialize(Component component) {
        return GsonComponentSerializer.gson().serialize(component);
    }

    public static Component deserialize(String json) {
        return GsonComponentSerializer.gson().deserialize(json);
    }
}
