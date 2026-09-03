package com.mira.factions.service;

import com.mira.factions.MiraFactionsPlugin;
import com.mira.factions.model.Faction;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.util.*;

public final class FactionSeasonService {
    private final MiraFactionsPlugin plugin;
    private final File file;
    private final YamlConfiguration yaml;

    public FactionSeasonService(MiraFactionsPlugin plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "seasons.yml");
        this.yaml = YamlConfiguration.loadConfiguration(file);
    }

    public String currentSeason() {
        return plugin.getConfig().getString("seasons.current", "season-1");
    }

    public synchronized void snapshot(Faction faction, double wealth, int ftopRank) {
        if (faction == null) return;
        String base = base(faction);
        long now = System.currentTimeMillis();
        if (!yaml.contains(base + ".started-at")) {
            yaml.set(base + ".started-at", now);
            yaml.set(base + ".start-wealth", wealth);
        }
        yaml.set(base + ".last-snapshot", now);
        yaml.set(base + ".current-wealth", wealth);
        yaml.set(base + ".peak-wealth", Math.max(wealth, yaml.getDouble(base + ".peak-wealth", 0D)));
        int best = yaml.getInt(base + ".best-ftop-rank", Integer.MAX_VALUE);
        if (ftopRank > 0 && ftopRank < best) yaml.set(base + ".best-ftop-rank", ftopRank);
        save();
    }

    public synchronized void recordRaid(Faction attacker, Faction defender, double transferredValue) {
        if (attacker != null) {
            String base = base(attacker);
            yaml.set(base + ".raids-won", yaml.getInt(base + ".raids-won", 0) + 1);
            yaml.set(base + ".raid-value-gained", yaml.getDouble(base + ".raid-value-gained", 0D) + Math.max(0D, transferredValue));
        }
        if (defender != null) {
            String base = base(defender);
            yaml.set(base + ".raids-lost", yaml.getInt(base + ".raids-lost", 0) + 1);
            yaml.set(base + ".raid-value-lost", yaml.getDouble(base + ".raid-value-lost", 0D) + Math.max(0D, transferredValue));
        }
        save();
    }

    public synchronized Stats stats(Faction faction) {
        if (faction == null) return Stats.EMPTY;
        String base = base(faction);
        int best = yaml.getInt(base + ".best-ftop-rank", 0);
        return new Stats(
                yaml.getDouble(base + ".start-wealth", 0D),
                yaml.getDouble(base + ".current-wealth", 0D),
                yaml.getDouble(base + ".peak-wealth", 0D),
                best == Integer.MAX_VALUE ? 0 : best,
                yaml.getInt(base + ".raids-won", 0),
                yaml.getInt(base + ".raids-lost", 0),
                yaml.getDouble(base + ".raid-value-gained", 0D),
                yaml.getDouble(base + ".raid-value-lost", 0D));
    }

    public synchronized double highestValueEver() {
        ConfigurationSection season = yaml.getConfigurationSection("seasons." + currentSeason() + ".factions");
        if (season == null) return 0D;
        double highest = 0D;
        for (String id : season.getKeys(false)) highest = Math.max(highest, season.getDouble(id + ".peak-wealth", 0D));
        return highest;
    }

    public synchronized String highestValueFaction() {
        ConfigurationSection season = yaml.getConfigurationSection("seasons." + currentSeason() + ".factions");
        if (season == null) return "";
        String name = "";
        double highest = -1D;
        for (String id : season.getKeys(false)) {
            double value = season.getDouble(id + ".peak-wealth", 0D);
            if (value > highest) {
                highest = value;
                name = season.getString(id + ".name", "");
            }
        }
        return name;
    }

    private String base(Faction faction) {
        String base = "seasons." + currentSeason() + ".factions." + faction.id();
        yaml.set(base + ".name", faction.name());
        return base;
    }

    private void save() {
        try { yaml.save(file); }
        catch (Exception ex) { plugin.getLogger().warning("Could not save seasons.yml: " + ex.getMessage()); }
    }

    public record Stats(double startWealth, double currentWealth, double peakWealth, int bestFtopRank,
                        int raidsWon, int raidsLost, double raidValueGained, double raidValueLost) {
        public static final Stats EMPTY = new Stats(0,0,0,0,0,0,0,0);
        public double wealthChange() { return currentWealth - startWealth; }
    }
}
