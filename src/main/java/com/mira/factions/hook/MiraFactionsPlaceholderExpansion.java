package com.mira.factions.hook;

import com.mira.factions.MiraFactionsPlugin;
import com.mira.factions.model.Faction;
import com.mira.factions.service.FactionLandValueService;
import com.mira.factions.service.FactionService;
import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

public final class MiraFactionsPlaceholderExpansion extends PlaceholderExpansion {
    private final MiraFactionsPlugin plugin;
    private final FactionService service;
    private final FactionLandValueService landValue;
    private volatile long topCacheAt;
    private volatile List<Wealth> topCache = List.of();

    public MiraFactionsPlaceholderExpansion(MiraFactionsPlugin plugin, FactionService service, FactionLandValueService landValue) {
        this.plugin = plugin;
        this.service = service;
        this.landValue = landValue;
    }

    @Override public @NotNull String getIdentifier() { return "mirafactions"; }
    @Override public @NotNull String getAuthor() { return "FiveS"; }
    @Override public @NotNull String getVersion() { return plugin.getPluginMeta().getVersion(); }
    @Override public boolean persist() { return true; }

    @Override
    public @Nullable String onRequest(OfflinePlayer offline, @NotNull String params) {
        String key = params.toLowerCase(Locale.ROOT);
        String top = topPlaceholder(key);
        if (top != null) return top;
        if (offline == null) return "";

        Faction faction = service.of(offline.getUniqueId());
        return switch (key) {
            case "faction", "faction_name" -> faction == null ? "" : faction.name();
            case "faction_id" -> faction == null ? "" : faction.id().toString();
            case "description" -> faction == null ? "" : faction.description();
            case "link" -> faction == null ? "" : faction.link();
            case "rank" -> faction == null || faction.rank(offline.getUniqueId()) == null ? "" : faction.rank(offline.getUniqueId()).name();
            case "title" -> faction == null ? "" : faction.title(offline.getUniqueId());
            case "power" -> String.format(Locale.US, "%.1f", service.power(offline.getUniqueId()));
            case "faction_power" -> faction == null ? "0.0" : String.format(Locale.US, "%.1f", service.factionPower(faction));
            case "claims" -> faction == null ? "0" : Integer.toString(faction.claims().size());
            case "max_claims" -> faction == null ? "0" : Integer.toString(service.maxClaims(faction));
            case "raidable" -> Boolean.toString(faction != null && service.raidable(faction));
            case "bank" -> faction == null ? "0.00" : String.format(Locale.US, "%.2f", faction.bankBalance());
            case "tnt" -> faction == null ? "0" : Integer.toString(faction.tntBalance());
            case "members" -> faction == null ? "0" : Integer.toString(faction.members().size());
            case "online" -> faction == null ? "0" : Long.toString(faction.members().keySet().stream().map(plugin.getServer()::getPlayer).filter(java.util.Objects::nonNull).count());
            case "value", "wealth" -> faction == null ? "0.00" : String.format(Locale.US, "%.2f", landValue.value(faction) + faction.bankBalance());
            case "land_value", "spawner_value" -> faction == null ? "0.00" : String.format(Locale.US, "%.2f", landValue.value(faction));
            case "territory" -> {
                if (!(offline instanceof Player player)) yield "";
                Faction owner = service.owner(player.getLocation());
                yield owner == null ? service.territoryType(player.getLocation()).name() : owner.name();
            }
            case "territory_relation" -> {
                if (!(offline instanceof Player player)) yield "";
                Faction owner = service.owner(player.getLocation());
                yield owner == null ? service.territoryType(player.getLocation()).name() : service.relation(faction, owner).name();
            }
            default -> null;
        };
    }

    private String topPlaceholder(String key) {
        if (!key.startsWith("top_")) return null;
        String[] parts = key.split("_");
        if (parts.length != 3) return null;
        int rank;
        try { rank = Integer.parseInt(parts[1]); }
        catch (NumberFormatException ex) { return null; }
        if (rank < 1 || rank > 10) return "";
        List<Wealth> top = top();
        if (rank > top.size()) return "";
        Wealth entry = top.get(rank - 1);
        return switch (parts[2]) {
            case "name" -> entry.faction().name();
            case "value", "wealth" -> String.format(Locale.US, "%.2f", entry.total());
            case "land", "spawners" -> String.format(Locale.US, "%.2f", entry.land());
            case "bank" -> String.format(Locale.US, "%.2f", entry.faction().bankBalance());
            case "power" -> String.format(Locale.US, "%.1f", service.factionPower(entry.faction()));
            case "members" -> Integer.toString(entry.faction().members().size());
            default -> null;
        };
    }

    private List<Wealth> top() {
        long now = System.currentTimeMillis();
        if (now - topCacheAt < 10_000L) return topCache;
        synchronized (this) {
            if (now - topCacheAt < 10_000L) return topCache;
            List<Wealth> values = new ArrayList<>();
            for (Faction faction : service.all()) {
                double land = landValue.value(faction);
                values.add(new Wealth(faction, land, land + faction.bankBalance()));
            }
            values.sort(Comparator.comparingDouble(Wealth::total).reversed().thenComparing(w -> w.faction().name(), String.CASE_INSENSITIVE_ORDER));
            if (values.size() > 10) values = new ArrayList<>(values.subList(0, 10));
            topCache = List.copyOf(values);
            topCacheAt = now;
            return topCache;
        }
    }

    private record Wealth(Faction faction, double land, double total) {}
}
