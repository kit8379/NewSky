package org.me.newsky.network;

import org.junit.jupiter.api.Test;
import org.me.newsky.NewSky;
import org.me.newsky.cluster.IslandRegistry;
import org.me.newsky.messaging.CrossServerMessenger;
import org.me.newsky.model.Actor;
import org.me.newsky.thread.BukkitAsyncExecutor;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;

import static org.mockito.Mockito.*;

class IslandDistributorHostedRoutingTest {
    private final UUID island = UUID.randomUUID();
    private final UUID player = UUID.randomUUID();
    private final String world = "island-" + island;
    private final NewSky plugin = mock(NewSky.class);
    private final IslandRegistry registry = mock(IslandRegistry.class);
    private final IslandOperator operator = mock(IslandOperator.class);
    private final CrossServerMessenger messenger = mock(CrossServerMessenger.class);
    private final IslandDistributor distributor = new IslandDistributor(plugin, operator, null, null, registry, null, null, messenger, "local");

    IslandDistributorHostedRoutingTest() {
        when(plugin.getBukkitAsyncExecutor()).thenReturn(mock(BukkitAsyncExecutor.class));
        when(operator.isHosted(island)).thenReturn(true);
        when(operator.asHost(eq(island), any())).thenAnswer(call -> call.<Supplier<CompletableFuture<?>>>getArgument(1).get());
    }

    @Test
    void mutationOnAnIslandHostedHereSkipsTheRegistry() {
        Actor actor = new Actor.Player(player);
        when(operator.addMember(actor, island, player, "member")).thenReturn(CompletableFuture.completedFuture(null));

        distributor.addMember(actor, island, player, "member").join();

        verify(operator).addMember(actor, island, player, "member");
        verifyNoInteractions(registry, messenger);
    }

    @Test
    void expelOnAnIslandHostedHereSkipsTheRegistry() {
        Actor actor = new Actor.Player(player);
        UUID target = UUID.randomUUID();
        when(operator.expelPlayer(actor, island, target)).thenReturn(CompletableFuture.completedFuture(null));

        distributor.expelPlayer(actor, island, target).join();

        verify(operator).expelPlayer(actor, island, target);
        verifyNoInteractions(registry, messenger);
    }

    @Test
    void teleportToAnIslandHostedHereSkipsTheRegistry() {
        when(operator.prepareTeleport(player, world, "0,70,0,0,0")).thenReturn(CompletableFuture.completedFuture(true));

        distributor.teleportIsland(island, player, world, "0,70,0,0,0").join();

        verify(operator).prepareTeleport(player, world, "0,70,0,0,0");
        verifyNoInteractions(registry, messenger);
    }
}
