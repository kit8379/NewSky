package org.me.newsky.listener;

import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockFormEvent;
import org.me.newsky.NewSky;
import org.me.newsky.island.CobblestoneGeneratorHandler;
import org.me.newsky.island.UpgradeHandler;
import org.me.newsky.model.Island;
import org.me.newsky.util.IslandUtils;
import snapshot.IslandSnapshot;

import java.util.Map;
import java.util.UUID;

public final class CobblestoneGeneratorListener implements Listener {

    private final NewSky plugin;
    private final IslandSnapshot islandSnapshot;
    private final CobblestoneGeneratorHandler generatorHandler;

    public CobblestoneGeneratorListener(NewSky plugin, IslandSnapshot islandSnapshot, CobblestoneGeneratorHandler generatorHandler) {
        this.plugin = plugin;
        this.islandSnapshot = islandSnapshot;
        this.generatorHandler = generatorHandler;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBlockForm(BlockFormEvent event) {
        Block block = event.getBlock();
        World world = block.getWorld();

        if (!IslandUtils.isIslandWorld(world.getName())) {
            return;
        }

        if (event.getNewState().getType() != Material.COBBLESTONE) {
            return;
        }

        UUID islandUuid = IslandUtils.nameToUUID(world.getName());

        Island island = islandSnapshot.get(islandUuid);
        if (island == null) {
            return;
        }

        Map<String, Integer> upgrades = island.getUpgrades();
        int genLevel = upgrades.getOrDefault(UpgradeHandler.UPGRADE_GENERATOR_RATES, 1);

        Material result = generatorHandler.roll(genLevel);
        event.getNewState().setType(result);

        plugin.debug("CobblestoneGeneratorListener", "Cobblestone generator roll: island=" + islandUuid + ", level=" + genLevel + ", result=" + result + ", location=" + block.getLocation());
    }
}