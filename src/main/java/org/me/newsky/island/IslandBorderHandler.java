// IslandBorderHandler.java
package org.me.newsky.island;

import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.WorldBorder;
import org.me.newsky.NewSky;
import org.me.newsky.util.IslandUtils;

import java.util.UUID;

public final class IslandBorderHandler {

    private final NewSky plugin;
    private final UpgradeHandler upgradeHandler;

    public IslandBorderHandler(NewSky plugin, UpgradeHandler upgradeHandler) {
        this.plugin = plugin;
        this.upgradeHandler = upgradeHandler;
    }

    /**
     * World-load path: you already have the World.
     * Must be called on main thread.
     */
    public void applyBorder(World world) {
        UUID islandUuid = IslandUtils.nameToUUID(world.getName());
        apply(world, islandUuid);
    }

    /**
     * Upgrade/runtime path: you only have islandUuid.
     * Thread-safe: schedules to main thread, applies only if world is loaded.
     */
    public void applyBorder(UUID islandUuid) {
        if (Bukkit.isPrimaryThread()) {
            World world = Bukkit.getWorld(islandUuid.toString());
            if (world != null) {
                apply(world, islandUuid);
            }
            return;
        }

        Bukkit.getScheduler().runTask(plugin, () -> {
            World world = Bukkit.getWorld(islandUuid.toString());
            if (world != null) {
                apply(world, islandUuid);
            }
        });
    }

    private void apply(World world, UUID islandUuid) {
        int level = upgradeHandler.getCurrentUpgradeLevel(islandUuid, UpgradeHandler.UPGRADE_ISLAND_SIZE);
        int islandSize = upgradeHandler.getIslandSize(level);

        WorldBorder border = world.getWorldBorder();
        border.setCenter(0.0, 0.0);
        border.setSize(islandSize);
        border.setWarningDistance(0);
        border.setDamageAmount(0.1);
        border.setDamageBuffer(1.0);

        plugin.debug("IslandBorderHandler", "Set world border for " + world.getName() + " size=" + islandSize + " level=" + level);
    }
}
