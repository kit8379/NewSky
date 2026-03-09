package org.me.newsky.listener;

import org.bukkit.GameRule;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.World;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.world.WorldLoadEvent;
import org.me.newsky.NewSky;
import org.me.newsky.config.ConfigHandler;
import org.me.newsky.scheduler.LevelUpdateScheduler;
import org.me.newsky.util.IslandUtils;

import java.util.Map;
import java.util.UUID;

public class WorldLoadListener implements Listener {

    private final NewSky plugin;
    private final ConfigHandler config;
    private final LevelUpdateScheduler levelUpdateScheduler;

    public WorldLoadListener(NewSky plugin, ConfigHandler config, LevelUpdateScheduler levelUpdateScheduler) {
        this.plugin = plugin;
        this.config = config;
        this.levelUpdateScheduler = levelUpdateScheduler;
    }

    @EventHandler(ignoreCancelled = true)
    public void onWorldLoad(WorldLoadEvent event) {
        World world = event.getWorld();
        String name = world.getName();

        if (!IslandUtils.isIslandWorld(name)) {
            return;
        }

        UUID islandUuid = IslandUtils.nameToUUID(name);

        applyGameRules(world);
        registerLevelUpdate(islandUuid);
    }

    @SuppressWarnings("unchecked")
    private void applyGameRules(World world) {
        Map<String, Object> gamerules = config.getIslandGameRules();
        if (gamerules == null || gamerules.isEmpty()) return;

        for (Map.Entry<String, Object> entry : gamerules.entrySet()) {
            String keyString = "minecraft:" + entry.getKey();
            Object value = entry.getValue();

            NamespacedKey key = NamespacedKey.fromString(keyString);
            if (key == null) {
                plugin.warning("Invalid gamerule key (not a valid NamespacedKey): " + keyString);
                continue;
            }

            GameRule<?> rule = Registry.GAME_RULE.get(key);
            if (rule == null) {
                plugin.warning("Unknown gamerule (not found in registry): " + keyString);
                continue;
            }

            try {
                Class<?> type = rule.getType();

                if (type == Boolean.class) {
                    if (!(value instanceof Boolean)) {
                        plugin.warning("Unsupported gamerule value type (expected boolean): " + keyString + " = " + value);
                        continue;
                    }
                    world.setGameRule((GameRule<Boolean>) rule, (Boolean) value);
                    plugin.debug("WorldLoadListener", "Set gamerule " + keyString + " = " + value);

                } else if (type == Integer.class) {
                    if (!(value instanceof Number)) {
                        plugin.warning("Unsupported gamerule value type (expected int): " + keyString + " = " + value);
                        continue;
                    }
                    world.setGameRule((GameRule<Integer>) rule, ((Number) value).intValue());
                    plugin.debug("WorldLoadListener", "Set gamerule " + keyString + " = " + value);

                } else {
                    plugin.warning("Unsupported gamerule type: " + keyString + " (type=" + type.getName() + ")");
                }

            } catch (Exception ex) {
                plugin.warning("Failed to set gamerule " + keyString + ": " + ex.getMessage());
            }
        }
    }

    private void registerLevelUpdate(UUID islandUuid) {
        levelUpdateScheduler.registerIsland(islandUuid);
        plugin.debug("WorldLoadListener", "Registered level update scheduler for island " + IslandUtils.UUIDToName(islandUuid));
    }
}