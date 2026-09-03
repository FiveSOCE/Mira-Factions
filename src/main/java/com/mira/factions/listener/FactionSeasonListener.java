package com.mira.factions.listener;

import com.mira.factions.MiraFactionsPlugin;
import com.mira.factions.model.Faction;
import com.mira.factions.service.FactionLandValueService;
import com.mira.factions.service.FactionSeasonService;
import com.mira.factions.service.FactionService;
import org.bukkit.Chunk;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;

import java.util.*;

public final class FactionSeasonListener implements Listener {
    private final MiraFactionsPlugin plugin;
    private final FactionService service;
    private final FactionLandValueService landValue;
    private final FactionSeasonService seasons;

    public FactionSeasonListener(MiraFactionsPlugin plugin, FactionService service, FactionLandValueService landValue, FactionSeasonService seasons) {
        this.plugin = plugin;
        this.service = service;
        this.landValue = landValue;
        this.seasons = seasons;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onCommand(PlayerCommandPreprocessEvent event) {
        String[] args = event.getMessage().substring(1).trim().split("\\s+");
        if (args.length < 2 || !(args[0].equalsIgnoreCase("f") || args[0].equalsIgnoreCase("faction") || args[0].equalsIgnoreCase("factions"))) return;
        if (!args[1].equalsIgnoreCase("claim")) return;
        Player player = event.getPlayer();
        Faction attacker = service.of(player.getUniqueId());
        Faction defender = service.owner(player.getLocation());
        if (attacker == null || defender == null || attacker == defender || !service.raidable(defender)) return;
        Chunk chunk = player.getLocation().getChunk();
        UUID defenderId = defender.id();
        UUID attackerId = attacker.id();
        double value = landValue.breakdown(chunk).spawnerValue();
        plugin.getServer().getScheduler().runTask(plugin, () -> {
            Faction now = service.owner(player.getLocation());
            if (now != null && now.id().equals(attackerId)) {
                seasons.recordRaid(service.byId(attackerId), service.byId(defenderId), value);
            }
        });
    }

    public void snapshotAll() {
        List<Faction> ranked = new ArrayList<>(service.all());
        ranked.sort(Comparator.comparingDouble(this::wealth).reversed());
        for (int i = 0; i < ranked.size(); i++) seasons.snapshot(ranked.get(i), wealth(ranked.get(i)), i + 1);
    }

    private double wealth(Faction faction) { return landValue.value(faction) + faction.bankBalance(); }
}
