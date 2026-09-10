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

    @EventHandler(priority = EventPriority.MONITOR)
    public void onRespawn(PlayerRespawnEvent event) {
        Player player = event.getPlayer();
        UUID playerUuid = player.getUniqueId();
        pendingLobby.remove(playerUuid);
        if (event.getRespawnReason() != PlayerRespawnEvent.RespawnReason.DEATH) {
            return;
        }

        UUID islandUuid = IslandUtils.parseIslandUuid(player.getWorld().getName());
        if (islandUuid == null) {
            return;
        }

        Island island = islandSnapshot.get(islandUuid);
        if (island != null && (island.getOwner().equals(playerUuid) || island.getMembers().contains(playerUuid))) {
            return;
        }

        pendingLobby.add(playerUuid);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPostRespawn(PlayerPostRespawnEvent event) {
        UUID playerUuid = event.getPlayer().getUniqueId();
        if (!pendingLobby.remove(playerUuid) || !event.getPlayer().isOnline()) {
            return;
        }

        plugin.getApi().lobby(playerUuid).exceptionally(ex -> {
            plugin.severe("Error sending respawned island visitor " + playerUuid + " to lobby.", ex);
            return null;
        });
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        pendingLobby.remove(event.getPlayer().getUniqueId());
    }
}
