package org.me.newsky.island;

import org.me.newsky.NewSky;
import org.me.newsky.cache.CacheHandler;
import org.me.newsky.redis.RedisBroker;

import java.util.UUID;

public class PreIslandHandler {

    private final NewSky plugin;
    private final CacheHandler cacheHandler;
    private final RedisBroker broker;
    private final PostIslandHandler postIslandHandler;
    private final String serverID;

    public PreIslandHandler(NewSky plugin, CacheHandler cacheHandler, RedisBroker broker, PostIslandHandler postIslandHandler, String serverID) {
        this.plugin = plugin;
        this.cacheHandler = cacheHandler;
        this.broker = broker;
        this.postIslandHandler = postIslandHandler;
        this.serverID = serverID;
    }

    public void createIsland(UUID islandUuid, UUID playerUuid, String spawnLocation) {
        handleIslandAction(null, () -> {
            postIslandHandler.createIsland(islandUuid, playerUuid, spawnLocation);
        }, "create:" + islandUuid + ":" + playerUuid + ":" + spawnLocation);
    }

    public void deleteIsland(UUID islandUuid) {
        handleIslandAction(getServerByIsland(islandUuid), () -> postIslandHandler.deleteIsland(islandUuid), "delete:" + islandUuid);
    }

    public void loadIsland(UUID islandUuid) {
        handleIslandAction(getServerByIsland(islandUuid), () -> postIslandHandler.loadIsland(islandUuid), "load:" + islandUuid);
    }

    public void unloadIsland(UUID islandUuid) {
        handleIslandAction(getServerByIsland(islandUuid), () -> postIslandHandler.unloadIsland(islandUuid), "unload:" + islandUuid);
    }

    public void teleportIsland(UUID islandUuid, UUID playerUuid, String teleportLocation) {
        handleIslandAction(getServerByIsland(islandUuid), () -> postIslandHandler.teleportIsland(islandUuid, playerUuid, teleportLocation), "teleport:" + islandUuid + ":" + playerUuid + ":" + teleportLocation);
    }

    public void lockIsland(UUID islandUuid) {
        handleIslandAction(getServerByIsland(islandUuid), () -> {
            postIslandHandler.lockIsland(islandUuid);
        }, "lock:" + islandUuid);
    }

    private void handleIslandAction(String islandActiveServer, Runnable localAction, String remoteAction) {
        String targetServer = islandActiveServer != null ? islandActiveServer : getRandomServer();
        if (targetServer.equals(serverID)) {
            localAction.run();
            plugin.debug("Run local action");
        } else {
            broker.publish(serverID + ":" + remoteAction);
            plugin.debug("Run remote action");
        }
    }

    private String getServerByIsland(UUID islandUuid) {
        return cacheHandler.getIslandLoadedServer(islandUuid);
    }

    private String getRandomServer() {
        return cacheHandler.getActiveServers().keySet().stream().findAny().orElse(null);
    }
}
