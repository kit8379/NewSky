package org.me.newsky.island.distributor;

import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.me.newsky.NewSky;
import org.me.newsky.broker.IslandBroker;
import org.me.newsky.cache.RedisCache;
import org.me.newsky.exceptions.IslandAlreadyLoadedException;
import org.me.newsky.exceptions.IslandNotLoadedException;
import org.me.newsky.exceptions.NoActiveServerException;
import org.me.newsky.island.operation.IslandOperation;
import org.me.newsky.routing.ServerSelector;
import org.me.newsky.util.ComponentUtils;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Level;

public class IslandDistributor {

    private final NewSky plugin;
    private final RedisCache redisCache;
    private final IslandOperation islandOperation;
    private final ServerSelector serverSelector;
    private final String serverID;

    private IslandBroker islandBroker;

    public IslandDistributor(NewSky plugin, RedisCache redisCache, IslandOperation islandOperation, ServerSelector serverSelector, String serverID) {
        this.plugin = plugin;
        this.redisCache = redisCache;
        this.islandOperation = islandOperation;
        this.serverSelector = serverSelector;
        this.serverID = serverID;
    }

    public void setIslandBroker(IslandBroker islandBroker) {
        this.islandBroker = islandBroker;
    }

    public CompletableFuture<Void> createIsland(UUID islandUuid, UUID ownerUuid, String spawnLocation) {
        String targetServer = selectServer();

        if (targetServer == null) {
            return CompletableFuture.failedFuture(new NoActiveServerException());
        }

        if (targetServer.equals(serverID)) {
            return islandOperation.createIsland(islandUuid, ownerUuid, spawnLocation);
        } else {
            return islandBroker.sendRequest(targetServer, "create", islandUuid.toString(), ownerUuid.toString(), spawnLocation);
        }
    }

    public CompletableFuture<Void> deleteIsland(UUID islandUuid) {
        String islandServer = getServerByIsland(islandUuid);

        if (islandServer == null) {
            return islandOperation.deleteIsland(islandUuid);
        } else {
            if (islandServer.equals(serverID)) {
                return islandOperation.deleteIsland(islandUuid);
            } else {
                return islandBroker.sendRequest(islandServer, "delete", islandUuid.toString());
            }
        }
    }

    public CompletableFuture<Void> loadIsland(UUID islandUuid) {
        String islandServer = getServerByIsland(islandUuid);

        if (islandServer == null) {
            String targetServer = selectServer();
            if (targetServer == null) {
                return CompletableFuture.failedFuture(new NoActiveServerException());
            }

            if (targetServer.equals(serverID)) {
                return islandOperation.loadIsland(islandUuid);
            } else {
                return islandBroker.sendRequest(targetServer, "load", islandUuid.toString());
            }
        } else {
            return CompletableFuture.failedFuture(new IslandAlreadyLoadedException());
        }
    }

    public CompletableFuture<Void> unloadIsland(UUID islandUuid) {
        String islandServer = getServerByIsland(islandUuid);

        if (islandServer == null) {
            return CompletableFuture.failedFuture(new IslandNotLoadedException());
        } else {
            if (islandServer.equals(serverID)) {
                return islandOperation.unloadIsland(islandUuid);
            } else {
                return islandBroker.sendRequest(islandServer, "unload", islandUuid.toString());
            }
        }
    }

    public void lockIsland(UUID islandUuid) {
        String islandServer = getServerByIsland(islandUuid);

        if (islandServer == null) {
            CompletableFuture.completedFuture(null);
        } else {
            if (islandServer.equals(serverID)) {
                islandOperation.lockIsland(islandUuid);
            } else {
                islandBroker.sendRequest(islandServer, "lock", islandUuid.toString());
            }
        }
    }

    public CompletableFuture<Void> teleportIsland(UUID islandUuid, UUID playerUuid, String teleportLocation) {
        String islandServer = getServerByIsland(islandUuid);

        if (islandServer == null) {
            String targetServer = selectServer();
            if (targetServer == null) {
                return CompletableFuture.failedFuture(new NoActiveServerException());
            }

            if (targetServer.equals(serverID)) {
                return islandOperation.teleportIsland(islandUuid, playerUuid, teleportLocation);
            } else {
                return islandBroker.sendRequest(targetServer, "teleport", islandUuid.toString(), playerUuid.toString(), teleportLocation).thenRun(() -> connectToServer(playerUuid, targetServer));
            }
        } else {
            if (islandServer.equals(serverID)) {
                return islandOperation.teleportIsland(islandUuid, playerUuid, teleportLocation);
            } else {
                return islandBroker.sendRequest(islandServer, "teleport", islandUuid.toString(), playerUuid.toString(), teleportLocation).thenRun(() -> connectToServer(playerUuid, islandServer));
            }
        }
    }

    public void expelPlayer(UUID islandUuid, UUID playerUuid) {
        String islandServer = getServerByIsland(islandUuid);

        if (islandServer == null) {
            CompletableFuture.completedFuture(null);
        } else {
            if (islandServer.equals(serverID)) {
                islandOperation.expelPlayer(islandUuid, playerUuid);
            } else {
                islandBroker.sendRequest(islandServer, "expel", islandUuid.toString(), playerUuid.toString());
            }
        }
    }

    public void sendPlayerMessage(UUID playerUuid, Component message) {
        String playerName = Bukkit.getOfflinePlayer(playerUuid).getName();

        String playerServer = getServerByPlayer(playerName);

        if (playerServer == null) {
        } else {
            if (playerServer.equals(serverID)) {
                islandOperation.sendPlayerMessage(playerUuid, message);
            } else {
                islandBroker.sendRequest(playerServer, "message", playerUuid.toString(), ComponentUtils.serialize(message));
            }
        }
    }

    private String selectServer() {
        Map<String, String> activeServers = redisCache.getActiveGameServers();
        return serverSelector.selectServer(activeServers);
    }

    private String getServerByIsland(UUID islandUuid) {
        return redisCache.getIslandLoadedServer(islandUuid).orElse(null);
    }

    public String getServerByPlayer(String playerName) {
        return redisCache.getOnlinePlayerServer(playerName).orElse(null);
    }

    private void connectToServer(UUID playerUuid, String serverName) {
        Player player = Bukkit.getPlayer(playerUuid);
        if (player != null) {
            try (ByteArrayOutputStream byteArray = new ByteArrayOutputStream(); DataOutputStream out = new DataOutputStream(byteArray)) {
                out.writeUTF("Connect");
                out.writeUTF(serverName);
                player.sendPluginMessage(plugin, "BungeeCord", byteArray.toByteArray());
            } catch (IOException e) {
                plugin.getLogger().log(Level.SEVERE, "Failed to send plugin message to player " + player.getName(), e);
            }
        }
    }
}
