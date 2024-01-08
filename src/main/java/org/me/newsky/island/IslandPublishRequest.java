package org.me.newsky.island;

import org.me.newsky.redis.RedisHandler;

public class IslandPublishRequest {
    private final RedisHandler redisHandler;

    public IslandPublishRequest(RedisHandler redisHandler) {
        this.redisHandler = redisHandler;
    }

    public void publishCreateIslandRequest(String serverID, String playerUuid) {
        // Construct the message in a specific format
        String message = "createIsland:" + serverID + ":" + playerUuid;
        redisHandler.publish("island_requests", message);
    }
}