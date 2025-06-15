package org.me.newsky.config;

import net.kyori.adventure.text.Component;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.me.newsky.NewSky;
import org.me.newsky.util.ColorUtils;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.logging.Level;

public class ConfigHandler {
    private final NewSky plugin;
    private FileConfiguration config;
    private FileConfiguration messages;
    private FileConfiguration commands;
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
        commands = loadConfig("commands.yml");
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
        updateConfig(commands, "commands.yml");
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

    public String getRedisCacheChannel() {
        return config.getString("redis.channel.cache");
    }

    public String getRedisIslandChannel() {
        return config.getString("redis.channel.island");
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

    public String getLobbyCommand(String player) {
        return Objects.requireNonNull(config.getString("lobby.command")).replace("{player}", player);
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

    public String getBaseCommandMode() {
        return config.getString("command.base-command-mode");
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

    // ================================================================================================================
    // Commands Section
    // ================================================================================================================

    public List<String> getPlayerCommandOrder() {
        return Objects.requireNonNull(commands.getConfigurationSection("commands.player")).getKeys(false).stream().toList();
    }

    public String[] getPlayerInviteAliases() {
        return commands.getStringList("commands.player.invite.aliases").toArray(new String[0]);
    }

    public String getPlayerInvitePermission() {
        return commands.getString("commands.player.invite.permission");
    }

    public String getPlayerInviteSyntax() {
        return commands.getString("commands.player.invite.syntax");
    }

    public String getPlayerInviteDescription() {
        return commands.getString("commands.player.invite.description");
    }

    public String[] getPlayerAcceptAliases() {
        return commands.getStringList("commands.player.accept.aliases").toArray(new String[0]);
    }

    public String getPlayerAcceptPermission() {
        return commands.getString("commands.player.accept.permission");
    }

    public String getPlayerAcceptSyntax() {
        return commands.getString("commands.player.accept.syntax");
    }

    public String getPlayerAcceptDescription() {
        return commands.getString("commands.player.accept.description");
    }

    public String[] getPlayerRejectAliases() {
        return commands.getStringList("commands.player.reject.aliases").toArray(new String[0]);
    }

    public String getPlayerRejectPermission() {
        return commands.getString("commands.player.reject.permission");
    }

    public String getPlayerRejectSyntax() {
        return commands.getString("commands.player.reject.syntax");
    }

    public String getPlayerRejectDescription() {
        return commands.getString("commands.player.reject.description");
    }

    public String[] getPlayerBanAliases() {
        return commands.getStringList("commands.player.ban.aliases").toArray(new String[0]);
    }

    public String getPlayerBanPermission() {
        return commands.getString("commands.player.ban.permission");
    }

    public String getPlayerBanSyntax() {
        return commands.getString("commands.player.ban.syntax");
    }

    public String getPlayerBanDescription() {
        return commands.getString("commands.player.ban.description");
    }

    public String[] getPlayerBanListAliases() {
        return commands.getStringList("commands.player.banlist.aliases").toArray(new String[0]);
    }

    public String getPlayerBanListPermission() {
        return commands.getString("commands.player.banlist.permission");
    }

    public String getPlayerBanListSyntax() {
        return commands.getString("commands.player.banlist.syntax");
    }

    public String getPlayerBanListDescription() {
        return commands.getString("commands.player.banlist.description");
    }

    public String[] getPlayerCoopAliases() {
        return commands.getStringList("commands.player.coop.aliases").toArray(new String[0]);
    }

    public String getPlayerCoopPermission() {
        return commands.getString("commands.player.coop.permission");
    }

    public String getPlayerCoopSyntax() {
        return commands.getString("commands.player.coop.syntax");
    }

    public String getPlayerCoopDescription() {
        return commands.getString("commands.player.coop.description");
    }

    public String[] getPlayerCoopListAliases() {
        return commands.getStringList("commands.player.cooplist.aliases").toArray(new String[0]);
    }

    public String getPlayerCoopListPermission() {
        return commands.getString("commands.player.cooplist.permission");
    }

    public String getPlayerCoopListSyntax() {
        return commands.getString("commands.player.cooplist.syntax");
    }

    public String getPlayerCoopListDescription() {
        return commands.getString("commands.player.cooplist.description");
    }

    public String[] getPlayerCreateAliases() {
        return commands.getStringList("commands.player.create.aliases").toArray(new String[0]);
    }

    public String getPlayerCreatePermission() {
        return commands.getString("commands.player.create.permission");
    }

    public String getPlayerCreateSyntax() {
        return commands.getString("commands.player.create.syntax");
    }

    public String getPlayerCreateDescription() {
        return commands.getString("commands.player.create.description");
    }

    public String[] getPlayerDeleteAliases() {
        return commands.getStringList("commands.player.delete.aliases").toArray(new String[0]);
    }

    public String getPlayerDeletePermission() {
        return commands.getString("commands.player.delete.permission");
    }

    public String getPlayerDeleteSyntax() {
        return commands.getString("commands.player.delete.syntax");
    }

    public String getPlayerDeleteDescription() {
        return commands.getString("commands.player.delete.description");
    }

    public String[] getPlayerDelHomeAliases() {
        return commands.getStringList("commands.player.delhome.aliases").toArray(new String[0]);
    }

    public String getPlayerDelHomePermission() {
        return commands.getString("commands.player.delhome.permission");
    }

    public String getPlayerDelHomeSyntax() {
        return commands.getString("commands.player.delhome.syntax");
    }

    public String getPlayerDelHomeDescription() {
        return commands.getString("commands.player.delhome.description");
    }

    public String[] getPlayerDelWarpAliases() {
        return commands.getStringList("commands.player.delwarp.aliases").toArray(new String[0]);
    }

    public String getPlayerDelWarpPermission() {
        return commands.getString("commands.player.delwarp.permission");
    }

    public String getPlayerDelWarpSyntax() {
        return commands.getString("commands.player.delwarp.syntax");
    }

    public String getPlayerDelWarpDescription() {
        return commands.getString("commands.player.delwarp.description");
    }

    public String[] getPlayerExpelAliases() {
        return commands.getStringList("commands.player.expel.aliases").toArray(new String[0]);
    }

    public String getPlayerExpelPermission() {
        return commands.getString("commands.player.expel.permission");
    }

    public String getPlayerExpelSyntax() {
        return commands.getString("commands.player.expel.syntax");
    }

    public String getPlayerExpelDescription() {
        return commands.getString("commands.player.expel.description");
    }

    public String[] getPlayerHelpAliases() {
        return commands.getStringList("commands.player.help.aliases").toArray(new String[0]);
    }

    public String getPlayerHelpPermission() {
        return commands.getString("commands.player.help.permission");
    }

    public String getPlayerHelpSyntax() {
        return commands.getString("commands.player.help.syntax");
    }

    public String getPlayerHelpDescription() {
        return commands.getString("commands.player.help.description");
    }

    public String[] getPlayerHomeAliases() {
        return commands.getStringList("commands.player.home.aliases").toArray(new String[0]);
    }

    public String getPlayerHomePermission() {
        return commands.getString("commands.player.home.permission");
    }

    public String getPlayerHomeSyntax() {
        return commands.getString("commands.player.home.syntax");
    }

    public String getPlayerHomeDescription() {
        return commands.getString("commands.player.home.description");
    }

    public String[] getPlayerInfoAliases() {
        return commands.getStringList("commands.player.info.aliases").toArray(new String[0]);
    }

    public String getPlayerInfoPermission() {
        return commands.getString("commands.player.info.permission");
    }

    public String getPlayerInfoSyntax() {
        return commands.getString("commands.player.info.syntax");
    }

    public String getPlayerInfoDescription() {
        return commands.getString("commands.player.info.description");
    }

    public String[] getPlayerLeaveAliases() {
        return commands.getStringList("commands.player.leave.aliases").toArray(new String[0]);
    }

    public String getPlayerLeavePermission() {
        return commands.getString("commands.player.leave.permission");
    }

    public String getPlayerLeaveSyntax() {
        return commands.getString("commands.player.leave.syntax");
    }

    public String getPlayerLeaveDescription() {
        return commands.getString("commands.player.leave.description");
    }

    public String[] getPlayerLevelAliases() {
        return commands.getStringList("commands.player.level.aliases").toArray(new String[0]);
    }

    public String getPlayerLevelPermission() {
        return commands.getString("commands.player.level.permission");
    }

    public String getPlayerLevelSyntax() {
        return commands.getString("commands.player.level.syntax");
    }

    public String getPlayerLevelDescription() {
        return commands.getString("commands.player.level.description");
    }

    public String[] getPlayerLockAliases() {
        return commands.getStringList("commands.player.lock.aliases").toArray(new String[0]);
    }

    public String getPlayerLockPermission() {
        return commands.getString("commands.player.lock.permission");
    }

    public String getPlayerLockSyntax() {
        return commands.getString("commands.player.lock.syntax");
    }

    public String getPlayerLockDescription() {
        return commands.getString("commands.player.lock.description");
    }

    public String[] getPlayerPvpAliases() {
        return commands.getStringList("commands.player.pvp.aliases").toArray(new String[0]);
    }

    public String getPlayerPvpPermission() {
        return commands.getString("commands.player.pvp.permission");
    }

    public String getPlayerPvpSyntax() {
        return commands.getString("commands.player.pvp.syntax");
    }

    public String getPlayerPvpDescription() {
        return commands.getString("commands.player.pvp.description");
    }

    public String[] getPlayerRemoveMemberAliases() {
        return commands.getStringList("commands.player.removemember.aliases").toArray(new String[0]);
    }

    public String getPlayerRemoveMemberPermission() {
        return commands.getString("commands.player.removemember.permission");
    }

    public String getPlayerRemoveMemberSyntax() {
        return commands.getString("commands.player.removemember.syntax");
    }

    public String getPlayerRemoveMemberDescription() {
        return commands.getString("commands.player.removemember.description");
    }

    public String[] getPlayerSetHomeAliases() {
        return commands.getStringList("commands.player.sethome.aliases").toArray(new String[0]);
    }

    public String getPlayerSetHomePermission() {
        return commands.getString("commands.player.sethome.permission");
    }

    public String getPlayerSetHomeSyntax() {
        return commands.getString("commands.player.sethome.syntax");
    }

    public String getPlayerSetHomeDescription() {
        return commands.getString("commands.player.sethome.description");
    }

    public String[] getPlayerSetOwnerAliases() {
        return commands.getStringList("commands.player.setowner.aliases").toArray(new String[0]);
    }

    public String getPlayerSetOwnerPermission() {
        return commands.getString("commands.player.setowner.permission");
    }

    public String getPlayerSetOwnerSyntax() {
        return commands.getString("commands.player.setowner.syntax");
    }

    public String getPlayerSetOwnerDescription() {
        return commands.getString("commands.player.setowner.description");
    }

    public String[] getPlayerSetWarpAliases() {
        return commands.getStringList("commands.player.setwarp.aliases").toArray(new String[0]);
    }

    public String getPlayerSetWarpPermission() {
        return commands.getString("commands.player.setwarp.permission");
    }

    public String getPlayerSetWarpSyntax() {
        return commands.getString("commands.player.setwarp.syntax");
    }

    public String getPlayerSetWarpDescription() {
        return commands.getString("commands.player.setwarp.description");
    }

    public String[] getPlayerTopAliases() {
        return commands.getStringList("commands.player.top.aliases").toArray(new String[0]);
    }

    public String getPlayerTopPermission() {
        return commands.getString("commands.player.top.permission");
    }

    public String getPlayerTopSyntax() {
        return commands.getString("commands.player.top.syntax");
    }

    public String getPlayerTopDescription() {
        return commands.getString("commands.player.top.description");
    }

    public String[] getPlayerUnbanAliases() {
        return commands.getStringList("commands.player.unban.aliases").toArray(new String[0]);
    }

    public String getPlayerUnbanPermission() {
        return commands.getString("commands.player.unban.permission");
    }

    public String getPlayerUnbanSyntax() {
        return commands.getString("commands.player.unban.syntax");
    }

    public String getPlayerUnbanDescription() {
        return commands.getString("commands.player.unban.description");
    }

    public String[] getPlayerUncoopAliases() {
        return commands.getStringList("commands.player.uncoop.aliases").toArray(new String[0]);
    }

    public String getPlayerUncoopPermission() {
        return commands.getString("commands.player.uncoop.permission");
    }

    public String getPlayerUncoopSyntax() {
        return commands.getString("commands.player.uncoop.syntax");
    }

    public String getPlayerUncoopDescription() {
        return commands.getString("commands.player.uncoop.description");
    }

    public String[] getPlayerValueAliases() {
        return commands.getStringList("commands.player.value.aliases").toArray(new String[0]);
    }

    public String getPlayerValuePermission() {
        return commands.getString("commands.player.value.permission");
    }

    public String getPlayerValueSyntax() {
        return commands.getString("commands.player.value.syntax");
    }

    public String getPlayerValueDescription() {
        return commands.getString("commands.player.value.description");
    }

    public String[] getPlayerWarpAliases() {
        return commands.getStringList("commands.player.warp.aliases").toArray(new String[0]);
    }

    public String getPlayerWarpPermission() {
        return commands.getString("commands.player.warp.permission");
    }

    public String getPlayerWarpSyntax() {
        return commands.getString("commands.player.warp.syntax");
    }

    public String getPlayerWarpDescription() {
        return commands.getString("commands.player.warp.description");
    }

    public List<String> getAdminCommandOrder() {
        return Objects.requireNonNull(commands.getConfigurationSection("commands.admin")).getKeys(false).stream().toList();
    }

    public String[] getAdminAddMemberAliases() {
        return commands.getStringList("commands.admin.addmember.aliases").toArray(new String[0]);
    }

    public String getAdminAddMemberPermission() {
        return commands.getString("commands.admin.addmember.permission");
    }

    public String getAdminAddMemberSyntax() {
        return commands.getString("commands.admin.addmember.syntax");
    }

    public String getAdminAddMemberDescription() {
        return commands.getString("commands.admin.addmember.description");
    }

    public String[] getAdminBanAliases() {
        return commands.getStringList("commands.admin.ban.aliases").toArray(new String[0]);
    }

    public String getAdminBanPermission() {
        return commands.getString("commands.admin.ban.permission");
    }

    public String getAdminBanSyntax() {
        return commands.getString("commands.admin.ban.syntax");
    }

    public String getAdminBanDescription() {
        return commands.getString("commands.admin.ban.description");
    }

    public String[] getAdminCoopAliases() {
        return commands.getStringList("commands.admin.coop.aliases").toArray(new String[0]);
    }

    public String getAdminCoopPermission() {
        return commands.getString("commands.admin.coop.permission");
    }

    public String getAdminCoopSyntax() {
        return commands.getString("commands.admin.coop.syntax");
    }

    public String getAdminCoopDescription() {
        return commands.getString("commands.admin.coop.description");
    }

    public String[] getAdminCreateAliases() {
        return commands.getStringList("commands.admin.create.aliases").toArray(new String[0]);
    }

    public String getAdminCreatePermission() {
        return commands.getString("commands.admin.create.permission");
    }

    public String getAdminCreateSyntax() {
        return commands.getString("commands.admin.create.syntax");
    }

    public String getAdminCreateDescription() {
        return commands.getString("commands.admin.create.description");
    }

    public String[] getAdminDeleteAliases() {
        return commands.getStringList("commands.admin.delete.aliases").toArray(new String[0]);
    }

    public String getAdminDeletePermission() {
        return commands.getString("commands.admin.delete.permission");
    }

    public String getAdminDeleteSyntax() {
        return commands.getString("commands.admin.delete.syntax");
    }

    public String getAdminDeleteDescription() {
        return commands.getString("commands.admin.delete.description");
    }

    public String[] getAdminDelHomeAliases() {
        return commands.getStringList("commands.admin.delhome.aliases").toArray(new String[0]);
    }

    public String getAdminDelHomePermission() {
        return commands.getString("commands.admin.delhome.permission");
    }

    public String getAdminDelHomeSyntax() {
        return commands.getString("commands.admin.delhome.syntax");
    }

    public String getAdminDelHomeDescription() {
        return commands.getString("commands.admin.delhome.description");
    }

    public String[] getAdminDelWarpAliases() {
        return commands.getStringList("commands.admin.delwarp.aliases").toArray(new String[0]);
    }

    public String getAdminDelWarpPermission() {
        return commands.getString("commands.admin.delwarp.permission");
    }

    public String getAdminDelWarpSyntax() {
        return commands.getString("commands.admin.delwarp.syntax");
    }

    public String getAdminDelWarpDescription() {
        return commands.getString("commands.admin.delwarp.description");
    }

    public String[] getAdminHelpAliases() {
        return commands.getStringList("commands.admin.help.aliases").toArray(new String[0]);
    }

    public String getAdminHelpPermission() {
        return commands.getString("commands.admin.help.permission");
    }

    public String getAdminHelpSyntax() {
        return commands.getString("commands.admin.help.syntax");
    }

    public String getAdminHelpDescription() {
        return commands.getString("commands.admin.help.description");
    }

    public String[] getAdminHomeAliases() {
        return commands.getStringList("commands.admin.home.aliases").toArray(new String[0]);
    }

    public String getAdminHomePermission() {
        return commands.getString("commands.admin.home.permission");
    }

    public String getAdminHomeSyntax() {
        return commands.getString("commands.admin.home.syntax");
    }

    public String getAdminHomeDescription() {
        return commands.getString("commands.admin.home.description");
    }

    public String[] getAdminLoadAliases() {
        return commands.getStringList("commands.admin.load.aliases").toArray(new String[0]);
    }

    public String getAdminLoadPermission() {
        return commands.getString("commands.admin.load.permission");
    }

    public String getAdminLoadSyntax() {
        return commands.getString("commands.admin.load.syntax");
    }

    public String getAdminLoadDescription() {
        return commands.getString("commands.admin.load.description");
    }

    public String[] getAdminLockAliases() {
        return commands.getStringList("commands.admin.lock.aliases").toArray(new String[0]);
    }

    public String getAdminLockPermission() {
        return commands.getString("commands.admin.lock.permission");
    }

    public String getAdminLockSyntax() {
        return commands.getString("commands.admin.lock.syntax");
    }

    public String getAdminLockDescription() {
        return commands.getString("commands.admin.lock.description");
    }

    public String[] getAdminPvpAliases() {
        return commands.getStringList("commands.admin.pvp.aliases").toArray(new String[0]);
    }

    public String getAdminPvpPermission() {
        return commands.getString("commands.admin.pvp.permission");
    }

    public String getAdminPvpSyntax() {
        return commands.getString("commands.admin.pvp.syntax");
    }

    public String getAdminPvpDescription() {
        return commands.getString("commands.admin.pvp.description");
    }

    public String[] getAdminReloadAliases() {
        return commands.getStringList("commands.admin.reload.aliases").toArray(new String[0]);
    }

    public String getAdminReloadPermission() {
        return commands.getString("commands.admin.reload.permission");
    }

    public String getAdminReloadSyntax() {
        return commands.getString("commands.admin.reload.syntax");
    }

    public String getAdminReloadDescription() {
        return commands.getString("commands.admin.reload.description");
    }

    public String[] getAdminRemoveMemberAliases() {
        return commands.getStringList("commands.admin.removemember.aliases").toArray(new String[0]);
    }

    public String getAdminRemoveMemberPermission() {
        return commands.getString("commands.admin.removemember.permission");
    }

    public String getAdminRemoveMemberSyntax() {
        return commands.getString("commands.admin.removemember.syntax");
    }

    public String getAdminRemoveMemberDescription() {
        return commands.getString("commands.admin.removemember.description");
    }

    public String[] getAdminSetHomeAliases() {
        return commands.getStringList("commands.admin.sethome.aliases").toArray(new String[0]);
    }

    public String getAdminSetHomePermission() {
        return commands.getString("commands.admin.sethome.permission");
    }

    public String getAdminSetHomeSyntax() {
        return commands.getString("commands.admin.sethome.syntax");
    }

    public String getAdminSetHomeDescription() {
        return commands.getString("commands.admin.sethome.description");
    }

    public String[] getAdminSetWarpAliases() {
        return commands.getStringList("commands.admin.setwarp.aliases").toArray(new String[0]);
    }

    public String getAdminSetWarpPermission() {
        return commands.getString("commands.admin.setwarp.permission");
    }

    public String getAdminSetWarpSyntax() {
        return commands.getString("commands.admin.setwarp.syntax");
    }

    public String getAdminSetWarpDescription() {
        return commands.getString("commands.admin.setwarp.description");
    }

    public String[] getAdminUnbanAliases() {
        return commands.getStringList("commands.admin.unban.aliases").toArray(new String[0]);
    }

    public String getAdminUnbanPermission() {
        return commands.getString("commands.admin.unban.permission");
    }

    public String getAdminUnbanSyntax() {
        return commands.getString("commands.admin.unban.syntax");
    }

    public String getAdminUnbanDescription() {
        return commands.getString("commands.admin.unban.description");
    }

    public String[] getAdminUncoopAliases() {
        return commands.getStringList("commands.admin.uncoop.aliases").toArray(new String[0]);
    }

    public String getAdminUncoopPermission() {
        return commands.getString("commands.admin.uncoop.permission");
    }

    public String getAdminUncoopSyntax() {
        return commands.getString("commands.admin.uncoop.syntax");
    }

    public String getAdminUncoopDescription() {
        return commands.getString("commands.admin.uncoop.description");
    }

    public String[] getAdminUnloadAliases() {
        return commands.getStringList("commands.admin.unload.aliases").toArray(new String[0]);
    }

    public String getAdminUnloadPermission() {
        return commands.getString("commands.admin.unload.permission");
    }

    public String getAdminUnloadSyntax() {
        return commands.getString("commands.admin.unload.syntax");
    }

    public String getAdminUnloadDescription() {
        return commands.getString("commands.admin.unload.description");
    }

    public String[] getAdminWarpAliases() {
        return commands.getStringList("commands.admin.warp.aliases").toArray(new String[0]);
    }

    public String getAdminWarpPermission() {
        return commands.getString("commands.admin.warp.permission");
    }

    public String getAdminWarpSyntax() {
        return commands.getString("commands.admin.warp.syntax");
    }

    public String getAdminWarpDescription() {
        return commands.getString("commands.admin.warp.description");
    }

    // =========================================================
    // Messages Section
    // =========================================================

    // General Messages
    public Component getPluginReloadedMessage() {
        return ColorUtils.colorize(messages.getString("messages.plugin-reloaded"));
    }

    public Component getOnlyPlayerCanRunCommandMessage() {
        return ColorUtils.colorize(messages.getString("messages.only-player-can-run-command"));
    }

    public Component getUnknownSubCommandMessage() {
        return ColorUtils.colorize(Objects.requireNonNull(messages.getString("messages.unknown-sub-command")));
    }

    public Component getNoPermissionCommandMessage() {
        return ColorUtils.colorize(Objects.requireNonNull(messages.getString("messages.no-permission-command")));
    }

    public Component getCommandUsageMessage(String command, String syntax) {
        return ColorUtils.colorize(Objects.requireNonNull(messages.getString("messages.usage-command")).replace("{command}", command).replace("{syntax}", syntax));
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

    public Component getIslandLoadSuccessMessage(String player) {
        return ColorUtils.colorize(Objects.requireNonNull(messages.getString("messages.island-load-success")).replace("{player}", player));
    }

    public Component getIslandUnloadSuccessMessage(String player) {
        return ColorUtils.colorize(Objects.requireNonNull(messages.getString("messages.island-unload-success")).replace("{player}", player));
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

    public Component getIslandMemberExistsMessage(String player) {
        return ColorUtils.colorize(Objects.requireNonNull(messages.getString("messages.island-member-exists")).replace("{player}", player));
    }

    public Component getIslandMemberNotExistsMessage(String player) {
        return ColorUtils.colorize(Objects.requireNonNull(messages.getString("messages.not-island-member")).replace("{player}", player));
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

    public Component getNoWarpMessage(String player, String warp) {
        return ColorUtils.colorize(Objects.requireNonNull(messages.getString("messages.no-warp")).replace("{player}", player).replace("{warp}", warp));
    }

    public Component getNoIslandMessage(String player) {
        return ColorUtils.colorize(Objects.requireNonNull(messages.getString("messages.no-island")).replace("{player}", player));
    }

    public Component getAlreadyHasIslandMessage(String player) {
        return ColorUtils.colorize(Objects.requireNonNull(messages.getString("messages.already-has-island")).replace("{player}", player));
    }

    public Component getWasAddedToIslandMessage(String owner) {
        return ColorUtils.colorize(Objects.requireNonNull(messages.getString("messages.was-added-to-island")).replace("{owner}", owner));
    }

    public Component getWasRemovedFromIslandMessage(String owner) {
        return ColorUtils.colorize(Objects.requireNonNull(messages.getString("messages.was-removed-from-island")).replace("{owner}", owner));
    }

    public Component getWasBannedFromIslandMessage(String owner) {
        return ColorUtils.colorize(Objects.requireNonNull(messages.getString("messages.was-banned-from-island")).replace("{owner}", owner));
    }

    public Component getWasUnbannedFromIslandMessage(String owner) {
        return ColorUtils.colorize(Objects.requireNonNull(messages.getString("messages.was-unbanned-from-island")).replace("{owner}", owner));
    }

    public Component getWasCoopedToIslandMessage(String owner) {
        return ColorUtils.colorize(Objects.requireNonNull(messages.getString("messages.was-cooped-from-island")).replace("{owner}", owner));
    }

    public Component getWasUncoopedFromIslandMessage(String owner) {
        return ColorUtils.colorize(Objects.requireNonNull(messages.getString("messages.was-uncooped-from-island")).replace("{owner}", owner));
    }

    public Component getNewMemberNotificationMessage(String player) {
        return ColorUtils.colorize(Objects.requireNonNull(messages.getString("messages.new-member-notification")).replace("{player}", player));
    }

    public Component getCannotRemoveOwnerMessage() {
        return ColorUtils.colorize(messages.getString("messages.cannot-remove-owner"));
    }

    public Component getUnknownExceptionMessage() {
        return ColorUtils.colorize(messages.getString("messages.unknown-exception"));
    }

    // Admin Command Messages
    public Component getAdminNoIslandMessage(String player) {
        return ColorUtils.colorize(Objects.requireNonNull(messages.getString("messages.admin-no-island")).replace("{player}", player));
    }

    public Component getAdminAddMemberSuccessMessage(String target, String owner) {
        return ColorUtils.colorize(Objects.requireNonNull(messages.getString("messages.admin-add-member-success")).replace("{target}", target).replace("{owner}", owner));
    }

    public Component getAdminRemoveMemberSuccessMessage(String target, String owner) {
        return ColorUtils.colorize(Objects.requireNonNull(messages.getString("messages.admin-remove-member-success")).replace("{target}", target).replace("{owner}", owner));
    }

    public Component getAdminCreateSuccessMessage(String player) {
        return ColorUtils.colorize(Objects.requireNonNull(messages.getString("messages.admin-create-island-success")).replace("{player}", player));
    }

    public Component getAdminDeleteWarningMessage(String player) {
        return ColorUtils.colorize(Objects.requireNonNull(messages.getString("messages.admin-delete-warning")).replace("{player}", player));
    }

    public Component getAdminDeleteSuccessMessage(String player) {
        return ColorUtils.colorize(Objects.requireNonNull(messages.getString("messages.admin-delete-island-success")).replace("{player}", player));
    }

    public Component getAdminCannotDeleteDefaultHomeMessage(String player) {
        return ColorUtils.colorize(Objects.requireNonNull(messages.getString("messages.admin-cannot-delete-default-home")).replace("{player}", player));
    }

    public Component getAdminNoHomeMessage(String player, String home) {
        return ColorUtils.colorize(Objects.requireNonNull(messages.getString("messages.admin-no-home")).replace("{player}", player).replace("{home}", home));
    }

    public Component getAdminHomeSuccessMessage(String player, String home) {
        return ColorUtils.colorize(Objects.requireNonNull(messages.getString("messages.admin-home-success")).replace("{player}", player).replace("{home}", home));
    }

    public Component getAdminDelHomeSuccessMessage(String player, String home) {
        return ColorUtils.colorize(Objects.requireNonNull(messages.getString("messages.admin-delete-home-success")).replace("{player}", player).replace("{home}", home));
    }

    public Component getAdminNoWarpMessage(String player, String warp) {
        return ColorUtils.colorize(Objects.requireNonNull(messages.getString("messages.admin-no-warp")).replace("{player}", player).replace("{warp}", warp));
    }

    public Component getAdminDelWarpSuccessMessage(String player, String warp) {
        return ColorUtils.colorize(Objects.requireNonNull(messages.getString("messages.admin-delete-warp-success")).replace("{player}", player).replace("{warp}", warp));
    }

    public Component getAdminLockSuccessMessage(String player) {
        return ColorUtils.colorize(Objects.requireNonNull(messages.getString("messages.admin-lock-success")).replace("{player}", player));
    }

    public Component getAdminUnLockSuccessMessage(String player) {
        return ColorUtils.colorize(Objects.requireNonNull(messages.getString("messages.admin-unlock-success")).replace("{player}", player));
    }

    public Component getAdminMustInIslandSetHomeMessage(String player) {
        return ColorUtils.colorize(Objects.requireNonNull(messages.getString("messages.admin-must-in-island-set-home")).replace("{player}", player));
    }

    public Component getAdminMustInIslandSetWarpMessage(String player) {
        return ColorUtils.colorize(Objects.requireNonNull(messages.getString("messages.admin-must-in-island-set-warp")).replace("{player}", player));
    }

    public Component getAdminSetHomeSuccessMessage(String player, String home) {
        return ColorUtils.colorize(Objects.requireNonNull(messages.getString("messages.admin-set-home-success")).replace("{player}", player).replace("{home}", home));
    }

    public Component getAdminSetWarpSuccessMessage(String player, String warp) {
        return ColorUtils.colorize(Objects.requireNonNull(messages.getString("messages.admin-set-warp-success")).replace("{player}", player).replace("{warp}", warp));
    }

    public Component getAdminPvpEnableSuccessMessage(String player) {
        return ColorUtils.colorize(Objects.requireNonNull(messages.getString("messages.admin-pvp-enable-success")).replace("{player}", player));
    }

    public Component getAdminPvpDisableSuccessMessage(String player) {
        return ColorUtils.colorize(Objects.requireNonNull(messages.getString("messages.admin-pvp-disable-success")).replace("{player}", player));
    }

    public Component getAdminBanSuccessMessage(String owner, String target) {
        return ColorUtils.colorize(Objects.requireNonNull(messages.getString("messages.admin-ban-success")).replace("{owner}", owner).replace("{target}", target));
    }

    public Component getAdminUnbanSuccessMessage(String owner, String target) {
        return ColorUtils.colorize(Objects.requireNonNull(messages.getString("messages.admin-unban-success")).replace("{owner}", owner).replace("{target}", target));
    }

    public Component getAdminCoopSuccessMessage(String owner, String target) {
        return ColorUtils.colorize(Objects.requireNonNull(messages.getString("messages.admin-coop-success")).replace("{owner}", owner).replace("{target}", target));
    }

    public Component getAdminUncoopSuccessMessage(String owner, String target) {
        return ColorUtils.colorize(Objects.requireNonNull(messages.getString("messages.admin-uncoop-success")).replace("{owner}", owner).replace("{target}", target));
    }

    // Player Command Messages
    public Component getPlayerNoIslandMessage() {
        return ColorUtils.colorize(messages.getString("messages.player-no-island"));
    }

    public Component getPlayerAlreadyHasIslandMessage() {
        return ColorUtils.colorize(messages.getString("messages.player-already-has-island"));
    }

    public Component getPlayerMustInIslandSetHomeMessage() {
        return ColorUtils.colorize(messages.getString("messages.player-must-in-island-set-home"));
    }

    public Component getPlayerMustInIslandSetWarpMessage() {
        return ColorUtils.colorize(messages.getString("messages.player-must-in-island-set-warp"));
    }

    public Component getPlayerInviteSentMessage(String target) {
        return ColorUtils.colorize(Objects.requireNonNull(messages.getString("messages.player-invite-sent")).replace("{player}", target));
    }

    public Component getPlayerInviteReceiveMessage(String inviter) {
        return ColorUtils.colorize(Objects.requireNonNull(messages.getString("messages.player-invite-receive")).replace("{player}", inviter));
    }

    public Component getPlayerInviteAcceptedNotifyMessage(String player) {
        return ColorUtils.colorize(Objects.requireNonNull(messages.getString("messages.player-invite-accepted-notify")).replace("{player}", player));
    }

    public Component getPlayerInviteRejectedNotifyMessage(String player) {
        return ColorUtils.colorize(Objects.requireNonNull(messages.getString("messages.player-invite-rejected-notify")).replace("{player}", player));
    }

    public Component getPlayerInviteAcceptedMessage() {
        return ColorUtils.colorize(messages.getString("messages.player-invite-accepted"));
    }

    public Component getPlayerInviteRejectedMessage() {
        return ColorUtils.colorize(messages.getString("messages.player-invite-rejected"));
    }

    public Component getPlayerAlreadyInvitedMessage(String player) {
        return ColorUtils.colorize(Objects.requireNonNull(messages.getString("messages.player-already-invited")).replace("{player}", player));
    }

    public Component getPlayerNoPendingInviteMessage() {
        return ColorUtils.colorize(messages.getString("messages.player-no-pending-invite"));
    }

    public Component getPlayerRemoveMemberSuccessMessage(String player) {
        return ColorUtils.colorize(Objects.requireNonNull(messages.getString("messages.player-remove-member-success")).replace("{player}", player));
    }

    public Component getPlayerCannotRemoveSelfMessage() {
        return ColorUtils.colorize(messages.getString("messages.player-cannot-remove-self"));
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

    public Component getPlayerSetOwnerSuccessMessage(String player) {
        return ColorUtils.colorize(Objects.requireNonNull(messages.getString("messages.player-set-owner-success")).replace("{player}", player));
    }

    public Component getPlayerAlreadyOwnerMessage(String player) {
        return ColorUtils.colorize(Objects.requireNonNull(messages.getString("messages.player-already-owner")).replace("{player}", player));
    }

    public Component getPlayerCannotLeaveAsOwnerMessage() {
        return ColorUtils.colorize(messages.getString("messages.player-cannot-leave-as-owner"));
    }

    public Component getPlayerLeaveSuccessMessage() {
        return ColorUtils.colorize(messages.getString("messages.player-leave-success"));
    }

    public Component getPlayerExpelSuccessMessage(String player) {
        return ColorUtils.colorize(Objects.requireNonNull(messages.getString("messages.player-expel-success")).replace("{player}", player));
    }

    public Component getPlayerBanSuccessMessage(String player) {
        return ColorUtils.colorize(Objects.requireNonNull(messages.getString("messages.player-ban-success")).replace("{player}", player));
    }

    public Component getPlayerUnbanSuccessMessage(String player) {
        return ColorUtils.colorize(Objects.requireNonNull(messages.getString("messages.player-unban-success")).replace("{player}", player));
    }

    public Component getPlayerAlreadyBannedMessage(String player) {
        return ColorUtils.colorize(Objects.requireNonNull(messages.getString("messages.player-already-banned")).replace("{player}", player));
    }

    public Component getPlayerNotBannedMessage(String player) {
        return ColorUtils.colorize(Objects.requireNonNull(messages.getString("messages.player-not-banned")).replace("{player}", player));
    }

    public Component getPlayerCannotBanIslandPlayerMessage() {
        return ColorUtils.colorize(messages.getString("messages.player-cannot-ban-island-player"));
    }

    public Component getPlayerCoopSuccessMessage(String player) {
        return ColorUtils.colorize(Objects.requireNonNull(messages.getString("messages.player-coop-success")).replace("{player}", player));
    }

    public Component getPlayerUncoopSuccessMessage(String player) {
        return ColorUtils.colorize(Objects.requireNonNull(messages.getString("messages.player-uncoop-success")).replace("{player}", player));
    }

    public Component getPlayerAlreadyCoopedMessage(String player) {
        return ColorUtils.colorize(Objects.requireNonNull(messages.getString("messages.player-already-cooped")).replace("{player}", player));
    }

    public Component getPlayerNotCoopedMessage(String player) {
        return ColorUtils.colorize(Objects.requireNonNull(messages.getString("messages.player-not-cooped")).replace("{player}", player));
    }

    public Component getPlayerCannotCoopIslandPlayerMessage() {
        return ColorUtils.colorize(messages.getString("messages.player-cannot-coop-island-player"));
    }

    public Component getPlayerNoItemInHandMessage() {
        return ColorUtils.colorize(messages.getString("messages.no-item-in-hand"));
    }

    public Component getPlayerBlockValueCommandMessage(String block, int value) {
        return ColorUtils.colorize(Objects.requireNonNull(messages.getString("messages.block-value")).replace("{block}", block).replace("{value}", String.valueOf(value)));
    }

    public Component getPlayerNotOnlineMessage(String player) {
        return ColorUtils.colorize(Objects.requireNonNull(messages.getString("messages.player-not-online")).replace("{player}", player));
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

    public Component getIslandInfoOwnerMessage(String owner) {
        return ColorUtils.colorize(Objects.requireNonNull(messages.getString("messages.island-info-owner")).replace("{owner}", owner));
    }

    public Component getIslandInfoMembersMessage(String members) {
        return ColorUtils.colorize(Objects.requireNonNull(messages.getString("messages.island-info-members")).replace("{members}", members));
    }

    public Component getIslandInfoNoMembersMessage() {
        return ColorUtils.colorize(messages.getString("messages.island-info-no-members"));
    }

    // Top
    public Component getTopIslandsHeaderMessage() {
        return ColorUtils.colorize(messages.getString("messages.top-islands-header"));
    }

    public Component getTopIslandMessage(int rank, String owner, String members, int level) {
        return ColorUtils.colorize(Objects.requireNonNull(messages.getString("messages.top-islands-message")).replace("{rank}", String.valueOf(rank)).replace("{owner}", owner).replace("{members}", members).replace("{level}", String.valueOf(level)));
    }

    public Component getNoIslandsFoundMessage() {
        return ColorUtils.colorize(messages.getString("messages.top-islands-no-island"));
    }

    // Ban
    public Component getBannedPlayersHeaderMessage() {
        return ColorUtils.colorize(messages.getString("messages.banned-players-header"));
    }

    public Component getBannedPlayerMessage(String player) {
        return ColorUtils.colorize(Objects.requireNonNull(messages.getString("messages.banned-player-message")).replace("{player}", player));
    }

    public Component getNoBannedPlayersMessage() {
        return ColorUtils.colorize(messages.getString("messages.banned-player-no-banned"));
    }

    // Coop
    public Component getCoopedPlayersHeaderMessage() {
        return ColorUtils.colorize(messages.getString("messages.cooped-players-header"));
    }

    public Component getCoopedPlayerMessage(String player) {
        return ColorUtils.colorize(Objects.requireNonNull(messages.getString("messages.cooped-player-message")).replace("{player}", player));
    }

    public Component getNoCoopedPlayersMessage() {
        return ColorUtils.colorize(messages.getString("messages.cooped-player-no-cooped"));
    }

    public Component getBlockLimitMessage(String block) {
        return ColorUtils.colorize(Objects.requireNonNull(messages.getString("messages.block-limit-reached")).replace("{block}", block));
    }

    // Help
    public Component getPlayerHelpHeader() {
        return ColorUtils.colorize(Objects.requireNonNull(messages.getString("messages.player-help-header")));
    }

    public Component getPlayerHelpEntry(String command, String syntax, String description) {
        return ColorUtils.colorize(Objects.requireNonNull(messages.getString("messages.player-help-entry")).replace("{command}", command).replace("{syntax}", syntax).replace("{description}", description));
    }

    public Component getPlayerHelpFooter(int page, int total) {
        return ColorUtils.colorize(Objects.requireNonNull(messages.getString("messages.player-help-footer")).replace("{prev}", String.valueOf(page - 1)).replace("{next}", String.valueOf(page + 1)).replace("{page}", String.valueOf(page)).replace("{total}", String.valueOf(total)));
    }

    public Component getAdminHelpHeader() {
        return ColorUtils.colorize(Objects.requireNonNull(messages.getString("messages.admin-help-header")));
    }

    public Component getAdminHelpEntry(String command, String syntax, String description) {
        return ColorUtils.colorize(Objects.requireNonNull(messages.getString("messages.admin-help-entry")).replace("{command}", command).replace("{syntax}", syntax).replace("{description}", description));
    }

    public Component getAdminHelpFooter(int page, int total) {
        return ColorUtils.colorize(Objects.requireNonNull(messages.getString("messages.admin-help-footer")).replace("{prev}", String.valueOf(page - 1)).replace("{next}", String.valueOf(page + 1)).replace("{page}", String.valueOf(page)).replace("{total}", String.valueOf(total)));
    }
}
