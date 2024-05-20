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

    @HelpCommand
    @CommandPermission("newsky.admin.island.help")
    @Description("Displays the help page")
    @SuppressWarnings("unused")
    public void onHelp(CommandSender sender) {
        sender.sendMessage(config.getAdminCommandHelpMessage());
        sender.sendMessage("/isadmin create <player> - Admin command to create an island for a player");
        sender.sendMessage("/isadmin delete <player> - Admin command to delete a player's island");
        sender.sendMessage("/isadmin addmember <member> <owner> - Admin command to add a member to a player's island");
        sender.sendMessage("/isadmin removemember <member> <owner> - Admin command to remove a member from a player's island");
        sender.sendMessage("/isadmin home <player> [home] [target] - Admin command to teleport to a player's island home");
        sender.sendMessage("/isadmin sethome <player> <home> - Admin command to set a home on a player's island");
        sender.sendMessage("/isadmin delhome <player> <home> - Admin command to delete a home on a player's island");
        sender.sendMessage("/isadmin warp <player> [warp] [target] - Admin command to teleport to a warp point on a player's island");
        sender.sendMessage("/isadmin setwarp <player> <warp> - Admin command to set a warp point on a player's island");
        sender.sendMessage("/isadmin delwarp <player> <warp> - Admin command to delete a warp point on a player's island");
        sender.sendMessage("/isadmin lock <player> - Admin command to toggle the lock status of a player's island");
        sender.sendMessage("/isadmin pvp <player> - Admin command to toggle the PvP status on a player's island");
        sender.sendMessage("/isadmin load <player> - Admin command to load a player's island");
        sender.sendMessage("/isadmin unload <player> - Admin command to unload a player's island");
    }

    @Subcommand("create")
    @CommandPermission("newsky.admin.island.create")
    @Description("Admin command to create an island for a player")
    @Syntax("/isadmin create <player>")
    @CommandCompletion("@players")
    @SuppressWarnings("unused")
    public void onAdminCreate(CommandSender sender, @Single String targetPlayerName) {
        if (targetPlayerName.isEmpty()) {
            sender.sendMessage(config.getUsagePrefix() + "/isadmin create <player>");
            return;
        }

        OfflinePlayer targetPlayer = Bukkit.getOfflinePlayer(targetPlayerName);
        UUID targetUuid = targetPlayer.getUniqueId();

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
    @Syntax("/isadmin delete <player>")
    @CommandCompletion("@players")
    @SuppressWarnings("unused")
    public void onAdminDelete(CommandSender sender, @Single String targetPlayerName) {
        if (targetPlayerName.isEmpty()) {
            sender.sendMessage(config.getUsagePrefix() + "/isadmin delete <player>");
            return;
        }

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
    @Syntax("/isadmin addmember <member> <owner>")
    @CommandCompletion("@players @players")
    @SuppressWarnings("unused")
    public void onAdminAddMember(CommandSender sender, @Single String targetMemberName, @Single String islandOwnerName) {
        if (targetMemberName.isEmpty() || islandOwnerName.isEmpty()) {
            sender.sendMessage(config.getUsagePrefix() + "/isadmin addmember <member> <owner>");
            return;
        }

        OfflinePlayer targetMember = Bukkit.getOfflinePlayer(targetMemberName);
        OfflinePlayer islandOwner = Bukkit.getOfflinePlayer(islandOwnerName);
        UUID targetMemberUuid = targetMember.getUniqueId();
        UUID islandOwnerUuid = islandOwner.getUniqueId();

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
    @Syntax("/isadmin removemember <member> <owner>")
    @CommandCompletion("@players @players")
    @SuppressWarnings("unused")
    public void onAdminRemoveMember(CommandSender sender, @Single String targetMemberName, @Single String islandOwnerName) {
        if (targetMemberName.isEmpty() || islandOwnerName.isEmpty()) {
            sender.sendMessage(config.getUsagePrefix() + "/isadmin removemember <member> <owner>");
            return;
        }

        OfflinePlayer targetMember = Bukkit.getOfflinePlayer(targetMemberName);
        OfflinePlayer islandOwner = Bukkit.getOfflinePlayer(islandOwnerName);
        UUID targetMemberUuid = targetMember.getUniqueId();
        UUID islandOwnerUuid = islandOwner.getUniqueId();

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
    @Syntax("/isadmin home <player> [home] [target]")
    @CommandCompletion("@players @homes @players")
    @SuppressWarnings("unused")
    public void onAdminHome(CommandSender sender, @Single String homePlayerName, @Default("default") @Single String homeName, @Optional @Single String teleportPlayerName) {
        if (homePlayerName.isEmpty()) {
            sender.sendMessage(config.getUsagePrefix() + "/isadmin home <player> [home] [target]");
            return;
        }

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
    @Syntax("/isadmin sethome <player> <home>")
    @CommandCompletion("@players @homes")
    @SuppressWarnings("unused")
    public void onAdminSetHome(CommandSender sender, @Single String homePlayerName, @Single String homeName) {
        if (homePlayerName.isEmpty() || homeName.isEmpty()) {
            sender.sendMessage(config.getUsagePrefix() + "/isadmin sethome <player> <home>");
            return;
        }

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
    @Syntax("/isadmin delhome <player> <home>")
    @CommandCompletion("@players @homes")
    @SuppressWarnings("unused")
    public void onAdminDelHome(CommandSender sender, @Single String homePlayerName, @Single String homeName) {
        if (homePlayerName.isEmpty() || homeName.isEmpty()) {
            sender.sendMessage(config.getUsagePrefix() + "/isadmin delhome <player> <home>");
            return;
        }

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
    @Syntax("/isadmin warp <player> [warp] [target]")
    @CommandCompletion("@players @warps @players")
    @SuppressWarnings("unused")
    public void onAdminWarp(CommandSender sender, @Single String warpPlayerName, @Default("default") @Single String warpName, @Optional @Single String teleportPlayerName) {
        if (warpPlayerName.isEmpty()) {
            sender.sendMessage(config.getUsagePrefix() + "/isadmin warp <player> [warp] [target]");
            return;
        }

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
    @Syntax("/isadmin setwarp <player> <warp>")
    @CommandCompletion("@players @warps")
    @SuppressWarnings("unused")
    public void onAdminSetWarp(CommandSender sender, @Single String warpPlayerName, @Single String warpName) {
        if (warpPlayerName.isEmpty() || warpName.isEmpty()) {
            sender.sendMessage(config.getUsagePrefix() + "/isadmin setwarp <player> <warp>");
            return;
        }

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
    @Description("Admin command to delete a warp point on a player's island")
    @Syntax("/isadmin delwarp <player> <warp>")
    @CommandCompletion("@players @warps")
    @SuppressWarnings("unused")
    public void onAdminDelWarp(CommandSender sender, @Single String warpPlayerName, @Single String warpName) {
        if (warpPlayerName.isEmpty() || warpName.isEmpty()) {
            sender.sendMessage(config.getUsagePrefix() + "/isadmin delwarp <player> <warp>");
            return;
        }

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
    @Syntax("/isadmin lock <player>")
    @CommandCompletion("@players")
    @SuppressWarnings("unused")
    public void onAdminLock(CommandSender sender, @Single String targetPlayerName) {
        if (targetPlayerName.isEmpty()) {
            sender.sendMessage(config.getUsagePrefix() + "/isadmin lock <player>");
            return;
        }

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
    @Syntax("/isadmin pvp <player>")
    @CommandCompletion("@players")
    @SuppressWarnings("unused")
    public void onAdminPvp(CommandSender sender, @Single String targetPlayerName) {
        if (targetPlayerName.isEmpty()) {
            sender.sendMessage(config.getUsagePrefix() + "/isadmin pvp <player>");
            return;
        }

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
    @Syntax("/isadmin load <player>")
    @CommandCompletion("@players")
    @SuppressWarnings("unused")
    public void onAdminLoadIsland(CommandSender sender, @Single String targetPlayerName) {
        if (targetPlayerName.isEmpty()) {
            sender.sendMessage(config.getUsagePrefix() + "/isadmin load <player>");
            return;
        }

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
    @Syntax("/isadmin unload <player>")
    @CommandCompletion("@players")
    @SuppressWarnings("unused")
    public void onAdminUnloadIsland(CommandSender sender, @Single String targetPlayerName) {
        if (targetPlayerName.isEmpty()) {
            sender.sendMessage(config.getUsagePrefix() + "/isadmin unload <player>");
            return;
        }

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
