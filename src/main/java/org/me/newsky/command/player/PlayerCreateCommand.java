package org.me.newsky.command.player;

import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.me.newsky.api.NewSkyAPI;
import org.me.newsky.cache.CacheHandler;
import org.me.newsky.command.base.BaseCreateCommand;
import org.me.newsky.config.ConfigHandler;
import org.me.newsky.island.IslandHandler;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public class PlayerCreateCommand extends BaseCreateCommand {

    public PlayerCreateCommand(ConfigHandler config, NewSkyAPI api) {
        super(config, api);
    }

    @Override
    protected boolean validateArgs(CommandSender sender, String[] args) {
        return true;
    }

    @Override
    protected UUID getTargetUuid(CommandSender sender, String[] args) {
        return ((Player) sender).getUniqueId();
    }

    @Override
    protected String getExistingIslandMessage(String[] args) {
        return config.getPlayerAlreadyHasIslandMessage();
    }

    @Override
    protected String getIslandCreateSuccessMessage(String[] args) {
        return config.getPlayerCreateSuccessMessage();
    }


    @Override
    public String getUsageCommandMessage() {
        return config.getPlayerCreateUsageMessage();
    }
}
