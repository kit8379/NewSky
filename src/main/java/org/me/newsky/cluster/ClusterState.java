package org.me.newsky.cluster;

import org.me.newsky.NewSky;
import org.me.newsky.redis.RedisHandler;
import redis.clients.jedis.Jedis;

import java.util.Objects;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * Base class for Redis-backed cluster-wide state. Encapsulates connection handling,
 * error reporting and value serialization so concrete stores only implement their logic.
 */
public abstract class ClusterState {

    protected final NewSky plugin;
    protected final RedisHandler redisHandler;

    protected ClusterState(NewSky plugin, RedisHandler redisHandler) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        this.redisHandler = Objects.requireNonNull(redisHandler, "redisHandler");
    }

    protected <T> T execute(Function<Jedis, T> operation, String errorMessage) {
        try (Jedis jedis = redisHandler.getJedis()) {
            return operation.apply(jedis);
        } catch (Exception e) {
            plugin.severe(errorMessage, e);
            throw new RuntimeException(errorMessage, e);
        }
    }

    protected void run(Consumer<Jedis> operation, String errorMessage) {
        execute(jedis -> {
            operation.accept(jedis);
            return null;
        }, errorMessage);
    }

    protected UUID parseUuid(String value, String fieldName) {
        if (value == null || value.isEmpty()) {
            throw new IllegalStateException("Missing UUID value for field: " + fieldName);
        }

        try {
            return UUID.fromString(value);
        } catch (IllegalArgumentException e) {
            throw new IllegalStateException("Invalid UUID value for field " + fieldName + ": " + value, e);
        }
    }
}
