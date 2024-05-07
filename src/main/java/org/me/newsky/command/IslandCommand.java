package org.me.newsky.command;

import co.aikar.commands.BaseCommand;
import co.aikar.commands.annotation.*;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.me.newsky.api.NewSkyAPI;
import org.me.newsky.config.ConfigHandler;
import org.me.newsky.exceptions.*;

import java.util.UUID;

@CommandAlias("is")
@Description("Primary command for island interactions")
public class IslandCommand extends BaseCommand {

    private final ConfigHandler config;
    private final NewSkyAPI api;

    public IslandCommand(ConfigHandler config, NewSkyAPI api) {
        this.config = config;
        this.api = api;
    }

    @Subcommand("home")
    @CommandPermission("newsky.island.home")
    @CommandCompletion("@homes")
    @Description("Teleports you to your island home")
    public void onHome(Player player, @Default("default") String homeName) {
        api.home(player.getUniqueId(), homeName).thenRun(() -> {
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
    public void onWarp(Player player, String warpName) {
        api.warp(player.getUniqueId(), warpName).thenRun(() -> {
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

    @Subcommand("create")
    @CommandPermission("newsky.island.create")
    @Description("Creates an island for the player")
    public void onCreate(Player player) {
        UUID playerUuid = player.getUniqueId();

        api.createIsland(playerUuid).thenRun(() -> {
            player.sendMessage(config.getPlayerCreateSuccessMessage());
            api.home(player.getUniqueId(), "default").thenRun(() -> {
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
    public void onDelete(Player player) {
        api.deleteIsland(player.getUniqueId()).thenRun(() -> {
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
    }
}
