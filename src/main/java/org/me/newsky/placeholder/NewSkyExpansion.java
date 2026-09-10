package org.me.newsky.placeholder;

import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.OfflinePlayer;
import org.bukkit.scheduler.BukkitTask;
import org.jetbrains.annotations.NotNull;
import org.me.newsky.NewSky;
import org.me.newsky.api.NewSkyAPI;
import org.me.newsky.exceptions.IslandDoesNotExistException;
import org.me.newsky.model.IslandTop;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.function.LongSupplier;
import java.util.function.Supplier;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public final class NewSkyExpansion extends PlaceholderExpansion {

    private static final long REFRESH_NANOS = TimeUnit.SECONDS.toNanos(5);
    private static final long RANK_REFRESH_NANOS = TimeUnit.SECONDS.toNanos(30);
    private static final long EXPIRE_NANOS = TimeUnit.MINUTES.toNanos(1);
    private static final Pattern TOP = Pattern.compile("top_([1-9][0-9]*)_(owner|level|uuid|members)");
    private static final Map<String, String> NO_ISLAND = Map.ofEntries(
            Map.entry("island_level", "0"), Map.entry("island_members", "0"),
            Map.entry("island_owner", ""), Map.entry("island_uuid", ""),
            Map.entry("has_island", "false"), Map.entry("island_role", "none"),
            Map.entry("island_owner_uuid", ""), Map.entry("island_lock", "false"),
            Map.entry("island_pvp", "false"), Map.entry("island_players", "0"),
            Map.entry("island_members_list", ""), Map.entry("island_coops", "0"),
            Map.entry("island_coops_list", ""), Map.entry("island_bans", "0"),
            Map.entry("island_bans_list", ""), Map.entry("island_rank", "0"));

    private final NewSky plugin;
    private final NewSkyAPI api;
    private final LongSupplier clock;
    private final Cache<UUID, UUID> playerIslands = new Cache<>();
    private final Cache<UUID, UUID> owners = new Cache<>();
    private final Cache<String, Set<UUID>> playerSets = new Cache<>();
    private final Cache<String, String> values = new Cache<>();
    private final Cache<Set<UUID>, Map<UUID, String>> names = new Cache<>();
    private final List<Cache<?, ?>> caches = List.of(playerIslands, owners, playerSets, values, names);
    private final BukkitTask cleanupTask;
    private Cached<Map<String, String>> topCache;
    private int requestedTopLimit;
    private int loadedTopLimit;

    public NewSkyExpansion(NewSky plugin, NewSkyAPI api) {
        this(plugin, api, System::nanoTime);
    }

    NewSkyExpansion(NewSky plugin, NewSkyAPI api, LongSupplier clock) {
        this.plugin = plugin;
        this.api = api;
        this.clock = clock;
        cleanupTask = plugin.getServer().getScheduler().runTaskTimerAsynchronously(plugin, () -> {
            long now = clock.getAsLong();
            caches.forEach(cache -> cache.entries.entrySet().removeIf(entry -> entry.getValue().data().isDone()
                    && now - entry.getValue().requestedAt() >= EXPIRE_NANOS));
        }, 1200L, 1200L);
    }

    @Override
    public @NotNull String getIdentifier() {
        return "newsky";
    }

    @Override
    public @NotNull String getAuthor() {
        return String.join(", ", plugin.getPluginMeta().getAuthors());
    }

    @Override
    public @NotNull String getVersion() {
        return plugin.getPluginMeta().getVersion();
    }

    @Override
    public boolean persist() {
        return true;
    }

    @Override
    public String onRequest(OfflinePlayer player, @NotNull String params) {
        String key = params.toLowerCase(Locale.ROOT);
        Matcher top = TOP.matcher(key);
        if (top.matches()) {
            int position;
            try {
                position = Integer.parseInt(top.group(1));
            } catch (NumberFormatException error) {
                return null;
            }
            return topValue(position, key);
        }
        if (!NO_ISLAND.containsKey(key)) {
            return null;
        }
        if (player == null) {
            return "";
        }

        UUID playerUuid = player.getUniqueId();
        // Chain only the requested data. An unfinished first load returns immediately via getNow.
        return playerIslands.get(playerUuid, () -> api.getIslandUuid(playerUuid))
                .thenCompose(islandUuid -> islandValue(islandUuid, playerUuid, key))
                .exceptionally(error -> cause(error) instanceof IslandDoesNotExistException ? NO_ISLAND.get(key) : "")
                .getNow("");
    }

    private CompletableFuture<String> islandValue(UUID islandUuid, UUID playerUuid, String key) {
        return switch (key) {
            case "has_island" -> CompletableFuture.completedFuture("true");
            case "island_uuid" -> CompletableFuture.completedFuture(islandUuid.toString());
            case "island_level" -> values.get("level:" + islandUuid,
                    () -> api.getIslandLevel(islandUuid).thenApply(String::valueOf));
            case "island_lock" -> values.get("lock:" + islandUuid,
                    () -> api.isIslandLock(islandUuid).thenApply(String::valueOf));
            case "island_pvp" -> values.get("pvp:" + islandUuid,
                    () -> api.isIslandPvp(islandUuid).thenApply(String::valueOf));
            case "island_rank" -> values.get("rank:" + islandUuid, RANK_REFRESH_NANOS,
                    () -> api.getIslandRank(islandUuid).thenApply(String::valueOf));
            case "island_owner_uuid", "island_role", "island_owner" ->
                    owners.get(islandUuid, () -> api.getIslandOwner(islandUuid)).thenCompose(owner -> switch (key) {
                        case "island_owner_uuid" -> CompletableFuture.completedFuture(owner.toString());
                        case "island_role" -> CompletableFuture.completedFuture(owner.equals(playerUuid) ? "owner" : "member");
                        default -> playerNames(Set.of(owner)).thenApply(resolved -> resolved.getOrDefault(owner, owner.toString()));
                    });
            case "island_members", "island_players", "island_members_list" ->
                    playerSets.get("members:" + islandUuid, () -> api.getIslandMembers(islandUuid))
                            .thenCompose(members -> key.equals("island_members_list") ? playerList(members)
                                    : CompletableFuture.completedFuture(Integer.toString(members.size() + (key.equals("island_players") ? 1 : 0))));
            case "island_coops", "island_coops_list" ->
                    playerSets.get("coops:" + islandUuid, () -> api.getIslandCoops(islandUuid))
                            .thenCompose(coops -> key.endsWith("_list") ? playerList(coops)
                                    : CompletableFuture.completedFuture(Integer.toString(coops.size())));
            case "island_bans", "island_bans_list" ->
                    playerSets.get("bans:" + islandUuid, () -> api.getIslandBans(islandUuid))
                            .thenCompose(bans -> key.endsWith("_list") ? playerList(bans)
                                    : CompletableFuture.completedFuture(Integer.toString(bans.size())));
            default -> throw new IllegalArgumentException("Unknown placeholder: " + key);
        };
    }

    private CompletableFuture<Map<UUID, String>> playerNames(Set<UUID> players) {
        if (players.isEmpty()) {
            return CompletableFuture.completedFuture(Map.of());
        }
        Set<UUID> key = Set.copyOf(players);
        return names.get(key, () -> api.getPlayerNames(key));
    }

    private static Throwable cause(Throwable error) {
        Throwable cause = error;
        while (cause instanceof CompletionException && cause.getCause() != null) {
            cause = cause.getCause();
        }
        return cause;
    }

    private synchronized String topValue(int position, String key) {
        requestedTopLimit = Math.max(requestedTopLimit, position);
        long now = clock.getAsLong();
        if (topCache == null || (topCache.data().isDone()
                && (loadedTopLimit < requestedTopLimit || now - topCache.requestedAt() >= RANK_REFRESH_NANOS))) {
            Map<String, String> previous = topCache == null ? Map.of() : topCache.data().getNow(Map.of());
            loadedTopLimit = requestedTopLimit;
            topCache = new Cached<>(loadTop(loadedTopLimit), previous, now);
        }
        return topCache.data().getNow(topCache.previous()).getOrDefault(key, "");
    }

    private CompletableFuture<Map<String, String>> loadTop(int limit) {
        return api.getTopIslandLevels(limit).thenCompose(islands -> {
            if (islands.isEmpty()) {
                return CompletableFuture.completedFuture(Map.<String, String>of());
            }
            Set<UUID> owners = islands.stream().map(IslandTop::getOwnerUuid).collect(Collectors.toSet());
            return api.getPlayerNames(owners).thenApply(names -> {
                Map<String, String> values = new HashMap<>();
                for (int i = 0; i < islands.size(); i++) {
                    IslandTop island = islands.get(i);
                    String prefix = "top_" + (i + 1) + "_";
                    values.put(prefix + "owner", names.getOrDefault(island.getOwnerUuid(), island.getOwnerUuid().toString()));
                    values.put(prefix + "level", Integer.toString(island.getLevel()));
                    values.put(prefix + "uuid", island.getIslandUuid().toString());
                    values.put(prefix + "members", Integer.toString(island.getMembers().size()));
                }
                return Map.copyOf(values);
            });
        }).exceptionally(error -> {
            plugin.severe("Failed to load leaderboard placeholders", error);
            return Map.of();
        });
    }

    private CompletableFuture<String> playerList(Set<UUID> players) {
        return playerNames(players).thenApply(resolved ->
                sortedList(players.stream().map(uuid -> resolved.getOrDefault(uuid, uuid.toString())).toList()));
    }

    private static String sortedList(Collection<String> values) {
        return values.stream().sorted(String.CASE_INSENSITIVE_ORDER).collect(Collectors.joining(", "));
    }

    public void stop() {
        unregister();
        cleanupTask.cancel();
        caches.forEach(cache -> cache.entries.clear());
    }

    private final class Cache<K, V> {
        private final Map<K, Cached<V>> entries = new ConcurrentHashMap<>();

        private CompletableFuture<V> get(K key, Supplier<CompletableFuture<V>> loader) {
            return get(key, REFRESH_NANOS, loader);
        }

        private CompletableFuture<V> get(K key, long refreshNanos, Supplier<CompletableFuture<V>> loader) {
            Cached<V> cached = entries.compute(key, (ignored, current) -> {
                long now = clock.getAsLong();
                if (current != null && (!current.data().isDone() || now - current.requestedAt() < refreshNanos)) {
                    return current;
                }
                V previous = current == null || current.data().isCompletedExceptionally() ? null : current.data().getNow(null);
                CompletableFuture<V> data = loader.get().whenComplete((result, error) -> {
                    if (error != null && !(cause(error) instanceof IslandDoesNotExistException)) {
                        plugin.severe("Failed to load placeholder data for " + key, cause(error));
                    }
                });
                return new Cached<>(data, previous, now);
            });
            // Keep serving the previous successful value during refresh, without blocking callers.
            return !cached.data().isDone() && cached.previous() != null
                    ? CompletableFuture.completedFuture(cached.previous()) : cached.data();
        }
    }

    private record Cached<V>(CompletableFuture<V> data, V previous, long requestedAt) {
    }
}
