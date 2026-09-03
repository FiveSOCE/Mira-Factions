package com.mira.factions.listener;

import com.mira.factions.MiraFactionsPlugin;
import com.mira.factions.command.FactionAdminCommand;
import com.mira.factions.model.ChatMode;
import com.mira.factions.model.Faction;
import com.mira.factions.model.Relation;
import com.mira.factions.service.FactionLandValueService;
import com.mira.factions.service.FactionService;
import io.papermc.paper.event.player.AsyncChatEvent;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.lang.reflect.Field;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public final class FactionOperatorListener implements Listener {
    private final MiraFactionsPlugin plugin;
    private final FactionService service;
    private final FactionLandValueService landValue;
    private final Set<UUID> autoMap = ConcurrentHashMap.newKeySet();

    public FactionOperatorListener(MiraFactionsPlugin plugin, FactionService service) {
        this.plugin = plugin;
        this.service = service;
        this.landValue = new FactionLandValueService(plugin);
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onCommand(PlayerCommandPreprocessEvent event) {
        String raw = event.getMessage().trim();
        String normalized = raw.toLowerCase(Locale.ROOT).replaceAll("\\s+", " ");
        String[] args = raw.substring(1).trim().split("\\s+");
        if (args.length == 0) return;

        if (isFactionAlias(args[0]) && args.length >= 2 && args[1].equalsIgnoreCase("list")) {
            event.setCancelled(true);
            if (!event.getPlayer().hasPermission("mirafactions.use")) return;
            int page = args.length >= 3 ? parseInt(args[2], 1) : 1;
            showFactionList(event.getPlayer(), page);
            return;
        }

        if (isFactionAlias(args[0]) && args.length >= 2 && args[1].equalsIgnoreCase("value")) {
            event.setCancelled(true);
            if (!event.getPlayer().hasPermission("mirafactions.use")) return;
            Faction faction = args.length >= 3 ? service.byName(args[2]) : service.of(event.getPlayer().getUniqueId());
            showValue(event.getPlayer(), faction);
            return;
        }

        if (isAdminAlias(args[0]) && args.length == 4 && args[1].equalsIgnoreCase("power")) {
            if (!event.getPlayer().hasPermission("mirafactions.admin")) return;
            double amount;
            try { amount = Double.parseDouble(args[3]); }
            catch (NumberFormatException ex) { return; }
            OfflinePlayer target = findPlayer(args[2]);
            if (target == null) return;
            event.setCancelled(true);
            setAdminPower(target.getUniqueId(), amount);
            plugin.msg(event.getPlayer(), "&aSet &f" + displayName(target) + "&a power to &f" + format(amount) + "&a. Admin-set power is not capped.");
            return;
        }

        if (normalized.equals("/f map auto")
                || normalized.equals("/faction map auto")
                || normalized.equals("/factions map auto")
                || normalized.equals("/f automap")
                || normalized.equals("/faction automap")
                || normalized.equals("/factions automap")) {
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
            return;
        }

        if (isFactionAlias(args[0]) && args.length >= 2 && Set.of("info", "show", "status").contains(args[1].toLowerCase(Locale.ROOT))) {
            Faction faction = args.length >= 3 ? service.byName(args[2]) : service.of(event.getPlayer().getUniqueId());
            if (faction != null) Bukkit.getScheduler().runTask(plugin, () -> showValue(event.getPlayer(), faction));
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

    private void showFactionList(Player player, int requestedPage) {
        List<Faction> factions = service.all().stream()
                .sorted(Comparator.comparingDouble((Faction f) -> service.factionPower(f)).reversed().thenComparing(Faction::name, String.CASE_INSENSITIVE_ORDER))
                .toList();
        int pages = Math.max(1, (factions.size() + 9) / 10);
        int page = Math.max(1, Math.min(requestedPage, pages));
        int start = (page - 1) * 10;
        int end = Math.min(factions.size(), start + 10);

        plugin.msg(player, "&5&m--------------------------------");
        plugin.msg(player, "&dFactions List &7(Page &f" + page + "&7/&f" + pages + "&7)");
        if (factions.isEmpty()) plugin.msg(player, "&7No factions exist yet.");
        for (int i = start; i < end; i++) {
            Faction faction = factions.get(i);
            long online = faction.members().keySet().stream().map(Bukkit::getPlayer).filter(Objects::nonNull).count();
            plugin.msg(player, "&f" + (i + 1) + ". &d" + faction.name()
                    + " &7Members &f" + online + "/" + faction.members().size()
                    + " &7Power &f" + format(service.factionPower(faction))
                    + " &7Land &f" + faction.claims().size()
                    + (service.raidable(faction) ? " &cRAIDABLE" : ""));
        }
        plugin.msg(player, "&7Use &f/f list <page>&7 to change pages. 10 factions per page.");
    }

    private void showValue(Player player, Faction faction) {
        if (faction == null) {
            plugin.msg(player, "&cFaction not found.");
            return;
        }
        double value = landValue.value(faction);
        plugin.msg(player, "&7Spawner Land Value: &a$" + String.format(Locale.US, "%,.2f", value));
    }

    @SuppressWarnings("unchecked")
    private void setAdminPower(UUID uuid, double value) {
        if (!Double.isFinite(value)) return;
        try {
            Field field = FactionService.class.getDeclaredField("power");
            field.setAccessible(true);
            Map<UUID, Double> power = (Map<UUID, Double>) field.get(service);
            power.put(uuid, value);
            service.save();
        } catch (ReflectiveOperationException ex) {
            plugin.getLogger().severe("Could not apply uncapped admin power: " + ex.getMessage());
            service.setPower(uuid, value);
        }
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
        int radius = Math.max(2, plugin.getConfig().getInt("map.radius", 8));
        for (String line : service.map(player, radius).split("\\n")) player.sendMessage(plugin.component(line));
    }

    private boolean isFactionAlias(String raw) {
        return raw.equalsIgnoreCase("f") || raw.equalsIgnoreCase("faction") || raw.equalsIgnoreCase("factions");
    }

    private boolean isAdminAlias(String raw) {
        return raw.equalsIgnoreCase("fa") || raw.equalsIgnoreCase("fadmin") || raw.equalsIgnoreCase("factionadmin");
    }

    private OfflinePlayer findPlayer(String name) {
        Player online = Bukkit.getPlayerExact(name);
        if (online != null) return online;
        for (OfflinePlayer player : Bukkit.getOfflinePlayers()) if (player.getName() != null && player.getName().equalsIgnoreCase(name)) return player;
        return null;
    }

    private String displayName(OfflinePlayer player) {
        return player.getName() == null ? player.getUniqueId().toString() : player.getName();
    }

    private static int parseInt(String raw, int fallback) {
        try { return Integer.parseInt(raw); } catch (NumberFormatException ex) { return fallback; }
    }

    private static String format(double value) {
        return String.format(Locale.US, value == Math.rint(value) ? "%.0f" : "%.1f", value);
    }
}
