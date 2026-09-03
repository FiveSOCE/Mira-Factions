package com.mira.factions.listener;

import com.mira.factions.MiraFactionsPlugin;
import com.mira.factions.command.FactionAdminCommand;
import com.mira.factions.model.ChatMode;
import com.mira.factions.model.Faction;
import com.mira.factions.model.Relation;
import com.mira.factions.service.FactionService;
import io.papermc.paper.event.player.AsyncChatEvent;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class FactionOperatorListener implements Listener {
    private final MiraFactionsPlugin plugin;
    private final FactionService service;
    private final Set<UUID> autoMap = ConcurrentHashMap.newKeySet();

    public FactionOperatorListener(MiraFactionsPlugin plugin, FactionService service) {
        this.plugin = plugin;
        this.service = service;
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onCommand(PlayerCommandPreprocessEvent event) {
        String raw = event.getMessage().trim();
        String normalized = raw.toLowerCase(Locale.ROOT).replaceAll("\\s+", " ");
        if (!normalized.equals("/f map auto")
                && !normalized.equals("/faction map auto")
                && !normalized.equals("/factions map auto")
                && !normalized.equals("/f automap")
                && !normalized.equals("/faction automap")
                && !normalized.equals("/factions automap")) return;

        event.setCancelled(true);
        Player player = event.getPlayer();
        if (!player.hasPermission("mirafactions.use")) return;

        if (!autoMap.remove(player.getUniqueId())) {
            autoMap.add(player.getUniqueId());
            plugin.msg(player, "&aFaction auto-map enabled. The map will redraw whenever you enter a new chunk.");
            showMap(player);
        } else {
            plugin.msg(player, "&7Faction auto-map disabled.");
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onMove(PlayerMoveEvent event) {
        if (event.getTo() == null || !autoMap.contains(event.getPlayer().getUniqueId())) return;
        if (event.getFrom().getWorld() == event.getTo().getWorld()
                && event.getFrom().getChunk().getX() == event.getTo().getChunk().getX()
                && event.getFrom().getChunk().getZ() == event.getTo().getChunk().getZ()) return;

        showMap(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onFactionChatSpy(AsyncChatEvent event) {
        Player sender = event.getPlayer();
        ChatMode mode = service.chatMode(sender.getUniqueId());
        if (mode == ChatMode.PUBLIC || service.of(sender.getUniqueId()) == null) return;

        var message = plugin.component("&8[SPY:&7" + mode.name() + "&8] &f" + sender.getName() + "&7: ").append(event.message());
        for (UUID uuid : FactionAdminCommand.chatSpies()) {
            Player spy = Bukkit.getPlayer(uuid);
            if (spy == null || spy.equals(sender) || normallyReceives(spy, sender, mode)) continue;
            spy.sendMessage(message);
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        autoMap.remove(event.getPlayer().getUniqueId());
    }

    private boolean normallyReceives(Player viewer, Player sender, ChatMode mode) {
        Faction source = service.of(sender.getUniqueId());
        Faction target = service.of(viewer.getUniqueId());
        if (source == null) return false;
        return switch (mode) {
            case FACTION -> target == source;
            case ALLY -> target == source || service.relation(source, target) == Relation.ALLY;
            case TRUCE -> target == source || service.relation(source, target) == Relation.TRUCE;
            default -> false;
        };
    }

    private void showMap(Player player) {
        int radius = Math.max(2, plugin.getConfig().getInt("map.radius", 4));
        for (String line : service.map(player, radius).split("\\n")) player.sendMessage(plugin.component(line));
    }
}
