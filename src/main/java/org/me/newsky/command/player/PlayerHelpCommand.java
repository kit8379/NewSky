package org.me.newsky.command.player;

import org.bukkit.command.CommandSender;
import org.me.newsky.command.SubCommand;
import org.me.newsky.config.ConfigHandler;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;

/**
 * /is help [page]
 */
public class PlayerHelpCommand implements SubCommand {
    private final ConfigHandler config;
    private final Set<SubCommand> allSubs;
    private static final int COMMANDS_PER_PAGE = 8;

    public PlayerHelpCommand(ConfigHandler config, Set<SubCommand> allSubs) {
        this.config = config;
        this.allSubs = allSubs;
    }

    @Override
    public String getName() {
        return "help";
    }

    @Override
    public String[] getAliases() {
        return config.getPlayerHelpAliases();
    }

    @Override
    public String getPermission() {
        return config.getPlayerHelpPermission();
    }

    @Override
    public String getSyntax() {
        return config.getPlayerHelpSyntax();
    }

    @Override
    public String getDescription() {
        return config.getPlayerHelpDescription();
    }

    @Override
    public boolean execute(CommandSender sender, String[] args) {
        int page;
        if (args.length >= 2) {
            try {
                page = Integer.parseInt(args[1]);
            } catch (NumberFormatException e) {
                page = 1;
            }
        } else {
            page = 1;
        }

        List<String> priorityOrder = config.getPlayerCommandOrder();

        List<SubCommand> visible = new ArrayList<>();
        for (SubCommand cmd : allSubs) {
            String perm = cmd.getPermission();
            if (perm == null || perm.isEmpty() || sender.hasPermission(perm)) {
                visible.add(cmd);
            }
        }

        visible.sort(Comparator.comparingInt(cmd -> {
            int index = priorityOrder.indexOf(cmd.getName());
            return index == -1 ? Integer.MAX_VALUE : index;
        }));

        int totalCommands = visible.size();
        int totalPages = (int) Math.ceil((double) totalCommands / COMMANDS_PER_PAGE);
        if (totalPages == 0) totalPages = 1;
        if (page < 1) page = 1;
        if (page > totalPages) page = totalPages;

        int startIndex = (page - 1) * COMMANDS_PER_PAGE;
        int endIndex = Math.min(startIndex + COMMANDS_PER_PAGE, totalCommands);

        sender.sendMessage(config.getPlayerHelpHeader());

        for (int i = startIndex; i < endIndex; i++) {
            SubCommand cmd = visible.get(i);
            sender.sendMessage(config.getPlayerHelpEntry(cmd.getName(), cmd.getSyntax(), cmd.getDescription()));
        }

        sender.sendMessage(config.getPlayerHelpFooter(page, totalPages));

        return true;
    }
}
