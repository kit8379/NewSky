package org.me.newsky.broker;

import org.json.JSONObject;
import org.me.newsky.NewSky;
import org.me.newsky.cache.Cache;
import org.me.newsky.redis.RedisHandler;
import redis.clients.jedis.JedisPubSub;

import java.util.UUID;

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
                plugin.debug("CacheBroker", "Received message on channel " + channel + ": " + message);
                try {
                    JSONObject json = new JSONObject(message);
                    String source = json.getString("source");
                    String type = json.getString("type");
                    String uuid = json.getString("uuid");

                    if (serverID.equals(source)) {
                        plugin.debug("CacheBroker", "Ignoring cache update message because it is from the same server: " + source);
                        return;
                    }

                    plugin.debug("CacheBroker", "Handling update type " + type + " from server " + source);
                    handleUpdate(type, UUID.fromString(uuid));
                } catch (Exception e) {
                    plugin.severe("CacheBroker failed to process message: " + message, e);
                }
            }
        };

        redisHandler.subscribe(subscriber, channelID);
        plugin.debug("CacheBroker", "Subscribed to channel " + channelID);
    }

    public void unsubscribe() {
        if (subscriber != null) {
            subscriber.unsubscribe();
            plugin.debug("CacheBroker", "Unsubscribed from channel " + channelID);
        }
    }

    public void publishUpdate(String type, UUID uuid) {
        try {
            JSONObject json = new JSONObject();
            json.put("source", serverID);
            json.put("type", type);
            json.put("uuid", uuid.toString());

            plugin.debug("CacheBroker", "Publishing update type " + type);
            redisHandler.publish(channelID, json.toString());
        } catch (Exception e) {
            plugin.severe("CacheBroker failed to publish update with type " + type, e);
        }
    }

    private void handleUpdate(String type, UUID uuid) {
        try {
            switch (type) {
                case "island_data":
                    cache.reloadIslandData(uuid);
                    break;
                case "island_players":
                    cache.reloadIslandPlayers(uuid);
                    break;
                case "island_homes":
                    cache.reloadIslandHomes(uuid);
                    break;
                case "island_warps":
                    cache.reloadIslandWarps(uuid);
                    break;
                case "island_bans":
                    cache.reloadIslandBans(uuid);
                    break;
                case "island_coops":
                    cache.reloadIslandCoops(uuid);
                    break;
                case "island_levels":
                    cache.reloadIslandLevels(uuid);
                    break;
                case "island_upgrades":
                    cache.reloadIslandUpgrades(uuid);
                    break;
                case "player_uuid":
                    cache.reloadPlayerUuid(uuid);
                    break;
                default:
                    plugin.getLogger().warning("Unknown cache update type: " + type);
            }
        } catch (Exception e) {
            plugin.severe("CacheBroker failed to handle update type " + type, e);
        }
    }
}