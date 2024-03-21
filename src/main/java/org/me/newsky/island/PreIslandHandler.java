package org.me.newsky.island;

import org.me.newsky.NewSky;
import org.me.newsky.redis.RedisBroker;

import java.util.UUID;

public class PreIslandHandler {

    private final NewSky plugin;
    private final RedisBroker broker;
    private final PostIslandHandler postIslandHandler;
    private final String serverID;

    public PreIslandHandler(NewSky plugin, RedisBroker broker, PostIslandHandler postIslandHandler, String serverID) {
        this.plugin = plugin;
        this.broker = broker;
        this.postIslandHandler = postIslandHandler;
        this.serverID = serverID;
    }

    public void createIsland(UUID islandUuid, UUID playerUuid, String spawnLocation) {
        handleIslandAction(islandUuid, serverID, () -> postIslandHandler.createIsland(islandUuid, playerUuid, spawnLocation), "create:" + islandUuid + ":" + playerUuid + ":" + spawnLocation, "Create island");
    }

    public void deleteIsland(UUID islandUuid) {
        handleIslandAction(islandUuid, getServerByIsland(islandUuid), () -> postIslandHandler.deleteIsland(islandUuid), "delete:" + islandUuid, "Delete island");
    }

    public void loadIsland(UUID islandUuid) {
        handleIslandAction(islandUuid, null, () -> postIslandHandler.loadIsland(islandUuid), "load:" + islandUuid, "Load island");
    }

    public void unloadIsland(UUID islandUuid) {
        handleIslandAction(islandUuid, getServerByIsland(islandUuid), () -> postIslandHandler.unloadIsland(islandUuid), "unload:" + islandUuid, "Unload island");
    }

    public void teleportIsland(UUID islandUuid, UUID playerUuid, String teleportLocation) {
        handleIslandAction(islandUuid, getServerByIsland(islandUuid), () -> postIslandHandler.teleportIsland(islandUuid, playerUuid, teleportLocation), "teleport:" + islandUuid + ":" + playerUuid + ":" + teleportLocation, "Teleport island");
    }

    public void lockIsland(UUID islandUuid) {
        handleIslandAction(islandUuid, getServerByIsland(islandUuid), () -> postIslandHandler.lockIsland(islandUuid), "lock:" + islandUuid, "Lock island");
    }

    private void handleIslandAction(UUID islandUuid, String islandActiveServer, Runnable localAction, String remoteAction, String actionDescription) {
        String targetServer = islandActiveServer != null ? islandActiveServer : getRandomServer();
        if (targetServer.equals(serverID)) {
            localAction.run();
            plugin.debug(actionDescription + " " + islandUuid + " in the current server");
        } else {
            broker.publish(serverID + ":" + remoteAction);
            plugin.debug("Published " + actionDescription.toLowerCase() + " message to " + targetServer);
        }
    }
}
