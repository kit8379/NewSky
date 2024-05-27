package org.me.newsky.command;

import co.aikar.commands.BaseCommand;
import co.aikar.commands.CommandHelp;
import co.aikar.commands.annotation.Optional;
import co.aikar.commands.annotation.*;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.me.newsky.api.NewSkyAPI;
import org.me.newsky.config.ConfigHandler;
import org.me.newsky.exceptions.*;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

@CommandAlias("is|island")
@Description("Primary command for island interactions")
public class IslandCommand extends BaseCommand {

    private final ConfigHandler config;
    private final NewSkyAPI api;
    private final Map<UUID, Long> confirmationTimes = new HashMap<>();

    public IslandCommand(ConfigHandler config, NewSkyAPI api) {
        this.config = config;
        this.api = api;
    }

    @HelpCommand
    @CommandPermission("newsky.island.help")
    @Description("Displays the help page")
    @SuppressWarnings("unused")
    public void onHelp(CommandSender sender, CommandHelp help) {
        help.showHelp();
    }

    @Subcommand("create")
    @CommandPermission("newsky.island.create")
    @Description("Creates an island for the player")
    @Syntax("<player>")
    @SuppressWarnings("unused")
    public void onCreate(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(config.getOnlyPlayerCanRunCommandMessage());
            return;
        }

        UUID playerUuid = player.getUniqueId();

        api.createIsland(playerUuid).thenRun(() -> {
            player.sendMessage(config.getPlayerCreateSuccessMessage());
            api.home(playerUuid, "default", playerUuid).thenRun(() -> {
                player.sendMessage(config.getPlayerHomeSuccessMessage("default"));
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
    @Syntax("<player>")
    @SuppressWarnings("unused")
    public void onDelete(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(config.getOnlyPlayerCanRunCommandMessage());
            return;
        }

        UUID playerUuid = player.getUniqueId();

        if (confirmationTimes.containsKey(playerUuid) && (System.currentTimeMillis() - confirmationTimes.get(playerUuid) < 15000)) {
            confirmationTimes.remove(playerUuid);

            api.getIslandUuid(playerUuid).thenCompose(api::deleteIsland).thenRun(() -> {
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
            confirmationTimes.put(playerUuid, System.currentTimeMillis());
            player.sendMessage(config.getPlayerDeleteWarningMessage());
        }
    }

    @Subcommand("addmember")
    @CommandPermission("newsky.island.addmember")
    @CommandCompletion("@players")
    @Description("Adds a member to your island")
    @Syntax("<player>")
    @SuppressWarnings("unused")
    public void onAddMember(CommandSender sender, @Single String targetPlayerName) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(config.getOnlyPlayerCanRunCommandMessage());
            return;
        }

        OfflinePlayer targetPlayer = Bukkit.getOfflinePlayer(targetPlayerName);
        UUID playerUuid = player.getUniqueId();
        UUID targetPlayerUuid = targetPlayer.getUniqueId();

        api.getIslandUuid(playerUuid).thenCompose(islandUuid -> api.addMember(islandUuid, targetPlayerUuid, "member")).thenRun(() -> {
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
    @Syntax("<player>")
    @SuppressWarnings("unused")
    public void onRemoveMember(CommandSender sender, @Single String targetPlayerName) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(config.getOnlyPlayerCanRunCommandMessage());
            return;
        }

        OfflinePlayer targetPlayer = Bukkit.getOfflinePlayer(targetPlayerName);
        UUID playerUuid = player.getUniqueId();
        UUID targetPlayerUuid = targetPlayer.getUniqueId();

        api.getIslandUuid(playerUuid).thenCompose(islandUuid -> api.removeMember(islandUuid, targetPlayerUuid)).thenRun(() -> {
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
    @Syntax("[homeName]")
    @SuppressWarnings("unused")
    public void onHome(CommandSender sender, @Default("default") String homeName) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(config.getOnlyPlayerCanRunCommandMessage());
            return;
        }

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
    @Syntax("[homeName]")
    @SuppressWarnings("unused")
    public void onSetHome(CommandSender sender, @Default("default") String homeName) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(config.getOnlyPlayerCanRunCommandMessage());
            return;
        }

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
    @Syntax("<homeName>")
    @SuppressWarnings("unused")
    public void onDelHome(CommandSender sender, String homeName) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(config.getOnlyPlayerCanRunCommandMessage());
            return;
        }

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
    @CommandCompletion("@players @warps")
    @Description("Teleports you or another player to a specified warp point on an island")
    @Syntax("<player> [warpName]")
    @SuppressWarnings("unused")
    public void onWarp(CommandSender sender, @Single String warpPlayerName, @Default("default") String warpName) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(config.getOnlyPlayerCanRunCommandMessage());
            return;
        }

        OfflinePlayer target = Bukkit.getOfflinePlayer(warpPlayerName);

        api.warp(target.getUniqueId(), warpName, player.getUniqueId()).thenRun(() -> {
            player.sendMessage(config.getWarpSuccessMessage(warpName));
        }).exceptionally(ex -> {
            if (ex.getCause() instanceof IslandDoesNotExistException) {
                player.sendMessage(config.getNoIslandMessage(warpPlayerName));
            } else if (ex.getCause() instanceof WarpDoesNotExistException) {
                player.sendMessage(config.getNoWarpMessage(target.getName(), warpName));
            } else if (ex.getCause() instanceof PlayerBannedException) {
                player.sendMessage(config.getPlayerBannedMessage());
            } else if (ex.getCause() instanceof IslandLockedException) {
                player.sendMessage(config.getIslandLockedMessage());
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
    @Syntax("[warpName]")
    @SuppressWarnings("unused")
    public void onSetWarp(CommandSender sender, @Default("default") String warpName) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(config.getOnlyPlayerCanRunCommandMessage());
            return;
        }

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
    @Syntax("<warpName>")
    @SuppressWarnings("unused")
    public void onDelWarp(CommandSender sender, @Default("default") String warpName) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(config.getOnlyPlayerCanRunCommandMessage());
            return;
        }

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

    @Subcommand("setowner")
    @CommandPermission("newsky.island.setowner")
    @CommandCompletion("@players")
    @Description("Sets a new owner for your island")
    @Syntax("<player>")
    @SuppressWarnings("unused")
    public void onSetOwner(CommandSender sender, @Single String targetPlayerName) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(config.getOnlyPlayerCanRunCommandMessage());
            return;
        }

        OfflinePlayer targetPlayer = Bukkit.getOfflinePlayer(targetPlayerName);
        UUID playerUuid = player.getUniqueId();
        UUID targetPlayerUuid = targetPlayer.getUniqueId();

        api.getIslandUuid(playerUuid).thenCompose(islandUuid -> api.setOwner(islandUuid, targetPlayerUuid)).thenRun(() -> {
            player.sendMessage(config.getPlayerSetOwnerSuccessMessage(targetPlayerName));
        }).exceptionally(ex -> {
            if (ex.getCause() instanceof IslandDoesNotExistException) {
                player.sendMessage(config.getPlayerNoIslandMessage());
            } else if (ex.getCause() instanceof AlreadyOwnerException) {
                player.sendMessage(config.getPlayerAlreadyOwnerMessage(targetPlayerName));
            } else {
                player.sendMessage("There was an error setting the owner.");
                ex.printStackTrace();
            }
            return null;
        });
    }

    @Subcommand("leave")
    @CommandPermission("newsky.island.leave")
    @Description("Leaves your current island")
    @SuppressWarnings("unused")
    public void onLeave(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(config.getOnlyPlayerCanRunCommandMessage());
            return;
        }

        UUID playerUuid = player.getUniqueId();

        api.getIslandUuid(playerUuid).thenCompose(islandUuid -> api.removeMember(islandUuid, playerUuid)).thenRun(() -> {
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
    public void onLevel(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(config.getOnlyPlayerCanRunCommandMessage());
            return;
        }

        UUID playerUuid = player.getUniqueId();

        api.getIslandUuid(playerUuid).thenCompose(api::getIslandLevel).thenAccept(level -> {
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
    public void onLock(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(config.getOnlyPlayerCanRunCommandMessage());
            return;
        }

        UUID playerUuid = player.getUniqueId();

        api.getIslandUuid(playerUuid).thenCompose(api::toggleIslandLock).thenAccept(isLocked -> {
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
    public void onPvp(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(config.getOnlyPlayerCanRunCommandMessage());
            return;
        }

        UUID playerUuid = player.getUniqueId();

        api.getIslandUuid(playerUuid).thenCompose(api::toggleIslandPvp).thenAccept(isPvpEnabled -> {
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

    @Subcommand("top")
    @CommandPermission("newsky.island.top")
    @Description("Shows the top 10 islands by level")
    @SuppressWarnings("unused")
    public void onTop(CommandSender sender) {
        api.getTopIslandLevels(10).thenCompose(topIslands -> {
            if (topIslands.isEmpty()) {
                sender.sendMessage(config.getNoIslandsFoundMessage());
                return CompletableFuture.completedFuture(Collections.emptyList());
            }

            sender.sendMessage(config.getTopIslandsHeaderMessage());

            List<CompletableFuture<String>> islandInfoFutures = new ArrayList<>();
            for (int i = 0; i < topIslands.size(); i++) {
                Map.Entry<UUID, Integer> entry = topIslands.get(i);
                UUID islandUuid = entry.getKey();
                int level = entry.getValue();
                int finalI = i;

                CompletableFuture<String> islandInfoFuture = api.getIslandOwner(islandUuid).thenCombine(api.getIslandMembers(islandUuid), (ownerUuid, members) -> {
                    String ownerName = Bukkit.getOfflinePlayer(ownerUuid).getName();
                    String memberNames = members.stream().map(uuid -> Bukkit.getOfflinePlayer(uuid).getName()).reduce((a, b) -> a + ", " + b).orElse("");
                    return config.getTopIslandMessage(finalI + 1, ownerName, memberNames, level);
                }).exceptionally(ex -> {
                    ex.printStackTrace();
                    return null;
                });

                islandInfoFutures.add(islandInfoFuture);
            }

            return CompletableFuture.allOf(islandInfoFutures.toArray(new CompletableFuture[0])).thenApply(v -> islandInfoFutures.stream().map(CompletableFuture::join).filter(Objects::nonNull).collect(Collectors.toList()));
        }).thenAccept(islandInfos -> {
            if (islandInfos != null) {
                islandInfos.forEach(info -> sender.sendMessage((String) info));
            }
        }).exceptionally(ex -> {
            sender.sendMessage("There was an error getting the top islands.");
            ex.printStackTrace();
            return null;
        });
    }


    @Subcommand("info")
    @CommandPermission("newsky.island.info")
    @Description("Shows information about an island")
    @Syntax("[player]")
    @CommandCompletion("@players")
    @SuppressWarnings("unused")
    public void onInfo(CommandSender sender, @Optional @Single String targetPlayerName) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(config.getOnlyPlayerCanRunCommandMessage());
            return;
        }

        UUID playerUuid;
        if (targetPlayerName == null) {
            playerUuid = player.getUniqueId();
        } else {
            OfflinePlayer targetPlayer = Bukkit.getOfflinePlayer(targetPlayerName);
            playerUuid = targetPlayer.getUniqueId();
        }

        api.getIslandUuid(playerUuid).thenCompose(islandUuid -> {
            CompletableFuture<UUID> ownerFuture = api.getIslandOwner(islandUuid);
            CompletableFuture<Set<UUID>> membersFuture = api.getIslandMembers(islandUuid);
            CompletableFuture<Integer> levelFuture = api.getIslandLevel(islandUuid);

            return CompletableFuture.allOf(ownerFuture, membersFuture, levelFuture).thenApply(v -> {
                try {
                    UUID ownerUuid = ownerFuture.get();
                    Set<UUID> members = membersFuture.get();
                    int level = levelFuture.get();

                    String ownerName = Bukkit.getOfflinePlayer(ownerUuid).getName();
                    String memberNames = members.stream().map(uuid -> Bukkit.getOfflinePlayer(uuid).getName()).reduce((a, b) -> a + ", " + b).orElse(config.getIslandInfoNoMembersMessage());

                    sender.sendMessage(config.getIslandInfoUUIDMessage(islandUuid));
                    sender.sendMessage(config.getIslandInfoOwnerMessage(ownerName));
                    sender.sendMessage(config.getIslandInfoMembersMessage(memberNames));
                    sender.sendMessage(config.getIslandInfoLevelMessage(level));

                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
                return null;
            });
        }).exceptionally(ex -> {
            sender.sendMessage("There was an error getting the island information.");
            ex.printStackTrace();
            return null;
        });
    }


    @Subcommand("expel")
    @CommandPermission("newsky.island.expel")
    @Description("Expel a player from your island")
    @Syntax("<player>")
    @CommandCompletion("@players")
    @SuppressWarnings("unused")
    public void onExpel(CommandSender sender, @Single String targetPlayerName) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(config.getOnlyPlayerCanRunCommandMessage());
            return;
        }

        UUID playerUuid = player.getUniqueId();
        OfflinePlayer targetPlayer = Bukkit.getOfflinePlayer(targetPlayerName);
        UUID targetPlayerUuid = targetPlayer.getUniqueId();

        api.getIslandUuid(playerUuid).thenCompose(islandUuid -> api.expelPlayer(islandUuid, targetPlayerUuid).thenRun(() -> {
            sender.sendMessage(config.getPlayerExpelSuccessMessage(targetPlayerName));
        })).exceptionally(ex -> {
            if (ex.getCause() instanceof IslandDoesNotExistException) {
                sender.sendMessage(config.getPlayerNoIslandMessage());
            } else {
                sender.sendMessage("There was an error expelling the player.");
                ex.printStackTrace();
            }
            return null;
        });
    }

    @Subcommand("ban")
    @CommandPermission("newsky.island.ban")
    @CommandCompletion("@players")
    @Description("Bans a player from your island")
    @Syntax("<player>")
    @SuppressWarnings("unused")
    public void onBan(CommandSender sender, @Single String targetPlayerName) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(config.getOnlyPlayerCanRunCommandMessage());
            return;
        }

        OfflinePlayer targetPlayer = Bukkit.getOfflinePlayer(targetPlayerName);
        UUID playerUuid = player.getUniqueId();
        UUID targetPlayerUuid = targetPlayer.getUniqueId();

        api.getIslandUuid(playerUuid).thenCompose(islandUuid -> api.banPlayer(islandUuid, targetPlayerUuid)).thenRun(() -> {
            player.sendMessage(config.getPlayerBanSuccessMessage(targetPlayerName));
        }).exceptionally(ex -> {
            if (ex.getCause() instanceof IslandDoesNotExistException) {
                player.sendMessage(config.getPlayerNoIslandMessage());
            } else if (ex.getCause() instanceof PlayerAlreadyBannedException) {
                player.sendMessage(config.getPlayerAlreadyBannedMessage(targetPlayerName));
            } else if (ex.getCause() instanceof CannotBanIslandPlayerException) {
                player.sendMessage(config.getPlayerCannotBanIslandPlayerMessage());
            } else {
                player.sendMessage("There was an error banning the player.");
                ex.printStackTrace();
            }
            return null;
        });
    }

    @Subcommand("unban")
    @CommandPermission("newsky.island.unban")
    @CommandCompletion("@players")
    @Description("Unbans a player from your island")
    @Syntax("<player>")
    @SuppressWarnings("unused")
    public void onUnban(CommandSender sender, @Single String targetPlayerName) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(config.getOnlyPlayerCanRunCommandMessage());
            return;
        }

        OfflinePlayer targetPlayer = Bukkit.getOfflinePlayer(targetPlayerName);
        UUID playerUuid = player.getUniqueId();
        UUID targetPlayerUuid = targetPlayer.getUniqueId();

        api.getIslandUuid(playerUuid).thenCompose(islandUuid -> api.unbanPlayer(islandUuid, targetPlayerUuid)).thenRun(() -> {
            player.sendMessage(config.getPlayerUnbanSuccessMessage(targetPlayerName));
        }).exceptionally(ex -> {
            if (ex.getCause() instanceof IslandDoesNotExistException) {
                player.sendMessage(config.getPlayerNoIslandMessage());
            } else if (ex.getCause() instanceof PlayerNotBannedException) {
                player.sendMessage(config.getPlayerNotBannedMessage(targetPlayerName));
            } else {
                player.sendMessage("There was an error unbanning the player.");
                ex.printStackTrace();
            }
            return null;
        });
    }

    @Subcommand("banlist")
    @CommandPermission("newsky.island.banlist")
    @Description("Lists the players banned from your island")
    @SuppressWarnings("unused")
    public void onBanList(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(config.getOnlyPlayerCanRunCommandMessage());
            return;
        }

        UUID playerUuid = player.getUniqueId();

        api.getIslandUuid(playerUuid).thenCompose(api::getBannedPlayers).thenAccept(bannedPlayers -> {
            if (bannedPlayers.isEmpty()) {
                player.sendMessage(config.getNoBannedPlayersMessage());
                return;
            }

            StringBuilder bannedList = new StringBuilder(config.getBannedPlayersHeaderMessage());
            for (UUID bannedPlayerUuid : bannedPlayers) {
                OfflinePlayer bannedPlayer = Bukkit.getOfflinePlayer(bannedPlayerUuid);
                String playerName = bannedPlayer.getName();
                if (playerName == null) {
                    playerName = bannedPlayerUuid.toString();
                }
                bannedList.append(config.getBannedPlayerMessage(playerName));
            }
            player.sendMessage(bannedList.toString());
        }).exceptionally(ex -> {
            if (ex.getCause() instanceof IslandDoesNotExistException) {
                player.sendMessage(config.getPlayerNoIslandMessage());
            } else {
                player.sendMessage("There was an error retrieving the ban list.");
                ex.printStackTrace();
            }
            return null;
        });
    }
}
