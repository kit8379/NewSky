package org.me.newsky.util;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.gson.GsonComponentSerializer;

public class ComponentUtils {

    /**
     * Serializes a Component to a JSON string.
     *
     * @param component the Component to serialize
     * @return the JSON string representation of the Component
     */
    public static String serialize(Component component) {
        return GsonComponentSerializer.gson().serialize(component);
    }

    /**
     * Deserializes a JSON string to a Component.
     *
     * @param json the JSON string to deserialize
     * @return the Component represented by the JSON string
     */
    public static Component deserialize(String json) {
        return GsonComponentSerializer.gson().deserialize(json);
    }
}
