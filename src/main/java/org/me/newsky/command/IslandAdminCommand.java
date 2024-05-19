package org.me.newsky.command;

import co.aikar.commands.BaseCommand;
import co.aikar.commands.annotation.*;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.me.newsky.api.NewSkyAPI;
import org.me.newsky.config.ConfigHandler;
import org.me.newsky.exceptions.*;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@CommandAlias("isadmin")
@Description("Admin commands for island management")
public class IslandAdminCommand extends BaseCommand {

    private final ConfigHandler config;
    private final NewSkyAPI api;
    private final Map<UUID, Long> confirmationTimes = new HashMap<>();

    public IslandAdminCommand(ConfigHandler config, NewSkyAPI api) {
        this.config = config;
        this.api = api;
    }

    @Subcommand("create")
    @CommandPermission("newsky.admin.island.create")
    @Description("Admin command to create an island for a player")
    @Syntax("<player>")
    @CommandCompletion("@players")
    @SuppressWarnings("unused")
    public void onAdminCreate(CommandSender sender, @Single String targetPlayerName) {
        // Get the target player's UUID
        OfflinePlayer targetPlayer = Bukkit.getOfflinePlayer(targetPlayerName);
        UUID targetUuid = targetPlayer.getUniqueId();

        // Create the island
        api.createIsland(targetUuid).thenRun(() -> {
            sender.sendMessage(config.getAdminCreateSuccessMessage(targetPlayerName));
        }).exceptionally(ex -> {
            if (ex.getCause() instanceof IslandAlreadyExistException) {
                sender.sendMessage(config.getAdminAlreadyHasIslandMessage(targetPlayerName));
            } else if (ex.getCause() instanceof NoActiveServerException) {
                sender.sendMessage(config.getNoActiveServerMessage());
            } else {
                sender.sendMessage("There was an error creating the island");
                ex.printStackTrace();
            }
            return null;
        });
    }


    @Subcommand("delete")
    @CommandPermission("newsky.admin.island.delete")
    @Description("Admin command to delete a player's island")
    @Syntax("<player>")
    @CommandCompletion("@players")
    @SuppressWarnings("unused")
    public void onAdminDelete(CommandSender sender, @Single String targetPlayerName) {
        OfflinePlayer targetPlayer = Bukkit.getOfflinePlayer(targetPlayerName);
        UUID targetUuid = targetPlayer.getUniqueId();

        if (confirmationTimes.containsKey(targetUuid) && (System.currentTimeMillis() - confirmationTimes.get(targetUuid) < 15000)) {
            confirmationTimes.remove(targetUuid);

            api.getIslandUuid(targetUuid).thenCompose(api::deleteIsland).thenRun(() -> {
                sender.sendMessage(config.getAdminDeleteSuccessMessage(targetPlayerName));
            }).exceptionally(ex -> {
                if (ex.getCause() instanceof IslandDoesNotExistException) {
                    sender.sendMessage(config.getAdminNoIslandMessage(targetPlayerName));
                } else if (ex.getCause() instanceof NoActiveServerException) {
                    sender.sendMessage(config.getNoActiveServerMessage());
                } else {
                    sender.sendMessage("There was an error deleting the island");
                    ex.printStackTrace();
                }
                return null;
            });
        } else {
            confirmationTimes.put(targetUuid, System.currentTimeMillis());
            sender.sendMessage(config.getAdminDeleteWarningMessage(targetPlayerName));
        }
    }


    @Subcommand("addmember")
    @CommandPermission("newsky.admin.island.addmember")
    @Description("Admin command to add a member to a player's island")
    @Syntax("<member> <owner>")
    @CommandCompletion("@players @players")
    @SuppressWarnings("unused")
    public void onAdminAddMember(CommandSender sender, @Single String targetMemberName, @Single String islandOwnerName) {
        // Get the UUIDs
        OfflinePlayer targetMember = Bukkit.getOfflinePlayer(targetMemberName);
        OfflinePlayer islandOwner = Bukkit.getOfflinePlayer(islandOwnerName);
        UUID targetMemberUuid = targetMember.getUniqueId();
        UUID islandOwnerUuid = islandOwner.getUniqueId();

        // Add the target player to the island
        api.getIslandUuid(islandOwnerUuid).thenCompose(islandUuid -> api.addMember(islandUuid, targetMemberUuid, "member")).thenRun(() -> {
            sender.sendMessage(config.getAdminAddMemberSuccessMessage(targetMemberName, islandOwnerName));
        }).exceptionally(ex -> {
            if (ex.getCause() instanceof IslandDoesNotExistException) {
                sender.sendMessage(config.getAdminNoIslandMessage(islandOwnerName));
            } else if (ex.getCause() instanceof IslandPlayerAlreadyExistsException) {
                sender.sendMessage(config.getIslandMemberExistsMessage(targetMemberName));
            } else {
                sender.sendMessage("There was an error adding the member");
                ex.printStackTrace();
            }
            return null;
        });
    }


    @Subcommand("removemember")
    @CommandPermission("newsky.admin.island.removemember")
    @Description("Admin command to remove a member from a player's island")
    @Syntax("<member> <owner>")
    @CommandCompletion("@players @players")
    @SuppressWarnings("unused")
    public void onAdminRemoveMember(CommandSender sender, @Single String targetMemberName, @Single String islandOwnerName) {
        // Get the UUIDs
        OfflinePlayer targetMember = Bukkit.getOfflinePlayer(targetMemberName);
        OfflinePlayer islandOwner = Bukkit.getOfflinePlayer(islandOwnerName);
        UUID targetMemberUuid = targetMember.getUniqueId();
        UUID islandOwnerUuid = islandOwner.getUniqueId();

        // Remove the target player from the island
        api.getIslandUuid(islandOwnerUuid).thenCompose(islandUuid -> api.removeMember(islandUuid, targetMemberUuid)).thenRun(() -> {
            sender.sendMessage(config.getAdminRemoveMemberSuccessMessage(targetMemberName, islandOwnerName));
        }).exceptionally(ex -> {
            if (ex.getCause() instanceof IslandDoesNotExistException) {
                sender.sendMessage(config.getAdminNoIslandMessage(islandOwnerName));
            } else if (ex.getCause() instanceof IslandPlayerDoesNotExistException) {
                sender.sendMessage(config.getIslandMemberNotExistsMessage(targetMemberName));
            } else {
                sender.sendMessage("There was an error removing the member");
                ex.printStackTrace();
            }
            return null;
        });
    }


    @Subcommand("home")
    @CommandPermission("newsky.admin.island.home")
    @Description("Admin command to teleport to a player's island home")
    @Syntax("<player> [home] [target]")
    @CommandCompletion("@players @homes @players")
    @SuppressWarnings("unused")
    public void onAdminHome(CommandSender sender, @Single String homePlayerName, @Default("default") @Single String homeName, @Optional @Single String teleportPlayerName) {
        // Get the UUIDs
        OfflinePlayer homePlayer = Bukkit.getOfflinePlayer(homePlayerName);
        UUID homePlayerUuid = homePlayer.getUniqueId();
        UUID senderUuid;

        if (teleportPlayerName == null) {
            if (!(sender instanceof Player player)) {
                sender.sendMessage(config.getOnlyPlayerCanRunCommandMessage());
                return;
            }
            senderUuid = player.getUniqueId();
        } else {
            OfflinePlayer targetPlayer = Bukkit.getOfflinePlayer(teleportPlayerName);
            senderUuid = targetPlayer.getUniqueId();
        }

        // Teleport to the home
        api.home(homePlayerUuid, homeName, senderUuid).thenRun(() -> {
            sender.sendMessage(config.getAdminHomeSuccessMessage(homePlayerName, homeName));
        }).exceptionally(ex -> {
            if (ex.getCause() instanceof IslandDoesNotExistException) {
                sender.sendMessage(config.getAdminNoIslandMessage(homePlayerName));
            } else if (ex.getCause() instanceof HomeDoesNotExistException) {
                sender.sendMessage(config.getAdminNoHomeMessage(homePlayerName, homeName));
            } else if (ex.getCause() instanceof NoActiveServerException) {
                sender.sendMessage(config.getNoActiveServerMessage());
            } else {
                sender.sendMessage("There was an error teleporting to the home.");
                ex.printStackTrace();
            }
            return null;
        });
    }


    @Subcommand("sethome")
    @CommandPermission("newsky.admin.island.sethome")
    @Description("Admin command to set a home on a player's island")
    @Syntax("<player> <home>")
    @CommandCompletion("@players @homes")
    @SuppressWarnings("unused")
    public void onAdminSetHome(CommandSender sender, @Single String homePlayerName, @Single String homeName) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(config.getOnlyPlayerCanRunCommandMessage());
            return;
        }

        OfflinePlayer targetPlayer = Bukkit.getOfflinePlayer(homePlayerName);
        UUID targetUuid = targetPlayer.getUniqueId();
        Location loc = player.getLocation();

        api.setHome(targetUuid, homeName, loc).thenRun(() -> {
            sender.sendMessage(config.getAdminSetHomeSuccessMessage(homePlayerName, homeName));
        }).exceptionally(ex -> {
            if (ex.getCause() instanceof IslandDoesNotExistException) {
                sender.sendMessage(config.getAdminNoIslandMessage(homePlayerName));
            } else if (ex.getCause() instanceof LocationNotInIslandException) {
                sender.sendMessage(config.getAdminMustInIslandSetHomeMessage(homePlayerName));
            } else {
                sender.sendMessage("There was an error setting the home.");
                ex.printStackTrace();
            }
            return null;
        });
    }


    @Subcommand("delhome")
    @CommandPermission("newsky.admin.island.delhome")
    @Description("Admin command to delete a home on a player's island")
    @Syntax("<player> <home>")
    @CommandCompletion("@players @homes")
    @SuppressWarnings("unused")
    public void onAdminDelHome(CommandSender sender, @Single String homePlayerName, @Single String homeName) {
        OfflinePlayer targetPlayer = Bukkit.getOfflinePlayer(homePlayerName);
        UUID targetUuid = targetPlayer.getUniqueId();

        if ("default".equals(homeName)) {
            sender.sendMessage(config.getAdminCannotDeleteDefaultHomeMessage(homePlayerName));
            return;
        }

        api.delHome(targetUuid, homeName).thenRun(() -> {
            sender.sendMessage(config.getAdminDelHomeSuccessMessage(homePlayerName, homeName));
        }).exceptionally(ex -> {
            if (ex.getCause() instanceof IslandDoesNotExistException) {
                sender.sendMessage(config.getAdminNoIslandMessage(homePlayerName));
            } else if (ex.getCause() instanceof HomeDoesNotExistException) {
                sender.sendMessage(config.getAdminNoHomeMessage(homePlayerName, homeName));
            } else {
                sender.sendMessage("There was an error deleting the home.");
                ex.printStackTrace();
            }
            return null;
        });
    }


    @Subcommand("warp")
    @CommandPermission("newsky.admin.island.warp")
    @Description("Admin command to teleport to a warp point on a player's island")
    @Syntax("<player> <warp> [target]")
    @CommandCompletion("@players @warps @players")
    @SuppressWarnings("unused")
    public void onAdminWarp(CommandSender sender, @Single String warpPlayerName, @Default("default") @Single String warpName, @Optional @Single String teleportPlayerName) {
        OfflinePlayer warpPlayer = Bukkit.getOfflinePlayer(warpPlayerName);
        UUID warpPlayerUuid = warpPlayer.getUniqueId();
        UUID senderUuid;

        if (teleportPlayerName == null) {
            if (!(sender instanceof Player player)) {
                sender.sendMessage(config.getOnlyPlayerCanRunCommandMessage());
                return;
            }
            senderUuid = player.getUniqueId();
        } else {
            OfflinePlayer targetPlayer = Bukkit.getOfflinePlayer(teleportPlayerName);
            senderUuid = targetPlayer.getUniqueId();
        }

        api.warp(warpPlayerUuid, warpName, senderUuid).thenRun(() -> {
            sender.sendMessage(config.getWarpSuccessMessage(warpName));
        }).exceptionally(ex -> {
            if (ex.getCause() instanceof IslandDoesNotExistException) {
                sender.sendMessage(config.getNoIslandMessage(warpPlayerName));
            } else if (ex.getCause() instanceof WarpDoesNotExistException) {
                sender.sendMessage(config.getNoWarpMessage(warpPlayerName, warpName));
            } else if (ex.getCause() instanceof NoActiveServerException) {
                sender.sendMessage(config.getNoActiveServerMessage());
            } else {
                sender.sendMessage("There was an error teleporting to the warp.");
                ex.printStackTrace();
            }
            return null;
        });
    }


    @Subcommand("setwarp")
    @CommandPermission("newsky.admin.island.setwarp")
    @Description("Admin command to set a warp point on a player's island")
    @Syntax("<player> <warp>")
    @CommandCompletion("@players @warps")
    @SuppressWarnings("unused")
    public void onAdminSetWarp(CommandSender sender, @Single String warpPlayerName, @Single String warpName) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(config.getOnlyPlayerCanRunCommandMessage());
            return;
        }

        OfflinePlayer targetPlayer = Bukkit.getOfflinePlayer(warpPlayerName);
        UUID targetUuid = targetPlayer.getUniqueId();
        Location loc = player.getLocation();

        api.setWarp(targetUuid, warpName, loc).thenRun(() -> {
            sender.sendMessage(config.getAdminSetWarpSuccessMessage(warpPlayerName, warpName));
        }).exceptionally(ex -> {
            if (ex.getCause() instanceof IslandDoesNotExistException) {
                sender.sendMessage(config.getAdminNoIslandMessage(warpPlayerName));
            } else if (ex.getCause() instanceof LocationNotInIslandException) {
                sender.sendMessage(config.getAdminMustInIslandSetWarpMessage(warpPlayerName));
            } else {
                sender.sendMessage("There was an error setting the warp.");
                ex.printStackTrace();
            }
            return null;
        });
    }


    @Subcommand("delwarp")
    @CommandPermission("newsky.admin.island.delwarp")
    @Description("Admin command to delete a warp point on a player'6s island")
    @Syntax("<player> <warp>")
    @CommandCompletion("@players @warps")
    @SuppressWarnings("unused")
    public void onAdminDelWarp(CommandSender sender, @Single String warpPlayerName, @Single String warpName) {
        OfflinePlayer targetPlayer = Bukkit.getOfflinePlayer(warpPlayerName);
        UUID targetUuid = targetPlayer.getUniqueId();

        api.delWarp(targetUuid, warpName).thenRun(() -> {
            sender.sendMessage(config.getAdminDelWarpSuccessMessage(warpPlayerName, warpName));
        }).exceptionally(ex -> {
            if (ex.getCause() instanceof IslandDoesNotExistException) {
                sender.sendMessage(config.getAdminNoIslandMessage(warpPlayerName));
            } else if (ex.getCause() instanceof WarpDoesNotExistException) {
                sender.sendMessage(config.getAdminNoWarpMessage(warpPlayerName, warpName));
            } else {
                sender.sendMessage("There was an error deleting the warp.");
                ex.printStackTrace();
            }
            return null;
        });
    }


    @Subcommand("lock")
    @CommandPermission("newsky.admin.island.lock")
    @Description("Admin command to toggle the lock status of a player's island")
    @Syntax("<player>")
    @CommandCompletion("@players")
    @SuppressWarnings("unused")
    public void onAdminLock(CommandSender sender, @Single String targetPlayerName) {
        OfflinePlayer targetPlayer = Bukkit.getOfflinePlayer(targetPlayerName);
        UUID targetUuid = targetPlayer.getUniqueId();

        api.getIslandUuid(targetUuid).thenCompose(api::toggleIslandLock).thenAccept(isLocked -> {
            if (isLocked) {
                sender.sendMessage(config.getAdminLockSuccessMessage(targetPlayerName));
            } else {
                sender.sendMessage(config.getAdminUnLockSuccessMessage(targetPlayerName));
            }
        }).exceptionally(ex -> {
            if (ex.getCause() instanceof IslandDoesNotExistException) {
                sender.sendMessage(config.getAdminNoIslandMessage(targetPlayerName));
            } else {
                sender.sendMessage("There was an error toggling the island lock status.");
                ex.printStackTrace();
            }
            return null;
        });
    }


    @Subcommand("pvp")
    @CommandPermission("newsky.admin.island.pvp")
    @Description("Admin command to toggle the PvP status on a player's island")
    @Syntax("<player>")
    @CommandCompletion("@players")
    @SuppressWarnings("unused")
    public void onAdminPvp(CommandSender sender, @Single String targetPlayerName) {
        OfflinePlayer targetPlayer = Bukkit.getOfflinePlayer(targetPlayerName);
        UUID targetUuid = targetPlayer.getUniqueId();

        api.getIslandUuid(targetUuid).thenCompose(api::toggleIslandPvp).thenAccept(isPvpEnabled -> {
            if (isPvpEnabled) {
                sender.sendMessage(config.getAdminPvpEnableSuccessMessage(targetPlayerName));
            } else {
                sender.sendMessage(config.getAdminPvpDisableSuccessMessage(targetPlayerName));
            }
        }).exceptionally(ex -> {
            if (ex.getCause() instanceof IslandDoesNotExistException) {
                sender.sendMessage(config.getAdminNoIslandMessage(targetPlayerName));
            } else {
                sender.sendMessage("There was an error toggling the PvP status.");
                ex.printStackTrace();
            }
            return null;
        });
    }


    @Subcommand("load")
    @CommandPermission("newsky.admin.island.load")
    @Description("Admin command to load a player's island")
    @Syntax("<player>")
    @CommandCompletion("@players")
    @SuppressWarnings("unused")
    public void onAdminLoadIsland(CommandSender sender, @Single String targetPlayerName) {
        OfflinePlayer targetPlayer = Bukkit.getOfflinePlayer(targetPlayerName);
        UUID targetUuid = targetPlayer.getUniqueId();

        api.getIslandUuid(targetUuid).thenCompose(api::loadIsland).thenRun(() -> {
            sender.sendMessage(config.getIslandLoadSuccessMessage(targetPlayerName));
        }).exceptionally(ex -> {
            if (ex.getCause() instanceof IslandDoesNotExistException) {
                sender.sendMessage(config.getNoIslandMessage(targetPlayerName));
            } else if (ex.getCause() instanceof NoActiveServerException) {
                sender.sendMessage(config.getNoActiveServerMessage());
            } else if (ex.getCause() instanceof IslandAlreadyLoadedException) {
                sender.sendMessage(config.getIslandAlreadyLoadedMessage());
            } else {
                sender.sendMessage("There was an error loading the island.");
                ex.printStackTrace();
            }
            return null;
        });
    }


    @Subcommand("unload")
    @CommandPermission("newsky.admin.island.unload")
    @Description("Admin command to unload a player's island")
    @Syntax("<player>")
    @CommandCompletion("@players")
    @SuppressWarnings("unused")
    public void onAdminUnloadIsland(CommandSender sender, @Single String targetPlayerName) {
        OfflinePlayer targetPlayer = Bukkit.getOfflinePlayer(targetPlayerName);
        UUID targetUuid = targetPlayer.getUniqueId();

        api.getIslandUuid(targetUuid).thenCompose(api::unloadIsland).thenRun(() -> {
            sender.sendMessage(config.getIslandUnloadSuccessMessage(targetPlayerName));
        }).exceptionally(ex -> {
            if (ex.getCause() instanceof IslandDoesNotExistException) {
                sender.sendMessage(config.getNoIslandMessage(targetPlayerName));
            } else if (ex.getCause() instanceof NoActiveServerException) {
                sender.sendMessage(config.getNoActiveServerMessage());
            } else {
                sender.sendMessage("There was an error unloading the island.");
                ex.printStackTrace();
            }
            return null;
        });
    }
}
