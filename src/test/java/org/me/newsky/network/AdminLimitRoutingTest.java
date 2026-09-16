package org.me.newsky.network;

import org.json.JSONObject;
import org.junit.jupiter.api.Test;
import org.me.newsky.NewSky;
import org.me.newsky.cluster.IslandRegistry;
import org.me.newsky.messaging.CrossServerMessenger;
import org.me.newsky.model.Actor;
import org.me.newsky.thread.BukkitAsyncExecutor;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.*;

class AdminLimitRoutingTest {
    @Test
    void remoteHomeAndMembershipWritesPreserveTheActor() {
        UUID island = UUID.randomUUID();
        UUID player = UUID.randomUUID();
        NewSky plugin = mock(NewSky.class);
        when(plugin.getBukkitAsyncExecutor()).thenReturn(mock(BukkitAsyncExecutor.class));
        IslandRegistry registry = mock(IslandRegistry.class);
        when(registry.resolveHost(island)).thenReturn(new IslandRegistry.Host("remote", true));
        CrossServerMessenger messenger = mock(CrossServerMessenger.class);
        IslandDistributor distributor = new IslandDistributor(plugin, null, null, null, registry, null, null, messenger, "local");

        for (Actor actor : new Actor[]{new Actor.Bypass("console"), new Actor.Player(player)}) {
            when(messenger.request(eq("remote"), anyString(), any())).thenAnswer(call -> {
                JSONObject payload = call.getArgument(2);
                assertEquals(actor, Actor.fromJson(payload));
                assertEquals(island.toString(), payload.getString("islandUuid"));
                assertEquals(player.toString(), payload.getString("playerUuid"));
                return CompletableFuture.completedFuture(new JSONObject());
            });
            distributor.addMember(actor, island, player, "member").join();
            distributor.setHome(actor, island, player, "farm", "0,70,0,0,0").join();
        }
        verify(messenger, times(2)).request(eq("remote"), eq(IslandDistributor.ACTION_ISLAND_MEMBER_ADD), any());
        verify(messenger, times(2)).request(eq("remote"), eq(IslandDistributor.ACTION_ISLAND_HOME_SET), any());
    }
}
