package org.me.newsky.state;

import org.me.newsky.NewSky;
import org.me.newsky.model.Invitation;
import org.me.newsky.redis.RedisHandler;
import redis.clients.jedis.Jedis;

import java.util.Optional;
import java.util.UUID;

public class IslandInvitationState {

    private static final String ISLAND_INVITATION_PREFIX = "newsky:invitation:island:";

    private final RedisHandler redisHandler;
    private final NewSky plugin;

    public IslandInvitationState(NewSky plugin, RedisHandler redisHandler) {
        this.plugin = plugin;
        this.redisHandler = redisHandler;
    }


    private String islandInvitationKey(UUID inviteeUuid) {
        return ISLAND_INVITATION_PREFIX + inviteeUuid;
    }

    public void addIslandInvite(UUID inviteeUuid, UUID islandUuid, UUID inviterUuid, int ttlSeconds) {
        try (Jedis jedis = redisHandler.getJedis()) {
            String value = islandUuid + ":" + inviterUuid;
            jedis.setex(islandInvitationKey(inviteeUuid), ttlSeconds, value);
        } catch (Exception e) {
            plugin.severe("Failed to add island invite for: " + inviteeUuid, e);
            throw new RuntimeException(e);
        }
    }

    public void removeIslandInvite(UUID inviteeUuid) {
        try (Jedis jedis = redisHandler.getJedis()) {
            jedis.del(islandInvitationKey(inviteeUuid));
        } catch (Exception e) {
            plugin.severe("Failed to remove island invite for: " + inviteeUuid, e);
            throw new RuntimeException(e);
        }
    }

    public Optional<Invitation> getIslandInvite(UUID inviteeUuid) {
        try (Jedis jedis = redisHandler.getJedis()) {
            String value = jedis.get(islandInvitationKey(inviteeUuid));
            if (value == null || value.isEmpty()) {
                return Optional.empty();
            }

            String[] parts = value.split(":");
            if (parts.length != 2) {
                return Optional.empty();
            }

            UUID islandUuid = UUID.fromString(parts[0]);
            UUID inviterUuid = UUID.fromString(parts[1]);
            return Optional.of(new Invitation(islandUuid, inviterUuid));
        } catch (Exception e) {
            plugin.severe("Failed to get island invite for: " + inviteeUuid, e);
            return Optional.empty();
        }
    }
}