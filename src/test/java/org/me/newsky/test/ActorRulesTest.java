package org.me.newsky.test;

import org.me.newsky.exceptions.ActorNotAuthorizedException;
import org.me.newsky.model.Actor;
import org.json.JSONObject;

import java.util.UUID;

/**
 * The two identity rules every API write leans on. Small, but load-bearing: SELF and BYPASS are
 * the only authorization some writes get (homes, warps, island creation, world load/unload), so a
 * later "simplification" that lets a Player through requireBypass, or lets an actor act on another
 * player's UUID, has to fail here rather than in production.
 */
public final class ActorRulesTest {

    public static void main(String[] args) {
        selfRuleAcceptsOnlyTheSamePlayer();
        bypassSkipsEveryRule();
        bypassRuleRejectsPlayers();
        crossServerRoundTripKeepsIdentity();
        System.out.println("ActorRulesTest: ALL PASS");
    }

    private static void selfRuleAcceptsOnlyTheSamePlayer() {
        UUID self = UUID.randomUUID();
        UUID other = UUID.randomUUID();
        Actor actor = new Actor.Player(self);

        actor.requireSelf(self);
        Check.that(true, "a player may act on themselves");

        Check.that(throwsNotAuthorized(() -> actor.requireSelf(other)), "a player may not act on another player");
    }

    private static void bypassSkipsEveryRule() {
        Actor bypass = new Actor.Bypass("console");

        bypass.requireSelf(UUID.randomUUID());
        bypass.requireBypass();
        Check.that(true, "a bypass satisfies both the SELF and BYPASS rules");
    }

    private static void bypassRuleRejectsPlayers() {
        Actor actor = new Actor.Player(UUID.randomUUID());
        Check.that(throwsNotAuthorized(actor::requireBypass), "a player cannot satisfy the BYPASS rule");
    }

    // A restored actor must keep enforcing the same rules: an actor that decayed into a bypass
    // on the wire would hand out a silent authorization bypass on every cross-server write.
    private static void crossServerRoundTripKeepsIdentity() {
        UUID self = UUID.randomUUID();
        JSONObject payload = new JSONObject().put(Actor.FIELD, Actor.toJson(new Actor.Player(self)));

        Actor restored = Actor.fromJson(payload);
        Check.that(restored instanceof Actor.Player player && player.uuid().equals(self), "a player actor survives the wire unchanged");
        Check.that(throwsNotAuthorized(restored::requireBypass), "a restored player actor still cannot bypass");

        boolean rejectedMissing = false;
        try {
            Actor.fromJson(new JSONObject());
        } catch (IllegalArgumentException e) {
            rejectedMissing = true;
        }
        Check.that(rejectedMissing, "a payload with no actor is rejected rather than defaulted");
    }

    private static boolean throwsNotAuthorized(Runnable action) {
        try {
            action.run();
            return false;
        } catch (ActorNotAuthorizedException e) {
            return true;
        }
    }
}
