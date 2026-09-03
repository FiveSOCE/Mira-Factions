package com.mira.factions.listener;

import com.mira.factions.MiraFactionsPlugin;
import com.mira.factions.gui.FTopPodiumService;
import com.mira.factions.model.Faction;
import com.mira.factions.service.FactionSeasonService;
import com.mira.factions.service.FactionService;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;

import java.util.Locale;

public final class FactionSeasonCommandListener implements Listener {
    private final MiraFactionsPlugin plugin;
    private final FactionService factions;
    private final FactionSeasonService seasons;
    private final FTopPodiumService podium;

    public FactionSeasonCommandListener(MiraFactionsPlugin plugin, FactionService factions, FactionSeasonService seasons, FTopPodiumService podium) {
        this.plugin = plugin;
        this.factions = factions;
        this.seasons = seasons;
        this.podium = podium;
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onCommand(PlayerCommandPreprocessEvent event) {
        String raw = event.getMessage();
        if (raw == null || raw.length() < 2) return;
        String[] args = raw.substring(1).trim().split("\\s+");
        if (args.length < 2 || !isFactionAlias(args[0])) return;
        String sub = args[1].toLowerCase(Locale.ROOT);
        if (sub.equals("podium") || (sub.equals("top") && args.length >= 3 && args[2].equalsIgnoreCase("gui"))) {
            event.setCancelled(true);
            podium.open(event.getPlayer());
            return;
        }
        if (sub.equals("season") || sub.equals("seasonstats")) {
            event.setCancelled(true);
            showSeason(event.getPlayer(), args.length >= 3 ? args[2] : null);
        }
    }

    private void showSeason(Player player, String factionName) {
        Faction faction = factionName == null ? factions.of(player.getUniqueId()) : factions.byName(factionName);
        if (faction == null) { plugin.msg(player, "&cFaction not found."); return; }
        FactionSeasonService.Stats stats = seasons.stats(faction);
        plugin.msg(player, "&5&m----&d " + seasons.currentSeason() + " - " + faction.name() + " &5&m----");
        plugin.msg(player, "&7Current Wealth: &a" + money(stats.currentWealth()));
        plugin.msg(player, "&7Peak Wealth: &6" + money(stats.peakWealth()));
        plugin.msg(player, "&7Season Change: &f" + signedMoney(stats.wealthChange()));
        plugin.msg(player, "&7Best FTop Rank: &f#" + (stats.bestFtopRank() <= 0 ? "-" : stats.bestFtopRank()));
        plugin.msg(player, "&7Raids W/L: &a" + stats.raidsWon() + "&7/&c" + stats.raidsLost());
        plugin.msg(player, "&7Raid Value Gained: &a" + money(stats.raidValueGained()) + " &7Lost: &c" + money(stats.raidValueLost()));
    }

    private static boolean isFactionAlias(String value) {
        return value.equalsIgnoreCase("f") || value.equalsIgnoreCase("faction") || value.equalsIgnoreCase("factions");
    }
    private static String money(double value) { return String.format(Locale.US, "$%,.2f", value); }
    private static String signedMoney(double value) { return (value >= 0 ? "+" : "") + money(value); }
}
