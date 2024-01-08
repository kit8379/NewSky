package org.me.newsky.island;

import org.me.newsky.redis.RedisHandler;
import redis.clients.jedis.JedisPubSub;

import java.util.UUID;

public class IslandSubscribeRequest extends JedisPubSub {

    private final IslandHandler islandHandler;
    private final String serverName;

    public IslandSubscribeRequest(IslandHandler islandHandler, RedisHandler redisHandler, String serverName) {
        this.islandHandler = islandHandler;
        this.serverName = serverName;
        redisHandler.subscribe(this, "island_requests");
    }

    @Override
    public void onMessage(String channel, String message) {
        // Handle received messages
        if (message.startsWith("createIsland:")) {
            String[] parts = message.split(":");
            if (parts.length == 3) {
                String serverID = parts[1];
                String playerUuid= parts[2];
                // Check if the message is for this server
                if (serverName.equals(serverID)) {
                    islandHandler.postCreateIsland(UUID.fromString(playerUuid));
                }
            }
        }
    }

    // Additional methods for other types of messages
}
