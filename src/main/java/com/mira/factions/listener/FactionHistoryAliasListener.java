package com.mira.factions.listener;

import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;

import java.util.Locale;

public final class FactionHistoryAliasListener implements Listener {
    @EventHandler(priority = EventPriority.LOWEST)
    public void onCommand(PlayerCommandPreprocessEvent event) {
        String raw = event.getMessage();
        if (raw == null || raw.length() < 2) return;
        String[] args = raw.substring(1).trim().split("\\s+");
        if (args.length < 3 || !isFactionAlias(args[0])) return;
        if (!args[1].equalsIgnoreCase("value") || !args[2].equalsIgnoreCase("history")) return;
        StringBuilder rewritten = new StringBuilder("/").append(args[0]).append(" valuehistory");
        for (int i = 3; i < args.length; i++) rewritten.append(' ').append(args[i]);
        event.setMessage(rewritten.toString());
    }

    private static boolean isFactionAlias(String raw) {
        String value = raw.toLowerCase(Locale.ROOT);
        return value.equals("f") || value.equals("faction") || value.equals("factions");
    }
}
