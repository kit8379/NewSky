package org.me.newsky.model;

import org.json.JSONObject;
import org.me.newsky.exceptions.ActorNotAuthorizedException;

import java.util.UUID;

/**
 * Who is performing a write.
 * <p>
 * Every mutating API method takes one of these as its first parameter, so authorization cannot be
 * forgotten: only {@link Player} is subject to any rule, and skipping the rules requires
 * deliberately naming {@link Bypass}. What a given operation demands is fixed by the operation
 * itself and is never supplied by the caller, so a caller cannot weaken its own check.
 * <p>
 * There are exactly four rules, and every write's javadoc names the one it uses:
 * <ul>
 *   <li><b>OWNER</b> / <b>MEMBER</b> — an island role, read inside the write transaction under
 *       the island row lock (see {@code DatabaseHandler.requireRole}). Shared state, so it can
 *       only be checked there.</li>
 *   <li><b>SELF</b> — the actor must be the player being acted on, enforced by
 *       {@link #requireSelf(UUID)}. A pure identity comparison with no I/O, so unlike a role it
 *       can never be stale and is checked at the API boundary.</li>
 *   <li><b>BYPASS</b> — operator, console or internal task only, enforced by
 *       {@link #requireBypass()}.</li>
 * </ul>
 */
public sealed interface Actor {

    String FIELD = "actor";

    /**
     * Enforces the SELF rule: this actor may only act on that player, unless it is a
     * {@link Bypass}. Zero I/O - it compares two UUIDs, so it belongs at the API boundary and
     * cannot go stale the way an island role would.
     */
    default void requireSelf(UUID subjectPlayerUuid) {
        if (this instanceof Bypass) {
            return;
        }

        if (this instanceof Player player && player.uuid().equals(subjectPlayerUuid)) {
            return;
        }

        throw new ActorNotAuthorizedException();
    }

    /**
     * Enforces the BYPASS rule: operators, console and internal tasks only. Used by operations
     * that have no player-facing authorization at all, such as loading or unloading a world.
     */
    default void requireBypass() {
        if (!(this instanceof Bypass)) {
            throw new ActorNotAuthorizedException();
        }
    }

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

    /** A player acting through their own island commands. All rules apply. */
    record Player(UUID uuid) implements Actor {
    }

    /**
     * A caller deliberately exempt from island role rules: an operator, the console, or an
     * internal task. The source string identifies it in logs and payloads, nothing more.
     */
    record Bypass(String source) implements Actor {
    }
}
