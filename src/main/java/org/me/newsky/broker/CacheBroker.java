package org.me.newsky.broker;

import org.json.JSONObject;
import org.me.newsky.NewSky;
import org.me.newsky.cache.Cache;
import org.me.newsky.redis.RedisHandler;
import redis.clients.jedis.JedisPubSub;

import java.util.UUID;
import java.util.logging.Level;

public class CacheBroker {

    private final NewSky plugin;
    private final RedisHandler redisHandler;
    private final Cache cache;
    private JedisPubSub subscriber;

    private final String serverID;
    private final String channelID;

    public CacheBroker(NewSky plugin, RedisHandler redisHandler, Cache cache, String serverID, String channelID) {
        this.plugin = plugin;
        this.redisHandler = redisHandler;
        this.cache = cache;
        this.serverID = serverID;
        this.channelID = channelID;
    }

    public void subscribe() {
        subscriber = new JedisPubSub() {
            @Override
            public void onMessage(String channel, String message) {
                try {
                    JSONObject json = new JSONObject(message);
                    String source = json.getString("source");
                    String type = json.getString("type");
                    String island = json.getString("island");

                    // Skip if this server was the source of the update
                    if (serverID.equals(source)) {
                        return;
                    }

                    handleUpdate(type, UUID.fromString(island));
                } catch (Exception e) {
                    plugin.getLogger().log(Level.SEVERE, "Failed to process cache update message", e);
                }
            }
        };

        redisHandler.subscribe(subscriber, channelID);
    }

    public void unsubscribe() {
        if (subscriber != null) {
            subscriber.unsubscribe();
        }
    }

    public void publishUpdate(String type, UUID islandUuid) {
        try {
            JSONObject json = new JSONObject();
            json.put("source", serverID);
            json.put("type", type);
            json.put("island", islandUuid.toString());

            redisHandler.publish(channelID, json.toString());
        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to publish cache update", e);
        }
    }

    private void handleUpdate(String type, UUID islandUuid) {
        switch (type) {
            case "island_data":
                cache.reloadIslandData(islandUuid);
                break;
            case "island_players":
                cache.reloadIslandPlayers(islandUuid);
                break;
            case "island_homes":
                cache.reloadIslandHomes(islandUuid);
                break;
            case "island_warps":
                cache.reloadIslandWarps(islandUuid);
                break;
            case "island_bans":
                cache.reloadIslandBans(islandUuid);
                break;
            case "island_coops":
                cache.reloadIslandCoops(islandUuid);
                break;
            case "island_levels":
                cache.reloadIslandLevels(islandUuid);
                break;
            default:
                plugin.getLogger().warning("Unknown cache update type: " + type);
        }
    }
}
