package org.me.newsky.island;

import org.me.newsky.NewSky;
import org.me.newsky.config.ConfigHandler;
import org.me.newsky.database.DatabaseHandler;
import org.me.newsky.exceptions.*;
import org.me.newsky.model.Invitation;
import org.me.newsky.network.IslandDistributor;
import org.me.newsky.cluster.InvitationStore;

import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

public class PlayerHandler {

    private final NewSky plugin;
    private final ConfigHandler config;
    private final DatabaseHandler database;
    private final IslandDistributor islandDistributor;
    private final InvitationStore invitationStore;

    public PlayerHandler(NewSky plugin, ConfigHandler config, DatabaseHandler database, IslandDistributor islandDistributor, InvitationStore invitationStore) {
        this.plugin = plugin;
        this.config = config;
        this.database = database;
        this.islandDistributor = islandDistributor;
        this.invitationStore = invitationStore;
    }

    public CompletableFuture<Void> addMember(UUID islandUuid, UUID playerUuid, String role) {
        return CompletableFuture.supplyAsync(() -> {
            Optional<UUID> existingIsland = database.getIslandUuid(playerUuid);
            if (existingIsland.isPresent() && !existingIsland.get().equals(islandUuid)) {
                throw new IslandAlreadyExistException();
            }

            Set<UUID> members = database.getIslandPlayers(islandUuid).keySet();
            if (members.contains(playerUuid)) {
                throw new IslandPlayerAlreadyExistsException();
            }

            UUID ownerUuid = getIslandOwnerSync(islandUuid);

            return Optional.ofNullable(database.getIslandHomes(islandUuid, ownerUuid).get("default")).orElse(config.getIslandSpawnX() + "," + config.getIslandSpawnY() + "," + config.getIslandSpawnZ() + "," + config.getIslandSpawnYaw() + "," + config.getIslandSpawnPitch());
        }, plugin.getBukkitAsyncExecutor()).thenCompose(homeLocation -> islandDistributor.addMember(islandUuid, playerUuid, role, homeLocation));
    }

    public CompletableFuture<Void> removeMember(UUID islandUuid, UUID playerUuid) {
        return CompletableFuture.runAsync(() -> {
            UUID ownerUuid = getIslandOwnerSync(islandUuid);
            if (ownerUuid.equals(playerUuid)) {
                throw new CannotRemoveOwnerException();
            }

            Set<UUID> members = database.getIslandPlayers(islandUuid).keySet();
            if (!members.contains(playerUuid)) {
                throw new IslandPlayerDoesNotExistException();
            }
        }, plugin.getBukkitAsyncExecutor()).thenCompose(v -> islandDistributor.removeMember(islandUuid, playerUuid));
    }

    public CompletableFuture<Void> setOwner(UUID islandUuid, UUID newOwnerUuid) {
        return CompletableFuture.supplyAsync(() -> {
            Set<UUID> members = database.getIslandPlayers(islandUuid).keySet();
            if (!members.contains(newOwnerUuid)) {
                throw new IslandPlayerDoesNotExistException();
            }

            UUID oldOwnerUuid = getIslandOwnerSync(islandUuid);
            if (oldOwnerUuid.equals(newOwnerUuid)) {
                throw new PlayerAlreadyOwnerException();
            }

            return oldOwnerUuid;
        }, plugin.getBukkitAsyncExecutor()).thenCompose(oldOwnerUuid -> islandDistributor.setOwner(islandUuid, oldOwnerUuid, newOwnerUuid));
    }

    public CompletableFuture<Void> expelPlayer(UUID islandUuid, UUID playerUuid) {
        return CompletableFuture.runAsync(() -> {
            Set<UUID> players = database.getIslandPlayers(islandUuid).keySet();
            if (players.contains(playerUuid)) {
                throw new CannotExpelIslandPlayerException();
            }

        }, plugin.getBukkitAsyncExecutor()).thenCompose(v -> islandDistributor.expelPlayer(islandUuid, playerUuid));
    }

    public CompletableFuture<Void> addPendingInvite(UUID inviteeUuid, UUID islandUuid, UUID inviterUuid, int ttlSeconds) {
        return CompletableFuture.runAsync(() -> {
            Set<UUID> members = database.getIslandPlayers(islandUuid).keySet();
            if (members.contains(inviteeUuid)) {
                throw new IslandPlayerAlreadyExistsException();
            }

            Optional<UUID> existingIsland = database.getIslandUuid(inviteeUuid);
            if (existingIsland.isPresent() && !existingIsland.get().equals(islandUuid)) {
                throw new IslandAlreadyExistException();
            }

            Optional<Invitation> existingInvite = invitationStore.getIslandInvite(inviteeUuid);
            if (existingInvite.isPresent()) {
                throw new InvitedAlreadyException();
            }

            invitationStore.addIslandInvite(inviteeUuid, islandUuid, inviterUuid, ttlSeconds);
        }, plugin.getBukkitAsyncExecutor());
    }

    public CompletableFuture<Void> removePendingInvite(UUID playerUuid) {
        return CompletableFuture.runAsync(() -> invitationStore.removeIslandInvite(playerUuid), plugin.getBukkitAsyncExecutor());
    }

    public CompletableFuture<Optional<Invitation>> getPendingInvite(UUID playerUuid) {
        return CompletableFuture.supplyAsync(() -> invitationStore.getIslandInvite(playerUuid), plugin.getBukkitAsyncExecutor());
    }

    public CompletableFuture<UUID> getIslandOwner(UUID islandUuid) {
        return CompletableFuture.supplyAsync(() -> getIslandOwnerSync(islandUuid), plugin.getBukkitAsyncExecutor());
    }

    public CompletableFuture<Set<UUID>> getIslandMembers(UUID islandUuid) {
        return CompletableFuture.supplyAsync(() -> database.getIslandPlayers(islandUuid).entrySet().stream().filter(entry -> "member".equalsIgnoreCase(entry.getValue())).map(Map.Entry::getKey).collect(Collectors.toSet()), plugin.getBukkitAsyncExecutor());
    }

    public CompletableFuture<Set<UUID>> getIslandPlayers(UUID islandUuid) {
        return CompletableFuture.supplyAsync(() -> database.getIslandPlayers(islandUuid).keySet(), plugin.getBukkitAsyncExecutor());
    }

    private UUID getIslandOwnerSync(UUID islandUuid) {
        return database.getIslandCore(islandUuid).flatMap(DatabaseHandler.IslandCoreData::owner).orElseThrow(() -> new IllegalStateException("Island owner does not exist for island: " + islandUuid));
    }
}
