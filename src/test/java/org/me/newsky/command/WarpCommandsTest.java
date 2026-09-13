package org.me.newsky.command;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.me.newsky.NewSky;
import org.me.newsky.api.AdminActions;
import org.me.newsky.api.NewSkyAPI;
import org.me.newsky.api.PlayerActions;
import org.me.newsky.command.admin.AdminDelWarpCommand;
import org.me.newsky.command.admin.AdminSetWarpCommand;
import org.me.newsky.command.admin.AdminWarpCommand;
import org.me.newsky.command.player.PlayerDelWarpCommand;
import org.me.newsky.command.player.PlayerSetWarpCommand;
import org.me.newsky.command.player.PlayerWarpCommand;
import org.me.newsky.config.ConfigHandler;
import org.me.newsky.util.IslandUtils;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class WarpCommandsTest {
    private final UUID playerId = UUID.randomUUID();
    private final UUID targetId = UUID.randomUUID();
    private final UUID islandId = UUID.randomUUID();
    private final NewSky plugin = mock(NewSky.class);
    private final NewSkyAPI api = mock(NewSkyAPI.class);
    private final ConfigHandler config = mock(ConfigHandler.class);
    private final Player player = mock(Player.class);
    private final PlayerActions playerActions = mock(PlayerActions.class);
    private final AdminActions adminActions = mock(AdminActions.class);

    @BeforeEach
    void setUp() {
        when(player.getUniqueId()).thenReturn(playerId);
        World world = mock(World.class);
        when(world.getName()).thenReturn(IslandUtils.parseIslandName(islandId));
        when(player.getLocation()).thenReturn(new Location(world, 1, 2, 3));
        when(api.player(playerId)).thenReturn(playerActions);
        when(api.admin(player)).thenReturn(adminActions);
        when(api.getPlayerUuid("Member")).thenReturn(CompletableFuture.completedFuture(Optional.of(targetId)));
        when(api.getIslandUuid(targetId)).thenReturn(CompletableFuture.completedFuture(islandId));
        when(api.getIslandUuid(playerId)).thenReturn(CompletableFuture.completedFuture(islandId));
        when(api.getWarpNames(islandId)).thenReturn(CompletableFuture.completedFuture(Set.of("shop", "farm")));
    }

    @Test
    void playerWarpResolvesNamedMembersIsland() {
        when(playerActions.warp(islandId, "shop")).thenReturn(CompletableFuture.completedFuture(null));
        new PlayerWarpCommand(plugin, api, config).execute(player, new String[]{"warp", "Member", "shop"});
        verify(playerActions).warp(islandId, "shop");
    }

    @Test
    void adminSetAndDeleteResolveNamedMembersIsland() {
        String world = IslandUtils.parseIslandName(islandId);
        when(adminActions.setWarp(islandId, "shop", world, 1, 2, 3, 0, 0)).thenReturn(CompletableFuture.completedFuture(null));
        when(adminActions.deleteWarp(islandId, "shop")).thenReturn(CompletableFuture.completedFuture(null));
        new AdminSetWarpCommand(plugin, api, config).execute(player, new String[]{"setwarp", "Member", "shop"});
        new AdminDelWarpCommand(plugin, api, config).execute(player, new String[]{"delwarp", "Member", "shop"});
        verify(adminActions).setWarp(islandId, "shop", world, 1, 2, 3, 0, 0);
        verify(adminActions).deleteWarp(islandId, "shop");
    }

    @Test
    void adminWarpResolvesIslandForSelfAndNamedTraveler() {
        when(adminActions.warp(islandId, "shop", playerId)).thenReturn(CompletableFuture.completedFuture(null));
        when(adminActions.warp(islandId, "shop", targetId)).thenReturn(CompletableFuture.completedFuture(null));
        AdminWarpCommand command = new AdminWarpCommand(plugin, api, config);
        command.execute(player, new String[]{"warp", "Member", "shop"});
        command.execute(player, new String[]{"warp", "Member", "shop", "Member"});
        verify(adminActions).warp(islandId, "shop", playerId);
        verify(adminActions).warp(islandId, "shop", targetId);
    }

    @Test
    void allWarpCompletionsResolveIslandBeforeListingNames() {
        for (AsyncTabComplete command : List.of(
                new PlayerWarpCommand(plugin, api, config),
                new AdminWarpCommand(plugin, api, config),
                new AdminSetWarpCommand(plugin, api, config),
                new AdminDelWarpCommand(plugin, api, config))) {
            assertEquals(List.of("shop"), command.tabCompleteAsync(player, "is", new String[]{"warp", "Member", "s"}).join());
        }
        for (AsyncTabComplete command : List.of(
                new PlayerSetWarpCommand(plugin, api, config),
                new PlayerDelWarpCommand(plugin, api, config))) {
            assertEquals(List.of("shop"), command.tabCompleteAsync(player, "is", new String[]{"warp", "s"}).join());
        }
        verify(api, times(6)).getWarpNames(islandId);
        verify(api, never()).getWarpNames(targetId);
        verify(api, never()).getWarpNames(playerId);
    }
}
