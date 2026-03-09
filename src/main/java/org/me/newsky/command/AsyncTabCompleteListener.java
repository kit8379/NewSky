package org.me.newsky.command;

import com.destroystokyo.paper.event.server.AsyncTabCompleteEvent;
import org.bukkit.command.CommandSender;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.me.newsky.NewSky;

import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

public class AsyncTabCompleteListener implements Listener {

    private static final long TAB_TIMEOUT_MILLIS = 200L;

    private final NewSky plugin;
    private final Map<String, AsyncCommandTabRouter> routers = new HashMap<>();

    public AsyncTabCompleteListener(NewSky plugin) {
        this.plugin = plugin;
    }

    public void registerRoot(String rootLabel, AsyncCommandTabRouter router) {
        if (rootLabel == null || rootLabel.isEmpty() || router == null) {
            return;
        }
        routers.put(rootLabel.toLowerCase(Locale.ROOT), router);
    }

    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onAsyncTabComplete(AsyncTabCompleteEvent event) {
        if (!event.isCommand()) {
            return;
        }

        String buffer = event.getBuffer();
        if (buffer.isEmpty() || buffer.charAt(0) != '/') {
            return;
        }

        ParsedCommand parsed = parseCommandBuffer(buffer);
        if (parsed == null) {
            return;
        }

        AsyncCommandTabRouter router = routers.get(parsed.label.toLowerCase(Locale.ROOT));
        if (router == null) {
            return;
        }

        CommandSender sender = event.getSender();

        try {
            List<String> suggestions = router.completeAsync(sender, parsed.label, parsed.args).orTimeout(TAB_TIMEOUT_MILLIS, TimeUnit.MILLISECONDS).exceptionally(ex -> {
                plugin.severe("Async tab complete exception for buffer '" + buffer + "'", ex);
                return Collections.emptyList();
            }).get(TAB_TIMEOUT_MILLIS + 50L, TimeUnit.MILLISECONDS);

            if (suggestions == null) {
                suggestions = Collections.emptyList();
            }

            suggestions = suggestions.stream().filter(s -> s != null && !s.isEmpty()).distinct().collect(Collectors.toList());

            event.setCompletions(suggestions);
            event.setHandled(true);
        } catch (Exception ex) {
            plugin.severe("Failed to get async tab complete suggestions for buffer '" + buffer + "'", ex);
            event.setCompletions(Collections.emptyList());
            event.setHandled(true);
        }
    }

    private ParsedCommand parseCommandBuffer(String buffer) {
        String raw = buffer.startsWith("/") ? buffer.substring(1) : buffer;
        if (raw.isEmpty()) {
            return null;
        }

        String[] split = raw.split(" ", -1);
        if (split.length == 0) {
            return null;
        }

        String label = split[0];
        if (label.isEmpty()) {
            return null;
        }

        List<String> argsList = new ArrayList<>(Arrays.asList(split).subList(1, split.length));

        return new ParsedCommand(label, argsList.toArray(new String[0]));
    }

    private static final class ParsedCommand {
        private final String label;
        private final String[] args;

        private ParsedCommand(String label, String[] args) {
            this.label = label;
            this.args = args;
        }
    }
}