package org.me.newsky.world;

import com.infernalsuite.asp.api.exceptions.UnknownWorldException;
import com.infernalsuite.asp.api.loaders.SlimeLoader;

import java.io.IOException;
import java.util.List;

/**
 * The loader of one loaded lifetime of an island world. ASP writes every save of the world
 * through it from a shared pool with no per-world ordering, so an autosave queued before the
 * unload can finish after the final save. Writes are serialized here, and once the final save
 * is written the lifetime is closed: any later write is an older snapshot and is dropped.
 */
final class IslandWorldLoader implements SlimeLoader {

    private final SlimeLoader delegate;
    private boolean closed;

    IslandWorldLoader(SlimeLoader delegate) {
        this.delegate = delegate;
    }

    @Override
    public synchronized void saveWorld(String worldName, byte[] serializedWorld) throws IOException {
        if (!closed) {
            delegate.saveWorld(worldName, serializedWorld);
        }
    }

    synchronized void saveFinal(String worldName, byte[] serializedWorld) throws IOException {
        delegate.saveWorld(worldName, serializedWorld);
        closed = true;
    }

    @Override
    public byte[] readWorld(String worldName) throws UnknownWorldException, IOException {
        return delegate.readWorld(worldName);
    }

    @Override
    public boolean worldExists(String worldName) throws IOException {
        return delegate.worldExists(worldName);
    }

    @Override
    public List<String> listWorlds() throws IOException {
        return delegate.listWorlds();
    }

    @Override
    public void deleteWorld(String worldName) throws UnknownWorldException, IOException {
        delegate.deleteWorld(worldName);
    }
}
