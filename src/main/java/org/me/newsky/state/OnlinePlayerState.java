package org.me.newsky.state;

import org.me.newsky.NewSky;
import org.me.newsky.redis.RedisHandler;
import redis.clients.jedis.Jedis;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

public class OnlinePlayerState {

    private static final String ONLINE_PLAYERS_KEY = "newsky:online:players";

    private final RedisHandler redisHandler;
    private final NewSky plugin;

    public OnlinePlayerState(NewSky plugin, RedisHandler redisHandler) {
        this.plugin = plugin;
        this.redisHandler = redisHandler;
    }

    public void addOnlinePlayer(UUID playerUuid, String playerName) {
        try (Jedis jedis = redisHandler.getJedis()) {
            jedis.hset(ONLINE_PLAYERS_KEY, playerUuid.toString(), playerName);
        } catch (Exception e) {
            plugin.severe("Failed to add online player: " + playerUuid, e);
            throw new RuntimeException(e);
        }
    }

    public void removeOnlinePlayer(UUID playerUuid) {
        try (Jedis jedis = redisHandler.getJedis()) {
            jedis.hdel(ONLINE_PLAYERS_KEY, playerUuid.toString());
        } catch (Exception e) {
            plugin.severe("Failed to remove online player: " + playerUuid, e);
            throw new RuntimeException(e);
        }
    }

    public Set<UUID> getOnlinePlayersUUIDs() {
        try (Jedis jedis = redisHandler.getJedis()) {
            Set<String> keys = jedis.hkeys(ONLINE_PLAYERS_KEY);
            if (keys == null || keys.isEmpty()) {
                return Set.of();
            }

            Set<UUID> result = new HashSet<>(keys.size());
            for (String key : keys) {
                if (key == null || key.isEmpty()) {
                    throw new IllegalStateException("Missing UUID value in OnlinePlayerState for online players key");
                }

                try {
                    result.add(UUID.fromString(key));
                } catch (IllegalArgumentException e) {
                    throw new IllegalStateException("Invalid UUID in online players state: " + key, e);
                }
            }

            return Set.copyOf(result);
        } catch (Exception e) {
            plugin.severe("Failed to get online player UUIDs", e);
            throw new RuntimeException(e);
        }
    }

    public Set<String> getOnlinePlayersNames() {
        try (Jedis jedis = redisHandler.getJedis()) {
            List<String> values = jedis.hvals(ONLINE_PLAYERS_KEY);
            return values == null || values.isEmpty() ? Set.of() : Set.copyOf(values);
        } catch (Exception e) {
            plugin.severe("Failed to get online player names", e);
            throw new RuntimeException(e);
        }
    }
}