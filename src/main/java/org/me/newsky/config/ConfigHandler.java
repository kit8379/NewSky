package org.me.newsky.config;

import net.kyori.adventure.text.Component;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.me.newsky.NewSky;
import org.me.newsky.util.ColorUtils;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.logging.Level;

public class ConfigHandler {
    private final NewSky plugin;
    private FileConfiguration config;
    private FileConfiguration messages;
    private FileConfiguration levels;
    private FileConfiguration limits;

    public ConfigHandler(NewSky plugin) {
        this.plugin = plugin;
        loadConfigs();
        updateConfigs();
    }

    private void loadConfigs() {
        config = loadConfig("config.yml");
        messages = loadConfig("messages.yml");
        levels = loadConfig("levels.yml");
        limits = loadConfig("limits.yml");
    }

    private FileConfiguration loadConfig(String fileName) {
        File file = new File(plugin.getDataFolder(), fileName);
        if (!file.exists()) {
            plugin.saveResource(fileName, false);
        }
        return YamlConfiguration.loadConfiguration(file);
    }

    private void updateConfigs() {
        updateConfig(config, "config.yml");
        updateConfig(messages, "messages.yml");
        updateConfig(levels, "levels.yml");
        updateConfig(limits, "limits.yml");
    }

    private void updateConfig(FileConfiguration config, String fileName) {
        FileConfiguration defaultConfig = YamlConfiguration.loadConfiguration(new File(plugin.getDataFolder(), fileName));
        Set<String> keys = defaultConfig.getKeys(true);
        boolean updated = false;

        for (String key : keys) {
            if (!config.contains(key)) {
                config.set(key, defaultConfig.get(key));
                updated = true;
            }
        }

        if (updated) {
            saveConfig(config, fileName);
        }
    }

    public void saveConfig(FileConfiguration config, String fileName) {
        try {
            config.save(new File(plugin.getDataFolder(), fileName));
        } catch (IOException e) {
            plugin.getLogger().log(Level.SEVERE, "Could not save " + fileName, e);
        }
    }

    // ================================================================================================================
    // Config Section
    // ================================================================================================================

    public String getMySQLHost() {
        return config.getString("MySQL.host");
    }

    public int getMySQLPort() {
        return config.getInt("MySQL.port");
    }

    public String getMySQLDB() {
        return config.getString("MySQL.database");
    }

    public String getMySQLUsername() {
        return config.getString("MySQL.username");
    }

    public String getMySQLPassword() {
        return config.getString("MySQL.password");
    }

    public String getMySQLProperties() {
        return config.getString("MySQL.properties");
    }

    public int getMySQLMaxPoolSize() {
        return config.getInt("MySQL.max-pool-size");
    }

    public int getMySQLConnectionTimeout() {
        return config.getInt("MySQL.connection-timeout");
    }

    public String getMySQLCachePrepStmts() {
        return config.getString("MySQL.cache-prep-statements");
    }

    public String getMySQLPrepStmtCacheSize() {
        return config.getString("MySQL.prep-stmt-cache-size");
    }

    public String getMySQLPrepStmtCacheSqlLimit() {
        return config.getString("MySQL.prep-stmt-cache-sql-limit");
    }

    public String getRedisHost() {
        return config.getString("redis.host");
    }

    public int getRedisPort() {
        return config.getInt("redis.port");
    }

    public String getRedisPassword() {
        return config.getString("redis.password");
    }

    public int getRedisDatabase() {
        return config.getInt("redis.database");
    }

    public String getRedisChannel() {
        return config.getString("redis.channel");
    }

    public boolean isDebug() {
        return config.getBoolean("debug");
    }

    public String getServerName() {
        return config.getString("server.name");
    }

    public boolean isLobby() {
        return config.getBoolean("server.lobby");
    }

    public int getHeartbeatInterval() {
        return config.getInt("server.heartbeat-interval");
    }

    public String getServerSelector() {
        return config.getString("server.selector");
    }

    public int getMsptUpdateInterval() {
        return config.getInt("server.mspt-update-interval");
    }

    public String getLobbyCommand(String playerName) {
        return Objects.requireNonNull(config.getString("lobby.command")).replace("{player}", playerName);
    }

    public String getTemplateWorldName() {
        return config.getString("island.template");
    }

    public int getIslandSize() {
        return config.getInt("island.size");
    }

    public int getBufferSize() {
        return config.getInt("island.buffer");
    }

    public int getIslandSpawnX() {
        return config.getInt("island.spawn.x");
    }

    public int getIslandSpawnY() {
        return config.getInt("island.spawn.y");
    }

    public int getIslandSpawnZ() {
        return config.getInt("island.spawn.z");
    }

    public float getIslandSpawnYaw() {
        return (float) config.getDouble("island.spawn.yaw");
    }

    public float getIslandSpawnPitch() {
        return (float) config.getDouble("island.spawn.pitch");
    }

    public int getIslandUnloadInterval() {
        return config.getInt("island.island-unload-interval");
    }

    public int getIslandLevelUpdateInterval() {
        return config.getInt("island.level-update-interval");
    }

    public int getBlockLevel(String material) {
        return levels.getInt("blocks." + material, 0);
    }

    public int getBlockLimit(String block) {
        return limits.getInt("blocks." + block, -1);
    }

    public int getEntityLimit(String entity) {
        return limits.getInt("entities." + entity, -1);
    }

    // General Messages
    public Component getPluginReloadedMessage() {
        return ColorUtils.colorize(messages.getString("messages.plugin-reloaded"));
    }

    public Component getOnlyPlayerCanRunCommandMessage() {
        return ColorUtils.colorize(messages.getString("messages.only-player-can-run-command"));
    }

    public Component getNoActiveServerMessage() {
        return ColorUtils.colorize(messages.getString("messages.no-active-server"));
    }

    public Component getIslandNotLoadedMessage() {
        return ColorUtils.colorize(messages.getString("messages.island-not-loaded"));
    }

    public Component getIslandAlreadyLoadedMessage() {
        return ColorUtils.colorize(messages.getString("messages.island-already-loaded"));
    }

    public Component getIslandLevelMessage(int level) {
        return ColorUtils.colorize(Objects.requireNonNull(messages.getString("messages.island-level")).replace("{level}", String.valueOf(level)));
    }

    public Component getIslandLoadSuccessMessage(String name) {
        return ColorUtils.colorize(Objects.requireNonNull(messages.getString("messages.island-load-success")).replace("{name}", name));
    }

    public Component getIslandUnloadSuccessMessage(String name) {
        return ColorUtils.colorize(Objects.requireNonNull(messages.getString("messages.island-unload-success")).replace("{name}", name));
    }

    public Component getIslandLockedMessage() {
        return ColorUtils.colorize(messages.getString("messages.island-locked"));
    }

    public Component getPlayerBannedMessage() {
        return ColorUtils.colorize(messages.getString("messages.player-banned"));
    }

    public Component getIslandPvpDisabledMessage() {
        return ColorUtils.colorize(messages.getString("messages.island-pvp-disabled"));
    }

    public Component getIslandMemberExistsMessage(String name) {
        return ColorUtils.colorize(Objects.requireNonNull(messages.getString("messages.island-member-exists")).replace("{name}", name));
    }

    public Component getIslandMemberNotExistsMessage(String name) {
        return ColorUtils.colorize(Objects.requireNonNull(messages.getString("messages.not-island-member")).replace("{name}", name));
    }

    public Component getCannotEditIslandMessage() {
        return ColorUtils.colorize(messages.getString("messages.cannot-edit-island"));
    }

    public Component getCannotLeaveIslandBoundaryMessage() {
        return ColorUtils.colorize(messages.getString("messages.cannot-leave-island-boundary"));
    }

    public Component getWarpSuccessMessage(String warp) {
        return ColorUtils.colorize(Objects.requireNonNull(messages.getString("messages.warp-success")).replace("{warp}", warp));
    }

    public Component getNoWarpMessage(String name, String warp) {
        return ColorUtils.colorize(Objects.requireNonNull(messages.getString("messages.no-warp")).replace("{name}", name).replace("{warp}", warp));
    }

    public Component getNoIslandMessage(String name) {
        return ColorUtils.colorize(Objects.requireNonNull(messages.getString("messages.no-island")).replace("{name}", name));
    }

    // Admin Command Messages
    public Component getAdminNoIslandMessage(String name) {
        return ColorUtils.colorize(Objects.requireNonNull(messages.getString("messages.admin-no-island")).replace("{name}", name));
    }

    public Component getAdminAlreadyHasIslandMessage(String name) {
        return ColorUtils.colorize(Objects.requireNonNull(messages.getString("messages.admin-already-has-island")).replace("{name}", name));
    }

    public Component getAdminAddMemberSuccessMessage(String name, String member) {
        return ColorUtils.colorize(Objects.requireNonNull(messages.getString("messages.admin-add-member-success")).replace("{name}", name).replace("{member}", member));
    }

    public Component getAdminRemoveMemberSuccessMessage(String name, String member) {
        return ColorUtils.colorize(Objects.requireNonNull(messages.getString("messages.admin-remove-member-success")).replace("{name}", name).replace("{member}", member));
    }

    public Component getAdminCreateSuccessMessage(String name) {
        return ColorUtils.colorize(Objects.requireNonNull(messages.getString("messages.admin-create-island-success")).replace("{name}", name));
    }

    public Component getAdminDeleteWarningMessage(String name) {
        return ColorUtils.colorize(Objects.requireNonNull(messages.getString("messages.admin-delete-warning")).replace("{name}", name));
    }

    public Component getAdminDeleteSuccessMessage(String name) {
        return ColorUtils.colorize(Objects.requireNonNull(messages.getString("messages.admin-delete-island-success")).replace("{name}", name));
    }

    public Component getAdminCannotDeleteDefaultHomeMessage(String name) {
        return ColorUtils.colorize(Objects.requireNonNull(messages.getString("messages.admin-cannot-delete-default-home")).replace("{name}", name));
    }

    public Component getAdminNoHomeMessage(String name, String home) {
        return ColorUtils.colorize(Objects.requireNonNull(messages.getString("messages.admin-no-home")).replace("{name}", name).replace("{home}", home));
    }

    public Component getAdminHomeSuccessMessage(String name, String home) {
        return ColorUtils.colorize(Objects.requireNonNull(messages.getString("messages.admin-home-success")).replace("{home}", home).replace("{name}", name));
    }

    public Component getAdminDelHomeSuccessMessage(String name, String home) {
        return ColorUtils.colorize(Objects.requireNonNull(messages.getString("messages.admin-delete-home-success")).replace("{name}", name).replace("{home}", home));
    }

    public Component getAdminNoWarpMessage(String name, String warp) {
        return ColorUtils.colorize(Objects.requireNonNull(messages.getString("messages.admin-no-warp")).replace("{name}", name).replace("{warp}", warp));
    }

    public Component getAdminDelWarpSuccessMessage(String name, String warp) {
        return ColorUtils.colorize(Objects.requireNonNull(messages.getString("messages.admin-delete-warp-success")).replace("{name}", name).replace("{warp}", warp));
    }

    public Component getAdminLockSuccessMessage(String name) {
        return ColorUtils.colorize(Objects.requireNonNull(messages.getString("messages.admin-lock-success")).replace("{name}", name));
    }

    public Component getAdminUnLockSuccessMessage(String name) {
        return ColorUtils.colorize(Objects.requireNonNull(messages.getString("messages.admin-unlock-success")).replace("{name}", name));
    }

    public Component getAdminMustInIslandSetHomeMessage(String name) {
        return ColorUtils.colorize(Objects.requireNonNull(messages.getString("messages.admin-must-in-island-set-home")).replace("{name}", name));
    }

    public Component getAdminMustInIslandSetWarpMessage(String name) {
        return ColorUtils.colorize(Objects.requireNonNull(messages.getString("messages.admin-must-in-island-set-warp")).replace("{name}", name));
    }

    public Component getAdminSetHomeSuccessMessage(String name, String home) {
        return ColorUtils.colorize(Objects.requireNonNull(messages.getString("messages.admin-set-home-success")).replace("{name}", name).replace("{home}", home));
    }

    public Component getAdminSetWarpSuccessMessage(String name, String warp) {
        return ColorUtils.colorize(Objects.requireNonNull(messages.getString("messages.admin-set-warp-success")).replace("{name}", name).replace("{warp}", warp));
    }

    public Component getAdminPvpEnableSuccessMessage(String name) {
        return ColorUtils.colorize(Objects.requireNonNull(messages.getString("messages.admin-pvp-enable-success")).replace("{name}", name));
    }

    public Component getAdminPvpDisableSuccessMessage(String name) {
        return ColorUtils.colorize(Objects.requireNonNull(messages.getString("messages.admin-pvp-disable-success")).replace("{name}", name));
    }

    // Player Command Messages
    public Component getPlayerNoIslandMessage() {
        return ColorUtils.colorize(messages.getString("messages.player-no-island"));
    }

    public Component getPlayerAlreadyHasIslandMessage() {
        return ColorUtils.colorize(messages.getString("messages.player-already-has-island"));
    }

    public Component getPlayerAlreadyHasIslandOtherMessage(String playerName) {
        return ColorUtils.colorize(Objects.requireNonNull(messages.getString("messages.player-already-has-island-other")).replace("{player}", playerName));
    }

    public Component getPlayerMustInIslandSetHomeMessage() {
        return ColorUtils.colorize(messages.getString("messages.player-must-in-island-set-home"));
    }

    public Component getPlayerMustInIslandSetWarpMessage() {
        return ColorUtils.colorize(messages.getString("messages.player-must-in-island-set-warp"));
    }

    public Component getPlayerAddMemberSuccessMessage(String member) {
        return ColorUtils.colorize(Objects.requireNonNull(messages.getString("messages.player-add-member-success")).replace("{member}", member));
    }

    public Component getPlayerRemoveMemberSuccessMessage(String member) {
        return ColorUtils.colorize(Objects.requireNonNull(messages.getString("messages.player-remove-member-success")).replace("{member}", member));
    }

    public Component getPlayerCreateSuccessMessage() {
        return ColorUtils.colorize(messages.getString("messages.player-create-island-success"));
    }

    public Component getPlayerDeleteWarningMessage() {
        return ColorUtils.colorize(messages.getString("messages.player-delete-warning"));
    }

    public Component getPlayerDeleteSuccessMessage() {
        return ColorUtils.colorize(messages.getString("messages.player-delete-island-success"));
    }

    public Component getPlayerSetHomeSuccessMessage(String home) {
        return ColorUtils.colorize(Objects.requireNonNull(messages.getString("messages.player-set-home-success")).replace("{home}", home));
    }

    public Component getPlayerSetWarpSuccessMessage(String warp) {
        return ColorUtils.colorize(Objects.requireNonNull(messages.getString("messages.player-set-warp-success")).replace("{warp}", warp));
    }

    public Component getPlayerDelHomeSuccessMessage(String home) {
        return ColorUtils.colorize(Objects.requireNonNull(messages.getString("messages.player-delete-home-success")).replace("{home}", home));
    }

    public Component getPlayerCannotDeleteDefaultHomeMessage() {
        return ColorUtils.colorize(messages.getString("messages.player-cannot-delete-default-home"));
    }

    public Component getPlayerDelWarpSuccessMessage(String warp) {
        return ColorUtils.colorize(Objects.requireNonNull(messages.getString("messages.player-delete-warp-success")).replace("{warp}", warp));
    }

    public Component getPlayerLockSuccessMessage() {
        return ColorUtils.colorize(messages.getString("messages.player-lock-success"));
    }

    public Component getPlayerUnLockSuccessMessage() {
        return ColorUtils.colorize(messages.getString("messages.player-unlock-success"));
    }

    public Component getPlayerPvpEnableSuccessMessage() {
        return ColorUtils.colorize(messages.getString("messages.player-pvp-enable-success"));
    }

    public Component getPlayerPvpDisableSuccessMessage() {
        return ColorUtils.colorize(messages.getString("messages.player-pvp-disable-success"));
    }

    public Component getPlayerNoHomeMessage(String home) {
        return ColorUtils.colorize(Objects.requireNonNull(messages.getString("messages.player-no-home")).replace("{home}", home));
    }

    public Component getPlayerNoWarpMessage(String warp) {
        return ColorUtils.colorize(Objects.requireNonNull(messages.getString("messages.player-no-warp")).replace("{warp}", warp));
    }

    public Component getPlayerHomeSuccessMessage(String home) {
        return ColorUtils.colorize(Objects.requireNonNull(messages.getString("messages.player-home-success")).replace("{home}", home));
    }

    public Component getPlayerSetOwnerSuccessMessage(String name) {
        return ColorUtils.colorize(Objects.requireNonNull(messages.getString("messages.player-set-owner-success")).replace("{name}", name));
    }

    public Component getPlayerAlreadyOwnerMessage(String name) {
        return ColorUtils.colorize(Objects.requireNonNull(messages.getString("messages.player-already-owner")).replace("{name}", name));
    }

    public Component getPlayerCannotLeaveAsOwnerMessage() {
        return ColorUtils.colorize(messages.getString("messages.player-cannot-leave-as-owner"));
    }

    public Component getPlayerLeaveSuccessMessage() {
        return ColorUtils.colorize(messages.getString("messages.player-leave-success"));
    }


    // Info
    public Component getIslandInfoHeaderMessage() {
        return ColorUtils.colorize(messages.getString("messages.island-info-header"));
    }

    public Component getIslandInfoUUIDMessage(UUID islandUuid) {
        return ColorUtils.colorize(Objects.requireNonNull(Objects.requireNonNull(messages.getString("messages.island-info-uuid")).replace("{island_uuid}", islandUuid.toString())));
    }

    public Component getIslandInfoLevelMessage(int level) {
        return ColorUtils.colorize(Objects.requireNonNull(messages.getString("messages.island-info-level")).replace("{level}", String.valueOf(level)));
    }

    public Component getIslandInfoOwnerMessage(String ownerName) {
        return ColorUtils.colorize(Objects.requireNonNull(messages.getString("messages.island-info-owner")).replace("{owner}", ownerName));
    }

    public Component getIslandInfoMembersMessage(String membersString) {
        return ColorUtils.colorize(Objects.requireNonNull(messages.getString("messages.island-info-members")).replace("{members}", membersString));
    }

    public Component getIslandInfoNoMembersMessage() {
        return ColorUtils.colorize(messages.getString("messages.island-info-no-members"));
    }

    // Top
    public Component getTopIslandsHeaderMessage() {
        return ColorUtils.colorize(messages.getString("messages.top-islands-header"));
    }

    public Component getTopIslandMessage(int rank, String ownerName, String memberNames, int level) {
        return ColorUtils.colorize(Objects.requireNonNull(messages.getString("messages.top-islands-message")).replace("{rank}", String.valueOf(rank)).replace("{owner}", ownerName).replace("{members}", memberNames).replace("{level}", String.valueOf(level)));
    }

    public Component getNoIslandsFoundMessage() {
        return ColorUtils.colorize(messages.getString("messages.top-islands-no-island"));
    }

    public Component getPlayerExpelSuccessMessage(String playerName) {
        return ColorUtils.colorize(Objects.requireNonNull(messages.getString("messages.player-expel-success")).replace("{player}", playerName));
    }

    public Component getPlayerBanSuccessMessage(String playerName) {
        return ColorUtils.colorize(Objects.requireNonNull(messages.getString("messages.player-ban-success")).replace("{player}", playerName));
    }

    public Component getPlayerUnbanSuccessMessage(String playerName) {
        return ColorUtils.colorize(Objects.requireNonNull(messages.getString("messages.player-unban-success")).replace("{player}", playerName));
    }

    public Component getPlayerAlreadyBannedMessage(String playerName) {
        return ColorUtils.colorize(Objects.requireNonNull(messages.getString("messages.player-already-banned")).replace("{player}", playerName));
    }

    public Component getPlayerNotBannedMessage(String playerName) {
        return ColorUtils.colorize(Objects.requireNonNull(messages.getString("messages.player-not-banned")).replace("{player}", playerName));
    }

    public Component getPlayerCannotBanIslandPlayerMessage() {
        return ColorUtils.colorize(messages.getString("messages.player-cannot-ban-island-player"));
    }

    public Component getBannedPlayersHeaderMessage() {
        return ColorUtils.colorize(messages.getString("messages.banned-players-header"));
    }

    public Component getBannedPlayerMessage(String playerName) {
        return ColorUtils.colorize(Objects.requireNonNull(messages.getString("messages.banned-player-message")).replace("{player}", playerName));
    }

    public Component getNoBannedPlayersMessage() {
        return ColorUtils.colorize(messages.getString("messages.banned-player-no-banned"));
    }

    public Component getAdminBanSuccessMessage(String name, String playerName) {
        return ColorUtils.colorize(Objects.requireNonNull(messages.getString("messages.admin-ban-success")).replace("{player}", playerName).replace("{name}", name));
    }

    public Component getAdminUnbanSuccessMessage(String name, String playerName) {
        return ColorUtils.colorize(Objects.requireNonNull(messages.getString("messages.admin-unban-success")).replace("{player}", playerName).replace("{name}", name));
    }

    public Component getPlayerNoItemInHandMessage() {
        return ColorUtils.colorize(messages.getString("messages.no-item-in-hand"));
    }

    public Component getPlayerBlockValueCommandMessage(String blockName, int value) {
        return ColorUtils.colorize(Objects.requireNonNull(messages.getString("messages.block-value")).replace("{blockName}", blockName).replace("{value}", String.valueOf(value)));
    }

    public Component getPlayerNotOnlineMessage(String playerName) {
        return ColorUtils.colorize(Objects.requireNonNull(messages.getString("messages.player-not-online")).replace("{player}", playerName));
    }

    public Component getBlockLimitMessage(String block) {
        return ColorUtils.colorize(Objects.requireNonNull(messages.getString("messages.block-limit-reached")).replace("{block}", block));
    }
}
