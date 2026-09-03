package com.mira.factions.service;

import com.mira.factions.MiraFactionsPlugin;
import com.mira.factions.model.Faction;
import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.block.BlockState;
import org.bukkit.block.CreatureSpawner;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.EntityType;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;

import java.io.File;
import java.util.*;

public final class FactionLandValueService {
    private static final NamespacedKey STACK_SIZE = NamespacedKey.fromString("miraspawners:spawner_stack_size");

    private final MiraFactionsPlugin plugin;
    private final Map<EntityType, Double> spawnerPrices = new EnumMap<>(EntityType.class);
    private long lastPriceRefresh;
    private double essentialsGenericSpawnerValue = -1D;

    public FactionLandValueService(MiraFactionsPlugin plugin) {
        this.plugin = plugin;
    }

    public double value(Faction faction) {
        if (faction == null) return 0D;
        refreshPrices();
        double total = 0D;
        for (String claim : faction.claims()) {
            Claim parsed = parse(claim);
            if (parsed == null) continue;
            World world = Bukkit.getWorld(parsed.world());
            if (world == null) continue;
            var chunk = world.getChunkAt(parsed.x(), parsed.z());
            for (BlockState state : chunk.getTileEntities()) {
                if (!(state instanceof CreatureSpawner spawner)) continue;
                EntityType type = spawner.getSpawnedType();
                if (type == null) continue;
                int stack = 1;
                if (STACK_SIZE != null) {
                    Integer stored = spawner.getPersistentDataContainer().get(STACK_SIZE, PersistentDataType.INTEGER);
                    if (stored != null) stack = Math.max(1, stored);
                }
                double unit = spawnerPrices.getOrDefault(type, essentialsGenericSpawnerValue);
                if (unit > 0D) total += unit * stack;
            }
        }
        return total;
    }

    private void refreshPrices() {
        long now = System.currentTimeMillis();
        if (now - lastPriceRefresh < 30_000L) return;
        lastPriceRefresh = now;
        spawnerPrices.clear();
        essentialsGenericSpawnerValue = -1D;

        Plugin shop = Bukkit.getPluginManager().getPlugin("MiraShop");
        if (shop != null) {
            File file = new File(shop.getDataFolder(), "shops.yml");
            if (file.isFile()) {
                YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
                ConfigurationSection items = yaml.getConfigurationSection("sections.spawners.items");
                if (items != null) {
                    for (String id : items.getKeys(false)) {
                        String base = "sections.spawners.items." + id;
                        String rawType = yaml.getString(base + ".spawner-type");
                        double buy = yaml.getDouble(base + ".buy", -1D);
                        if (rawType == null || buy < 0D) continue;
                        try {
                            spawnerPrices.put(EntityType.valueOf(rawType.toUpperCase(Locale.ROOT)), buy);
                        } catch (IllegalArgumentException ignored) { }
                    }
                }
            }
        }

        Plugin essentials = Bukkit.getPluginManager().getPlugin("Essentials");
        if (essentials != null) {
            File worth = new File(essentials.getDataFolder(), "worth.yml");
            if (worth.isFile()) {
                YamlConfiguration yaml = YamlConfiguration.loadConfiguration(worth);
                essentialsGenericSpawnerValue = firstPositive(
                        yaml.getDouble("worth.spawner", -1D),
                        yaml.getDouble("worth.monster_spawner", -1D),
                        yaml.getDouble("spawner", -1D),
                        yaml.getDouble("monster_spawner", -1D)
                );
            }
        }
    }

    private static double firstPositive(double... values) {
        for (double value : values) if (Double.isFinite(value) && value >= 0D) return value;
        return -1D;
    }

    private Claim parse(String key) {
        if (key == null) return null;
        String[] parts = key.split(":");
        if (parts.length != 3) return null;
        try {
            return new Claim(UUID.fromString(parts[0]), Integer.parseInt(parts[1]), Integer.parseInt(parts[2]));
        } catch (Exception ignored) {
            plugin.getLogger().fine("Could not parse faction claim key for value: " + key);
            return null;
        }
    }

    private record Claim(UUID world, int x, int z) {}
}
