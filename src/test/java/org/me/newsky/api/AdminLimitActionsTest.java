package org.me.newsky.api;

import org.junit.jupiter.api.Test;
import org.me.newsky.island.HomeHandler;
import org.me.newsky.island.PlayerHandler;
import org.me.newsky.model.Actor;
import org.me.newsky.model.Invitation;
import org.me.newsky.util.IslandUtils;

import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static org.mockito.Mockito.*;

class AdminLimitActionsTest {
    @Test
    void adminBypassAndPlayerIdentityReachHomeAndMembershipHandlers() {
        UUID player = UUID.randomUUID();
        UUID island = UUID.randomUUID();
        String world = IslandUtils.parseIslandName(island);
        PlayerHandler members = mock(PlayerHandler.class);
        HomeHandler homes = mock(HomeHandler.class);
        when(members.addMember(any(), any(), any(), anyString())).thenReturn(CompletableFuture.completedFuture(null));
        when(homes.setHome(any(), any(), anyString(), anyString(), anyDouble(), anyDouble(), anyDouble(), anyFloat(), anyFloat())).thenReturn(CompletableFuture.completedFuture(null));
        when(members.getPendingInvite(player)).thenReturn(CompletableFuture.completedFuture(Optional.of(new Invitation(island, UUID.randomUUID()))));
        when(members.removePendingInvite(player)).thenReturn(CompletableFuture.completedFuture(null));

        AdminActions admin = new AdminActions("console", null, members, homes, null, null, null, null, null);
        admin.addMember(island, player).join();
        admin.setHome(player, "farm", world, 0, 70, 0, 0, 0).join();
        verify(members).addMember(new Actor.Bypass("console"), island, player, "member");
        verify(homes).setHome(new Actor.Bypass("console"), player, "farm", world, 0, 70, 0, 0, 0);

        PlayerActions normal = new PlayerActions(player, null, members, homes, null, null, null, null, null);
        normal.acceptInvite().join();
        normal.setHome("farm", world, 0, 70, 0, 0, 0).join();
        verify(members).addMember(new Actor.Player(player), island, player, "member");
        verify(homes).setHome(new Actor.Player(player), player, "farm", world, 0, 70, 0, 0, 0);
    }
}
