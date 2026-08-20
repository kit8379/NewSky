package org.me.newsky.cluster;

import org.me.newsky.NewSky;
import org.me.newsky.model.Invitation;
import org.me.newsky.redis.RedisHandler;
import redis.clients.jedis.params.SetParams;

import java.util.Optional;
import java.util.UUID;

/**
 * Stores transient pending island invitations with an expiry, keyed by invitee.
 */
public class InvitationStore extends ClusterState {

    public InvitationStore(NewSky plugin, RedisHandler redisHandler) {
        super(plugin, redisHandler);
    }

    /**
     * Adds an invite only if the invitee has none pending. Set-if-absent runs atomically in
     * Redis: two inviters racing for the same invitee resolve to exactly one stored invite
     * instead of the second silently overwriting the first.
     *
     * @return true if the invite was stored, false if one was already pending
     */
    public boolean addIslandInvite(UUID inviteeUuid, UUID islandUuid, UUID inviterUuid, int ttlSeconds) {
        String value = islandUuid + ":" + inviterUuid;
        return execute(jedis -> jedis.set(ClusterKeys.invitation(inviteeUuid), value, SetParams.setParams().nx().ex(ttlSeconds)) != null, "Failed to add island invite for: " + inviteeUuid);
    }

    public void removeIslandInvite(UUID inviteeUuid) {
        run(jedis -> jedis.del(ClusterKeys.invitation(inviteeUuid)), "Failed to remove island invite for: " + inviteeUuid);
    }

    public Optional<Invitation> getIslandInvite(UUID inviteeUuid) {
        return execute(jedis -> parseInvitation(inviteeUuid, jedis.get(ClusterKeys.invitation(inviteeUuid))), "Failed to get island invite for: " + inviteeUuid);
    }

    /**
     * Atomically reads and deletes the invite, so two concurrent accepts can never both redeem
     * the same invitation.
     */
    public Optional<Invitation> consumeIslandInvite(UUID inviteeUuid) {
        return execute(jedis -> parseInvitation(inviteeUuid, jedis.getDel(ClusterKeys.invitation(inviteeUuid))), "Failed to consume island invite for: " + inviteeUuid);
    }

    private Optional<Invitation> parseInvitation(UUID inviteeUuid, String value) {
        if (value == null || value.isEmpty()) {
            return Optional.empty();
        }

        String[] parts = value.split(":", 2);
        if (parts.length != 2) {
            throw new IllegalStateException("Invalid island invitation format for invitee: " + inviteeUuid + ", value=" + value);
        }

        UUID islandUuid = parseUuid(parts[0], "invitation.islandUuid");
        UUID inviterUuid = parseUuid(parts[1], "invitation.inviterUuid");
        return Optional.of(new Invitation(islandUuid, inviterUuid));
    }
}
