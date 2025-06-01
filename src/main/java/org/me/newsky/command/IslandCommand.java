package org.me.newsky.command;

import co.aikar.commands.BaseCommand;
import co.aikar.commands.CommandHelp;
import co.aikar.commands.annotation.Optional;
import co.aikar.commands.annotation.*;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.me.newsky.NewSky;
import org.me.newsky.api.NewSkyAPI;
import org.me.newsky.config.ConfigHandler;
import org.me.newsky.exceptions.*;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Level;
import java.util.stream.Collectors;

@CommandAlias("is|island")
public class IslandCommand extends BaseCommand {

    private final NewSky plugin;
    private final ConfigHandler config;
    private final NewSkyAPI api;
    private final Map<UUID, Long> confirmationTimes = new HashMap<>();

    public IslandCommand(NewSky plugin, ConfigHandler config, NewSkyAPI api) {
        this.plugin = plugin;
        this.config = config;
        this.api = api;
    }

    @HelpCommand
    @CommandPermission("newsky.island.help")
    @SuppressWarnings("unused")
    public void onHelp(CommandSender sender, CommandHelp help) {
        help.showHelp();
    }

    @Subcommand("create")
    @CommandPermission("newsky.island.create")
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
            api.home(playerUuid, "default", playerUuid).thenRun(() -> player.sendMessage(config.getPlayerHomeSuccessMessage("default")));
        }).exceptionally(ex -> {
            if (ex.getCause() instanceof IslandAlreadyExistException) {
                player.sendMessage(config.getPlayerAlreadyHasIslandMessage());
            } else if (ex.getCause() instanceof NoActiveServerException) {
                player.sendMessage(config.getNoActiveServerMessage());
            } else {
                player.sendMessage("There was an error creating the island");
                plugin.getLogger().log(Level.SEVERE, "Error creating island for player " + player.getName(), ex);
            }
            return null;
        });
    }

    @Subcommand("delete")
    @CommandPermission("newsky.island.delete")
    @SuppressWarnings("unused")
    public void onDelete(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(config.getOnlyPlayerCanRunCommandMessage());
            return;
        }

        UUID playerUuid = player.getUniqueId();

        api.getIslandUuid(playerUuid).thenAccept(islandUuid -> {
            if (islandUuid == null) {
                player.sendMessage(config.getPlayerNoIslandMessage());
                return;
            }

            if (confirmationTimes.containsKey(playerUuid) && (System.currentTimeMillis() - confirmationTimes.get(playerUuid) < 15000)) {

                confirmationTimes.remove(playerUuid);

                api.deleteIsland(islandUuid).thenRun(() -> player.sendMessage(config.getPlayerDeleteSuccessMessage())).exceptionally(ex -> {
                    if (ex.getCause() instanceof NoActiveServerException) {
                        player.sendMessage(config.getNoActiveServerMessage());
                    } else {
                        player.sendMessage("There was an error deleting the island");
                        plugin.getLogger().log(Level.SEVERE, "Error deleting island for player " + player.getName(), ex);
                    }
                    return null;
                });

            } else {
                confirmationTimes.put(playerUuid, System.currentTimeMillis());
                player.sendMessage(config.getPlayerDeleteWarningMessage());
            }

        }).exceptionally(ex -> {
            if (ex.getCause() instanceof IslandDoesNotExistException) {
                player.sendMessage(config.getPlayerNoIslandMessage());
            } else {
                player.sendMessage("There was an error checking your island");
                plugin.getLogger().log(Level.SEVERE, "Error checking island for player " + player.getName(), ex);
            }
            return null;
        });
    }

    @Subcommand("addmember")
    @CommandPermission("newsky.island.addmember")
    @CommandCompletion("@globalplayers")
    @Syntax("<player>")
    @SuppressWarnings("unused")
    public void onAddMember(CommandSender sender, @Single String targetPlayerName) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(config.getOnlyPlayerCanRunCommandMessage());
            return;
        }

        if (!api.getOnlinePlayers().contains(targetPlayerName)) {
            player.sendMessage(config.getPlayerNotOnlineMessage(targetPlayerName));
            return;
        }

        OfflinePlayer targetPlayer = Bukkit.getOfflinePlayer(targetPlayerName);
        UUID playerUuid = player.getUniqueId();
        UUID targetPlayerUuid = targetPlayer.getUniqueId();

        api.getIslandUuid(playerUuid).thenCompose(islandUuid -> api.addMember(islandUuid, targetPlayerUuid, "member")).thenRun(() -> player.sendMessage(config.getPlayerAddMemberSuccessMessage(targetPlayerName))).exceptionally(ex -> {
            if (ex.getCause() instanceof IslandDoesNotExistException) {
                player.sendMessage(config.getPlayerNoIslandMessage());
            } else if (ex.getCause() instanceof PlayerAlreadyInAnotherIslandException) {
                player.sendMessage(config.getPlayerAlreadyHasIslandOtherMessage(targetPlayerName));
            } else if (ex.getCause() instanceof IslandPlayerAlreadyExistsException) {
                player.sendMessage(config.getIslandMemberExistsMessage(targetPlayerName));
            } else {
                player.sendMessage("There was an error adding the member.");
                plugin.getLogger().log(Level.SEVERE, "Error adding member to island for player " + player.getName(), ex);
            }
            return null;
        });
    }

    @Subcommand("removemember")
    @CommandPermission("newsky.island.removemember")
    @CommandCompletion("@globalplayers")
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

        api.getIslandUuid(playerUuid).thenCompose(islandUuid -> api.removeMember(islandUuid, targetPlayerUuid)).thenRun(() -> player.sendMessage(config.getPlayerRemoveMemberSuccessMessage(targetPlayerName))).exceptionally(ex -> {
            if (ex.getCause() instanceof IslandDoesNotExistException) {
                player.sendMessage(config.getPlayerNoIslandMessage());
            } else if (ex.getCause() instanceof IslandPlayerDoesNotExistException) {
                player.sendMessage(config.getIslandMemberNotExistsMessage(targetPlayerName));
            } else {
                player.sendMessage("There was an error removing the member.");
                plugin.getLogger().log(Level.SEVERE, "Error removing member from island for player " + player.getName(), ex);
            }
            return null;
        });
    }

    @Subcommand("home")
    @CommandPermission("newsky.island.home")
    @CommandCompletion("@homes")
    @Syntax("[homeName]")
    @SuppressWarnings("unused")
    public void onHome(CommandSender sender, @Default("default") String homeName) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(config.getOnlyPlayerCanRunCommandMessage());
            return;
        }

        api.home(player.getUniqueId(), homeName, player.getUniqueId()).thenRun(() -> player.sendMessage(config.getPlayerHomeSuccessMessage(homeName))).exceptionally(ex -> {
            if (ex.getCause() instanceof IslandDoesNotExistException) {
                player.sendMessage(config.getPlayerNoIslandMessage());
            } else if (ex.getCause() instanceof HomeDoesNotExistException) {
                player.sendMessage(config.getPlayerNoHomeMessage(homeName));
            } else if (ex.getCause() instanceof NoActiveServerException) {
                player.sendMessage(config.getNoActiveServerMessage());
            } else {
                player.sendMessage("There was an error teleporting to the home.");
                plugin.getLogger().log(Level.SEVERE, "Error teleporting to home for player " + player.getName(), ex);
            }
            return null;
        });
    }

    @Subcommand("sethome")
    @CommandPermission("newsky.island.sethome")
    @CommandCompletion("@homes")
    @Syntax("[homeName]")
    @SuppressWarnings("unused")
    public void onSetHome(CommandSender sender, @Default("default") String homeName) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(config.getOnlyPlayerCanRunCommandMessage());
            return;
        }

        Location loc = player.getLocation();

        api.setHome(player.getUniqueId(), homeName, loc).thenRun(() -> player.sendMessage(config.getPlayerSetHomeSuccessMessage(homeName))).exceptionally(ex -> {
            if (ex.getCause() instanceof IslandDoesNotExistException) {
                player.sendMessage(config.getPlayerNoIslandMessage());
            } else if (ex.getCause() instanceof LocationNotInIslandException) {
                player.sendMessage(config.getPlayerMustInIslandSetHomeMessage());
            } else {
                player.sendMessage("There was an error setting the home.");
                plugin.getLogger().log(Level.SEVERE, "Error setting home for player " + player.getName(), ex);
            }
            return null;
        });
    }

    @Subcommand("delhome")
    @CommandPermission("newsky.island.delhome")
    @CommandCompletion("@homes")
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

        api.delHome(player.getUniqueId(), homeName).thenRun(() -> player.sendMessage(config.getPlayerDelHomeSuccessMessage(homeName))).exceptionally(ex -> {
            if (ex.getCause() instanceof IslandDoesNotExistException) {
                player.sendMessage(config.getPlayerNoIslandMessage());
            } else if (ex.getCause() instanceof HomeDoesNotExistException) {
                player.sendMessage(config.getPlayerNoHomeMessage(homeName));
            } else {
                player.sendMessage("There was an error deleting the home.");
                plugin.getLogger().log(Level.SEVERE, "Error deleting home for player " + player.getName(), ex);
            }
            return null;
        });
    }

    @Subcommand("warp")
    @CommandPermission("newsky.island.warp")
    @CommandCompletion("@globalplayers @warps")
    @Syntax("<player> [warpName]")
    @SuppressWarnings("unused")
    public void onWarp(CommandSender sender, @Single String warpPlayerName, @Default("default") String warpName) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(config.getOnlyPlayerCanRunCommandMessage());
            return;
        }

        OfflinePlayer target = Bukkit.getOfflinePlayer(warpPlayerName);

        api.warp(target.getUniqueId(), warpName, player.getUniqueId()).thenRun(() -> player.sendMessage(config.getWarpSuccessMessage(warpName))).exceptionally(ex -> {
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
                plugin.getLogger().log(Level.SEVERE, "Error teleporting to warp for player " + player.getName(), ex);
            }
            return null;
        });
    }


    @Subcommand("setwarp")
    @CommandPermission("newsky.island.setwarp")
    @CommandCompletion("@warps")
    @Syntax("[warpName]")
    @SuppressWarnings("unused")
    public void onSetWarp(CommandSender sender, @Default("default") String warpName) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(config.getOnlyPlayerCanRunCommandMessage());
            return;
        }

        Location loc = player.getLocation();

        api.setWarp(player.getUniqueId(), warpName, loc).thenRun(() -> player.sendMessage(config.getPlayerSetWarpSuccessMessage(warpName))).exceptionally(ex -> {
            if (ex.getCause() instanceof IslandDoesNotExistException) {
                player.sendMessage(config.getPlayerNoIslandMessage());
            } else if (ex.getCause() instanceof LocationNotInIslandException) {
                player.sendMessage(config.getPlayerMustInIslandSetWarpMessage());
            } else {
                player.sendMessage("There was an error setting the warp");
                plugin.getLogger().log(Level.SEVERE, "Error setting warp for player " + player.getName(), ex);
            }
            return null;
        });
    }

    @Subcommand("delwarp")
    @CommandPermission("newsky.island.delwarp")
    @CommandCompletion("@warps")
    @Syntax("<warpName>")
    @SuppressWarnings("unused")
    public void onDelWarp(CommandSender sender, @Default("default") String warpName) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(config.getOnlyPlayerCanRunCommandMessage());
            return;
        }

        api.delWarp(player.getUniqueId(), warpName).thenRun(() -> player.sendMessage(config.getPlayerDelWarpSuccessMessage(warpName))).exceptionally(ex -> {
            if (ex.getCause() instanceof IslandDoesNotExistException) {
                player.sendMessage(config.getPlayerNoIslandMessage());
            } else if (ex.getCause() instanceof WarpDoesNotExistException) {
                player.sendMessage(config.getPlayerNoWarpMessage(warpName));
            } else {
                player.sendMessage("There was an error deleting the warp.");
                plugin.getLogger().log(Level.SEVERE, "Error deleting warp for player " + player.getName(), ex);
            }
            return null;
        });
    }

    @Subcommand("setowner")
    @CommandPermission("newsky.island.setowner")
    @CommandCompletion("@globalplayers")
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

        api.getIslandUuid(playerUuid).thenCompose(islandUuid -> api.setOwner(islandUuid, targetPlayerUuid)).thenRun(() -> player.sendMessage(config.getPlayerSetOwnerSuccessMessage(targetPlayerName))).exceptionally(ex -> {
            if (ex.getCause() instanceof IslandDoesNotExistException) {
                player.sendMessage(config.getPlayerNoIslandMessage());
            } else if (ex.getCause() instanceof AlreadyOwnerException) {
                player.sendMessage(config.getPlayerAlreadyOwnerMessage(targetPlayerName));
            } else {
                player.sendMessage("There was an error setting the owner.");
                plugin.getLogger().log(Level.SEVERE, "Error setting owner for island for player " + player.getName(), ex);
            }
            return null;
        });
    }

    @Subcommand("leave")
    @CommandPermission("newsky.island.leave")
    @SuppressWarnings("unused")
    public void onLeave(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(config.getOnlyPlayerCanRunCommandMessage());
            return;
        }

        UUID playerUuid = player.getUniqueId();

        api.getIslandUuid(playerUuid).thenCompose(islandUuid -> api.removeMember(islandUuid, playerUuid)).thenRun(() -> player.sendMessage(config.getPlayerLeaveSuccessMessage())).exceptionally(ex -> {
            if (ex.getCause() instanceof IslandDoesNotExistException) {
                player.sendMessage(config.getPlayerNoIslandMessage());
            } else if (ex.getCause() instanceof CannotRemoveOwnerException) {
                player.sendMessage(config.getPlayerCannotLeaveAsOwnerMessage());
            } else if (ex.getCause() instanceof IslandPlayerDoesNotExistException) {
                player.sendMessage(config.getPlayerNoIslandMessage());
            } else {
                player.sendMessage("There was an error leaving the island");
                plugin.getLogger().log(Level.SEVERE, "Error leaving island for player " + player.getName(), ex);
            }
            return null;
        });
    }

    @Subcommand("level")
    @CommandPermission("newsky.island.level")
    @SuppressWarnings("unused")
    public void onLevel(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(config.getOnlyPlayerCanRunCommandMessage());
            return;
        }

        UUID playerUuid = player.getUniqueId();

        api.getIslandUuid(playerUuid).thenCompose(api::getIslandLevel).thenAccept(level -> player.sendMessage(config.getIslandLevelMessage(level))).exceptionally(ex -> {
            if (ex.getCause() instanceof IslandDoesNotExistException) {
                player.sendMessage(config.getPlayerNoIslandMessage());
            } else {
                player.sendMessage("There was an error calculating the island level.");
                plugin.getLogger().log(Level.SEVERE, "Error calculating island level for player " + player.getName(), ex);
            }
            return null;
        });
    }

    @Subcommand("value")
    @CommandPermission("newsky.island.value")
    @SuppressWarnings("unused")
    public void onValue(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(config.getOnlyPlayerCanRunCommandMessage());
            return;
        }

        // Get the item in the player's main hand
        ItemStack itemInHand = player.getInventory().getItemInMainHand();

        // Check if the item is not air or null
        if (itemInHand.getType() == Material.AIR) {
            player.sendMessage(config.getPlayerNoItemInHandMessage());
            return;
        }

        // Get the material of the item
        Material material = itemInHand.getType();

        // Get the block level value from the configuration
        int value = config.getBlockLevel(material.name());

        // Send the value to the player
        player.sendMessage(config.getPlayerBlockValueCommandMessage(material.name(), value));
    }

    @Subcommand("lock")
    @CommandPermission("newsky.island.togglelock")
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
                plugin.getLogger().log(Level.SEVERE, "Error toggling island lock status for player " + player.getName(), ex);
            }
            return null;
        });
    }

    @Subcommand("pvp")
    @CommandPermission("newsky.island.togglepvp")
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
                plugin.getLogger().log(Level.SEVERE, "Error toggling PvP status for player " + player.getName(), ex);
            }
            return null;
        });
    }

    @Subcommand("top")
    @CommandPermission("newsky.island.top")
    @SuppressWarnings("unused")
    public void onTop(CommandSender sender) {
        api.getTopIslandLevels(10).thenCompose(topIslands -> {
            if (topIslands.isEmpty()) {
                sender.sendMessage(config.getNoIslandsFoundMessage());
                return CompletableFuture.completedFuture(Collections.emptyList());
            }

            sender.sendMessage(config.getTopIslandsHeaderMessage());

            List<CompletableFuture<Component>> islandInfoFutures = topIslands.entrySet().stream().map(entry -> {
                UUID islandUuid = entry.getKey();
                int level = entry.getValue();
                int rank = new ArrayList<>(topIslands.keySet()).indexOf(islandUuid) + 1;

                return api.getIslandOwner(islandUuid).thenCombine(api.getIslandMembers(islandUuid), (ownerUuid, members) -> {
                    String ownerName = Bukkit.getOfflinePlayer(ownerUuid).getName();
                    String memberNames = members.stream().map(uuid -> Bukkit.getOfflinePlayer(uuid).getName()).collect(Collectors.joining(", "));
                    return config.getTopIslandMessage(rank, ownerName, memberNames, level); // ✅ returns Component
                }).exceptionally(ex -> {
                    plugin.getLogger().log(Level.SEVERE, "Failed to build top island message for island " + islandUuid, ex);
                    return Component.text("There was an error retrieving the island information.");
                });
            }).toList();


            return CompletableFuture.allOf(islandInfoFutures.toArray(new CompletableFuture[0])).thenApply(v -> islandInfoFutures.stream().map(CompletableFuture::join).filter(Objects::nonNull).collect(Collectors.toList()));
        }).thenAccept(islandInfos -> {
            if (islandInfos != null) {
                islandInfos.forEach(info -> sender.sendMessage((String) info));
            }
        }).exceptionally(ex -> {
            sender.sendMessage("There was an error getting the top islands.");
            plugin.getLogger().log(Level.SEVERE, "Error getting top islands", ex);
            return null;
        });
    }

    @Subcommand("info")
    @CommandPermission("newsky.island.info")
    @Syntax("[player]")
    @CommandCompletion("@globalplayers")
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
                    String memberNames = members.stream().map(uuid -> Bukkit.getOfflinePlayer(uuid).getName()).reduce((a, b) -> a + ", " + b).orElse(LegacyComponentSerializer.legacyAmpersand().serialize(config.getIslandInfoNoMembersMessage()));

                    sender.sendMessage(config.getIslandInfoHeaderMessage());
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
            if (ex.getCause() instanceof IslandDoesNotExistException) {
                sender.sendMessage(config.getPlayerNoIslandMessage());
            } else {
                sender.sendMessage("There was an error getting the island information.");
                plugin.getLogger().log(Level.SEVERE, "Error getting island information for player " + player.getName(), ex);
            }
            return null;
        });
    }


    @Subcommand("expel")
    @CommandPermission("newsky.island.expel")
    @Syntax("<player>")
    @CommandCompletion("@globalplayers")
    @SuppressWarnings("unused")
    public void onExpel(CommandSender sender, @Single String targetPlayerName) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(config.getOnlyPlayerCanRunCommandMessage());
            return;
        }

        if (!api.getOnlinePlayers().contains(targetPlayerName)) {
            player.sendMessage(config.getPlayerNotOnlineMessage(targetPlayerName));
            return;
        }

        UUID playerUuid = player.getUniqueId();
        OfflinePlayer targetPlayer = Bukkit.getOfflinePlayer(targetPlayerName);
        UUID targetPlayerUuid = targetPlayer.getUniqueId();

        api.getIslandUuid(playerUuid).thenCompose(islandUuid -> api.expelPlayer(islandUuid, targetPlayerUuid).thenRun(() -> sender.sendMessage(config.getPlayerExpelSuccessMessage(targetPlayerName)))).exceptionally(ex -> {
            if (ex.getCause() instanceof IslandDoesNotExistException) {
                sender.sendMessage(config.getPlayerNoIslandMessage());
            } else {
                sender.sendMessage("There was an error expelling the player.");
                plugin.getLogger().log(Level.SEVERE, "Error expelling player " + targetPlayerName + " from island of player " + player.getName(), ex);
            }
            return null;
        });
    }

    @Subcommand("ban")
    @CommandPermission("newsky.island.ban")
    @CommandCompletion("@globalplayers")
    @Syntax("<player>")
    @SuppressWarnings("unused")
    public void onBan(CommandSender sender, @Single String targetPlayerName) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(config.getOnlyPlayerCanRunCommandMessage());
            return;
        }

        if (!api.getOnlinePlayers().contains(targetPlayerName)) {
            player.sendMessage(config.getPlayerNotOnlineMessage(targetPlayerName));
            return;
        }

        OfflinePlayer targetPlayer = Bukkit.getOfflinePlayer(targetPlayerName);
        UUID playerUuid = player.getUniqueId();
        UUID targetPlayerUuid = targetPlayer.getUniqueId();

        api.getIslandUuid(playerUuid).thenCompose(islandUuid -> api.banPlayer(islandUuid, targetPlayerUuid)).thenRun(() -> player.sendMessage(config.getPlayerBanSuccessMessage(targetPlayerName))).exceptionally(ex -> {
            if (ex.getCause() instanceof IslandDoesNotExistException) {
                player.sendMessage(config.getPlayerNoIslandMessage());
            } else if (ex.getCause() instanceof PlayerAlreadyBannedException) {
                player.sendMessage(config.getPlayerAlreadyBannedMessage(targetPlayerName));
            } else if (ex.getCause() instanceof CannotBanIslandPlayerException) {
                player.sendMessage(config.getPlayerCannotBanIslandPlayerMessage());
            } else {
                player.sendMessage("There was an error banning the player.");
                plugin.getLogger().log(Level.SEVERE, "Error banning player " + targetPlayerName + " from island of player " + player.getName(), ex);
            }
            return null;
        });
    }

    @Subcommand("unban")
    @CommandPermission("newsky.island.unban")
    @CommandCompletion("@globalplayers")
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

        api.getIslandUuid(playerUuid).thenCompose(islandUuid -> api.unbanPlayer(islandUuid, targetPlayerUuid)).thenRun(() -> player.sendMessage(config.getPlayerUnbanSuccessMessage(targetPlayerName))).exceptionally(ex -> {
            if (ex.getCause() instanceof IslandDoesNotExistException) {
                player.sendMessage(config.getPlayerNoIslandMessage());
            } else if (ex.getCause() instanceof PlayerNotBannedException) {
                player.sendMessage(config.getPlayerNotBannedMessage(targetPlayerName));
            } else {
                player.sendMessage("There was an error unbanning the player.");
                plugin.getLogger().log(Level.SEVERE, "Error unbanning player " + targetPlayerName + " from island of player " + player.getName(), ex);
            }
            return null;
        });
    }

    @Subcommand("banlist")
    @CommandPermission("newsky.island.banlist")
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

            Component bannedList = config.getBannedPlayersHeaderMessage();
            for (UUID bannedPlayerUuid : bannedPlayers) {
                OfflinePlayer bannedPlayer = Bukkit.getOfflinePlayer(bannedPlayerUuid);
                String playerName = bannedPlayer.getName();
                if (playerName == null) {
                    playerName = bannedPlayerUuid.toString();
                }
                bannedList = bannedList.append(config.getBannedPlayerMessage(playerName));
            }

            player.sendMessage(bannedList);

        }).exceptionally(ex -> {
            if (ex.getCause() instanceof IslandDoesNotExistException) {
                player.sendMessage(config.getPlayerNoIslandMessage());
            } else {
                player.sendMessage(Component.text("There was an error retrieving the ban list."));
                plugin.getLogger().log(Level.SEVERE, "Error retrieving ban list for " + player.getName(), ex);
            }
            return null;
        });
    }

    @Subcommand("coop")
    @CommandPermission("newsky.island.coop")
    @Syntax("<player>")
    @CommandCompletion("@globalplayers")
    @SuppressWarnings("unused")
    public void onCoop(CommandSender sender, @Single String targetPlayerName) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(config.getOnlyPlayerCanRunCommandMessage());
            return;
        }

        OfflinePlayer target = Bukkit.getOfflinePlayer(targetPlayerName);
        UUID playerUuid = player.getUniqueId();
        UUID targetUuid = target.getUniqueId();

        if (playerUuid.equals(targetUuid)) {
            player.sendMessage(Component.text("You cannot coop yourself."));
            return;
        }

        api.getIslandUuid(playerUuid).thenCompose(islandUuid -> api.addCoop(islandUuid, targetUuid)).thenRun(() -> player.sendMessage(config.getPlayerCoopSuccessMessage(targetPlayerName))).exceptionally(ex -> {
            if (ex.getCause() instanceof IslandDoesNotExistException) {
                player.sendMessage(config.getPlayerNoIslandMessage());
            } else if (ex.getCause() instanceof PlayerAlreadyCoopedException) {
                player.sendMessage(config.getPlayerAlreadyCoopedMessage(targetPlayerName));
            } else if (ex.getCause() instanceof CannotCoopIslandPlayerException) {
                player.sendMessage(config.getPlayerCannotCoopIslandPlayerMessage());
            } else {
                player.sendMessage(Component.text("There was an error cooping the player."));
                plugin.getLogger().log(Level.SEVERE, "Error cooping player " + targetPlayerName + " for " + player.getName(), ex);
            }
            return null;
        });
    }

    @Subcommand("uncoop")
    @CommandPermission("newsky.island.uncoop")
    @Syntax("<player>")
    @CommandCompletion("@globalplayers")
    @SuppressWarnings("unused")
    public void onUncoop(CommandSender sender, @Single String targetPlayerName) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(config.getOnlyPlayerCanRunCommandMessage());
            return;
        }

        OfflinePlayer target = Bukkit.getOfflinePlayer(targetPlayerName);
        UUID playerUuid = player.getUniqueId();
        UUID targetUuid = target.getUniqueId();

        api.getIslandUuid(playerUuid).thenCompose(islandUuid -> api.removeCoop(islandUuid, targetUuid)).thenRun(() -> player.sendMessage(config.getPlayerUncoopSuccessMessage(targetPlayerName))).exceptionally(ex -> {
            if (ex.getCause() instanceof IslandDoesNotExistException) {
                player.sendMessage(config.getPlayerNoIslandMessage());
            } else if (ex.getCause() instanceof PlayerNotCoopedException) {
                player.sendMessage(config.getPlayerNotCoopedMessage(targetPlayerName));
            } else {
                player.sendMessage(Component.text("There was an error uncooping the player."));
                plugin.getLogger().log(Level.SEVERE, "Error uncooping player " + targetPlayerName + " for " + player.getName(), ex);
            }
            return null;
        });
    }

    @Subcommand("cooplist")
    @CommandPermission("newsky.island.cooplist")
    @SuppressWarnings("unused")
    public void onCoopList(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(config.getOnlyPlayerCanRunCommandMessage());
            return;
        }

        UUID playerUuid = player.getUniqueId();

        api.getIslandUuid(playerUuid).thenCompose(api::getCoopedPlayers).thenAccept(coopedPlayers -> {
            if (coopedPlayers.isEmpty()) {
                player.sendMessage(config.getNoCoopedPlayersMessage());
                return;
            }

            Component coopList = config.getCoopedPlayersHeaderMessage();
            for (UUID coopedPlayerUuid : coopedPlayers) {
                OfflinePlayer coopedPlayer = Bukkit.getOfflinePlayer(coopedPlayerUuid);
                String name = coopedPlayer.getName() != null ? coopedPlayer.getName() : coopedPlayerUuid.toString();
                coopList = coopList.append(config.getCoopedPlayerMessage(name));
            }

            player.sendMessage(coopList);

        }).exceptionally(ex -> {
            if (ex.getCause() instanceof IslandDoesNotExistException) {
                player.sendMessage(config.getPlayerNoIslandMessage());
            } else {
                player.sendMessage(Component.text("There was an error retrieving the coop list."));
                plugin.getLogger().log(Level.SEVERE, "Error retrieving coop list for " + player.getName(), ex);
            }
            return null;
        });
    }
}
