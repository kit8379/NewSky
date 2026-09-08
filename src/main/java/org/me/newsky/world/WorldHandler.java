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
import java.util.function.Predicate;

public class WorldHandler {

    private static final String TEMPLATE_WORLD_NAME = "newsky-template";

    public final NewSky plugin;
    public final ConfigHandler config;
    private final SlimeLoader slimeLoader;
    private final SlimePropertyMap properties;
    private final SlimeWorld templateWorld;
    private final AdvancedSlimePaperAPI asp = AdvancedSlimePaperAPI.instance();

    public WorldHandler(NewSky plugin, ConfigHandler config) {
        this.plugin = plugin;
        this.config = config;

        try {
            this.slimeLoader = new MysqlLoader("jdbc:mysql://{host}:{port}/{database}?useSSL={usessl}&" + config.getMySQLProperties(), config.getMySQLHost(), config.getMySQLPort(), config.getMySQLDB(), config.getMySQLUseSSL(), config.getMySQLUsername(), config.getMySQLPassword());
            plugin.debug("WorldHandler", "Initialized MySQL slimeLoader successfully.");
        } catch (SQLException e) {
            plugin.severe("Failed to initialize MySQL slimeLoader", e);
            throw new RuntimeException(e);
        }

        properties = new SlimePropertyMap();
        properties.setValue(SlimeProperties.DIFFICULTY, "normal");
        properties.setValue(SlimeProperties.ENVIRONMENT, "normal");
        properties.setValue(SlimeProperties.SPAWN_X, config.getIslandSpawnX());
        properties.setValue(SlimeProperties.SPAWN_Y, config.getIslandSpawnY());
        properties.setValue(SlimeProperties.SPAWN_Z, config.getIslandSpawnZ());
        properties.setValue(SlimeProperties.SPAWN_YAW, config.getIslandSpawnYaw());
        plugin.debug("WorldHandler", "Default slime world properties configured.");

        this.templateWorld = config.isLobbyOnly() ? null : readTemplateWorld();
    }

    /**
     * Parses the vanilla template folder once; every island world is a clone of the result.
     * The template is never stored (null loader) and never mutated: clone() deep-copies it.
     */
    private SlimeWorld readTemplateWorld() {
        File templateDir = plugin.getDataFolder().toPath().resolve("template/" + config.getTemplateWorldName()).toFile();
        plugin.debug("WorldHandler", "Template world path resolved: " + templateDir.getAbsolutePath());

        if (!templateDir.exists()) {
            throw new IllegalStateException("Template world folder not found: " + templateDir.getAbsolutePath());
        }

        try {
            SlimeWorld template = asp.readVanillaWorld(templateDir, TEMPLATE_WORLD_NAME, null);
            plugin.debug("WorldHandler", "Template world cached in memory: " + templateDir.getAbsolutePath());
            return template;
        } catch (Exception e) {
            throw new IllegalStateException("Failed to read template world: " + templateDir.getAbsolutePath(), e);
        }
    }

    public CompletableFuture<Void> createWorld(String worldName) {
        plugin.debug("WorldHandler", "Creating world: " + worldName);

        try {
            if (templateWorld == null) {
                throw new IllegalStateException("Lobby-only server has no template world to create islands from");
            }

            SlimeWorld newWorld = templateWorld.clone(worldName, slimeLoader);
            newWorld.getPropertyMap().merge(properties);
            plugin.debug("WorldHandler", "World cloned from template and saved to slime loader: " + worldName);
            return loadWorldToBukkit(newWorld).thenRunAsync(() -> {
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
            SlimeWorld world = asp.readWorld(slimeLoader, worldName, false, properties);
            plugin.debug("WorldHandler", "World read from slime loader: " + worldName);
            return loadWorldToBukkit(world).thenRunAsync(() -> {
                plugin.debug("WorldHandler", "World loaded into Bukkit: " + worldName);
            }, plugin.getBukkitAsyncExecutor());
        } catch (Exception e) {
            plugin.severe("Failed to load world: " + worldName, e);
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
                plugin.debug("WorldHandler", "World already absent from Bukkit, treating as unloaded: " + worldName);
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

    public CompletableFuture<Boolean> removePlayerFromWorld(String worldName, UUID playerUuid) {
        return CompletableFuture.supplyAsync(() -> {
            World world = Bukkit.getWorld(worldName);
            if (world == null) {
                return false;
            }

            Player player = Bukkit.getPlayer(playerUuid);
            if (player != null && player.getWorld().equals(world)) {
                player.teleportAsync(Bukkit.getWorlds().getFirst().getSpawnLocation());
                plugin.getApi().lobby(playerUuid);
                plugin.debug("WorldHandler", "Removed player " + playerUuid + " from world: " + worldName);
                return true;
            }

            return false;
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
                    plugin.debug("WorldHandler", "Removed player " + player.getUniqueId() + " from world: " + worldName);
                }
            }
        }, Bukkit.getScheduler().getMainThreadExecutor(plugin));
    }

    public void unloadAllWorldsOnShutdown() {
        plugin.debug("WorldHandler", "Unloading all worlds on shutdown...");
        List<SlimeWorldInstance> loadedWorlds = asp.getLoadedWorlds();
        for (SlimeWorldInstance worldInstance : loadedWorlds) {
            try {
                asp.saveWorld(worldInstance);
                for (Player player : worldInstance.getBukkitWorld().getPlayers()) {
                    plugin.debug("WorldHandler", "Teleporting player: " + player.getName());
                    player.teleport(Bukkit.getWorlds().getFirst().getSpawnLocation());
                }
                if (Bukkit.unloadWorld(worldInstance.getName(), false)) {
                    plugin.debug("WorldHandler", "World unloaded successfully from Bukkit: " + worldInstance.getName());
                } else {
                    plugin.severe("Failed to unload world from Bukkit: " + worldInstance.getName());
                }
            } catch (Exception e) {
                plugin.severe("Failed to unload world on shutdown: " + worldInstance.getName(), e);
            }
        }
    }
}
