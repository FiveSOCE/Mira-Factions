package com.mira.factions.hook;

import com.mira.factions.MiraFactionsPlugin;
import com.mira.factions.model.Faction;
import com.mira.factions.service.FactionService;
import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Locale;

public final class MiraFactionsPlaceholderExpansion extends PlaceholderExpansion {
    private final MiraFactionsPlugin plugin;
    private final FactionService service;

    public MiraFactionsPlaceholderExpansion(MiraFactionsPlugin plugin, FactionService service) {
        this.plugin = plugin;
        this.service = service;
    }

    @Override public @NotNull String getIdentifier() { return "mirafactions"; }
    @Override public @NotNull String getAuthor() { return "FiveS"; }
    @Override public @NotNull String getVersion() { return plugin.getPluginMeta().getVersion(); }
    @Override public boolean persist() { return true; }

    @Override
    public @Nullable String onRequest(OfflinePlayer offline, @NotNull String params) {
        if (offline == null) return "";
        Faction faction = service.of(offline.getUniqueId());
        String key = params.toLowerCase(Locale.ROOT);
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
}
