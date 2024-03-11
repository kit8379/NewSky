package org.me.newsky.island.pubsub;

import org.me.newsky.NewSky;
import org.me.newsky.island.post.PostIslandHandler;
import org.me.newsky.redis.RedisHandler;
import redis.clients.jedis.JedisPubSub;

import java.util.concurrent.CompletableFuture;

public class IslandSubscribeRequest {

    private final NewSky plugin;
    private final RedisHandler redisHandler;
    private final PostIslandHandler postIslandHandler;
    private final String serverID;
    private JedisPubSub requestSubscriber;

    public IslandSubscribeRequest(NewSky plugin, RedisHandler redisHandler, PostIslandHandler postIslandHandler, String serverID) {
        this.plugin = plugin;
        this.redisHandler = redisHandler;
        this.postIslandHandler = postIslandHandler;
        this.serverID = serverID;
    }

    public void subscribeToRequests() {
        requestSubscriber = new JedisPubSub() {
            @Override
            public void onMessage(String channel, String message) {
                String[] parts = message.split(":");
                String requestID = parts[0];
                String sourceServer = parts[1];
                String targetServer = parts[2];
                String operation = parts[3];

                if (targetServer.equals(serverID) || targetServer.equals("all")) {
                    plugin.debug("Received request: " + requestID + " from server: " + sourceServer + " for operation: " + operation);
                    processRequest(parts).thenAccept((String responseData) -> {
                        redisHandler.publish("newsky-response-channel-" + requestID, serverID + ":" + responseData);
                        plugin.debug("Sent response back to server: " + sourceServer + " for request: " + requestID + " for operation: " + operation + " with data: " + responseData);
                    });
                }
            }
        };

        redisHandler.subscribe(requestSubscriber, "newsky-request-channel");
    }

    public void unsubscribeFromRequests() {
        if (requestSubscriber != null) {
            requestSubscriber.unsubscribe();
        }
    }

    private CompletableFuture<String> processRequest(String[] parts) {
        String operation = parts[3];

        switch (operation) {
            case "updateWorldList":
                return postIslandHandler.updateWorldList().thenApply(updatedList -> {
                    return updatedList;
                });

            case "createIsland":
                String worldNameForCreate = parts[4];
                return postIslandHandler.createWorld(worldNameForCreate).thenApply(v -> {
                    return "Created";
                });

            case "loadIsland":
                String worldNameForLoad = parts[4];
                return postIslandHandler.loadWorld(worldNameForLoad).thenApply(v -> {
                    return "Loaded";
                });

            case "unloadIsland":
                String worldNameForUnload = parts[4];
                return postIslandHandler.unloadWorld(worldNameForUnload).thenApply(v -> {
                    return "Unloaded";
                });

            case "deleteIsland":
                String worldNameForDelete = parts[4];
                return postIslandHandler.deleteWorld(worldNameForDelete).thenApply(v -> {
                    return "Deleted";
                });

            case "teleportToIsland":
                String worldNameForTeleport = parts[4];
                String playerName = parts[5];
                String locationString = parts[6];
                return postIslandHandler.teleportToWorld(worldNameForTeleport, playerName, locationString).thenApply(v -> {
                    return "Teleported";
                });

            default:
                return CompletableFuture.failedFuture(new IllegalStateException("Unknown operation: " + operation));
        }
    }
}