package org.me.newsky.world;

import com.infernalsuite.asp.api.AdvancedSlimePaperAPI;
import com.infernalsuite.asp.api.loaders.SlimeLoader;
import com.infernalsuite.asp.api.world.SlimeWorld;
import com.infernalsuite.asp.api.world.SlimeWorldInstance;
import com.infernalsuite.asp.api.world.properties.SlimeProperties;
import com.infernalsuite.asp.api.world.properties.SlimePropertyMap;
import com.infernalsuite.asp.loaders.mysql.MysqlLoader;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.me.newsky.NewSky;
import org.me.newsky.config.ConfigHandler;

import java.io.File;
import java.sql.SQLException;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.function.BooleanSupplier;
import java.util.function.Predicate;

public class WorldHandler {

    private final NewSky plugin;
    private final ConfigHandler config;
    private final SlimeLoader slimeLoader;
    private final SlimePropertyMap properties;
    private final AdvancedSlimePaperAPI asp = AdvancedSlimePaperAPI.instance();

    public WorldHandler(NewSky plugin, ConfigHandler config) {
        this.plugin = plugin;
        this.config = config;

        this.slimeLoader = createSlimeLoader();
        this.properties = createWorldProperties();
        plugin.debug("WorldHandler", "Default slime world properties configured.");
    }

    private SlimeLoader createSlimeLoader() {
        String jdbcUrl = "jdbc:mysql://{host}:{port}/{database}?useSSL={usessl}&"
                + config.getMySQLProperties();

        try {
            SlimeLoader loader = new MysqlLoader(jdbcUrl, config.getMySQLHost(),
                    config.getMySQLPort(), config.getMySQLDB(), config.getMySQLUseSSL(),
                    config.getMySQLUsername(), config.getMySQLPassword());
            plugin.debug("WorldHandler", "Initialized MySQL slimeLoader successfully.");
            return loader;
        } catch (SQLException error) {
            plugin.severe("Failed to initialize MySQL slimeLoader", error);
            throw new RuntimeException(error);
        }
    }

    private SlimePropertyMap createWorldProperties() {
        SlimePropertyMap result = new SlimePropertyMap();
        result.setValue(SlimeProperties.DIFFICULTY, "normal");
        result.setValue(SlimeProperties.ENVIRONMENT, "normal");
        result.setValue(SlimeProperties.SPAWN_X, config.getIslandSpawnX());
        result.setValue(SlimeProperties.SPAWN_Y, config.getIslandSpawnY());
        result.setValue(SlimeProperties.SPAWN_Z, config.getIslandSpawnZ());
        result.setValue(SlimeProperties.SPAWN_YAW, config.getIslandSpawnYaw());
        return result;
    }

    public CompletableFuture<Void> createWorld(String worldName) {
        plugin.debug("WorldHandler", "Creating world: " + worldName);

        File templateWorld = plugin.getDataFolder().toPath()
                .resolve("template/" + config.getTemplateWorldName())
                .toFile();
        plugin.debug("WorldHandler", "Template world path resolved: " + templateWorld.getAbsolutePath());

        if (!templateWorld.exists()) {
            plugin.severe("Template world folder not found: " + templateWorld.getAbsolutePath());
            return CompletableFuture.failedFuture(new IllegalStateException("Template folder not found"));
        }

        try {
            SlimeWorld newWorld = asp.readVanillaWorld(templateWorld, worldName, slimeLoader);
            plugin.debug("WorldHandler", "Vanilla world read successfully for: " + worldName);
            asp.saveWorld(newWorld);
            plugin.debug("WorldHandler", "World saved to slime loader: " + worldName);
            SlimeWorld loadedWorld = readWorld(worldName);
            return loadWorldToBukkit(loadedWorld).thenRunAsync(() -> {
                plugin.debug("WorldHandler", "World loaded into Bukkit: " + worldName);
            }, plugin.getBukkitAsyncExecutor());
        } catch (Exception e) {
            plugin.severe("Failed to create slime world: " + worldName, e);
            return CompletableFuture.failedFuture(e);
        }
    }

    public CompletableFuture<Void> loadWorld(String worldName) {
        plugin.debug("WorldHandler", "Loading world: " + worldName);
        try {
            SlimeWorld world = readWorld(worldName);
            plugin.debug("WorldHandler", "World read from slime loader: " + worldName);
            return loadWorldToBukkit(world).thenRunAsync(() -> {
                plugin.debug("WorldHandler", "World loaded into Bukkit: " + worldName);
            }, plugin.getBukkitAsyncExecutor());
        } catch (Exception e) {
            plugin.severe("Failed to load world: " + worldName, e);
            return CompletableFuture.failedFuture(e);
        }
    }

    private SlimeWorld readWorld(String worldName) throws Exception {
        return asp.readWorld(slimeLoader, worldName, false, properties);
    }

    public CompletableFuture<Void> resumeProvisioningWorld(String worldName) {
        try {
            return slimeLoader.worldExists(worldName) ? loadWorld(worldName) : createWorld(worldName);
        } catch (Exception e) {
            plugin.severe("Failed to inspect provisioning world: " + worldName, e);
            return CompletableFuture.failedFuture(e);
        }
    }

    public CompletableFuture<Void> unloadWorld(String worldName) {
        plugin.debug("WorldHandler", "Unloading world: " + worldName);

        try {
            SlimeWorld world = asp.getLoadedWorld(worldName);

            if (world != null) {
                asp.saveWorld(world);
                plugin.debug("WorldHandler", "World saved before unload: " + worldName);
            } else {
                plugin.debug("WorldHandler", "ASP loaded world not found for unload, skipping save: " + worldName);
            }

            return unloadWorldFromBukkit(worldName).thenRunAsync(() -> {
                plugin.debug("WorldHandler", "World successfully unloaded: " + worldName);
            }, plugin.getBukkitAsyncExecutor());
        } catch (Exception e) {
            plugin.severe("Failed to unload slime world: " + worldName, e);
            return CompletableFuture.failedFuture(e);
        }
    }

    public CompletableFuture<Boolean> unloadWorldIfIdle(String worldName, BooleanSupplier stillIdle) {
        plugin.debug("WorldHandler", "Conditionally unloading idle world: " + worldName);

        try {
            SlimeWorld world = asp.getLoadedWorld(worldName);
            if (world != null) {
                asp.saveWorld(world);
            }

            return CompletableFuture.supplyAsync(() -> {
                World bukkitWorld = Bukkit.getWorld(worldName);
                if (bukkitWorld == null) {
                    return true;
                }
                if (!stillIdle.getAsBoolean() || !bukkitWorld.getPlayers().isEmpty()) {
                    return false;
                }
                boolean unloaded = Bukkit.unloadWorld(bukkitWorld, false);
                if (!unloaded) {
                    throw new IllegalStateException("Failed to unload world from Bukkit: " + worldName);
                }
                return true;
            }, Bukkit.getScheduler().getMainThreadExecutor(plugin));
        } catch (Exception e) {
            plugin.severe("Failed to conditionally unload slime world: " + worldName, e);
            return CompletableFuture.failedFuture(e);
        }
    }

    public CompletableFuture<Void> deleteWorld(String worldName) {
        plugin.debug("WorldHandler", "Deleting world: " + worldName);
        return unloadWorldFromBukkit(worldName).thenComposeAsync(v -> {
            try {
                slimeLoader.deleteWorld(worldName);
                plugin.debug("WorldHandler", "Deleted slime world: " + worldName);
                return CompletableFuture.completedFuture(null);
            } catch (Exception e) {
                plugin.severe("Failed to delete slime world: " + worldName, e);
                return CompletableFuture.failedFuture(e);
            }
        }, plugin.getBukkitAsyncExecutor());
    }

    private CompletableFuture<Void> loadWorldToBukkit(SlimeWorld world) {
        return CompletableFuture.runAsync(() -> {
            asp.loadWorld(world, true);
            plugin.debug("WorldHandler", "World loaded into Bukkit: " + world.getName());
        }, Bukkit.getScheduler().getMainThreadExecutor(plugin));
    }

    public CompletableFuture<Void> unloadWorldFromBukkit(String worldName) {
        return CompletableFuture.runAsync(() -> {
            World world = Bukkit.getWorld(worldName);

            if (world == null) {
                plugin.debug("WorldHandler", "World already absent from Bukkit: " + worldName);
                return;
            }

            removePlayersFromWorld(world);

            if (Bukkit.unloadWorld(world, false)) {
                plugin.debug("WorldHandler", "World unloaded successfully from Bukkit: " + worldName);
            } else {
                plugin.severe("Failed to unload world from Bukkit: " + worldName);
                throw new IllegalStateException("Failed to unload world from Bukkit: " + worldName);
            }
        }, Bukkit.getScheduler().getMainThreadExecutor(plugin));
    }

    public void removePlayersFromWorld(World world) {
        plugin.debug("WorldHandler", "Removing players from world: " + world.getName());
        for (Player player : world.getPlayers()) {
            plugin.debug("WorldHandler", "Teleporting player: " + player.getName());
            player.teleport(Bukkit.getWorlds().getFirst().getSpawnLocation());
            plugin.getApi().lobby(player.getUniqueId());
        }
    }

    public CompletableFuture<Void> removePlayerFromWorld(String worldName, UUID playerUuid) {
        return CompletableFuture.runAsync(() -> {
            World world = Bukkit.getWorld(worldName);
            if (world == null) {
                return;
            }

            Player player = Bukkit.getPlayer(playerUuid);
            if (player != null && player.getWorld().equals(world)) {
                player.teleportAsync(Bukkit.getWorlds().getFirst().getSpawnLocation());
                plugin.getApi().lobby(playerUuid);
                plugin.debug("WorldHandler", "Removed player " + playerUuid
                        + " from world: " + worldName);
            }
        }, Bukkit.getScheduler().getMainThreadExecutor(plugin));
    }

    public CompletableFuture<Void> removePlayersFromWorld(String worldName, Predicate<Player> shouldRemove) {
        return CompletableFuture.runAsync(() -> {
            World world = Bukkit.getWorld(worldName);
            if (world == null) {
                return;
            }

            for (Player player : world.getPlayers()) {
                if (shouldRemove.test(player)) {
                    player.teleportAsync(Bukkit.getWorlds().getFirst().getSpawnLocation());
                    plugin.getApi().lobby(player.getUniqueId());
                    plugin.debug("WorldHandler", "Removed player " + player.getUniqueId()
                            + " from world: " + worldName);
                }
            }
        }, Bukkit.getScheduler().getMainThreadExecutor(plugin));
    }

    public void unloadAllWorldsOnShutdown(boolean saveWorlds) {
        plugin.debug("WorldHandler", "Unloading all worlds on shutdown...");
        List<SlimeWorldInstance> loadedWorlds = asp.getLoadedWorlds();
        for (SlimeWorldInstance worldInstance : loadedWorlds) {
            try {
                if (saveWorlds) {
                    asp.saveWorld(worldInstance);
                } else {
                    plugin.warning("Skipping save for fenced world " + worldInstance.getName()
                            + " because this JVM no longer owns its cluster lease");
                }
                for (Player player : worldInstance.getBukkitWorld().getPlayers()) {
                    plugin.debug("WorldHandler", "Teleporting player: " + player.getName());
                    player.teleport(Bukkit.getWorlds().getFirst().getSpawnLocation());
                }
                if (Bukkit.unloadWorld(worldInstance.getName(), false)) {
                    plugin.debug("WorldHandler", "World unloaded successfully from Bukkit: "
                            + worldInstance.getName());
                } else {
                    plugin.severe("Failed to unload world from Bukkit: " + worldInstance.getName());
                }
            } catch (Exception e) {
                plugin.severe("Failed to unload world on shutdown: " + worldInstance.getName(), e);
            }
        }
    }
}
