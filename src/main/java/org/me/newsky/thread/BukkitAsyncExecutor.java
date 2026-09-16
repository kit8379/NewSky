package org.me.newsky.thread;

import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.NotNull;

import java.util.concurrent.Executor;

public class BukkitAsyncExecutor implements Executor {

    private final Plugin plugin;

    public BukkitAsyncExecutor(Plugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public void execute(@NotNull Runnable command) {
        // runNow hands off to Paper's async pool immediately. The legacy runTaskAsynchronously
        // is tick-driven: it waits for the next main-thread heartbeat, so every hop through this
        // executor would cost up to a tick of latency.
        Bukkit.getAsyncScheduler().runNow(plugin, task -> command.run());
    }
}
