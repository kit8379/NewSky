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
        Bukkit.getScheduler().runTaskAsynchronously(plugin, command);
    }
}
