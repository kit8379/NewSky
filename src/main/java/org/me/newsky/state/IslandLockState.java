package org.me.newsky.state;

import org.me.newsky.NewSky;
import org.me.newsky.redis.RedisHandler;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.params.SetParams;

import java.util.Objects;

public final class IslandLockState {

    private static final String LUA_RELEASE_LOCK = "if redis.call('GET', KEYS[1]) == ARGV[1] then " + "return redis.call('DEL', KEYS[1]) " + "else " + "return 0 " + "end";
    private static final String LUA_EXTEND_LOCK = "if redis.call('GET', KEYS[1]) == ARGV[1] then " + "return redis.call('PEXPIRE', KEYS[1], ARGV[2]) " + "else " + "return 0 " + "end";

    private final NewSky plugin;
    private final RedisHandler redisHandler;

    public IslandLockState(NewSky plugin, RedisHandler redisHandler) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        this.redisHandler = Objects.requireNonNull(redisHandler, "redisHandler");
    }

    public boolean tryAcquire(String key, String token, long ttlMillis) {
        Objects.requireNonNull(key, "key");
        Objects.requireNonNull(token, "token");

        try (Jedis jedis = redisHandler.getJedis()) {
            String result = jedis.set(key, token, SetParams.setParams().nx().px(Math.max(1L, ttlMillis)));
            return "OK".equalsIgnoreCase(result);
        } catch (Exception e) {
            plugin.severe("Failed to acquire lock for key: " + key, e);
            return false;
        }
    }

    public boolean extend(String key, String token, long ttlMillis) {
        Objects.requireNonNull(key, "key");
        Objects.requireNonNull(token, "token");

        try (Jedis jedis = redisHandler.getJedis()) {
            Object result = jedis.eval(LUA_EXTEND_LOCK, 1, key, token, String.valueOf(Math.max(1L, ttlMillis)));
            return result instanceof Long && ((Long) result) > 0L;
        } catch (Exception e) {
            plugin.severe("Failed to extend lock for key: " + key, e);
            return false;
        }
    }

    public void release(String key, String token) {
        Objects.requireNonNull(key, "key");
        Objects.requireNonNull(token, "token");

        try (Jedis jedis = redisHandler.getJedis()) {
            jedis.eval(LUA_RELEASE_LOCK, 1, key, token);
        } catch (Exception e) {
            plugin.severe("Failed to release lock for key: " + key, e);
        }
    }

    public boolean exists(String key) {
        Objects.requireNonNull(key, "key");

        try (Jedis jedis = redisHandler.getJedis()) {
            return jedis.exists(key);
        } catch (Exception e) {
            plugin.severe("Failed to check lock for key: " + key, e);
            return false;
        }
    }

    public long pttl(String key) {
        Objects.requireNonNull(key, "key");

        try (Jedis jedis = redisHandler.getJedis()) {
            return jedis.pttl(key);
        } catch (Exception e) {
            plugin.severe("Failed to get lock TTL for key: " + key, e);
            return -1L;
        }
    }
}