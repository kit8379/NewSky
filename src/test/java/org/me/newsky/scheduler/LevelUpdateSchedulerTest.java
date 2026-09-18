package org.me.newsky.scheduler;

import org.bukkit.Server;
import org.bukkit.scheduler.BukkitScheduler;
import org.bukkit.scheduler.BukkitTask;
import org.junit.jupiter.api.Test;
import org.me.newsky.NewSky;
import org.me.newsky.island.LevelHandler;
import org.mockito.ArgumentCaptor;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class LevelUpdateSchedulerTest {
    private final UUID island = UUID.randomUUID();
    private final NewSky plugin = mock(NewSky.class);
    private final BukkitScheduler bukkitScheduler = mock(BukkitScheduler.class);
    private final LevelHandler levelHandler = mock(LevelHandler.class);
    private final LevelUpdateScheduler scheduler = new LevelUpdateScheduler(plugin, levelHandler);

    LevelUpdateSchedulerTest() {
        Server server = mock(Server.class);
        when(plugin.getServer()).thenReturn(server);
        when(server.getScheduler()).thenReturn(bukkitScheduler);
        // The main thread runs completions inline here.
        when(bukkitScheduler.runTask(eq(plugin), any(Runnable.class))).thenAnswer(call -> {
            call.<Runnable>getArgument(1).run();
            return mock(BukkitTask.class);
        });
    }

    @Test
    void staleScanCompletionLeavesTheFreshRegistrationDueNow() {
        CompletableFuture<Integer> staleScan = new CompletableFuture<>();
        when(levelHandler.calIslandLevel(island)).thenReturn(staleScan, new CompletableFuture<>());
        Runnable tick = startAndCaptureTick();

        scheduler.registerIsland(island);
        tick.run();
        verify(levelHandler, times(1)).calIslandLevel(island);

        // The world unloads mid-scan and is loaded again: a fresh entry is due for its first scan.
        scheduler.unregisterIsland(island);
        scheduler.registerIsland(island);

        staleScan.complete(7);
        tick.run();

        verify(levelHandler, times(2)).calIslandLevel(island);
    }

    @Test
    void staleScanFailureLeavesTheFreshRegistrationDueNow() {
        CompletableFuture<Integer> staleScan = new CompletableFuture<>();
        when(levelHandler.calIslandLevel(island)).thenReturn(staleScan, new CompletableFuture<>());
        Runnable tick = startAndCaptureTick();

        scheduler.registerIsland(island);
        tick.run();
        scheduler.unregisterIsland(island);
        scheduler.registerIsland(island);

        staleScan.completeExceptionally(new IllegalStateException("World unloaded during level scan"));
        tick.run();

        verify(levelHandler, times(2)).calIslandLevel(island);
    }

    @Test
    void ownCompletionStillReschedulesTheIsland() {
        CompletableFuture<Integer> scan = new CompletableFuture<>();
        when(levelHandler.calIslandLevel(island)).thenReturn(scan);
        Runnable tick = startAndCaptureTick();

        scheduler.registerIsland(island);
        tick.run();
        scan.complete(7);
        tick.run();

        // Rescheduled minutes ahead, so the next poll does not start another scan.
        verify(levelHandler, times(1)).calIslandLevel(island);
    }

    private Runnable startAndCaptureTick() {
        ArgumentCaptor<Runnable> tick = ArgumentCaptor.forClass(Runnable.class);
        when(bukkitScheduler.runTaskTimer(eq(plugin), tick.capture(), anyLong(), anyLong())).thenReturn(mock(BukkitTask.class));
        scheduler.start();
        return tick.getValue();
    }
}
