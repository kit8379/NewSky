package org.me.newsky.redis;

import org.me.newsky.NewSky;
import org.me.newsky.config.ConfigHandler;
import org.me.newsky.island.PostIslandHandler;
import redis.clients.jedis.JedisPubSub;

import java.util.UUID;

public class RedisBroker {

    private final NewSky plugin;
    private final ConfigHandler config;
    private final RedisHandler redisHandler;
    private final PostIslandHandler postIslandHandler;
    private final Subscriber subscriber;

    public RedisBroker(NewSky plugin, ConfigHandler config, RedisHandler redisHandler, PostIslandHandler postIslandHandler) {
        this.plugin = plugin;
        this.config = config;
        this.redisHandler = redisHandler;
        this.postIslandHandler = postIslandHandler;
        this.subscriber = new Subscriber();
    }

    public void publish(String message) {
        redisHandler.publish("newsky-channel", message);
    }

    public void subscribe() {
        redisHandler.subscribe(subscriber, "newsky-channel");
    }

    public void unsubscribe() {
        subscriber.unsubscribe();
    }

    private class Subscriber extends JedisPubSub {
        @Override
        public void onMessage(String channel, String message) {
            String[] parts = message.split(":");
            String targetServer = parts[0];
            String operation = parts[1];

            if (targetServer.equals(config.getServerName()) || targetServer.equals("all")) {
                switch (operation) {
                    case "create":
                        UUID islandUuidForCreate = UUID.fromString(parts[2]);
                        UUID playerUuidForCreate = UUID.fromString(parts[3]);
                        String spawnLocation = parts[4];
                        postIslandHandler.createIsland(islandUuidForCreate, playerUuidForCreate, spawnLocation);
                        break;
                    case "delete":
                        UUID islandUuidForDelete = UUID.fromString(parts[2]);
                        postIslandHandler.deleteIsland(islandUuidForDelete);
                        break;
                    case "load":
                        UUID islandUuidForLoad = UUID.fromString(parts[2]);
                        postIslandHandler.loadIsland(islandUuidForLoad);
                        break;
                    case "unload":
                        UUID islandUuidForUnload = UUID.fromString(parts[2]);
                        postIslandHandler.unloadIsland(islandUuidForUnload);
                        break;
                    case "teleport":
                        UUID islandUuidForTeleport = UUID.fromString(parts[2]);
                        UUID playerUuidForTeleport = UUID.fromString(parts[3]);
                        String teleportLocation = parts[4];
                        postIslandHandler.teleportIsland(islandUuidForTeleport, playerUuidForTeleport, teleportLocation);
                        break;
                    case "lock":
                        UUID islandUuidForLock = UUID.fromString(parts[2]);
                        postIslandHandler.lockIsland(islandUuidForLock);
                        break;
                    default:
                        plugin.info("Unknown operation: " + operation);
                }
            }
        }
    }
}
