package org.me.newsky.state;

import org.me.newsky.NewSky;
import org.me.newsky.redis.RedisHandler;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.Pipeline;
import redis.clients.jedis.params.ScanParams;
import redis.clients.jedis.resps.ScanResult;

import java.util.LinkedHashMap;
import java.util.Map;

public class ServerHeartbeatState {

    private static final String SERVER_HEARTBEAT_PREFIX = "newsky:heartbeat:server:";
    private static final String GAME_SERVER_HEARTBEAT_PREFIX = "newsky:heartbeat:game_server:";

    private static final String ISLAND_SERVER_KEY = "newsky:island:server";
    private static final String SERVER_MSPT_KEY = "newsky:server:mspt";

    private final RedisHandler redisHandler;
    private final NewSky plugin;

    public ServerHeartbeatState(NewSky plugin, RedisHandler redisHandler) {
        this.plugin = plugin;
        this.redisHandler = redisHandler;
    }

    private String serverHeartbeatKey(String serverName) {
        return SERVER_HEARTBEAT_PREFIX + serverName;
    }

    private String gameServerHeartbeatKey(String serverName) {
        return GAME_SERVER_HEARTBEAT_PREFIX + serverName;
    }

    public void updateActiveServer(String serverName, boolean lobby, int ttlSeconds) {
        String timestamp = String.valueOf(System.currentTimeMillis());

        try (Jedis jedis = redisHandler.getJedis()) {
            jedis.setex(serverHeartbeatKey(serverName), ttlSeconds, timestamp);

            if (lobby) {
                jedis.del(gameServerHeartbeatKey(serverName));
            } else {
                jedis.setex(gameServerHeartbeatKey(serverName), ttlSeconds, timestamp);
            }
        } catch (Exception e) {
            plugin.severe("Failed to update active server for: " + serverName, e);
        }
    }

    public void removeActiveServer(String serverName) {
        try (Jedis jedis = redisHandler.getJedis()) {

            Pipeline pipeline = jedis.pipelined();
            pipeline.del(serverHeartbeatKey(serverName));
            pipeline.del(gameServerHeartbeatKey(serverName));
            pipeline.hdel(SERVER_MSPT_KEY, serverName);
            pipeline.sync();

            Map<String, String> islandServerMap = jedis.hgetAll(ISLAND_SERVER_KEY);

            if (!islandServerMap.isEmpty()) {
                Pipeline islandPipeline = jedis.pipelined();

                for (Map.Entry<String, String> entry : islandServerMap.entrySet()) {
                    if (serverName.equals(entry.getValue())) {
                        islandPipeline.hdel(ISLAND_SERVER_KEY, entry.getKey());
                    }
                }

                islandPipeline.sync();
            }

            plugin.debug("ServerHeartbeatState", "Cleaned up all state data for server: " + serverName);

        } catch (Exception e) {
            plugin.severe("Failed to remove active server: " + serverName, e);
        }
    }

    public Map<String, String> getActiveServers() {
        Map<String, String> result = new LinkedHashMap<>();

        try (Jedis jedis = redisHandler.getJedis()) {

            String cursor = ScanParams.SCAN_POINTER_START;
            ScanParams params = new ScanParams().match(SERVER_HEARTBEAT_PREFIX + "*").count(200);

            do {
                ScanResult<String> scan = jedis.scan(cursor, params);

                for (String key : scan.getResult()) {
                    String value = jedis.get(key);
                    if (value == null) continue;

                    String serverName = key.substring(SERVER_HEARTBEAT_PREFIX.length());
                    result.put(serverName, value);
                }

                cursor = scan.getCursor();

            } while (!ScanParams.SCAN_POINTER_START.equals(cursor));

        } catch (Exception e) {
            plugin.severe("Failed to get active servers", e);
        }

        return result;
    }

    public Map<String, String> getActiveGameServers() {
        Map<String, String> result = new LinkedHashMap<>();

        try (Jedis jedis = redisHandler.getJedis()) {

            String cursor = ScanParams.SCAN_POINTER_START;
            ScanParams params = new ScanParams().match(GAME_SERVER_HEARTBEAT_PREFIX + "*").count(200);

            do {
                ScanResult<String> scan = jedis.scan(cursor, params);

                for (String key : scan.getResult()) {
                    String value = jedis.get(key);
                    if (value == null) continue;

                    String serverName = key.substring(GAME_SERVER_HEARTBEAT_PREFIX.length());
                    result.put(serverName, value);
                }

                cursor = scan.getCursor();

            } while (!ScanParams.SCAN_POINTER_START.equals(cursor));

        } catch (Exception e) {
            plugin.severe("Failed to get active game servers", e);
        }

        return result;
    }
}