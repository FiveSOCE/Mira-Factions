package com.mira.factions.service;

import com.mira.factions.MiraFactionsPlugin;
import com.mira.factions.model.Faction;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.*;

public final class FactionHistoryService {
    private final MiraFactionsPlugin plugin;
    private final File file;
    private final YamlConfiguration yaml;
    private final Map<UUID, Double> bankBaseline = new HashMap<>();
    private final SimpleDateFormat displayDate = new SimpleDateFormat("yyyy-MM-dd HH:mm");

    public FactionHistoryService(MiraFactionsPlugin plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "faction-history.yml");
        this.yaml = YamlConfiguration.loadConfiguration(file);
    }

    public synchronized void initializeBanks(Collection<Faction> factions) {
        bankBaseline.clear();
        for (Faction faction : factions) bankBaseline.put(faction.id(), faction.bankBalance());
    }

    public synchronized void acknowledgeBank(Faction faction) {
        if (faction != null) bankBaseline.put(faction.id(), faction.bankBalance());
    }

    public synchronized void pollBanks(Collection<Faction> factions) {
        Set<UUID> live = new HashSet<>();
        for (Faction faction : factions) {
            live.add(faction.id());
            Double previous = bankBaseline.put(faction.id(), faction.bankBalance());
            if (previous == null) continue;
            double delta = faction.bankBalance() - previous;
            if (Math.abs(delta) < 0.0001D) continue;
            logBank(faction, null, "SYSTEM", delta, faction.bankBalance());
        }
        bankBaseline.keySet().removeIf(id -> !live.contains(id));
    }

    public synchronized void logAudit(Faction faction, UUID actor, String type, String message) {
        if (faction == null) return;
        logAudit(faction.id(), faction.name(), actor, type, message);
    }

    public synchronized void logAudit(UUID factionId, String factionName, UUID actor, String type, String message) {
        if (factionId == null) return;
        String path = root(factionId) + ".audit";
        List<Map<?, ?>> entries = new ArrayList<>(yaml.getMapList(path));
        Map<String, Object> entry = baseEntry(actor);
        entry.put("type", safe(type));
        entry.put("message", message == null ? "" : message);
        entry.put("faction-name", factionName == null ? "" : factionName);
        entries.add(entry);
        trim(entries, maxAuditEntries());
        yaml.set(path, entries);
        save();
    }

    public synchronized void logBank(Faction faction, UUID actor, String reason, double delta, double balance) {
        if (faction == null || Math.abs(delta) < 0.0001D) return;
        String path = root(faction.id()) + ".bank";
        List<Map<?, ?>> entries = new ArrayList<>(yaml.getMapList(path));
        Map<String, Object> entry = baseEntry(actor);
        entry.put("reason", reason == null ? "UNKNOWN" : reason);
        entry.put("delta", delta);
        entry.put("balance", balance);
        entries.add(entry);
        trim(entries, maxBankEntries());
        yaml.set(path, entries);
        bankBaseline.put(faction.id(), balance);
        logAudit(faction.id(), faction.name(), actor, "BANK",
                (delta >= 0D ? "+" : "") + formatMoney(delta) + " -> " + formatMoney(balance) + " (" + reason + ")");
        save();
    }

    public synchronized void recordValue(Faction faction, double spawnerValue, double bankValue) {
        if (faction == null) return;
        double total = Math.max(0D, spawnerValue) + Math.max(0D, bankValue);
        String path = root(faction.id()) + ".value";
        List<Map<?, ?>> entries = new ArrayList<>(yaml.getMapList(path));
        long now = System.currentTimeMillis();
        if (!entries.isEmpty()) {
            Map<?, ?> last = entries.get(entries.size() - 1);
            long lastTime = number(last.get("time")).longValue();
            double lastTotal = number(last.get("total")).doubleValue();
            if (now - lastTime < 3_600_000L && Math.abs(lastTotal - total) < 0.01D) return;
        }
        Map<String, Object> entry = new LinkedHashMap<>();
        entry.put("time", now);
        entry.put("spawners", spawnerValue);
        entry.put("bank", bankValue);
        entry.put("total", total);
        entries.add(entry);
        trim(entries, maxValueEntries());
        yaml.set(path, entries);
        save();
    }

    public synchronized void recordRaid(Faction defender, Faction attacker, UUID actor, String chunk, double valueLost) {
        if (defender != null) {
            logAudit(defender, actor, "RAID_LOSS", "Lost " + formatMoney(valueLost) + " of spawner value at " + chunk
                    + (attacker == null ? "" : " to " + attacker.name()) + ".");
        }
        if (attacker != null) {
            logAudit(attacker, actor, "RAID_GAIN", "Captured " + formatMoney(valueLost) + " of spawner value at " + chunk
                    + (defender == null ? "" : " from " + defender.name()) + ".");
        }
    }

    public synchronized List<AuditEntry> audit(Faction faction) {
        if (faction == null) return List.of();
        List<AuditEntry> out = new ArrayList<>();
        for (Map<?, ?> map : yaml.getMapList(root(faction.id()) + ".audit")) {
            out.add(new AuditEntry(number(map.get("time")).longValue(), string(map.get("actor")), string(map.get("actor-name")),
                    string(map.get("type")), string(map.get("message"))));
        }
        Collections.reverse(out);
        return out;
    }

    public synchronized List<BankEntry> bank(Faction faction) {
        if (faction == null) return List.of();
        List<BankEntry> out = new ArrayList<>();
        for (Map<?, ?> map : yaml.getMapList(root(faction.id()) + ".bank")) {
            out.add(new BankEntry(number(map.get("time")).longValue(), string(map.get("actor")), string(map.get("actor-name")),
                    string(map.get("reason")), number(map.get("delta")).doubleValue(), number(map.get("balance")).doubleValue()));
        }
        Collections.reverse(out);
        return out;
    }

    public synchronized List<ValueEntry> values(Faction faction) {
        if (faction == null) return List.of();
        List<ValueEntry> out = new ArrayList<>();
        for (Map<?, ?> map : yaml.getMapList(root(faction.id()) + ".value")) {
            out.add(new ValueEntry(number(map.get("time")).longValue(), number(map.get("spawners")).doubleValue(),
                    number(map.get("bank")).doubleValue(), number(map.get("total")).doubleValue()));
        }
        Collections.reverse(out);
        return out;
    }

    public String displayTime(long millis) {
        return displayDate.format(new Date(millis));
    }

    private Map<String, Object> baseEntry(UUID actor) {
        Map<String, Object> entry = new LinkedHashMap<>();
        entry.put("time", System.currentTimeMillis());
        if (actor == null) {
            entry.put("actor", "SYSTEM");
            entry.put("actor-name", "SYSTEM");
        } else {
            OfflinePlayer offline = Bukkit.getOfflinePlayer(actor);
            entry.put("actor", actor.toString());
            entry.put("actor-name", offline.getName() == null ? actor.toString() : offline.getName());
        }
        return entry;
    }

    private int maxAuditEntries() { return Math.max(100, plugin.getConfig().getInt("history.audit-max-entries", 1000)); }
    private int maxBankEntries() { return Math.max(100, plugin.getConfig().getInt("history.bank-max-entries", 1000)); }
    private int maxValueEntries() { return Math.max(100, plugin.getConfig().getInt("history.value-max-entries", 1000)); }

    private static void trim(List<?> entries, int max) {
        while (entries.size() > max) entries.remove(0);
    }

    private static Number number(Object value) {
        return value instanceof Number n ? n : 0D;
    }

    private static String string(Object value) { return value == null ? "" : String.valueOf(value); }
    private static String safe(String value) { return value == null ? "UNKNOWN" : value.toUpperCase(Locale.ROOT); }
    private static String root(UUID id) { return "factions." + id; }
    private static String formatMoney(double value) { return String.format(Locale.US, "$%,.2f", value); }

    private void save() {
        try { yaml.save(file); }
        catch (Exception ex) { plugin.getLogger().warning("Could not save faction-history.yml: " + ex.getMessage()); }
    }

    public record AuditEntry(long time, String actorId, String actorName, String type, String message) {}
    public record BankEntry(long time, String actorId, String actorName, String reason, double delta, double balance) {}
    public record ValueEntry(long time, double spawnerValue, double bankValue, double totalValue) {}
}
