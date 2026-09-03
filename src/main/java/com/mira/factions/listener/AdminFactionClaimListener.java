package com.mira.factions.listener;

import com.mira.factions.MiraFactionsPlugin;
import com.mira.factions.model.Faction;
import com.mira.factions.model.TerritoryType;
import com.mira.factions.service.FactionService;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerMoveEvent;

import java.lang.reflect.Field;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/** Admin-only force claim helpers for /fa claim <Faction> <Amount> and /fa autoclaim <Faction>. */
public final class AdminFactionClaimListener implements Listener {
    private final MiraFactionsPlugin plugin;
    private final FactionService service;
    private final Map<UUID, UUID> autoClaimFaction = new ConcurrentHashMap<>();
    private final Map<String, UUID> claimOwner;

    @SuppressWarnings("unchecked")
    public AdminFactionClaimListener(MiraFactionsPlugin plugin, FactionService service) {
        this.plugin = plugin;
        this.service = service;
        try {
            Field field = FactionService.class.getDeclaredField("claimOwner");
            field.setAccessible(true);
            this.claimOwner = (Map<String, UUID>) field.get(service);
        } catch (ReflectiveOperationException ex) {
            throw new IllegalStateException("Unable to initialize admin faction claiming", ex);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onCommand(PlayerCommandPreprocessEvent event) {
        String raw = event.getMessage().trim();
        String[] args = raw.substring(1).split("\\s+");
        if (args.length == 0 || !(args[0].equalsIgnoreCase("fa") || args[0].equalsIgnoreCase("fadmin") || args[0].equalsIgnoreCase("factionadmin"))) return;
        Player player = event.getPlayer();
        if (!player.hasPermission("mirafactions.admin")) return;

        if (args.length >= 2 && args[1].equalsIgnoreCase("autoclaim")) {
            event.setCancelled(true);
            handleAutoClaim(player, args);
            return;
        }

        // Preserve existing /fa claim safezone|warzone|wilderness syntax.
        if (args.length == 4 && args[1].equalsIgnoreCase("claim")) {
            Faction faction = service.byName(args[2]);
            if (faction == null) return;
            int amount;
            try { amount = Integer.parseInt(args[3]); }
            catch (NumberFormatException ex) { return; }
            event.setCancelled(true);
            handleClaim(player, faction, amount);
        }
    }

    private void handleClaim(Player player, Faction faction, int amount) {
        if (amount < 1 || amount > 10000) {
            plugin.msg(player, "&cAmount must be between 1 and 10,000 chunks.");
            return;
        }
        Chunk center = player.getLocation().getChunk();
        int claimed = 0;
        int radius = 0;
        while (claimed < amount && radius <= 100) {
            for (int dx = -radius; dx <= radius && claimed < amount; dx++) {
                for (int dz = -radius; dz <= radius && claimed < amount; dz++) {
                    if (Math.max(Math.abs(dx), Math.abs(dz)) != radius) continue;
                    Location location = new Location(player.getWorld(), (center.getX() + dx) * 16.0, player.getY(), (center.getZ() + dz) * 16.0);
                    if (forceClaim(faction, location)) claimed++;
                }
            }
            radius++;
        }
        service.save();
        plugin.msg(player, "&aForce-claimed &f" + claimed + " &achunk" + (claimed == 1 ? "" : "s") + " for &f" + faction.name() + "&a.");
    }

    private void handleAutoClaim(Player player, String[] args) {
        if (args.length < 3 || args[2].equalsIgnoreCase("off")) {
            boolean removed = autoClaimFaction.remove(player.getUniqueId()) != null;
            plugin.msg(player, removed ? "&7Admin faction autoclaim disabled." : "&cUsage: /fa autoclaim <faction|off>");
            return;
        }
        Faction faction = service.byName(args[2]);
        if (faction == null) {
            plugin.msg(player, "&cFaction not found.");
            return;
        }
        UUID current = autoClaimFaction.get(player.getUniqueId());
        if (faction.id().equals(current)) {
            autoClaimFaction.remove(player.getUniqueId());
            plugin.msg(player, "&7Admin autoclaim for &f" + faction.name() + " &7disabled.");
            return;
        }
        autoClaimFaction.put(player.getUniqueId(), faction.id());
        forceClaim(faction, player.getLocation());
        service.save();
        plugin.msg(player, "&aAdmin autoclaim enabled for &f" + faction.name() + "&a. Move between chunks to claim them.");
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onMove(PlayerMoveEvent event) {
        if (event.getTo() == null || event.getFrom().getChunk().equals(event.getTo().getChunk())) return;
        UUID factionId = autoClaimFaction.get(event.getPlayer().getUniqueId());
        if (factionId == null) return;
        Faction faction = service.byId(factionId);
        if (faction == null) {
            autoClaimFaction.remove(event.getPlayer().getUniqueId());
            return;
        }
        if (forceClaim(faction, event.getTo())) {
            service.save();
            plugin.msg(event.getPlayer(), "&aAdmin autoclaim: claimed this chunk for &f" + faction.name() + "&a.");
        }
    }

    private boolean forceClaim(Faction faction, Location location) {
        TerritoryType type = service.territoryType(location);
        if (type == TerritoryType.SAFEZONE || type == TerritoryType.WARZONE) return false;
        String key = service.claimKey(location);
        UUID oldId = claimOwner.get(key);
        if (faction.id().equals(oldId)) return false;
        if (oldId != null) {
            Faction old = service.byId(oldId);
            if (old != null) {
                old.claims().remove(key);
                old.claimZones().remove(key);
            }
        }
        claimOwner.put(key, faction.id());
        faction.claims().add(key);
        return true;
    }
}
