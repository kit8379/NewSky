package org.me.newsky.listener;

import org.bukkit.GameRule;
import org.bukkit.World;
import org.bukkit.WorldBorder;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.world.WorldLoadEvent;
import org.me.newsky.NewSky;
import org.me.newsky.config.ConfigHandler;
import org.me.newsky.util.IslandUtils;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

public class WorldLoadListener implements Listener {

    private static final Map<String, GameRule<?>> GAME_RULES;

    static {
        Map<String, GameRule<?>> map = new HashMap<>();
        for (GameRule<?> rule : GameRule.values()) {
            map.put(rule.getName().toLowerCase(), rule);
        }
        GAME_RULES = Collections.unmodifiableMap(map);
    }

    private final NewSky plugin;
    private final ConfigHandler config;

    public WorldLoadListener(NewSky plugin, ConfigHandler config) {
        this.plugin = plugin;
        this.config = config;
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onWorldLoad(WorldLoadEvent event) {
        World world = event.getWorld();
        String name = world.getName();
        if (!IslandUtils.isIslandWorld(name)) {
            applyGameRules(world);
            applyWorldBorder(world);
            calculateIslandLevel(name);
        }
    }

    @SuppressWarnings("unchecked")
    private void applyGameRules(World world) {
        Map<String, Object> gamerules = config.getIslandGameRules();
        if (gamerules == null || gamerules.isEmpty()) return;

        for (Map.Entry<String, Object> entry : gamerules.entrySet()) {
            String key = entry.getKey().toLowerCase();
            Object value = entry.getValue();
            GameRule<?> rule = GAME_RULES.get(key);
            if (rule == null) {
                plugin.warning("Invalid gamerule: " + key);
                continue;
            }

            try {
                if (value instanceof Boolean) {
                    world.setGameRule((GameRule<Boolean>) rule, (Boolean) value);
                } else if (value instanceof Number) {
                    world.setGameRule((GameRule<Integer>) rule, ((Number) value).intValue());
                } else {
                    plugin.warning("Unsupported gamerule type: " + key + " = " + value);
                    continue;
                }
                plugin.debug("WorldLoadListener", "Set gamerule " + key + " = " + value);
            } catch (Exception ex) {
                plugin.warning("Failed to set gamerule " + key + ": " + ex.getMessage());
            }
        }
    }

    private void applyWorldBorder(World world) {
        WorldBorder border = world.getWorldBorder();
        border.setCenter(0.0, 0.0);
        border.setSize(config.getIslandSize());
        border.setWarningDistance(0);
        border.setDamageAmount(0.1);
        border.setDamageBuffer(1.0);
        plugin.debug("WorldLoadListener", "Set world border for " + world.getName());
    }

    private void calculateIslandLevel(String worldName) {
        plugin.getApi().calIslandLevel(IslandUtils.nameToUUID(worldName));
        plugin.debug("WorldLoadListener", "Force-calculated island level for " + worldName);
    }
}
