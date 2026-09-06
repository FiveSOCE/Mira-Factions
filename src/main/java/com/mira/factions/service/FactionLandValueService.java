package com.mira.factions.service;

import com.mira.factions.MiraFactionsPlugin;
import com.mira.factions.model.Faction;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.block.BlockState;
import org.bukkit.block.CreatureSpawner;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.scheduler.BukkitTask;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.*;

public final class FactionLandValueService {
    private static final NamespacedKey STACK_SIZE = NamespacedKey.fromString("miraspawners:spawner_stack_size");

    private final MiraFactionsPlugin plugin;
    private final File cacheFile;
    private final Map<String, ChunkSnapshot> chunkCache = new HashMap<>();
    private volatile Map<EntityType, Double> spawnerPrices = Map.of();
    private double essentialsGenericSpawnerValue = -1D;
    private boolean dirty;
    private BukkitTask rebuildTask;

    public FactionLandValueService(MiraFactionsPlugin plugin) {
        this.plugin = plugin;
        this.cacheFile = new File(plugin.getDataFolder(), "ftop-cache.yml");
        loadCache();
        loadEssentialsFallback();
    }

    public double value(Faction faction) {
        return breakdown(faction).spawnerValue();
    }

    public Breakdown breakdown(Faction faction) {
        if (faction == null) return new Breakdown(0D, Map.of());

        EnumMap<EntityType, Integer> counts = new EnumMap<>(EntityType.class);
        for (String claim : faction.claims()) {
            ChunkSnapshot snapshot = chunkCache.get(claim);
            if (snapshot == null) continue;
            snapshot.counts().forEach((type, amount) -> counts.merge(type, amount, Integer::sum));
        }
        return new Breakdown(price(counts), Collections.unmodifiableMap(counts));
    }

    /**
     * Calculates a chunk that is already available to the caller. This method never loads
     * another chunk and does not alter the FTop cache by itself.
     */
    public ChunkBreakdown breakdown(Chunk chunk) {
        if (chunk == null || !chunk.isLoaded()) return new ChunkBreakdown(0D, Map.of());
        EnumMap<EntityType, Integer> counts = scanChunk(chunk);
        return new ChunkBreakdown(price(counts), Collections.unmodifiableMap(counts));
    }

    public double unitPrice(EntityType type) {
        if (type == null) return -1D;
        return spawnerPrices.getOrDefault(type, essentialsGenericSpawnerValue);
    }

    /**
     * Silent passive refresh. Nothing happens unless the player:
     * 1) belongs to a faction,
     * 2) is standing in that faction's own claim,
     * 3) has nearby chunks already loaded,
     * 4) and those chunks are also owned by that same faction.
     *
     * No chunks are loaded by this path.
     */
    public void refreshLoadedAround(Player player) {
        if (player == null || !player.isOnline()) return;
        FactionService factions = plugin.factions();
        Faction faction = factions.of(player.getUniqueId());
        if (faction == null) return;
        if (factions.owner(player.getLocation()) != faction) return;

        int radius = Math.max(0, Math.min(12, plugin.getConfig().getInt("ftop.passive-radius-chunks", 5)));
        Chunk centre = player.getLocation().getChunk();
        World world = player.getWorld();
        boolean changed = false;

        for (int x = centre.getX() - radius; x <= centre.getX() + radius; x++) {
            for (int z = centre.getZ() - radius; z <= centre.getZ() + radius; z++) {
                if (!world.isChunkLoaded(x, z)) continue;

                Location probe = new Location(world, (x << 4) + 8, world.getMinHeight(), (z << 4) + 8);
                if (factions.owner(probe) != faction) continue;

                Chunk chunk = world.getChunkAt(x, z);
                String claim = claimKey(world, x, z);
                ChunkSnapshot next = new ChunkSnapshot(scanChunk(chunk));
                if (!next.equals(chunkCache.put(claim, next))) changed = true;
            }
        }

        if (changed) dirty = true;
    }

    /**
     * Explicit administrator-only full update. This is the only path that deliberately
     * loads every claimed chunk. Work is batched over ticks to avoid one giant freeze.
     */
    public boolean startFullRebuild(CommandSender sender) {
        if (rebuildTask != null) {
            if (sender != null) plugin.msg(sender, "&cAn FTop full update is already running.");
            return false;
        }

        List<Claim> claims = new ArrayList<>();
        for (Faction faction : plugin.factions().all()) {
            for (String key : faction.claims()) {
                Claim claim = parse(key);
                if (claim != null) claims.add(claim);
            }
        }

        chunkCache.clear();
        dirty = true;

        if (claims.isEmpty()) {
            saveCache();
            if (sender != null) plugin.msg(sender, "&aFTop cache updated. There are no faction claims to scan.");
            return true;
        }

        final Iterator<Claim> iterator = claims.iterator();
        final int total = claims.size();
        final int[] processed = {0};
        final int batch = Math.max(1, Math.min(50, plugin.getConfig().getInt("ftop.manual-chunks-per-tick", 8)));

        if (sender != null) plugin.msg(sender, "&eStarting full FTop update for &f" + total + " &eclaimed chunk(s).");

        rebuildTask = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            int work = 0;
            while (iterator.hasNext() && work++ < batch) {
                Claim claim = iterator.next();
                processed[0]++;

                World world = Bukkit.getWorld(claim.world());
                if (world == null) continue;

                Location probe = new Location(world, (claim.x() << 4) + 8, world.getMinHeight(), (claim.z() << 4) + 8);
                Faction owner = plugin.factions().owner(probe);
                if (owner == null) continue;

                Chunk chunk = world.getChunkAt(claim.x(), claim.z());
                chunkCache.put(claim.key(), new ChunkSnapshot(scanChunk(chunk)));
                dirty = true;
            }

            if (!iterator.hasNext()) {
                BukkitTask done = rebuildTask;
                rebuildTask = null;
                if (done != null) done.cancel();
                saveCache();
                if (sender != null) {
                    plugin.msg(sender, "&aFTop full update complete. Scanned &f" + processed[0] + "&a claimed chunk(s).");
                }
            }
        }, 1L, 1L);

        return true;
    }

    public boolean rebuildRunning() {
        return rebuildTask != null;
    }

    public void flushIfDirty() {
        if (dirty) saveCache();
    }

    public void shutdown() {
        if (rebuildTask != null) {
            rebuildTask.cancel();
            rebuildTask = null;
        }
        saveCache();
    }

    public void updateSpawnerPrices(Map<EntityType, Double> prices) {
        if (prices == null || prices.isEmpty()) {
            spawnerPrices = Map.of();
            return;
        }
        EnumMap<EntityType, Double> copy = new EnumMap<>(EntityType.class);
        for (Map.Entry<EntityType, Double> entry : prices.entrySet()) {
            if (entry.getKey() != null && entry.getValue() != null
                    && Double.isFinite(entry.getValue()) && entry.getValue() >= 0D) {
                copy.put(entry.getKey(), entry.getValue());
            }
        }
        spawnerPrices = Map.copyOf(copy);
        plugin.getLogger().info("Refreshed cached MiraShop spawner prices: " + spawnerPrices.size() + " type(s).");
    }

    private EnumMap<EntityType, Integer> scanChunk(Chunk chunk) {
        EnumMap<EntityType, Integer> counts = new EnumMap<>(EntityType.class);
        for (BlockState state : chunk.getTileEntities()) {
            if (!(state instanceof CreatureSpawner spawner)) continue;
            EntityType type = spawner.getSpawnedType();
            if (type == null) continue;
            counts.merge(type, stackSize(spawner), Integer::sum);
        }
        return counts;
    }

    private int stackSize(CreatureSpawner spawner) {
        if (STACK_SIZE == null) return 1;
        Integer stored = spawner.getPersistentDataContainer().get(STACK_SIZE, PersistentDataType.INTEGER);
        return stored == null ? 1 : Math.max(1, stored);
    }

    private double price(Map<EntityType, Integer> counts) {
        double total = 0D;
        for (Map.Entry<EntityType, Integer> entry : counts.entrySet()) {
            double unit = unitPrice(entry.getKey());
            if (unit > 0D) total += unit * entry.getValue();
        }
        return total;
    }

    private void loadEssentialsFallback() {
        essentialsGenericSpawnerValue = -1D;
        var essentials = Bukkit.getPluginManager().getPlugin("Essentials");
        if (essentials == null) return;

        File worth = new File(essentials.getDataFolder(), "worth.yml");
        if (!worth.isFile()) return;

        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(worth);
        essentialsGenericSpawnerValue = firstPositive(
                yaml.getDouble("worth.spawner", -1D),
                yaml.getDouble("worth.monster_spawner", -1D),
                yaml.getDouble("spawner", -1D),
                yaml.getDouble("monster_spawner", -1D)
        );
    }

    private void loadCache() {
        if (!cacheFile.isFile()) return;
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(cacheFile);
        ConfigurationSection chunks = yaml.getConfigurationSection("chunks");
        if (chunks == null) return;

        for (String encoded : chunks.getKeys(false)) {
            String claim;
            try {
                claim = new String(Base64.getUrlDecoder().decode(encoded), StandardCharsets.UTF_8);
            } catch (IllegalArgumentException ex) {
                continue;
            }

            EnumMap<EntityType, Integer> counts = new EnumMap<>(EntityType.class);
            ConfigurationSection countSection = chunks.getConfigurationSection(encoded + ".counts");
            if (countSection != null) {
                for (String typeName : countSection.getKeys(false)) {
                    try {
                        EntityType type = EntityType.valueOf(typeName);
                        int amount = Math.max(0, countSection.getInt(typeName));
                        if (amount > 0) counts.put(type, amount);
                    } catch (IllegalArgumentException ignored) { }
                }
            }
            chunkCache.put(claim, new ChunkSnapshot(counts));
        }
    }

    private synchronized void saveCache() {
        YamlConfiguration yaml = new YamlConfiguration();

        // Drop stale entries while saving. Only chunks that are still faction-owned survive.
        Set<String> liveClaims = new HashSet<>();
        for (Faction faction : plugin.factions().all()) liveClaims.addAll(faction.claims());
        chunkCache.keySet().removeIf(key -> !liveClaims.contains(key));

        for (Map.Entry<String, ChunkSnapshot> entry : chunkCache.entrySet()) {
            String encoded = Base64.getUrlEncoder().withoutPadding()
                    .encodeToString(entry.getKey().getBytes(StandardCharsets.UTF_8));
            for (Map.Entry<EntityType, Integer> count : entry.getValue().counts().entrySet()) {
                yaml.set("chunks." + encoded + ".counts." + count.getKey().name(), count.getValue());
            }
        }

        try {
            cacheFile.getParentFile().mkdirs();
            yaml.save(cacheFile);
            dirty = false;
        } catch (IOException ex) {
            plugin.getLogger().severe("Failed to save ftop-cache.yml: " + ex.getMessage());
        }
    }

    private static double firstPositive(double... values) {
        for (double value : values) if (Double.isFinite(value) && value >= 0D) return value;
        return -1D;
    }

    private static String claimKey(World world, int x, int z) {
        return world.getUID() + ":" + x + ":" + z;
    }

    private Claim parse(String key) {
        if (key == null) return null;
        String[] parts = key.split(":");
        if (parts.length != 3) return null;
        try {
            UUID world = UUID.fromString(parts[0]);
            int x = Integer.parseInt(parts[1]);
            int z = Integer.parseInt(parts[2]);
            return new Claim(world, x, z, key);
        } catch (Exception ignored) {
            return null;
        }
    }

    public record Breakdown(double spawnerValue, Map<EntityType, Integer> spawnerCounts) {
        public int totalSpawners() { return spawnerCounts.values().stream().mapToInt(Integer::intValue).sum(); }
    }

    public record ChunkBreakdown(double spawnerValue, Map<EntityType, Integer> spawnerCounts) {
        public int totalSpawners() { return spawnerCounts.values().stream().mapToInt(Integer::intValue).sum(); }
    }

    private record ChunkSnapshot(Map<EntityType, Integer> counts) {
        private ChunkSnapshot {
            EnumMap<EntityType, Integer> copy = new EnumMap<>(EntityType.class);
            if (counts != null) copy.putAll(counts);
            counts = Collections.unmodifiableMap(copy);
        }
    }

    private record Claim(UUID world, int x, int z, String key) { }
}
