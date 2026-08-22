package org.me.newsky.model;

import org.json.JSONObject;

import java.util.UUID;

/**
 * Who is performing an island write.
 * <p>
 * Every island mutation takes one of these, so island-level authorization cannot be forgotten:
 * only {@link Player} is subject to role checks, and skipping them requires deliberately naming
 * {@link Bypass}. The role a given operation demands is fixed by the operation itself and is
 * never supplied by the caller, so a caller cannot weaken its own check.
 */
public sealed interface Actor {

    String FIELD = "actor";

    static JSONObject toJson(Actor actor) {
        JSONObject json = new JSONObject();

        if (actor instanceof Player player) {
            json.put("type", "player");
            json.put("uuid", player.uuid().toString());
        } else if (actor instanceof Bypass bypass) {
            json.put("type", "bypass");
            json.put("source", bypass.source());
        } else {
            throw new IllegalArgumentException("Unsupported actor: " + actor);
        }

        return json;
    }

    /**
     * Rebuilds an actor from a cross-server payload. A missing or unrecognised actor is an error
     * rather than a default: defaulting would silently hand out an authorization bypass.
     */
    static Actor fromJson(JSONObject payload) {
        if (!payload.has(FIELD)) {
            throw new IllegalArgumentException("Cross-server payload is missing its actor");
        }

        JSONObject json = payload.getJSONObject(FIELD);
        String type = json.optString("type", "");

        return switch (type) {
            case "player" -> new Player(UUID.fromString(json.getString("uuid")));
            case "bypass" -> new Bypass(json.getString("source"));
            default -> throw new IllegalArgumentException("Unknown actor type in cross-server payload: " + type);
        };
    }

    /** A player acting through their own island commands. Island role rules apply. */
    record Player(UUID uuid) implements Actor {
    }

    /**
     * A caller deliberately exempt from island role rules: an operator, the console, or an
     * internal task. The source string identifies it in logs and payloads, nothing more.
     */
    record Bypass(String source) implements Actor {
    }
}
