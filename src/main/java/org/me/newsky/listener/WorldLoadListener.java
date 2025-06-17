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

import java.util.Map;

public class WorldLoadListener implements Listener {

    private final NewSky plugin;
    private final ConfigHandler config;

    public WorldLoadListener(NewSky plugin, ConfigHandler config) {
        this.plugin = plugin;
        this.config = config;
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onWorldLoad(WorldLoadEvent event) {
        World world = event.getWorld();
        String worldName = world.getName();

        if (IslandUtils.isIslandWorld(worldName)) {
            Map<String, Object> gamerules = config.getIslandGameRules();
            for (Map.Entry<String, Object> entry : gamerules.entrySet()) {
                String key = entry.getKey();
                Object value = entry.getValue();
                GameRule<?> rule = getGameRuleByName(key);
                if (rule == null) {
                    plugin.warning("Invalid gamerule in config: " + key);
                    continue;
                }

                if (value instanceof Boolean) {
                    @SuppressWarnings("unchecked") GameRule<Boolean> booleanRule = (GameRule<Boolean>) rule;
                    world.setGameRule(booleanRule, (Boolean) value);
                } else if (value instanceof Number) {
                    @SuppressWarnings("unchecked") GameRule<Integer> intRule = (GameRule<Integer>) rule;
                    world.setGameRule(intRule, ((Number) value).intValue());
                } else {
                    plugin.warning("Unsupported value type for gamerule: " + key + " = " + value);
                    continue;
                }
                plugin.debug("WorldLoadListener", "Set gamerule " + key + " = " + value + " for island world: " + worldName);
            }

            // Set the world border for the island world
            WorldBorder border = world.getWorldBorder();
            border.setCenter(0.0, 0.0);
            border.setSize(config.getIslandSize());
            border.setWarningDistance(0);
            border.setDamageAmount(0.1);
            border.setDamageBuffer(1);
            plugin.debug("WorldLoadListener", "Set global world border to size " + config.getIslandSize() + " for island world: " + worldName);
        }
    }

    private GameRule<?> getGameRuleByName(String name) {
        for (GameRule<?> rule : GameRule.values()) {
            if (rule.getName().equalsIgnoreCase(name)) {
                return rule;
            }
        }
        return null;
    }
}