package org.me.newsky.world;

import com.infernalsuite.asp.api.loaders.SlimeLoader;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;

import java.io.IOException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class IslandWorldLoaderTest {
    private static final String NAME = "island-test";

    private final SlimeLoader database = mock(SlimeLoader.class);
    private final IslandWorldLoader loader = new IslandWorldLoader(database);
    private final byte[] autosave = {1};
    private final byte[] finalSave = {2};

    @Test
    void writesAfterTheFinalSaveAreDropped() throws IOException {
        loader.saveWorld(NAME, autosave);
        loader.saveFinal(NAME, finalSave);
        loader.saveWorld(NAME, autosave);

        InOrder order = inOrder(database);
        order.verify(database).saveWorld(NAME, autosave);
        order.verify(database).saveWorld(NAME, finalSave);
        verifyNoMoreInteractions(database);
    }

    @Test
    void finalSaveWaitsForAnInFlightWrite() throws Exception {
        CountDownLatch writing = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        doAnswer(call -> {
            writing.countDown();
            assertTrue(release.await(10, TimeUnit.SECONDS));
            return null;
        }).when(database).saveWorld(NAME, autosave);

        Thread pool = new Thread(() -> assertDoesNotThrow(() -> loader.saveWorld(NAME, autosave)));
        pool.start();
        assertTrue(writing.await(10, TimeUnit.SECONDS));

        Thread unload = new Thread(() -> assertDoesNotThrow(() -> loader.saveFinal(NAME, finalSave)));
        unload.start();
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
        while (unload.getState() != Thread.State.BLOCKED && System.nanoTime() < deadline) {
            Thread.onSpinWait();
        }
        assertEquals(Thread.State.BLOCKED, unload.getState());
        verify(database, never()).saveWorld(NAME, finalSave);

        release.countDown();
        pool.join(10_000);
        unload.join(10_000);
        InOrder order = inOrder(database);
        order.verify(database).saveWorld(NAME, autosave);
        order.verify(database).saveWorld(NAME, finalSave);
    }

    @Test
    void failedFinalSaveKeepsTheLoaderOpen() throws IOException {
        IOException failure = new IOException("database down");
        doThrow(failure).when(database).saveWorld(NAME, finalSave);

        assertSame(failure, assertThrows(IOException.class, () -> loader.saveFinal(NAME, finalSave)));
        loader.saveWorld(NAME, autosave);

        verify(database).saveWorld(NAME, autosave);
    }
}
