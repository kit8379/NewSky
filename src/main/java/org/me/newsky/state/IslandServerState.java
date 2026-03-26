package org.me.newsky.state;

import org.me.newsky.NewSky;
import org.me.newsky.redis.RedisHandler;
import redis.clients.jedis.Jedis;

import java.util.Optional;
import java.util.UUID;

public class IslandServerState {

    private static final String ISLAND_SERVER_KEY = "newsky:island:server";

    private final RedisHandler redisHandler;
    private final NewSky plugin;

    public IslandServerState(NewSky plugin, RedisHandler redisHandler) {
        this.plugin = plugin;
        this.redisHandler = redisHandler;
    }

    public void updateIslandLoadedServer(UUID islandUuid, String serverName) {
        try (Jedis jedis = redisHandler.getJedis()) {
            jedis.hset(ISLAND_SERVER_KEY, islandUuid.toString(), serverName);
        } catch (Exception e) {
            plugin.severe("Failed to update island loaded server for: " + islandUuid, e);
            throw new RuntimeException(e);
        }
    }

    public void removeIslandLoadedServer(UUID islandUuid) {
        try (Jedis jedis = redisHandler.getJedis()) {
            jedis.hdel(ISLAND_SERVER_KEY, islandUuid.toString());
        } catch (Exception e) {
            plugin.severe("Failed to remove island loaded server for: " + islandUuid, e);
            throw new RuntimeException(e);
        }
    }

    public Optional<String> getIslandLoadedServer(UUID islandUuid) {
        try (Jedis jedis = redisHandler.getJedis()) {
            return Optional.ofNullable(jedis.hget(ISLAND_SERVER_KEY, islandUuid.toString()));
        } catch (Exception e) {
            plugin.severe("Failed to get island loaded server for: " + islandUuid, e);
            return Optional.empty();
        }
    }
}