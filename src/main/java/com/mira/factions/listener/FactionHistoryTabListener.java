package com.mira.factions.listener;

import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.server.TabCompleteEvent;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;

public final class FactionHistoryTabListener implements Listener {
    @EventHandler
    public void onTab(TabCompleteEvent event) {
        String buffer = event.getBuffer();
        if (buffer == null || !buffer.startsWith("/")) return;
        String[] parts = buffer.substring(1).split(" ", -1);
        if (parts.length == 0 || !isFactionAlias(parts[0])) return;

        List<String> additions = new ArrayList<>();
        if (parts.length == 2) {
            additions.addAll(List.of("log", "bankhistory", "valuehistory", "history"));
        } else if (parts.length == 3 && parts[1].equalsIgnoreCase("money")) {
            additions.add("history");
        } else if (parts.length == 3 && parts[1].equalsIgnoreCase("history")) {
            additions.add("value");
        }
        if (additions.isEmpty()) return;

        String prefix = parts[parts.length - 1].toLowerCase(Locale.ROOT);
        LinkedHashSet<String> merged = new LinkedHashSet<>(event.getCompletions());
        additions.stream().filter(value -> value.startsWith(prefix)).forEach(merged::add);
        event.setCompletions(new ArrayList<>(merged));
    }

    private static boolean isFactionAlias(String raw) {
        return raw.equalsIgnoreCase("f") || raw.equalsIgnoreCase("faction") || raw.equalsIgnoreCase("factions");
    }
}
