# Async Tab Complete Guide

This guide explains how the plugin switches from synchronous tab completion to Paper's `AsyncTabCompleter`.

## Steps

1. **Implement `AsyncTabCompleter`**
   Replace `TabExecutor` with `AsyncTabCompleter` in command classes and return `CompletableFuture`.
2. **Run completion logic asynchronously**
   Use `CompletableFuture.supplyAsync` with `BukkitAsyncExecutor` to run existing tab-complete logic off the main thread.
3. **Register with `setAsyncTabCompleter`**
   In `NewSky#registerCommands`, call `setAsyncTabCompleter` instead of `setTabCompleter` for both commands.

See `IslandPlayerCommand` and `IslandAdminCommand` for concrete examples.
