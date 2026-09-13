package org.me.newsky.listener;

import com.destroystokyo.paper.event.player.PlayerPostRespawnEvent;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.me.newsky.NewSky;
import org.me.newsky.model.Island;
import org.me.newsky.snapshot.IslandSnapshot;
import org.me.newsky.util.IslandUtils;
import org.me.newsky.util.LocationUtils;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

public class IslandRespawnListener implements Listener {

    private final NewSky plugin;
    private final IslandSnapshot islandSnapshot;
    private final Set<UUID> pendingLobby = new HashSet<>();

    public IslandRespawnListener(NewSky plugin, IslandSnapshot islandSnapshot) {
        this.plugin = plugin;
        this.islandSnapshot = islandSnapshot;
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onRespawn(PlayerRespawnEvent event) {
        Player player = event.getPlayer();
        UUID playerUuid = player.getUniqueId();
        pendingLobby.remove(playerUuid);
        if (event.getRespawnReason() != PlayerRespawnEvent.RespawnReason.DEATH) {
            return;
        }

        // Paper fires this before moving the player to the respawn world.
        String worldName = player.getWorld().getName();
        UUID islandUuid = IslandUtils.parseIslandUuid(worldName);
        if (islandUuid == null) {
            return;
        }

        Island island = islandSnapshot.get(islandUuid);
        if (island == null) {
            plugin.severe("Cannot resolve respawn for " + playerUuid + ": island snapshot unavailable for " + islandUuid);
            return;
        }

        boolean member = island.getOwner().equals(playerUuid) || island.getMembers().contains(playerUuid);
        boolean banned = island.getBans().contains(playerUuid);
        boolean lockedOut = island.isLock() && !member && !island.getCoops().contains(playerUuid);
        if (!player.isOp() && (banned || lockedOut)) {
            pendingLobby.add(playerUuid);
            return;
        }

        String location = member ? island.getDefaultHomes().get(playerUuid) : island.getDefaultWarp();
        if (location == null) {
            if (member) {
                plugin.severe("Cannot resolve respawn for " + playerUuid + ": default home missing on island " + islandUuid);
            } else {
                pendingLobby.add(playerUuid);
            }
            return;
        }

        // Only override this respawn; beds and anchors never replace the island defaults.
        event.setRespawnLocation(LocationUtils.stringToLocation(worldName, location));
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPostRespawn(PlayerPostRespawnEvent event) {
        UUID playerUuid = event.getPlayer().getUniqueId();
        if (!pendingLobby.remove(playerUuid) || !event.getPlayer().isOnline()) {
            return;
        }

        plugin.getApi().lobby(playerUuid).exceptionally(error -> {
            plugin.severe("Failed to send respawned player " + playerUuid + " to lobby", error);
            return null;
        });
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        pendingLobby.remove(event.getPlayer().getUniqueId());
    }
}
