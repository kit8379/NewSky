package org.me.newsky.cluster;

import org.me.newsky.NewSky;
import org.me.newsky.model.Invitation;
import org.me.newsky.redis.RedisHandler;

import java.util.Optional;
import java.util.UUID;

/**
 * Stores transient pending island invitations with an expiry, keyed by invitee.
 */
public class InvitationStore extends ClusterState {

    public InvitationStore(NewSky plugin, RedisHandler redisHandler) {
        super(plugin, redisHandler);
    }

    public void addIslandInvite(UUID inviteeUuid, UUID islandUuid, UUID inviterUuid, int ttlSeconds) {
        String value = islandUuid + ":" + inviterUuid;
        run(jedis -> jedis.setex(ClusterKeys.invitation(inviteeUuid), ttlSeconds, value), "Failed to add island invite for: " + inviteeUuid);
    }

    public void removeIslandInvite(UUID inviteeUuid) {
        run(jedis -> jedis.del(ClusterKeys.invitation(inviteeUuid)), "Failed to remove island invite for: " + inviteeUuid);
    }

    public Optional<Invitation> getIslandInvite(UUID inviteeUuid) {
        return execute(jedis -> {
            String value = jedis.get(ClusterKeys.invitation(inviteeUuid));
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
        }, "Failed to get island invite for: " + inviteeUuid);
    }
}
