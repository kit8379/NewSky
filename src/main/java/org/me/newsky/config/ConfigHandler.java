package org.me.newsky.config;

import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.me.newsky.NewSky;
import org.me.newsky.util.ColorUtils;

import java.io.File;
import java.io.IOException;
import java.util.Objects;
import java.util.Set;

public class ConfigHandler {
    private final NewSky plugin;
    private FileConfiguration config;
    private FileConfiguration messages;
    private FileConfiguration levels;

    public ConfigHandler(NewSky plugin) {
        this.plugin = plugin;
        loadConfigs();
        updateConfigs();
    }

    private void loadConfigs() {
        config = loadConfig("config.yml");
        messages = loadConfig("messages.yml");
        levels = loadConfig("levels.yml");
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
            e.printStackTrace();
        }
    }

    public String getMySQLHost() {
        return config.getString("MySQL.host");
    }

    public int getMySQLPort() {
        return config.getInt("MySQL.port");
    }

    public String getMySQLName() {
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

    public boolean isDebug() {
        return config.getBoolean("debug");
    }

    public String getServerName() {
        return config.getString("server.name");
    }

    public int getHeartbeatInterval() {
        return config.getInt("server.heartbeat-interval");
    }

    public boolean isLobby() {
        return config.getBoolean("server.lobby");
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

    public String getDebugPrefix() {
        return ColorUtils.colorize(messages.getString("messages.debug-prefix"));
    }

    public String getUsagePrefix() {
        return ColorUtils.colorize(messages.getString("messages.usage-prefix"));
    }

    public String getCannotLeaveIslandBoundaryMessage() {
        return ColorUtils.colorize(messages.getString("messages.cannot-leave-island-boundary"));
    }

    public String getNoActiveServerMessage() {
        return ColorUtils.colorize(messages.getString("messages.no-active-server"));
    }

    public String getIslandNotLoadedMessage() {
        return ColorUtils.colorize(messages.getString("messages.island-not-loaded"));
    }

    public String getIslandAlreadyLoadedMessage() {
        return ColorUtils.colorize(messages.getString("messages.island-already-loaded"));
    }

    public String getCannotEditIslandMessage() {
        return ColorUtils.colorize(messages.getString("messages.cannot-edit-island"));
    }

    public String getIslandPvpDisabledMessage() {
        return ColorUtils.colorize(messages.getString("messages.island-pvp-disabled"));
    }

    public String getIslandMemberExistsMessage(String name) {
        return ColorUtils.colorize(Objects.requireNonNull(messages.getString("messages.island-member-exists")).replace("{name}", name));
    }

    public String getNotIslandMemberMessage(String name) {
        return ColorUtils.colorize(Objects.requireNonNull(messages.getString("messages.not-island-member")).replace("{name}", name));
    }

    public String getOnlyPlayerCanRunCommandMessage() {
        return ColorUtils.colorize(messages.getString("messages.only-player-can-run-command"));
    }

    public String getNoIslandMessage(String name) {
        return ColorUtils.colorize(Objects.requireNonNull(messages.getString("messages.no-island")).replace("{name}", name));
    }

    public String getNoOwnerMessage() {
        return ColorUtils.colorize(messages.getString("messages.no-owner"));
    }

    public String getIslandIDMessage(String islandId) {
        return ColorUtils.colorize(Objects.requireNonNull(messages.getString("messages.island-id")).replace("{island}", islandId));
    }

    public String getIslandOwnerMessage(String ownerName) {
        return ColorUtils.colorize(Objects.requireNonNull(messages.getString("messages.island-owner")).replace("{owner}", ownerName));
    }

    public String getIslandMembersMessage(String membersString) {
        return ColorUtils.colorize(Objects.requireNonNull(messages.getString("messages.island-members")).replace("{members}", membersString));
    }

    public String getIslandLoadSuccessMessage(String name) {
        return ColorUtils.colorize(Objects.requireNonNull(messages.getString("messages.island-load-success")).replace("{name}", name));
    }

    public String getIslandUnloadSuccessMessage(String name) {
        return ColorUtils.colorize(Objects.requireNonNull(messages.getString("messages.island-unload-success")).replace("{name}", name));
    }

    public String getPluginReloadedMessage() {
        return ColorUtils.colorize(messages.getString("messages.plugin-reloaded"));
    }

    public String getAdminCommandHelpMessage() {
        return ColorUtils.colorize(messages.getString("messages.admin-command-help"));
    }

    public String getAdminUnknownSubCommandMessage(String subCommand) {
        return ColorUtils.colorize(Objects.requireNonNull(messages.getString("messages.admin-unknown-sub-command")).replace("{subCommand}", subCommand));
    }

    public String getAdminAddMemberUsageMessage() {
        return ColorUtils.colorize(messages.getString("messages.admin-add-member-usage"));
    }

    public String getAdminRemoveMemberUsageMessage() {
        return ColorUtils.colorize(messages.getString("messages.admin-remove-member-usage"));
    }

    public String getAdminCreateUsageMessage() {
        return ColorUtils.colorize(messages.getString("messages.admin-create-island-usage"));
    }

    public String getAdminDeleteUsageMessage() {
        return ColorUtils.colorize(messages.getString("messages.admin-delete-island-usage"));
    }

    public String getAdminSetHomeUsageMessage() {
        return ColorUtils.colorize(messages.getString("messages.admin-set-home-usage"));
    }

    public String getAdminDelHomeUsageMessage() {
        return ColorUtils.colorize(messages.getString("messages.admin-delete-home-usage"));
    }

    public String getAdminHomeUsageMessage() {
        return ColorUtils.colorize(messages.getString("messages.admin-home-usage"));
    }

    public String getAdminSetWarpUsageMessage() {
        return ColorUtils.colorize(messages.getString("messages.admin-set-warps-usage"));
    }

    public String getAdminDelWarpUsageMessage() {
        return ColorUtils.colorize(messages.getString("messages.admin-delete-warp-usage"));
    }

    public String getAdminWarpUsageMessage() {
        return ColorUtils.colorize(messages.getString("messages.admin-warp-usage"));
    }

    public String getAdminInfoUsageMessage() {
        return ColorUtils.colorize(messages.getString("messages.admin-info-usage"));
    }

    public String getAdminLoadUsageMessage() {
        return ColorUtils.colorize(messages.getString("messages.admin-load-usage"));
    }

    public String getAdminUnloadUsageMessage() {
        return ColorUtils.colorize(messages.getString("messages.admin-unload-usage"));
    }

    public String getAdminReloadUsageMessage() {
        return ColorUtils.colorize(messages.getString("messages.admin-reload-usage"));
    }

    public String getAdminLockUsageMessage() {
        return ColorUtils.colorize(messages.getString("messages.admin-lock-usage"));
    }

    public String getAdminPvpUsageMessage() {
        return ColorUtils.colorize(messages.getString("messages.admin-pvp-usage"));
    }

    public String getAdminSetOwnerUsageMessage() {
        return ColorUtils.colorize(messages.getString("messages.admin-set-owner-usage"));
    }

    public String getAdminNoIslandMessage(String name) {
        return ColorUtils.colorize(Objects.requireNonNull(messages.getString("messages.admin-no-island")).replace("{name}", name));
    }

    public String getAdminAlreadyHasIslandMessage(String name) {
        return ColorUtils.colorize(Objects.requireNonNull(messages.getString("messages.admin-already-has-island")).replace("{name}", name));
    }

    public String getAdminAddMemberSuccessMessage(String name, String member) {
        return ColorUtils.colorize(Objects.requireNonNull(messages.getString("messages.admin-add-member-success")).replace("{name}", name).replace("{member}", member));
    }

    public String getAdminRemoveMemberSuccessMessage(String name, String member) {
        return ColorUtils.colorize(Objects.requireNonNull(messages.getString("messages.admin-remove-member-success")).replace("{name}", name).replace("{member}", member));
    }

    public String getAdminCreateSuccessMessage(String name) {
        return ColorUtils.colorize(Objects.requireNonNull(messages.getString("messages.admin-create-island-success")).replace("{name}", name));
    }

    public String getAdminDeleteWarningMessage(String name) {
        return ColorUtils.colorize(Objects.requireNonNull(messages.getString("messages.admin-delete-warning")).replace("{name}", name));
    }

    public String getAdminDeleteSuccessMessage(String name) {
        return ColorUtils.colorize(Objects.requireNonNull(messages.getString("messages.admin-delete-island-success")).replace("{name}", name));
    }

    public String getAdminCannotDeleteDefaultHomeMessage(String name) {
        return ColorUtils.colorize(Objects.requireNonNull(messages.getString("messages.admin-cannot-delete-default-home")).replace("{name}", name));
    }

    public String getAdminNoHomeMessage(String name, String home) {
        return ColorUtils.colorize(Objects.requireNonNull(messages.getString("messages.admin-no-home")).replace("{name}", name).replace("{home}", home));
    }

    public String getAdminHomeSuccessMessage(String home) {
        return ColorUtils.colorize(Objects.requireNonNull(messages.getString("messages.home-success")).replace("{home}", home));
    }

    public String getAdminDelHomeSuccessMessage(String name, String home) {
        return ColorUtils.colorize(Objects.requireNonNull(messages.getString("messages.admin-delete-home-success")).replace("{name}", name).replace("{home}", home));
    }

    public String getAdminNoWarpMessage(String name, String warp) {
        return ColorUtils.colorize(Objects.requireNonNull(messages.getString("messages.admin-no-warp")).replace("{name}", name).replace("{warp}", warp));
    }

    public String getAdminDelWarpSuccessMessage(String name, String warp) {
        return ColorUtils.colorize(Objects.requireNonNull(messages.getString("messages.admin-delete-warp-success")).replace("{name}", name).replace("{warp}", warp));
    }

    public String getAdminLockSuccessMessage(String name) {
        return ColorUtils.colorize(Objects.requireNonNull(messages.getString("messages.admin-lock-success")).replace("{name}", name));
    }

    public String getAdminUnLockSuccessMessage(String name) {
        return ColorUtils.colorize(Objects.requireNonNull(messages.getString("messages.admin-unlock-success")).replace("{name}", name));
    }


    public String getAdminMustInIslandSetHomeMessage(String name) {
        return ColorUtils.colorize(Objects.requireNonNull(messages.getString("messages.admin-must-in-island-set-home")).replace("{name}", name));
    }

    public String getAdminMustInIslandSetWarpMessage(String name) {
        return ColorUtils.colorize(Objects.requireNonNull(messages.getString("messages.admin-must-in-island-set-warp")).replace("{name}", name));
    }

    public String getAdminSetHomeSuccessMessage(String name, String home) {
        return ColorUtils.colorize(Objects.requireNonNull(messages.getString("messages.admin-set-home-success")).replace("{name}", name).replace("{home}", home));
    }

    public String getAdminSetWarpSuccessMessage(String name, String warp) {
        return ColorUtils.colorize(Objects.requireNonNull(messages.getString("messages.admin-set-warp-success")).replace("{name}", name).replace("{warp}", warp));
    }

    public String getAdminPvpEnableSuccessMessage(String name) {
        return ColorUtils.colorize(Objects.requireNonNull(messages.getString("messages.admin-pvp-enable-success")).replace("{name}", name));
    }

    public String getAdminPvpDisableSuccessMessage(String name) {
        return ColorUtils.colorize(Objects.requireNonNull(messages.getString("messages.admin-pvp-disable-success")).replace("{name}", name));
    }

    public String getAdminSetOwnerSuccessMessage(String name, String owner) {
        return ColorUtils.colorize(Objects.requireNonNull(messages.getString("messages.admin-set-owner-success")).replace("{name}", name).replace("{owner}", owner));
    }

    public String getAdminAlreadyOwnerMessage(String name, String owner) {
        return ColorUtils.colorize(Objects.requireNonNull(messages.getString("messages.admin-already-owner")).replace("{name}", name).replace("{owner}", owner));
    }

    public String getPlayerCommandHelpMessage() {
        return ColorUtils.colorize(messages.getString("messages.player-command-help"));
    }

    public String getPlayerUnknownSubCommandMessage(String subCommand) {
        return ColorUtils.colorize(Objects.requireNonNull(messages.getString("messages.player-unknown-sub-command")).replace("{subCommand}", subCommand));
    }

    public String getPlayerAddMemberUsageMessage() {
        return ColorUtils.colorize(messages.getString("messages.player-add-member-usage"));
    }

    public String getPlayerRemoveMemberUsageMessage() {
        return ColorUtils.colorize(messages.getString("messages.player-remove-member-usage"));
    }

    public String getPlayerCreateUsageMessage() {
        return ColorUtils.colorize(messages.getString("messages.player-create-island-usage"));
    }

    public String getPlayerDeleteUsageMessage() {
        return ColorUtils.colorize(messages.getString("messages.player-delete-island-usage"));
    }

    public String getPlayerHomeUsageMessage() {
        return ColorUtils.colorize(messages.getString("messages.player-home-usage"));
    }

    public String getPlayerSetHomeUsageMessage() {
        return ColorUtils.colorize(messages.getString("messages.player-set-home-usage"));
    }

    public String getPlayerDelHomeUsageMessage() {
        return ColorUtils.colorize(messages.getString("messages.player-delete-home-usage"));
    }

    public String getPlayerWarpUsageMessage() {
        return ColorUtils.colorize(messages.getString("messages.player-warp-usage"));
    }

    public String getPlayerSetWarpUsageMessage() {
        return ColorUtils.colorize(messages.getString("messages.player-set-warp-usage"));
    }

    public String getPlayerDelWarpUsageMessage() {
        return ColorUtils.colorize(messages.getString("messages.player-delete-warp-usage"));
    }

    public String getPlayerInfoUsageMessage() {
        return ColorUtils.colorize(messages.getString("messages.player-info-usage"));
    }

    public String getPlayerLockUsageMessage() {
        return ColorUtils.colorize(messages.getString("messages.player-lock-usage"));
    }

    public String getPlayerPvpUsageMessage() {
        return ColorUtils.colorize(messages.getString("messages.player-pvp-usage"));
    }

    public String getPlayerSetOwnerUsageMessage() {
        return ColorUtils.colorize(messages.getString("messages.player-set-owner-usage"));
    }

    public String getPlayerLeaveUsageMessage() {
        return ColorUtils.colorize(messages.getString("messages.player-leave-usage"));
    }

    public String getPlayerNoIslandMessage() {
        return ColorUtils.colorize(messages.getString("messages.player-no-island"));
    }

    public String getPlayerAlreadyHasIslandMessage() {
        return ColorUtils.colorize(messages.getString("messages.player-already-has-island"));
    }

    public String getPlayerMustInIslandSetHomeMessage() {
        return ColorUtils.colorize(messages.getString("messages.player-must-in-island-set-home"));
    }

    public String getPlayerMustInIslandSetWarpMessage() {
        return ColorUtils.colorize(messages.getString("messages.player-must-in-island-set-warp"));
    }

    public String getPlayerAddMemberSuccessMessage(String member) {
        return ColorUtils.colorize(Objects.requireNonNull(messages.getString("messages.player-add-member-success")).replace("{member}", member));
    }

    public String getPlayerRemoveMemberSuccessMessage(String member) {
        return ColorUtils.colorize(Objects.requireNonNull(messages.getString("messages.player-remove-member-success")).replace("{member}", member));
    }

    public String getPlayerRemoveMemberCannotRemoveSelfMessage() {
        return ColorUtils.colorize(messages.getString("messages.player-remove-member-cannot-remove-self"));
    }

    public String getPlayerCreateSuccessMessage() {
        return ColorUtils.colorize(messages.getString("messages.player-create-island-success"));
    }

    public String getPlayerDeleteWarningMessage() {
        return ColorUtils.colorize(messages.getString("messages.player-delete-warning"));
    }

    public String getPlayerDeleteSuccessMessage() {
        return ColorUtils.colorize(messages.getString("messages.player-delete-island-success"));
    }

    public String getPlayerSetHomeSuccessMessage(String home) {
        return ColorUtils.colorize(Objects.requireNonNull(messages.getString("messages.player-set-home-success")).replace("{home}", home));
    }

    public String getPlayerSetWarpSuccessMessage(String warp) {
        return ColorUtils.colorize(Objects.requireNonNull(messages.getString("messages.player-set-warp-success")).replace("{warp}", warp));
    }

    public String getPlayerDelHomeSuccessMessage(String home) {
        return ColorUtils.colorize(Objects.requireNonNull(messages.getString("messages.player-delete-home-success")).replace("{home}", home));
    }

    public String getPlayerCannotDeleteDefaultHomeMessage() {
        return ColorUtils.colorize(messages.getString("messages.player-cannot-delete-default-home"));
    }

    public String getPlayerDelWarpSuccessMessage(String warp) {
        return ColorUtils.colorize(Objects.requireNonNull(messages.getString("messages.player-delete-warp-success")).replace("{warp}", warp));
    }

    public String getPlayerPvpEnableSuccessMessage() {
        return ColorUtils.colorize(messages.getString("messages.player-pvp-enable-success"));
    }

    public String getPlayerPvpDisableSuccessMessage() {
        return ColorUtils.colorize(messages.getString("messages.player-pvp-disable-success"));
    }

    public String getPlayerUnLockSuccessMessage() {
        return ColorUtils.colorize(messages.getString("messages.player-unlock-success"));
    }

    public String getPlayerLockSuccessMessage() {
        return ColorUtils.colorize(messages.getString("messages.player-lock-success"));
    }

    public String getPlayerNoHomeMessage(String home) {
        return ColorUtils.colorize(Objects.requireNonNull(messages.getString("messages.player-no-home")).replace("{home}", home));
    }

    public String getPlayerNoWarpMessage(String warp) {
        return ColorUtils.colorize(Objects.requireNonNull(messages.getString("messages.player-no-warp")).replace("{warp}", warp));
    }

    public String getPlayerHomeSuccessMessage(String home) {
        return ColorUtils.colorize(Objects.requireNonNull(messages.getString("messages.player-home-success")).replace("{home}", home));
    }

    public String getPlayerNotOwnerMessage() {
        return ColorUtils.colorize(messages.getString("messages.player-not-owner"));
    }

    public String getWarpSuccessMessage(String warp) {
        return ColorUtils.colorize(Objects.requireNonNull(messages.getString("messages.warp-success")).replace("{warp}", warp));
    }

    public String getNoWarpMessage(String name, String warp) {
        return ColorUtils.colorize(Objects.requireNonNull(messages.getString("messages.no-warp")).replace("{name}", name).replace("{warp}", warp));
    }

    public String getIslandLockedMessage() {
        return ColorUtils.colorize(messages.getString("messages.island-locked"));
    }


    public String getPlayerSetOwnerSuccessMessage(String name) {
        return ColorUtils.colorize(Objects.requireNonNull(messages.getString("messages.player-set-owner-success")).replace("{name}", name));
    }

    public String getPlayerAlreadyOwnerMessage(String name) {
        return ColorUtils.colorize(Objects.requireNonNull(messages.getString("messages.player-already-owner")).replace("{name}", name));
    }

    public String getPlayerCannotLeaveAsOwnerMessage() {
        return ColorUtils.colorize(messages.getString("messages.player-cannot-leave-as-owner"));
    }

    public String getPlayerLeaveSuccessMessage() {
        return ColorUtils.colorize(messages.getString("messages.player-leave-success"));
    }

    public String getAdminIslandLevelMessage(int level) {
        return ColorUtils.colorize(Objects.requireNonNull(messages.getString("messages.admin-island-level")).replace("{level}", String.valueOf(level)));
    }

    public String getAdminLevelUsageMessage() {
        return ColorUtils.colorize(messages.getString("messages.admin-level-usage"));
    }

    public String getPlayerIslandLevelMessage(int level) {
        return ColorUtils.colorize(Objects.requireNonNull(messages.getString("messages.player-island-level")).replace("{level}", String.valueOf(level)));
    }

    public String getPlayerLevelUsageMessage() {
        return ColorUtils.colorize(messages.getString("messages.player-level-usage"));
    }

    public int getBlockLevel(String material) {
        return levels.getInt("blocks." + material, 0);
    }
}
