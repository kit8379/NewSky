package org.me.newsky.command.admin;

import org.bukkit.command.CommandSender;
import org.me.newsky.command.SubCommand;
import org.me.newsky.config.ConfigHandler;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;

/**
 * /isadmin help [page]
 */
public class AdminHelpCommand implements SubCommand {
    private final ConfigHandler config;
    private final Set<SubCommand> allSubs;
    private static final int COMMANDS_PER_PAGE = 8;

    public AdminHelpCommand(ConfigHandler config, Set<SubCommand> allSubs) {
        this.config = config;
        this.allSubs = allSubs;
    }

    @Override
    public String getName() {
        return "help";
    }

    @Override
    public String[] getAliases() {
        return config.getAdminHelpAliases();
    }

    @Override
    public String getPermission() {
        return config.getAdminHelpPermission();
    }

    @Override
    public String getSyntax() {
        return config.getAdminHelpSyntax();
    }

    @Override
    public String getDescription() {
        return config.getAdminHelpDescription();
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

        List<SubCommand> visible = new ArrayList<>();
        for (SubCommand cmd : allSubs) {
            String perm = cmd.getPermission();
            if (perm == null || perm.isEmpty() || sender.hasPermission(perm)) {
                visible.add(cmd);
            }
        }

        visible.sort(Comparator.comparing(SubCommand::getName));
        int totalCommands = visible.size();
        int totalPages = (int) Math.ceil((double) totalCommands / COMMANDS_PER_PAGE);
        if (totalPages == 0) totalPages = 1;
        if (page < 1) page = 1;
        if (page > totalPages) page = totalPages;

        int startIndex = (page - 1) * COMMANDS_PER_PAGE;
        int endIndex = Math.min(startIndex + COMMANDS_PER_PAGE, totalCommands);

        sender.sendMessage(config.getAdminHelpHeader());

        for (int i = startIndex; i < endIndex; i++) {
            SubCommand cmd = visible.get(i);
            sender.sendMessage(config.getAdminHelpEntry(cmd.getName(), cmd.getSyntax(), cmd.getDescription()));
        }

        sender.sendMessage(config.getAdminHelpFooter(page, totalPages));

        return true;
    }
}
