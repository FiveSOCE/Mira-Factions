package com.mira.factions.listener;

import com.mira.factions.MiraFactionsPlugin;
import com.mira.factions.model.Faction;
import com.mira.factions.model.FactionRank;
import com.mira.factions.model.UpgradeType;
import com.mira.factions.service.FactionHistoryService;
import com.mira.factions.service.FactionLandValueService;
import com.mira.factions.service.FactionService;
import org.bukkit.Chunk;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;

import java.util.*;

public final class FactionHistoryListener implements Listener {
    private final MiraFactionsPlugin plugin;
    private final FactionService service;
    private final FactionHistoryService history;
    private final FactionLandValueService landValue;

    public FactionHistoryListener(MiraFactionsPlugin plugin, FactionService service, FactionHistoryService history,
                                  FactionLandValueService landValue) {
        this.plugin = plugin;
        this.service = service;
        this.history = history;
        this.landValue = landValue;
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onCommand(PlayerCommandPreprocessEvent event) {
        String raw = event.getMessage();
        if (raw == null || raw.length() < 2) return;
        String[] args = raw.substring(1).trim().split("\\s+");
        if (args.length < 2 || !isFactionAlias(args[0])) return;

        Player player = event.getPlayer();
        String sub = args[1].toLowerCase(Locale.ROOT);

        if (sub.equals("log") || sub.equals("audit")) {
            event.setCancelled(true);
            showAudit(player, args.length >= 3 ? parsePage(args[2]) : 1);
            return;
        }
        if (sub.equals("bankhistory") || (sub.equals("money") && args.length >= 3 && args[2].equalsIgnoreCase("history"))) {
            event.setCancelled(true);
            int page = args.length >= 4 ? parsePage(args[3]) : (sub.equals("bankhistory") && args.length >= 3 ? parsePage(args[2]) : 1);
            showBank(player, page);
            return;
        }
        if (sub.equals("valuehistory") || (sub.equals("history") && args.length >= 3 && args[2].equalsIgnoreCase("value"))) {
            event.setCancelled(true);
            int page = args.length >= 4 ? parsePage(args[3]) : (sub.equals("valuehistory") && args.length >= 3 ? parsePage(args[2]) : 1);
            showValueHistory(player, page);
            return;
        }

        Faction beforeFaction = service.of(player.getUniqueId());
        Snapshot before = beforeFaction == null ? null : Snapshot.capture(beforeFaction);
        RaidSnapshot raid = null;
        if (sub.equals("claim") && beforeFaction != null) {
            Faction defender = service.owner(player.getLocation());
            if (defender != null && defender != beforeFaction && service.raidable(defender)) {
                Chunk chunk = player.getLocation().getChunk();
                raid = new RaidSnapshot(defender.id(), defender.name(), beforeFaction.id(), beforeFaction.name(),
                        chunk.getWorld().getName() + " " + chunk.getX() + "," + chunk.getZ(),
                        landValue.breakdown(chunk).spawnerValue());
            }
        }

        Snapshot finalBefore = before;
        RaidSnapshot finalRaid = raid;
        String commandText = raw.substring(1);
        plugin.getServer().getScheduler().runTask(plugin, () -> afterCommand(player, commandText, finalBefore, finalRaid));
    }

    private void afterCommand(Player actor, String commandText, Snapshot before, RaidSnapshot raid) {
        if (before == null) {
            Faction now = service.of(actor.getUniqueId());
            if (now != null) history.logAudit(now, actor.getUniqueId(), "MEMBERSHIP", actor.getName() + " joined/created the faction via /" + commandText + ".");
            return;
        }

        Faction current = service.byId(before.id());
        if (current == null) {
            history.logAudit(before.id(), before.name(), actor.getUniqueId(), "DISBAND", actor.getName() + " disbanded the faction.");
            return;
        }

        if (Math.abs(current.bankBalance() - before.bank()) > 0.0001D) {
            double delta = current.bankBalance() - before.bank();
            history.logBank(current, actor.getUniqueId(), reason(commandText), delta, current.bankBalance());
            history.acknowledgeBank(current);
        }

        Set<String> addedClaims = new HashSet<>(current.claims());
        addedClaims.removeAll(before.claims());
        Set<String> removedClaims = new HashSet<>(before.claims());
        removedClaims.removeAll(current.claims());
        if (!addedClaims.isEmpty()) history.logAudit(current, actor.getUniqueId(), "CLAIM", "Claimed " + addedClaims.size() + " chunk(s) via /" + commandText + ".");
        if (!removedClaims.isEmpty()) history.logAudit(current, actor.getUniqueId(), "UNCLAIM", "Unclaimed " + removedClaims.size() + " chunk(s) via /" + commandText + ".");

        Set<UUID> joined = new HashSet<>(current.members().keySet());
        joined.removeAll(before.members().keySet());
        for (UUID uuid : joined) history.logAudit(current, actor.getUniqueId(), "JOIN", playerName(uuid) + " joined the faction.");

        Set<UUID> left = new HashSet<>(before.members().keySet());
        left.removeAll(current.members().keySet());
        for (UUID uuid : left) history.logAudit(current, actor.getUniqueId(), "LEAVE", playerName(uuid) + " left/was removed from the faction.");

        for (Map.Entry<UUID, FactionRank> entry : current.members().entrySet()) {
            FactionRank old = before.members().get(entry.getKey());
            if (old != null && old != entry.getValue()) {
                history.logAudit(current, actor.getUniqueId(), "RANK", playerName(entry.getKey()) + " changed from " + old + " to " + entry.getValue() + ".");
            }
        }

        if (!current.name().equals(before.name())) history.logAudit(current, actor.getUniqueId(), "RENAME", before.name() + " renamed to " + current.name() + ".");
        if (current.open() != before.open()) history.logAudit(current, actor.getUniqueId(), "SETTING", "Open faction changed to " + current.open() + ".");
        if (!Objects.equals(current.description(), before.description())) history.logAudit(current, actor.getUniqueId(), "SETTING", "Faction description changed.");
        if (!Objects.equals(current.link(), before.link())) history.logAudit(current, actor.getUniqueId(), "SETTING", "Faction link changed.");

        Set<String> addedWarps = new HashSet<>(current.warps().keySet());
        addedWarps.removeAll(before.warps());
        for (String warp : addedWarps) history.logAudit(current, actor.getUniqueId(), "WARP", "Created faction warp " + warp + ".");
        Set<String> removedWarps = new HashSet<>(before.warps());
        removedWarps.removeAll(current.warps().keySet());
        for (String warp : removedWarps) history.logAudit(current, actor.getUniqueId(), "WARP", "Removed faction warp " + warp + ".");

        for (UpgradeType type : UpgradeType.values()) {
            int old = before.upgrades().getOrDefault(type, 0);
            int now = current.upgrade(type);
            if (old != now) history.logAudit(current, actor.getUniqueId(), "UPGRADE", type.name() + " changed from level " + old + " to " + now + ".");
        }

        if (current.tntBalance() != before.tnt()) history.logAudit(current, actor.getUniqueId(), "TNT", "TNT bank changed from " + before.tnt() + " to " + current.tntBalance() + ".");
        if (current.shieldUntil() != before.shieldUntil()) history.logAudit(current, actor.getUniqueId(), "SHIELD", "Faction shield state changed.");

        if (raid != null) {
            Faction attacker = service.byId(raid.attackerId());
            Faction defender = service.byId(raid.defenderId());
            Faction ownerNow = service.owner(actor.getLocation());
            if (attacker != null && ownerNow == attacker) {
                history.recordRaid(defender, attacker, actor.getUniqueId(), raid.chunk(), raid.value());
            }
        }
    }

    public void snapshotValues() {
        for (Faction faction : service.all()) {
            FactionLandValueService.Breakdown breakdown = landValue.breakdown(faction);
            history.recordValue(faction, breakdown.spawnerValue(), faction.bankBalance());
        }
    }

    private void showAudit(Player player, int page) {
        Faction faction = service.of(player.getUniqueId());
        if (faction == null) { plugin.msg(player, "&cYou are not in a faction."); return; }
        List<FactionHistoryService.AuditEntry> entries = history.audit(faction);
        int pages = Math.max(1, (entries.size() + 9) / 10);
        page = Math.max(1, Math.min(page, pages));
        plugin.msg(player, "&5&m----&d Faction Audit Log &7(" + page + "/" + pages + ") &5&m----");
        int start = (page - 1) * 10;
        if (entries.isEmpty()) plugin.msg(player, "&7No audit entries yet.");
        for (int i = start; i < Math.min(entries.size(), start + 10); i++) {
            var e = entries.get(i);
            plugin.msg(player, "&8" + history.displayTime(e.time()) + " &d[" + e.type() + "] &f" + e.message() + " &7(" + e.actorName() + ")");
        }
    }

    private void showBank(Player player, int page) {
        Faction faction = service.of(player.getUniqueId());
        if (faction == null) { plugin.msg(player, "&cYou are not in a faction."); return; }
        List<FactionHistoryService.BankEntry> entries = history.bank(faction);
        int pages = Math.max(1, (entries.size() + 9) / 10);
        page = Math.max(1, Math.min(page, pages));
        plugin.msg(player, "&5&m----&d Faction Bank History &7(" + page + "/" + pages + ") &5&m----");
        int start = (page - 1) * 10;
        if (entries.isEmpty()) plugin.msg(player, "&7No bank transactions yet.");
        for (int i = start; i < Math.min(entries.size(), start + 10); i++) {
            var e = entries.get(i);
            plugin.msg(player, "&8" + history.displayTime(e.time()) + " &f" + (e.delta() >= 0 ? "+" : "") + money(e.delta())
                    + " &7-> &f" + money(e.balance()) + " &d" + e.reason() + " &7(" + e.actorName() + ")");
        }
    }

    private void showValueHistory(Player player, int page) {
        Faction faction = service.of(player.getUniqueId());
        if (faction == null) { plugin.msg(player, "&cYou are not in a faction."); return; }
        List<FactionHistoryService.ValueEntry> entries = history.values(faction);
        int pages = Math.max(1, (entries.size() + 9) / 10);
        page = Math.max(1, Math.min(page, pages));
        plugin.msg(player, "&5&m----&d Faction Value History &7(" + page + "/" + pages + ") &5&m----");
        int start = (page - 1) * 10;
        if (entries.isEmpty()) plugin.msg(player, "&7No value snapshots yet.");
        for (int i = start; i < Math.min(entries.size(), start + 10); i++) {
            var e = entries.get(i);
            plugin.msg(player, "&8" + history.displayTime(e.time()) + " &7Total &a" + money(e.totalValue())
                    + " &7Spawners &f" + money(e.spawnerValue()) + " &7Bank &f" + money(e.bankValue()));
        }
    }

    private String reason(String commandText) {
        String lower = commandText.toLowerCase(Locale.ROOT);
        if (lower.contains(" money deposit")) return "DEPOSIT";
        if (lower.contains(" money withdraw")) return "WITHDRAW";
        if (lower.contains(" money pay")) return "TRANSFER";
        return "COMMAND";
    }

    private String playerName(UUID uuid) {
        String name = plugin.getServer().getOfflinePlayer(uuid).getName();
        return name == null ? uuid.toString() : name;
    }

    private static int parsePage(String raw) {
        try { return Math.max(1, Integer.parseInt(raw)); }
        catch (Exception ignored) { return 1; }
    }

    private static String money(double value) { return String.format(Locale.US, "$%,.2f", value); }
    private static boolean isFactionAlias(String raw) {
        return raw.equalsIgnoreCase("f") || raw.equalsIgnoreCase("faction") || raw.equalsIgnoreCase("factions");
    }

    private record RaidSnapshot(UUID defenderId, String defenderName, UUID attackerId, String attackerName, String chunk, double value) {}

    private record Snapshot(UUID id, String name, double bank, Set<String> claims, Map<UUID, FactionRank> members,
                            Set<String> warps, Map<UpgradeType, Integer> upgrades, boolean open, String description,
                            String link, int tnt, long shieldUntil) {
        static Snapshot capture(Faction faction) {
            return new Snapshot(faction.id(), faction.name(), faction.bankBalance(), new HashSet<>(faction.claims()),
                    new HashMap<>(faction.members()), new HashSet<>(faction.warps().keySet()), new EnumMap<>(faction.upgrades()),
                    faction.open(), faction.description(), faction.link(), faction.tntBalance(), faction.shieldUntil());
        }
    }
}
