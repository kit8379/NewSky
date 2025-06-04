package org.me.newsky.command.player;

import org.bukkit.Material;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.me.newsky.command.SubCommand;
import org.me.newsky.config.ConfigHandler;

/**
 * /is value
 */
public class PlayerValueCommand implements SubCommand {
    private final ConfigHandler config;

    public PlayerValueCommand(ConfigHandler config) {
        this.config = config;
    }

    @Override
    public String getName() {
        return "value";
    }

    @Override
    public String[] getAliases() {
        return config.getPlayerValueAliases();
    }

    @Override
    public String getPermission() {
        return config.getPlayerValuePermission();
    }

    @Override
    public String getSyntax() {
        return config.getPlayerValueSyntax();
    }

    @Override
    public String getDescription() {
        return config.getPlayerValueDescription();
    }

    @Override
    public boolean execute(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(config.getOnlyPlayerCanRunCommandMessage());
            return true;
        }

        ItemStack itemInHand = player.getInventory().getItemInMainHand();
        if (itemInHand.getType() == Material.AIR) {
            player.sendMessage(config.getPlayerNoItemInHandMessage());
            return true;
        }

        Material material = itemInHand.getType();
        int value = config.getBlockLevel(material.name());
        player.sendMessage(config.getPlayerBlockValueCommandMessage(material.name(), value));

        return true;
    }
}