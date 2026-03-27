package org.me.newsky.state;

import org.me.newsky.NewSky;
import org.me.newsky.redis.RedisHandler;
import redis.clients.jedis.Jedis;

import java.util.Locale;

public class ServerSelectorState {

    private static final String SERVER_MSPT_KEY = "newsky:server:mspt";
    private static final String ROUND_ROBIN_COUNTER_KEY = "newsky:server:round_robin_counter";

    private final RedisHandler redisHandler;
    private final NewSky plugin;

    public ServerSelectorState(NewSky plugin, RedisHandler redisHandler) {
        this.plugin = plugin;
        this.redisHandler = redisHandler;
    }

    public void updateServerMSPT(String serverName, double mspt) {
        try (Jedis jedis = redisHandler.getJedis()) {
            jedis.hset(SERVER_MSPT_KEY, serverName, String.format(Locale.ROOT, "%.2f", mspt));
        } catch (Exception e) {
            plugin.severe("Failed to update MSPT for server: " + serverName, e);
            throw new RuntimeException(e);
        }
    }

    public double getServerMSPT(String serverName) {
        try (Jedis jedis = redisHandler.getJedis()) {
            String value = jedis.hget(SERVER_MSPT_KEY, serverName);
            return value != null ? Double.parseDouble(value) : -1;
        } catch (Exception e) {
            plugin.severe("Failed to get MSPT for server: " + serverName, e);
            throw new RuntimeException(e);
        }
    }

    public long getRoundRobinCounter() {
        try (Jedis jedis = redisHandler.getJedis()) {
            long value = jedis.incr(ROUND_ROBIN_COUNTER_KEY);

            if (value >= 1_000_000_000L) {
                jedis.set(ROUND_ROBIN_COUNTER_KEY, "0");
                return 0;
            }

            return value;
        } catch (Exception e) {
            plugin.severe("Failed to increment round-robin counter", e);
            throw new RuntimeException(e);
        }
    }
}