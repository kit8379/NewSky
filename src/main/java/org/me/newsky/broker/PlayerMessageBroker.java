package org.me.newsky.broker;

import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.json.JSONObject;
import org.me.newsky.NewSky;
import org.me.newsky.redis.RedisHandler;
import org.me.newsky.util.ComponentUtils;
import redis.clients.jedis.JedisPubSub;

import java.util.UUID;

public class PlayerMessageBroker {

    private final NewSky plugin;
    private final RedisHandler redisHandler;

    private JedisPubSub subscriber;

    private final String serverID;
    private final String channelID;

    public PlayerMessageBroker(NewSky plugin, RedisHandler redisHandler, String serverID, String channelID) {
        this.plugin = plugin;
        this.redisHandler = redisHandler;
        this.serverID = serverID;
        this.channelID = channelID;
    }

    public void subscribe() {
        subscriber = new JedisPubSub() {
            @Override
            public void onMessage(String channel, String message) {
                plugin.debug("PlayerMessageBroker", "Received message on channel " + channel + ": " + message);
                try {
                    JSONObject json = new JSONObject(message);
                    String target = json.getString("server");
                    String uuid = json.getString("uuid");
                    String component = json.getString("component");

                    if (!serverID.equals(target)) {
                        plugin.debug("PlayerMessageBroker", "Message intended for server " + target + ", ignoring.");
                        return;
                    }

                    handlePlayerMessage(UUID.fromString(uuid), component);
                } catch (Exception e) {
                    plugin.severe("PlayerMessageBroker failed to process message: " + message, e);
                }
            }
        };

        redisHandler.subscribe(subscriber, channelID);
        plugin.debug("PlayerMessageBroker", "Subscribed to channel " + channelID);
    }

    public void unsubscribe() {
        if (subscriber != null) {
            subscriber.unsubscribe();
            plugin.debug("PlayerMessageBroker", "Unsubscribed from channel " + channelID);
        }
    }

    public void sendPlayerMessage(String playerServer, UUID playerUuid, Component component) {
        if (serverID.equals(playerServer)) {
            // Local: Bukkit API must be on main thread. Avoid scheduling if already on main.
            if (Bukkit.isPrimaryThread()) {
                try {
                    Player player = Bukkit.getPlayer(playerUuid);
                    if (player != null) {
                        player.sendMessage(component);
                        plugin.debug("PlayerMessageBroker", "Delivered message to player " + playerUuid);
                    }
                } catch (Exception e) {
                    plugin.severe("PlayerMessageBroker failed to deliver message to player " + playerUuid, e);
                }
            } else {
                Bukkit.getScheduler().runTask(plugin, () -> {
                    try {
                        Player player = Bukkit.getPlayer(playerUuid);
                        if (player != null) {
                            player.sendMessage(component);
                            plugin.debug("PlayerMessageBroker", "Delivered message to player " + playerUuid);
                        }
                    } catch (Exception e) {
                        plugin.severe("PlayerMessageBroker failed to deliver message to player " + playerUuid, e);
                    }
                });
            }
        } else {
            // Remote: publish to target server only.
            try {
                JSONObject json = new JSONObject();
                json.put("server", playerServer);
                json.put("uuid", playerUuid.toString());
                json.put("component", ComponentUtils.serialize(component));

                plugin.debug("PlayerMessageBroker", "Publishing message to player " + playerUuid + " on server " + playerServer);
                redisHandler.publish(channelID, json.toString());
            } catch (Exception e) {
                plugin.severe("PlayerMessageBroker failed to publish message to player " + playerUuid, e);
            }
        }

    }

    private void handlePlayerMessage(UUID playerUuid, String serializedComponent) {
        Bukkit.getScheduler().runTask(plugin, () -> {
            try {
                Player player = Bukkit.getPlayer(playerUuid);
                if (player != null) {
                    player.sendMessage(ComponentUtils.deserialize(serializedComponent));
                    plugin.debug("PlayerMessageBroker", "Delivered message to player " + playerUuid);
                }
            } catch (Exception e) {
                plugin.severe("PlayerMessageBroker failed to deliver message to player " + playerUuid, e);
            }
        });
    }
}
