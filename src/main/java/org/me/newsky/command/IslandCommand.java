package org.me.newsky.command;

import co.aikar.commands.BaseCommand;
import co.aikar.commands.annotation.*;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.me.newsky.api.NewSkyAPI;
import org.me.newsky.config.ConfigHandler;
import org.me.newsky.exceptions.*;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@CommandAlias("is")
@Description("Primary command for island interactions")
public class IslandCommand extends BaseCommand {

    private final ConfigHandler config;
    private final NewSkyAPI api;
    private final Map<UUID, Long> confirmationTimes = new HashMap<>();

    public IslandCommand(ConfigHandler config, NewSkyAPI api) {
        this.config = config;
        this.api = api;
    }


    @Subcommand("create")
    @CommandPermission("newsky.island.create")
    @Description("Creates an island for the player")
    @SuppressWarnings("unused")
    public void onCreate(Player player) {
        UUID playerUuid = player.getUniqueId();

        api.createIsland(playerUuid).thenRun(() -> {
            player.sendMessage(config.getPlayerCreateSuccessMessage());
            api.home(player.getUniqueId(), "default", player.getUniqueId()).thenRun(() -> {
                player.sendMessage(config.getPlayerHomeSuccessMessage("default"));
            }).exceptionally(ex -> {
                player.sendMessage("There was an error teleporting you to your island home");
                ex.printStackTrace();
                return null;
            });
        }).exceptionally(ex -> {
            if (ex.getCause() instanceof IslandAlreadyExistException) {
                player.sendMessage(config.getPlayerAlreadyHasIslandMessage());
            } else if (ex.getCause() instanceof NoActiveServerException) {
                player.sendMessage(config.getNoActiveServerMessage());
            } else {
                player.sendMessage("There was an error creating the island");
                ex.printStackTrace();
            }
            return null;
        });
    }

    @Subcommand("delete")
    @CommandPermission("newsky.island.delete")
    @Description("Deletes your island with a confirmation step")
    @SuppressWarnings("unused")
    public void onDelete(Player player) {
        UUID playerId = player.getUniqueId();

        if (confirmationTimes.containsKey(playerId) && (System.currentTimeMillis() - confirmationTimes.get(playerId) < 15000)) {
            confirmationTimes.remove(playerId);
            api.deleteIsland(playerId).thenRun(() -> {
                player.sendMessage(config.getPlayerDeleteSuccessMessage());
            }).exceptionally(ex -> {
                if (ex.getCause() instanceof IslandDoesNotExistException) {
                    player.sendMessage(config.getPlayerNoIslandMessage());
                } else if (ex.getCause() instanceof NoActiveServerException) {
                    player.sendMessage(config.getNoActiveServerMessage());
                } else {
                    player.sendMessage("There was an error deleting the island");
                    ex.printStackTrace();
                }
                return null;
            });
        } else {
            confirmationTimes.put(playerId, System.currentTimeMillis());
            player.sendMessage("Are you sure you want to delete your island? Type the command again within 15 seconds to confirm.");
        }
    }


    @Subcommand("addmember")
    @CommandPermission("newsky.island.addmember")
    @CommandCompletion("@players")
    @Description("Adds a member to your island")
    @SuppressWarnings("unused")
    public void onAddMember(Player player, @Single String targetPlayerName) {
        OfflinePlayer targetPlayer = Bukkit.getOfflinePlayer(targetPlayerName);

        api.addMember(player.getUniqueId(), targetPlayer.getUniqueId(), "member").thenRun(() -> {
            player.sendMessage(config.getPlayerAddMemberSuccessMessage(targetPlayerName));
        }).exceptionally(ex -> {
            if (ex.getCause() instanceof IslandDoesNotExistException) {
                player.sendMessage(config.getPlayerNoIslandMessage());
            } else if (ex.getCause() instanceof IslandPlayerAlreadyExistsException) {
                player.sendMessage(config.getIslandMemberExistsMessage(targetPlayerName));
            } else {
                player.sendMessage("There was an error adding the member.");
                ex.printStackTrace();
            }
            return null;
        });
    }


    @Subcommand("removemember")
    @CommandPermission("newsky.island.removemember")
    @CommandCompletion("@players")
    @Description("Removes a member from your island")
    @SuppressWarnings("unused")
    public void onRemoveMember(Player player, @Single String targetPlayerName) {
        OfflinePlayer targetPlayer = Bukkit.getOfflinePlayer(targetPlayerName);

        api.removeMember(player.getUniqueId(), targetPlayer.getUniqueId()).thenRun(() -> {
            player.sendMessage(config.getPlayerRemoveMemberSuccessMessage(targetPlayerName));
        }).exceptionally(ex -> {
            if (ex.getCause() instanceof IslandDoesNotExistException) {
                player.sendMessage(config.getPlayerNoIslandMessage());
            } else if (ex.getCause() instanceof IslandPlayerDoesNotExistException) {
                player.sendMessage(config.getIslandMemberNotExistsMessage(targetPlayerName));
            } else {
                player.sendMessage("There was an error removing the member.");
                ex.printStackTrace();
            }
            return null;
        });
    }


    @Subcommand("home")
    @CommandPermission("newsky.island.home")
    @CommandCompletion("@homes")
    @Description("Teleports you to your island home")
    @SuppressWarnings("unused")
    public void onHome(Player player, @Default("default") String homeName) {
        api.home(player.getUniqueId(), homeName, player.getUniqueId()).thenRun(() -> {
            player.sendMessage(config.getPlayerHomeSuccessMessage(homeName));
        }).exceptionally(ex -> {
            if (ex.getCause() instanceof IslandDoesNotExistException) {
                player.sendMessage(config.getPlayerNoIslandMessage());
            } else if (ex.getCause() instanceof HomeDoesNotExistException) {
                player.sendMessage(config.getPlayerNoHomeMessage(homeName));
            } else if (ex.getCause() instanceof NoActiveServerException) {
                player.sendMessage(config.getNoActiveServerMessage());
            } else {
                player.sendMessage("There was an error teleporting to the home.");
                ex.printStackTrace();
            }
            return null;
        });
    }

    @Subcommand("sethome")
    @CommandPermission("newsky.island.sethome")
    @CommandCompletion("@homes")
    @Description("Sets your island home")
    @SuppressWarnings("unused")
    public void onSetHome(Player player, @Default("default") String homeName) {
        Location loc = player.getLocation();

        api.setHome(player.getUniqueId(), homeName, loc).thenRun(() -> {
            player.sendMessage(config.getPlayerSetHomeSuccessMessage(homeName));
        }).exceptionally(ex -> {
            if (ex.getCause() instanceof IslandDoesNotExistException) {
                player.sendMessage(config.getPlayerNoIslandMessage());
            } else if (ex.getCause() instanceof LocationNotInIslandException) {
                player.sendMessage(config.getPlayerMustInIslandSetHomeMessage());
            } else {
                player.sendMessage("There was an error setting the home.");
                ex.printStackTrace();
            }
            return null;
        });
    }

    @Subcommand("delhome")
    @CommandPermission("newsky.island.delhome")
    @CommandCompletion("@homes")
    @Description("Deletes a specified home")
    @SuppressWarnings("unused")
    public void onDelHome(Player player, String homeName) {
        if ("default".equals(homeName)) {
            player.sendMessage(config.getPlayerCannotDeleteDefaultHomeMessage());
            return;
        }

        api.delHome(player.getUniqueId(), homeName).thenRun(() -> {
            player.sendMessage(config.getPlayerDelHomeSuccessMessage(homeName));
        }).exceptionally(ex -> {
            if (ex.getCause() instanceof IslandDoesNotExistException) {
                player.sendMessage(config.getPlayerNoIslandMessage());
            } else if (ex.getCause() instanceof HomeDoesNotExistException) {
                player.sendMessage(config.getPlayerNoHomeMessage(homeName));
            } else {
                player.sendMessage("There was an error deleting the home.");
                ex.printStackTrace();
            }
            return null;
        });
    }

    @Subcommand("warp")
    @CommandPermission("newsky.island.warp")
    @CommandCompletion("@warps")
    @Description("Teleports you to a specified warp point on your island")
    @SuppressWarnings("unused")
    public void onWarp(Player player, String warpName) {
        api.warp(player.getUniqueId(), warpName, player.getUniqueId()).thenRun(() -> {
            player.sendMessage(config.getWarpSuccessMessage(warpName));
        }).exceptionally(ex -> {
            if (ex.getCause() instanceof IslandDoesNotExistException) {
                player.sendMessage(config.getNoIslandMessage());
            } else if (ex.getCause() instanceof WarpDoesNotExistException) {
                player.sendMessage(config.getNoWarpMessage(player.getName(), warpName));
            } else if (ex.getCause() instanceof NoActiveServerException) {
                player.sendMessage(config.getNoActiveServerMessage());
            } else {
                player.sendMessage("There was an error teleporting to the warp.");
                ex.printStackTrace();
            }
            return null;
        });
    }

    @Subcommand("setwarp")
    @CommandPermission("newsky.island.setwarp")
    @CommandCompletion("@warps")
    @Description("Sets a warp point on your island")
    @SuppressWarnings("unused")
    public void onSetWarp(Player player, String warpName) {
        Location loc = player.getLocation();

        api.setWarp(player.getUniqueId(), warpName, loc).thenRun(() -> {
            player.sendMessage(config.getPlayerSetWarpSuccessMessage(warpName));
        }).exceptionally(ex -> {
            if (ex.getCause() instanceof IslandDoesNotExistException) {
                player.sendMessage(config.getPlayerNoIslandMessage());
            } else if (ex.getCause() instanceof LocationNotInIslandException) {
                player.sendMessage(config.getPlayerMustInIslandSetWarpMessage());
            } else {
                player.sendMessage("There was an error setting the warp");
                ex.printStackTrace();
            }
            return null;
        });
    }

    @Subcommand("delwarp")
    @CommandPermission("newsky.island.delwarp")
    @CommandCompletion("@warps")
    @Description("Deletes a specified warp point on your island")
    @SuppressWarnings("unused")
    public void onDelWarp(Player player, String warpName) {
        api.delWarp(player.getUniqueId(), warpName).thenRun(() -> {
            player.sendMessage(config.getPlayerDelWarpSuccessMessage(warpName));
        }).exceptionally(ex -> {
            if (ex.getCause() instanceof IslandDoesNotExistException) {
                player.sendMessage(config.getPlayerNoIslandMessage());
            } else if (ex.getCause() instanceof WarpDoesNotExistException) {
                player.sendMessage(config.getPlayerNoWarpMessage(warpName));
            } else {
                player.sendMessage("There was an error deleting the warp.");
                ex.printStackTrace();
            }
            return null;
        });
    }


    @Subcommand("leave")
    @CommandPermission("newsky.island.leave")
    @Description("Leaves your current island")
    @SuppressWarnings("unused")
    public void onLeave(Player player) {
        UUID playerUuid = player.getUniqueId();

        api.removeMember(playerUuid, playerUuid).thenRun(() -> {
            player.sendMessage(config.getPlayerLeaveSuccessMessage());
        }).exceptionally(ex -> {
            if (ex.getCause() instanceof IslandDoesNotExistException) {
                player.sendMessage(config.getPlayerNoIslandMessage());
            } else if (ex.getCause() instanceof CannotRemoveOwnerException) {
                player.sendMessage(config.getPlayerCannotLeaveAsOwnerMessage());
            } else if (ex.getCause() instanceof IslandPlayerDoesNotExistException) {
                player.sendMessage(config.getPlayerNoIslandMessage());
            } else {
                player.sendMessage("There was an error leaving the island");
                ex.printStackTrace();
            }
            return null;
        });
    }


    @Subcommand("level")
    @CommandPermission("newsky.island.level")
    @Description("Displays the level of your island")
    @SuppressWarnings("unused")
    public void onLevel(Player player) {
        UUID playerUuid = player.getUniqueId();

        api.getIslandLevel(playerUuid).thenAccept(level -> {
            player.sendMessage(config.getIslandLevelMessage(level));
        }).exceptionally(ex -> {
            if (ex.getCause() instanceof IslandDoesNotExistException) {
                player.sendMessage(config.getPlayerNoIslandMessage());
            } else {
                player.sendMessage("There was an error calculating the island level.");
                ex.printStackTrace();
            }
            return null;
        });
    }


    @Subcommand("lock")
    @CommandPermission("newsky.island.togglelock")
    @Description("Toggles the lock status of your island")
    @SuppressWarnings("unused")
    public void onLock(Player player) {
        UUID playerUuid = player.getUniqueId();

        api.toggleIslandLock(playerUuid).thenAccept(isLocked -> {
            if (isLocked) {
                player.sendMessage(config.getPlayerLockSuccessMessage());
            } else {
                player.sendMessage(config.getPlayerUnLockSuccessMessage());
            }
        }).exceptionally(ex -> {
            if (ex.getCause() instanceof IslandDoesNotExistException) {
                player.sendMessage(config.getPlayerNoIslandMessage());
            } else {
                player.sendMessage("There was an error toggling the island lock status.");
                ex.printStackTrace();
            }
            return null;
        });
    }


    @Subcommand("pvp")
    @CommandPermission("newsky.island.togglepvp")
    @Description("Toggles the PvP status on your island")
    @SuppressWarnings("unused")
    public void onPvp(Player player) {
        UUID playerUuid = player.getUniqueId();

        api.toggleIslandPvp(playerUuid).thenAccept(isPvpEnabled -> {
            if (isPvpEnabled) {
                player.sendMessage(config.getPlayerPvpEnableSuccessMessage());
            } else {
                player.sendMessage(config.getPlayerPvpDisableSuccessMessage());
            }
        }).exceptionally(ex -> {
            if (ex.getCause() instanceof IslandDoesNotExistException) {
                player.sendMessage(config.getPlayerNoIslandMessage());
            } else {
                player.sendMessage("There was an error toggling the PvP status.");
                ex.printStackTrace();
            }
            return null;
        });
    }
}
