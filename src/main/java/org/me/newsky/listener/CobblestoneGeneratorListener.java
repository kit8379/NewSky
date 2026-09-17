package org.me.newsky.listener;

import org.bukkit.Material;
import org.bukkit.block.BlockState;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockFormEvent;
import org.me.newsky.island.CobblestoneGeneratorHandler;
import org.me.newsky.model.Island;
import org.me.newsky.snapshot.IslandSnapshot;
import org.me.newsky.util.IslandUtils;

import java.util.UUID;

/**
 * Cobblestone formed by lava meeting water on an island becomes the block its generator-rates
 * upgrade level rolls. Runs after protection has had its say, so a form outside the island
 * boundary is already cancelled here. An unavailable snapshot leaves vanilla cobblestone.
 */
public class CobblestoneGeneratorListener implements Listener {

    private final IslandSnapshot islandSnapshot;
    private final CobblestoneGeneratorHandler generator;

    public CobblestoneGeneratorListener(IslandSnapshot islandSnapshot, CobblestoneGeneratorHandler generator) {
        this.islandSnapshot = islandSnapshot;
        this.generator = generator;
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBlockForm(BlockFormEvent event) {
        BlockState formed = event.getNewState();
        if (formed.getType() != Material.COBBLESTONE) {
            return;
        }

        UUID islandUuid = IslandUtils.parseIslandUuid(event.getBlock().getWorld().getName());
        if (islandUuid == null) {
            return;
        }

        Island island = islandSnapshot.get(islandUuid);
        if (island == null) {
            return;
        }

        Material rolled = generator.roll(island.getGeneratorLevel());
        if (rolled != Material.COBBLESTONE) {
            formed.setType(rolled);
        }
    }
}
