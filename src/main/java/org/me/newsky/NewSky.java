package org.me.newsky;

import net.milkbowl.vault.economy.Economy;
import org.bukkit.Bukkit;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.java.JavaPlugin;
import org.json.JSONObject;
import org.me.newsky.api.NewSkyAPI;
import org.me.newsky.cluster.*;
import org.me.newsky.command.AsyncTabCompleteListener;
import org.me.newsky.command.IslandAdminCommand;
import org.me.newsky.command.IslandPlayerCommand;
import org.me.newsky.config.ConfigHandler;
import org.me.newsky.database.DatabaseHandler;
import org.me.newsky.island.*;
import org.me.newsky.listener.*;
import org.me.newsky.message.PlayerMessageHandler;
import org.me.newsky.messaging.CrossServerMessenger;
import org.me.newsky.model.Actor;
import org.me.newsky.network.IslandDistributor;
import org.me.newsky.network.IslandOperator;
import org.me.newsky.redis.RedisHandler;
import org.me.newsky.routing.MSPTServerSelector;
import org.me.newsky.routing.RandomServerSelector;
import org.me.newsky.routing.RoundRobinServerSelector;
import org.me.newsky.routing.ServerSelector;
import org.me.newsky.scheduler.HeartbeatScheduler;
import org.me.newsky.scheduler.IslandUnloadScheduler;
import org.me.newsky.scheduler.LevelUpdateScheduler;
import org.me.newsky.scheduler.MSPTUpdateScheduler;
import org.me.newsky.teleport.TeleportHandler;
import org.me.newsky.thread.BukkitAsyncExecutor;
import org.me.newsky.uuid.UuidHandler;
import org.me.newsky.world.WorldActivityHandler;
import org.me.newsky.world.WorldHandler;
import org.me.newsky.snapshot.IslandSnapshot;

import java.lang.reflect.Constructor;
import java.util.Collections;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Level;

public class NewSky extends JavaPlugin {

    private ConfigHandler config;
    private WorldHandler worldHandler;
    private RedisHandler redisHandler;
    private DatabaseHandler databaseHandler;
    private HeartbeatScheduler heartBeatScheduler;
    private OnlinePlayerRegistry onlinePlayerRegistry;
    private IslandUnloadScheduler islandUnloadScheduler;
    private LevelUpdateScheduler levelupdateSchedulerIsland;
    private MSPTUpdateScheduler msptUpdateScheduler;
    private CrossServerMessenger crossServerMessenger;
    private IslandOperator islandOperator;
    private LevelHandler levelHandler;
    private NewSkyAPI api;
    private BukkitAsyncExecutor bukkitAsyncExecutor;
    private Economy economy;
    private final AtomicBoolean clusterFenceTriggered = new AtomicBoolean();

    @Override
    public void onEnable() {
        long startTime = System.currentTimeMillis();
        info("Plugin enabling...");
        initialize();
        info("Plugin enabled!");
        long endTime = System.currentTimeMillis();
        info("Plugin initialization time: " + (endTime - startTime) + "ms");
    }

    private void initialize() {
        try {
            info("Start loading configuration now...");
            saveDefaultConfig();
            config = new ConfigHandler(this);
            info("Config load success!");

            info("Starting async executor");
            bukkitAsyncExecutor = new BukkitAsyncExecutor(this);
            info("Async executor started");

            info("Start loading server ID now...");
            String serverID = config.getServerName();
            IslandRegistry.HostClaim serverInstance = new IslandRegistry.HostClaim(serverID, UUID.randomUUID().toString());
            info("Server ID loaded success!");
            info("This Server ID: " + serverID);
            info("This Server Instance: " + serverInstance.instanceId());

            info("Start connecting to Redis now...");
            redisHandler = new RedisHandler(this, config);
            info("Redis connection success!");

            info("Start connecting to Database now...");
            databaseHandler = new DatabaseHandler(this, config);
            info("Database connection success!");

            info("Starting Redis cache state handler");
            onlinePlayerRegistry = new OnlinePlayerRegistry(this, redisHandler);
            InvitationStore invitationStore = new InvitationStore(this, redisHandler);
            IslandRegistry islandRegistry = new IslandRegistry(this, redisHandler);
            ServerRegistry serverRegistry = new ServerRegistry(this, redisHandler, islandRegistry, onlinePlayerRegistry);
            info("Redis cache state handler loaded");

            info("Loading island loaded snapshot");
            IslandSnapshot islandSnapshot = new IslandSnapshot(this, databaseHandler);
            info("Island loaded snapshot loaded");

            info("Starting world handler");
            worldHandler = new WorldHandler(this, config);
            info("World handler loaded");

            info("Starting teleport handler");
            TeleportHandler teleportHandler = new TeleportHandler();
            info("Teleport handler loaded");

            info("Starting server selector");
            ServerSelector serverSelector;
            switch (config.getServerSelector().toLowerCase(Locale.ROOT)) {
                case "round-robin":
                    serverSelector = new RoundRobinServerSelector(serverRegistry);
                    info("Using Round Robin server selector");
                    break;
                case "mspt":
                    serverSelector = new MSPTServerSelector(serverRegistry);
                    info("Using MSPT server selector");
                    break;
                case "random":
                default:
                    serverSelector = new RandomServerSelector();
                    info("Using Random server selector");
                    break;
            }
            info("Server selector loaded");

            info("Starting handlers for island remote requests");
            crossServerMessenger = new CrossServerMessenger(this, redisHandler, serverID);
            islandOperator = new IslandOperator(this, databaseHandler, worldHandler, teleportHandler, islandSnapshot, islandRegistry, serverInstance);
            IslandDistributor islandDistributor = new IslandDistributor(this, islandOperator, serverSelector, serverRegistry, islandRegistry, crossServerMessenger, serverID);
            registerCrossServerHandlers(crossServerMessenger, islandOperator);
            info("All handlers for remote requests loaded");

            info("Starting player message handler");
            PlayerMessageHandler playerMessageHandler = new PlayerMessageHandler(this, crossServerMessenger, onlinePlayerRegistry, serverID);
            info("Player message handler loaded");

            info("Starting economy provider");
            if (!setupEconomy()) {
                getLogger().severe("Economy provider not found. Disabling plugin.");
                getServer().getPluginManager().disablePlugin(this);
                return;
            }
            info("Economy provider loaded");

            info("Starting main handlers for the plugin");
            CoreHandler coreHandler = new CoreHandler(this, databaseHandler, islandDistributor);
            PlayerHandler playerHandler = new PlayerHandler(this, databaseHandler, islandDistributor, invitationStore, onlinePlayerRegistry);
            HomeHandler homeHandler = new HomeHandler(this, databaseHandler, islandDistributor);
            WarpHandler warpHandler = new WarpHandler(this, databaseHandler, islandDistributor);
            levelHandler = new LevelHandler(this, config, databaseHandler);
            BanHandler banHandler = new BanHandler(this, databaseHandler, islandDistributor);
            CoopHandler coopHandler = new CoopHandler(this, databaseHandler, islandDistributor, onlinePlayerRegistry);
            BiomeHandler biomeHandler = new BiomeHandler(this, databaseHandler);
            LobbyHandler lobbyHandler = new LobbyHandler(this, config, islandDistributor);
            UuidHandler uuidHandler = new UuidHandler(this, databaseHandler);
            WorldActivityHandler worldActivityHandler = new WorldActivityHandler(this);
            info("All main handlers loaded");

            info("Starting plugin messaging");
            getServer().getMessenger().registerOutgoingPluginChannel(this, "BungeeCord");
            info("Plugin messaging loaded");

            info("Starting all schedulers for the plugin");
            heartBeatScheduler = new HeartbeatScheduler(this, config, serverRegistry, serverInstance, this::selfFenceClusterLease);
            islandUnloadScheduler = new IslandUnloadScheduler(this, config, islandOperator, worldActivityHandler);
            levelupdateSchedulerIsland = new LevelUpdateScheduler(this, levelHandler);

            if (serverSelector instanceof MSPTServerSelector) {
                info("MSPT server selector detected, creating MSPT update scheduler");
                msptUpdateScheduler = new MSPTUpdateScheduler(this, config, serverRegistry, serverID);
            } else {
                msptUpdateScheduler = null;
            }

            info("All schedulers loaded");

            info("Starting API");
            api = new NewSkyAPI(this, coreHandler, playerHandler, homeHandler, warpHandler, levelHandler, banHandler, coopHandler, lobbyHandler, playerMessageHandler, uuidHandler, biomeHandler);
            info("API loaded");

            info("Starting listeners");
            getServer().getPluginManager().registerEvents(new OnlinePlayersListener(this, onlinePlayerRegistry, serverInstance), this);
            getServer().getPluginManager().registerEvents(new WorldLoadListener(this, config, levelupdateSchedulerIsland, islandSnapshot, worldActivityHandler), this);
            getServer().getPluginManager().registerEvents(new WorldUnloadListener(this, levelupdateSchedulerIsland, islandSnapshot), this);
            getServer().getPluginManager().registerEvents(new WorldActivityListener(this, worldActivityHandler), this);
            getServer().getPluginManager().registerEvents(new TeleportRequestListener(this, teleportHandler), this);
            getServer().getPluginManager().registerEvents(new IslandProtectionListener(this, config, islandSnapshot), this);
            getServer().getPluginManager().registerEvents(new IslandAccessListener(this, config, islandSnapshot), this);
            getServer().getPluginManager().registerEvents(new IslandPvPListener(this, config, islandSnapshot), this);
            getServer().getPluginManager().registerEvents(new UuidUpdateListener(this, uuidHandler), this);
            getServer().getPluginManager().registerEvents(new IslandCoopListener(this, coopHandler, onlinePlayerRegistry), this);
            info("All listeners loaded");

            info("Registering commands");
            PluginCommand playerCommand = createCommand("island");
            playerCommand.setAliases(Collections.singletonList("is"));
            IslandPlayerCommand islandPlayerCommand = new IslandPlayerCommand(this, api, config);
            playerCommand.setExecutor(islandPlayerCommand);
            Bukkit.getCommandMap().register("island", playerCommand);

            PluginCommand adminCommand = createCommand("islandadmin");
            adminCommand.setAliases(Collections.singletonList("isadmin"));
            IslandAdminCommand islandAdminCommand = new IslandAdminCommand(this, api, config);
            adminCommand.setExecutor(islandAdminCommand);
            Bukkit.getCommandMap().register("islandadmin", adminCommand);

            AsyncTabCompleteListener asyncTabCompleteListener = new AsyncTabCompleteListener(this);
            asyncTabCompleteListener.registerRoot("island", islandPlayerCommand);
            asyncTabCompleteListener.registerRoot("is", islandPlayerCommand);
            asyncTabCompleteListener.registerRoot("islandadmin", islandAdminCommand);
            asyncTabCompleteListener.registerRoot("isadmin", islandAdminCommand);
            getServer().getPluginManager().registerEvents(asyncTabCompleteListener, this);
            info("All commands registered");

            // Order matters: registration proves this boot owns the configured server name and
            // atomically retires the previous boot's inbox before the messenger accepts work.
            // Claims are incarnation-fenced and are never swept merely because a name restarted.
            heartBeatScheduler.start();
            crossServerMessenger.start();
            islandUnloadScheduler.start();
            levelupdateSchedulerIsland.start();

            if (msptUpdateScheduler != null) {
                msptUpdateScheduler.start();
            } else {
                info("MSPT update scheduler not enabled (server selector is not MSPT), skipping start.");
            }

        } catch (Exception e) {
            getLogger().log(Level.SEVERE, "An error occurred during plugin initialization", e);
            getServer().getPluginManager().disablePlugin(this);
        }
    }

    private boolean setupEconomy() {
        if (Bukkit.getPluginManager().getPlugin("Vault") == null) {
            return false;
        }

        RegisteredServiceProvider<Economy> rsp = Bukkit.getServicesManager().getRegistration(Economy.class);
        if (rsp == null) {
            return false;
        }

        economy = rsp.getProvider();
        return true;
    }

    private void registerCrossServerHandlers(CrossServerMessenger messenger, IslandOperator islandOperator) {
        messenger.register(IslandDistributor.ACTION_ISLAND_CREATE, payload -> emptyResponse(islandOperator.createIsland(uuid(payload, "islandUuid"), uuid(payload, "ownerUuid"))));
        messenger.register(IslandDistributor.ACTION_ISLAND_LOAD, payload -> emptyResponse(islandOperator.loadIsland(uuid(payload, "islandUuid"))));
        messenger.register(IslandDistributor.ACTION_ISLAND_UNLOAD, payload -> emptyResponse(islandOperator.unloadIsland(uuid(payload, "islandUuid"))));
        messenger.register(IslandDistributor.ACTION_ISLAND_DELETE, payload -> emptyResponse(islandOperator.deleteIsland(uuid(payload, "islandUuid"), Actor.fromJson(payload))));
        messenger.register(IslandDistributor.ACTION_ISLAND_TELEPORT_PREPARE, payload -> emptyResponse(islandOperator.prepareTeleport(uuid(payload, "playerUuid"), payload.getString("teleportWorld"), payload.getString("teleportLocation"))));
        messenger.register(IslandDistributor.ACTION_ISLAND_MEMBER_ADD, payload -> emptyResponse(islandOperator.addMember(uuid(payload, "islandUuid"), uuid(payload, "playerUuid"), payload.getString("role"), optionalUuid(payload, "vouchedBy"))));
        messenger.register(IslandDistributor.ACTION_ISLAND_MEMBER_REMOVE, payload -> emptyResponse(islandOperator.removeMember(uuid(payload, "islandUuid"), Actor.fromJson(payload), uuid(payload, "playerUuid"))));
        messenger.register(IslandDistributor.ACTION_ISLAND_OWNER_SET, payload -> emptyResponse(islandOperator.setOwner(uuid(payload, "islandUuid"), Actor.fromJson(payload), uuid(payload, "newOwnerUuid"))));
        messenger.register(IslandDistributor.ACTION_ISLAND_BAN_ADD, payload -> emptyResponse(islandOperator.addBan(uuid(payload, "islandUuid"), Actor.fromJson(payload), uuid(payload, "playerUuid"))));
        messenger.register(IslandDistributor.ACTION_ISLAND_BAN_REMOVE, payload -> emptyResponse(islandOperator.removeBan(uuid(payload, "islandUuid"), Actor.fromJson(payload), uuid(payload, "playerUuid"))));
        messenger.register(IslandDistributor.ACTION_ISLAND_COOP_ADD, payload -> emptyResponse(islandOperator.addCoop(uuid(payload, "islandUuid"), Actor.fromJson(payload), uuid(payload, "playerUuid"))));
        messenger.register(IslandDistributor.ACTION_ISLAND_COOP_REMOVE, payload -> emptyResponse(islandOperator.removeCoop(uuid(payload, "islandUuid"), Actor.fromJson(payload), uuid(payload, "playerUuid"))));
        messenger.register(IslandDistributor.ACTION_ISLAND_LOCK_TOGGLE, payload -> islandOperator.toggleLock(uuid(payload, "islandUuid"), Actor.fromJson(payload), operationUuid(payload)).thenApply(locked -> new JSONObject().put("locked", locked)));
        messenger.register(IslandDistributor.ACTION_ISLAND_PVP_TOGGLE, payload -> islandOperator.togglePvp(uuid(payload, "islandUuid"), Actor.fromJson(payload), operationUuid(payload)).thenApply(pvp -> new JSONObject().put("pvp", pvp)));
        messenger.register(IslandDistributor.ACTION_ISLAND_EXPEL, payload -> emptyResponse(islandOperator.expelPlayer(uuid(payload, "islandUuid"), Actor.fromJson(payload), uuid(payload, "playerUuid"))));
    }

    private CompletableFuture<JSONObject> emptyResponse(CompletableFuture<Void> future) {
        return future.thenApply(v -> new JSONObject());
    }

    private UUID optionalUuid(JSONObject payload, String key) {
        String value = payload.optString(key, null);
        return value == null || value.isEmpty() ? null : UUID.fromString(value);
    }

    private UUID uuid(JSONObject payload, String key) {
        return UUID.fromString(payload.getString(key));
    }

    private UUID operationUuid(JSONObject payload) {
        String value = payload.optString("operationId", null);
        return value == null || value.isEmpty() ? UUID.randomUUID() : UUID.fromString(value);
    }

    private PluginCommand createCommand(String name) {
        try {
            Constructor<PluginCommand> c = PluginCommand.class.getDeclaredConstructor(String.class, Plugin.class);
            c.setAccessible(true);
            return c.newInstance(name, this);
        } catch (Exception e) {
            throw new RuntimeException("Failed to create command: " + name, e);
        }
    }

    @Override
    public void onDisable() {
        info("Plugin disabling...");
        shutdown();
        info("Plugin disabled!");
    }

    public void shutdown() {
        // Intake closes first: once worlds start unloading and the claim sweep runs, a late load
        // request could otherwise re-claim and re-load a world on a dying server. The heartbeat's
        // cleanup runs after the worlds are gone so it also releases claims taken by requests that
        // slipped in before the messenger stopped.
        boolean inFlightOperations = islandOperator != null && islandOperator.stopAcceptingOperations();

        if (crossServerMessenger != null) {
            inFlightOperations |= crossServerMessenger.stop();
        }

        if (msptUpdateScheduler != null) {
            msptUpdateScheduler.stop();
        }

        if (levelupdateSchedulerIsland != null) {
            levelupdateSchedulerIsland.stop();
        }

        if (islandUnloadScheduler != null) {
            islandUnloadScheduler.stop();
        }

        if (worldHandler != null) {
            worldHandler.unloadAllWorldsOnShutdown(!clusterFenceTriggered.get());
        }

        if (heartBeatScheduler != null) {
            heartBeatScheduler.stop(!inFlightOperations);
        }

        if (redisHandler != null) {
            redisHandler.disconnect();
        }

        if (databaseHandler != null) {
            databaseHandler.close();
        }
    }

    /**
     * Losing the Redis lease is a correctness event, not merely an availability warning. Disable
     * the plugin on the main thread so its normal shutdown unloads every island before another
     * server is allowed to keep serving a newly acquired claim.
     */
    private void selfFenceClusterLease() {
        if (!clusterFenceTriggered.compareAndSet(false, true)) {
            return;
        }

        Runnable disable = () -> {
            if (isEnabled()) {
                getLogger().severe("Disabling NewSky because this JVM can no longer prove ownership of its island leases.");
                getServer().getPluginManager().disablePlugin(this);
            }
        };

        if (Bukkit.isPrimaryThread()) {
            disable.run();
        } else {
            getServer().getScheduler().runTask(this, disable);
        }
    }

    public void reload() {
        info("Plugin configs reloading...");
        config.reload();
        levelHandler.startup();
        info("Plugin configs reloaded!");
    }

    @SuppressWarnings("unused")
    public CompletableFuture<Set<UUID>> getOnlinePlayersUUIDs() {
        return CompletableFuture.supplyAsync(() -> onlinePlayerRegistry.getOnlinePlayerUuids(), getBukkitAsyncExecutor());
    }

    @SuppressWarnings("unused")
    public CompletableFuture<Set<String>> getOnlinePlayersNames() {
        return CompletableFuture.supplyAsync(() -> onlinePlayerRegistry.getOnlinePlayerNames(), getBukkitAsyncExecutor());
    }

    @SuppressWarnings("unused")
    public BukkitAsyncExecutor getBukkitAsyncExecutor() {
        return bukkitAsyncExecutor;
    }

    @SuppressWarnings("unused")
    public NewSkyAPI getApi() {
        return api;
    }

    @SuppressWarnings("unused")
    public Economy getEconomy() {
        return economy;
    }

    @SuppressWarnings("unused")
    public void info(String message) {
        getLogger().log(Level.INFO, message);
    }

    @SuppressWarnings("unused")
    public void warning(String message) {
        getLogger().log(Level.WARNING, message);
    }

    @SuppressWarnings("unused")
    public void severe(String message) {
        getLogger().log(Level.SEVERE, message);
    }

    @SuppressWarnings("unused")
    public void severe(String message, Throwable throwable) {
        getLogger().log(Level.SEVERE, message, throwable);
    }

    @SuppressWarnings("unused")
    public void debug(String source, String message) {
        if (config.isDebug()) {
            getLogger().log(Level.INFO, "[" + source + "] " + message);
        }
    }
}
