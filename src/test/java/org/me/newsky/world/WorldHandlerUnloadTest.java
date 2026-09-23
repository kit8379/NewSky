package org.me.newsky.world;

import com.infernalsuite.asp.api.AdvancedSlimePaperAPI;
import com.infernalsuite.asp.api.world.SlimeWorld;
import com.infernalsuite.asp.api.world.SlimeWorldInstance;
import com.infernalsuite.asp.loaders.mysql.MysqlLoader;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.scheduler.BukkitScheduler;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.me.newsky.NewSky;
import org.me.newsky.config.ConfigHandler;
import org.me.newsky.thread.BukkitAsyncExecutor;
import org.mockito.MockedConstruction;
import org.mockito.MockedStatic;

import java.io.IOException;
import java.util.ArrayDeque;
import java.util.Queue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class WorldHandlerUnloadTest {
    private static final String NAME = "island-test";

    private final AdvancedSlimePaperAPI asp = mock(AdvancedSlimePaperAPI.class);
    private final World world = mock(World.class);
    private final SlimeWorldInstance instance = mock(SlimeWorldInstance.class);
    private final SlimeWorld finalState = mock(SlimeWorld.class);
    private final Queue<Runnable> mainTasks = new ArrayDeque<>();
    private final Queue<Runnable> asyncTasks = new ArrayDeque<>();
    private final AtomicBoolean unloaded = new AtomicBoolean();
    private boolean unloadAccepted = true;
    private final MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class);
    private final MockedStatic<AdvancedSlimePaperAPI> aspInstance = mockStatic(AdvancedSlimePaperAPI.class);
    private final MockedConstruction<MysqlLoader> loaders = mockConstruction(MysqlLoader.class);
    private final WorldHandler handler;

    WorldHandlerUnloadTest() {
        ConfigHandler config = mock(ConfigHandler.class);
        when(config.isLobbyOnly()).thenReturn(true);
        aspInstance.when(AdvancedSlimePaperAPI::instance).thenReturn(asp);
        BukkitScheduler scheduler = mock(BukkitScheduler.class);
        NewSky plugin = mock(NewSky.class);
        when(scheduler.getMainThreadExecutor(plugin)).thenReturn(mainTasks::add);
        bukkit.when(Bukkit::getScheduler).thenReturn(scheduler);
        BukkitAsyncExecutor async = mock(BukkitAsyncExecutor.class);
        doAnswer(call -> asyncTasks.add(call.getArgument(0))).when(async).execute(any());
        when(plugin.getBukkitAsyncExecutor()).thenReturn(async);

        bukkit.when(() -> Bukkit.getWorld(NAME)).thenReturn(world);
        bukkit.when(() -> Bukkit.unloadWorld(world, false)).thenAnswer(call -> {
            unloaded.set(true);
            return unloadAccepted;
        });
        when(asp.getLoadedWorld(NAME)).thenReturn(instance);
        when(instance.getSerializableCopy()).thenAnswer(call -> {
            assertFalse(unloaded.get(), "final state must be captured before the world is unloaded");
            return finalState;
        });

        handler = new WorldHandler(plugin, config);
    }

    @AfterEach
    void closeMocks() {
        loaders.close();
        aspInstance.close();
        bukkit.close();
    }

    @Test
    void finalStateIsCapturedInTheUnloadStepAndSavedBeforeTheUnloadCompletes() throws IOException {
        CompletableFuture<Void> unload = handler.unloadWorld(NAME);

        // One main-thread step both captures and unloads, so no world change can land in between.
        mainTasks.remove().run();
        assertTrue(unloaded.get());
        assertTrue(mainTasks.isEmpty());
        verify(asp, never()).saveWorld(any());
        assertFalse(unload.isDone());

        runAll(asyncTasks);
        verify(asp).saveWorld(finalState);
        verify(asp, never()).saveWorld(instance);
        assertNull(unload.join());
    }

    @Test
    void rejectedUnloadSavesNothing() throws IOException {
        unloadAccepted = false;

        CompletableFuture<Void> unload = handler.unloadWorld(NAME);
        mainTasks.remove().run();
        runAll(asyncTasks);

        CompletionException error = assertThrows(CompletionException.class, unload::join);
        assertInstanceOf(IllegalStateException.class, error.getCause());
        verify(asp, never()).saveWorld(any());
    }

    @Test
    void failedSaveFailsTheUnload() throws IOException {
        IOException failure = new IOException("database down");
        doThrow(failure).when(asp).saveWorld(finalState);

        CompletableFuture<Void> unload = handler.unloadWorld(NAME);
        mainTasks.remove().run();
        runAll(asyncTasks);

        CompletionException error = assertThrows(CompletionException.class, unload::join);
        assertSame(failure, error.getCause());
    }

    private static void runAll(Queue<Runnable> tasks) {
        while (!tasks.isEmpty()) {
            tasks.remove().run();
        }
    }
}
