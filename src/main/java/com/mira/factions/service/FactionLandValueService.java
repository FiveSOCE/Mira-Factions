package com.mira.factions.service;

import com.mira.factions.MiraFactionsPlugin;
import com.mira.factions.model.Faction;
import com.mira.shop.api.SpawnerPriceService;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.block.BlockState;
import org.bukkit.block.Chest;
import org.bukkit.block.Container;
import org.bukkit.block.CreatureSpawner;
import org.bukkit.block.ShulkerBox;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.BlockStateMeta;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.scheduler.BukkitTask;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.*;

public final class FactionLandValueService {
    private static final NamespacedKey STACK_SIZE = NamespacedKey.fromString("miraspawners:spawner_stack_size");
    private static final NamespacedKey SPAWNER_ITEM_TYPE = NamespacedKey.fromString("miraspawners:spawner_mob_type");
    private static final NamespacedKey LEGACY_ITEM_STACK_SIZE = NamespacedKey.fromString("miraspawners:spawner_item_stack_size");

    private final MiraFactionsPlugin plugin;
    private final File cacheFile;
    private final Map<String, ChunkSnapshot> chunkCache = new HashMap<>();
    private final Set<String> queuedRefreshes = new HashSet<>();
    private final Map<Material, Double> worthValues = new EnumMap<>(Material.class);
    private volatile Map<EntityType, Double> spawnerPrices = Map.of();
    private double essentialsGenericSpawnerValue = -1D;
    private boolean dirty;
    private BukkitTask rebuildTask;

    public FactionLandValueService(MiraFactionsPlugin plugin) {
        this.plugin = plugin;
        this.cacheFile = new File(plugin.getDataFolder(), "ftop-cache.yml");
        loadCache();
        loadWorthValues();
    }

    public double value(Faction faction) {
        return breakdown(faction).totalValue();
    }

    public Breakdown breakdown(Faction faction) {
        if (faction == null) {
            return new Breakdown(0D, 0D, 0D, Map.of(), Map.of(), Map.of(), Map.of());
        }

        EnumMap<EntityType, Integer> placedSpawners = new EnumMap<>(EntityType.class);
        EnumMap<EntityType, Integer> storedSpawners = new EnumMap<>(EntityType.class);
        EnumMap<Material, Long> placedBlocks = new EnumMap<>(Material.class);
        EnumMap<Material, Long> storedItems = new EnumMap<>(Material.class);

        for (String claim : faction.claims()) {
            ChunkSnapshot snapshot = chunkCache.get(claim);
            if (snapshot == null) continue;
            mergeInt(placedSpawners, snapshot.placedSpawners());
            mergeInt(storedSpawners, snapshot.storedSpawners());
            mergeLong(placedBlocks, snapshot.placedBlocks());
            mergeLong(storedItems, snapshot.storedItems());
        }

        double placedSpawnerValue = priceSpawners(placedSpawners);
        double storedSpawnerValue = priceSpawners(storedSpawners);
        double blockValue = priceMaterials(placedBlocks);
        double storedItemValue = priceMaterials(storedItems);

        return new Breakdown(
                placedSpawnerValue + storedSpawnerValue,
                blockValue,
                storedItemValue,
                Collections.unmodifiableMap(placedSpawners),
                Collections.unmodifiableMap(storedSpawners),
                Collections.unmodifiableMap(placedBlocks),
                Collections.unmodifiableMap(storedItems)
        );
    }

    public ChunkBreakdown breakdown(Chunk chunk) {
        if (chunk == null || !chunk.isLoaded()) {
            return new ChunkBreakdown(0D, 0D, 0D, Map.of(), Map.of(), Map.of(), Map.of());
        }
        ChunkSnapshot snapshot = scanChunk(chunk);
        return snapshot.breakdown(this);
    }

    public double unitPrice(EntityType type) {
        if (type == null) return -1D;
        return spawnerPrices.getOrDefault(type, essentialsGenericSpawnerValue);
    }

    public double unitPrice(Material material) {
        if (material == null) return -1D;

        String path = "ftop.item-values." + material.name();
        if (plugin.getConfig().contains(path)) {
            double configured = plugin.getConfig().getDouble(path, -1D);
            if (Double.isFinite(configured) && configured >= 0D) return configured;
        }
        return worthValues.getOrDefault(material, -1D);
    }

    public boolean isTrackedMaterial(Material material) {
        return material != null && unitPrice(material) > 0D;
    }

    public void refreshLoadedAround(Player player) {
        if (player == null || !player.isOnline()) return;
        FactionService factions = plugin.factions();
        Faction faction = factions.of(player.getUniqueId());
        if (faction == null || factions.owner(player.getLocation()) != faction) return;

        int radius = Math.max(0, Math.min(4, plugin.getConfig().getInt("ftop.passive-radius-chunks", 2)));
        Chunk centre = player.getLocation().getChunk();
        World world = player.getWorld();
        boolean changed = false;

        for (int x = centre.getX() - radius; x <= centre.getX() + radius; x++) {
            for (int z = centre.getZ() - radius; z <= centre.getZ() + radius; z++) {
                if (!world.isChunkLoaded(x, z)) continue;

                Location probe = new Location(world, (x << 4) + 8, world.getMinHeight(), (z << 4) + 8);
                if (factions.owner(probe) != faction) continue;

                String claim = claimKey(world, x, z);
                if (chunkCache.containsKey(claim)) continue;

                ChunkSnapshot next = scanChunk(world.getChunkAt(x, z));
                if (!next.equals(chunkCache.put(claim, next))) changed = true;
            }
        }

        if (changed) dirty = true;
    }

    public void queueRefresh(Location location) {
        if (location == null || location.getWorld() == null) return;
        String key = claimKey(location.getWorld(), location.getBlockX() >> 4, location.getBlockZ() >> 4);
        if (!queuedRefreshes.add(key)) return;

        Location snapshot = location.clone();
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            queuedRefreshes.remove(key);
            refreshChunk(snapshot);
        }, Math.max(1L, plugin.getConfig().getLong("ftop.refresh-debounce-ticks", 10L)));
    }

    public void refreshChunk(Location location) {
        if (location == null || location.getWorld() == null) return;
        Faction owner = plugin.factions().owner(location);
        if (owner == null) return;

        Chunk chunk = location.getChunk();
        if (!chunk.isLoaded()) return;

        String claim = plugin.factions().claimKey(location);
        ChunkSnapshot next = scanChunk(chunk);
        if (!next.equals(chunkCache.put(claim, next))) dirty = true;
    }

    public boolean startFullRebuild(CommandSender sender) {
        refreshSpawnerPricesFromService();
        loadWorthValues();

        if (rebuildTask != null) {
            if (sender != null) plugin.msg(sender, "&cAn FTop full update is already running.");
            return false;
        }

        List<Claim> claims = new ArrayList<>();
        for (Faction faction : plugin.factions().all()) {
            for (String key : faction.claims()) {
                Claim claim = parse(key, faction.id());
                if (claim != null) claims.add(claim);
            }
        }

        if (claims.isEmpty()) {
            chunkCache.clear();
            dirty = true;
            saveCache();
            if (sender != null) plugin.msg(sender, "&aFTop cache updated. There are no faction claims to scan.");
            return true;
        }

        final Iterator<Claim> iterator = claims.iterator();
        final Map<String, ChunkSnapshot> rebuilt = new HashMap<>();
        final int total = claims.size();
        final int[] processed = {0};
        final int batch = Math.max(1, Math.min(20, plugin.getConfig().getInt("ftop.manual-chunks-per-tick", 2)));

        if (sender != null) {
            plugin.msg(sender, "&eStarting full FTop asset update for &f" + total + " &eclaimed chunk(s).");
        }

        rebuildTask = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            int work = 0;
            while (iterator.hasNext() && work++ < batch) {
                Claim claim = iterator.next();
                processed[0]++;

                World world = Bukkit.getWorld(claim.world());
                if (world == null) continue;

                Location probe = new Location(world, (claim.x() << 4) + 8, world.getMinHeight(), (claim.z() << 4) + 8);
                Faction owner = plugin.factions().owner(probe);
                if (owner == null || !owner.id().equals(claim.factionId())) continue;

                Chunk chunk = world.getChunkAt(claim.x(), claim.z());
                rebuilt.put(claim.key(), scanChunk(chunk));
            }

            if (!iterator.hasNext()) {
                BukkitTask done = rebuildTask;
                rebuildTask = null;
                if (done != null) done.cancel();
                chunkCache.clear();
                chunkCache.putAll(rebuilt);
                dirty = true;
                saveCache();
                if (sender != null) {
                    plugin.msg(sender, "&aFTop full asset update complete. Scanned &f" + processed[0] + "&a claimed chunk(s).");
                }
            }
        }, 1L, 1L);

        return true;
    }

    public void applySpawnerChange(Location location, EntityType type, int oldAmount, int newAmount) {
        if (location == null || location.getWorld() == null || type == null) return;
        Faction owner = plugin.factions().owner(location);
        if (owner == null) return;

        String claim = plugin.factions().claimKey(location);
        ChunkSnapshot current = chunkCache.get(claim);
        if (current == null) {
            refreshChunk(location);
            return;
        }

        EnumMap<EntityType, Integer> placed = new EnumMap<>(EntityType.class);
        placed.putAll(current.placedSpawners());

        int existing = placed.getOrDefault(type, 0);
        int next = Math.max(0, existing + Math.max(0, newAmount) - Math.max(0, oldAmount));
        if (next <= 0) placed.remove(type);
        else placed.put(type, next);

        chunkCache.put(claim, new ChunkSnapshot(
                placed,
                current.storedSpawners(),
                current.placedBlocks(),
                current.storedItems()
        ));
        dirty = true;
    }

    public void refreshSpawnerPricesFromService() {
        SpawnerPriceService service = Bukkit.getServicesManager().load(SpawnerPriceService.class);
        if (service != null) updateSpawnerPrices(service.buyPrices());
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

    private ChunkSnapshot scanChunk(Chunk chunk) {
        EnumMap<EntityType, Integer> placedSpawners = new EnumMap<>(EntityType.class);
        EnumMap<EntityType, Integer> storedSpawners = new EnumMap<>(EntityType.class);
        EnumMap<Material, Long> placedBlocks = new EnumMap<>(Material.class);
        EnumMap<Material, Long> storedItems = new EnumMap<>(Material.class);

        for (BlockState state : chunk.getTileEntities()) {
            if (state instanceof CreatureSpawner spawner) {
                EntityType type = spawner.getSpawnedType();
                if (type != null) placedSpawners.merge(type, stackSize(spawner), Integer::sum);
                continue;
            }

            if (state instanceof Container container) {
                Inventory inventory = state instanceof Chest chest
                        ? chest.getBlockInventory()
                        : container.getInventory();
                scanInventory(inventory, storedSpawners, storedItems, 0);
            }
        }

        if (plugin.getConfig().getBoolean("ftop.count-placed-valuables", true)) {
            World world = chunk.getWorld();
            int minY = world.getMinHeight();
            int maxY = world.getMaxHeight();
            int baseX = chunk.getX() << 4;
            int baseZ = chunk.getZ() << 4;

            for (int x = 0; x < 16; x++) {
                for (int z = 0; z < 16; z++) {
                    for (int y = minY; y < maxY; y++) {
                        Material material = world.getBlockAt(baseX + x, y, baseZ + z).getType();
                        if (isTrackedMaterial(material)) {
                            placedBlocks.merge(material, 1L, Long::sum);
                        }
                    }
                }
            }
        }

        return new ChunkSnapshot(placedSpawners, storedSpawners, placedBlocks, storedItems);
    }

    private void scanInventory(
            Inventory inventory,
            EnumMap<EntityType, Integer> storedSpawners,
            EnumMap<Material, Long> storedItems,
            int depth
    ) {
        if (inventory == null) return;
        int maxDepth = Math.max(0, Math.min(8, plugin.getConfig().getInt("ftop.container-max-nesting-depth", 4)));

        for (ItemStack item : inventory.getContents()) {
            if (item == null || item.getType().isAir() || item.getAmount() <= 0) continue;

            Optional<EntityType> spawnerType = spawnerItemType(item);
            if (spawnerType.isPresent()) {
                storedSpawners.merge(spawnerType.get(), spawnerItemUnits(item), Integer::sum);
                continue;
            }

            if (plugin.getConfig().getBoolean("ftop.count-container-items", true) && isTrackedMaterial(item.getType())) {
                storedItems.merge(item.getType(), (long) item.getAmount(), Long::sum);
            }

            if (depth < maxDepth) {
                Inventory nested = shulkerInventory(item);
                if (nested != null) scanInventory(nested, storedSpawners, storedItems, depth + 1);
            }
        }
    }

    private Inventory shulkerInventory(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return null;
        ItemMeta meta = item.getItemMeta();
        if (!(meta instanceof BlockStateMeta blockStateMeta)) return null;
        BlockState state = blockStateMeta.getBlockState();
        if (!(state instanceof ShulkerBox shulker)) return null;
        return shulker.getInventory();
    }

    private Optional<EntityType> spawnerItemType(ItemStack item) {
        if (item == null || item.getType() != Material.SPAWNER || !item.hasItemMeta()) return Optional.empty();

        ItemMeta meta = item.getItemMeta();
        if (SPAWNER_ITEM_TYPE != null) {
            String stored = meta.getPersistentDataContainer().get(SPAWNER_ITEM_TYPE, PersistentDataType.STRING);
            if (stored != null) {
                try {
                    return Optional.of(EntityType.valueOf(stored));
                } catch (IllegalArgumentException ignored) { }
            }
        }

        if (meta instanceof BlockStateMeta blockStateMeta) {
            BlockState state = blockStateMeta.getBlockState();
            if (state instanceof CreatureSpawner spawner && spawner.getSpawnedType() != null) {
                return Optional.of(spawner.getSpawnedType());
            }
        }
        return Optional.empty();
    }

    private int spawnerItemUnits(ItemStack item) {
        int perItem = 1;
        if (LEGACY_ITEM_STACK_SIZE != null && item.hasItemMeta()) {
            Integer stored = item.getItemMeta().getPersistentDataContainer()
                    .get(LEGACY_ITEM_STACK_SIZE, PersistentDataType.INTEGER);
            if (stored != null) perItem = Math.max(1, stored);
        }

        long total = (long) perItem * Math.max(1, item.getAmount());
        return total > Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) total;
    }

    private int stackSize(CreatureSpawner spawner) {
        if (STACK_SIZE == null) return 1;
        Integer stored = spawner.getPersistentDataContainer().get(STACK_SIZE, PersistentDataType.INTEGER);
        return stored == null ? 1 : Math.max(1, stored);
    }

    private double priceSpawners(Map<EntityType, Integer> counts) {
        double total = 0D;
        for (Map.Entry<EntityType, Integer> entry : counts.entrySet()) {
            double unit = unitPrice(entry.getKey());
            if (unit > 0D) total += unit * entry.getValue();
        }
        return total;
    }

    private double priceMaterials(Map<Material, Long> counts) {
        double total = 0D;
        for (Map.Entry<Material, Long> entry : counts.entrySet()) {
            double unit = unitPrice(entry.getKey());
            if (unit > 0D) total += unit * entry.getValue();
        }
        return total;
    }

    private void loadWorthValues() {
        worthValues.clear();
        essentialsGenericSpawnerValue = -1D;

        var essentials = Bukkit.getPluginManager().getPlugin("Essentials");
        if (essentials != null) {
            File worth = new File(essentials.getDataFolder(), "worth.yml");
            if (worth.isFile()) {
                YamlConfiguration yaml = YamlConfiguration.loadConfiguration(worth);
                ConfigurationSection section = yaml.getConfigurationSection("worth");
                if (section == null) section = yaml;

                for (String key : section.getKeys(false)) {
                    Material material = Material.matchMaterial(key);
                    if (material == null) material = Material.matchMaterial(key.toUpperCase(Locale.ROOT));
                    double value = section.getDouble(key, -1D);
                    if (material != null && Double.isFinite(value) && value >= 0D) {
                        worthValues.put(material, value);
                    }
                }

                essentialsGenericSpawnerValue = firstPositive(
                        section.getDouble("spawner", -1D),
                        section.getDouble("monster_spawner", -1D),
                        yaml.getDouble("spawner", -1D),
                        yaml.getDouble("monster_spawner", -1D)
                );
            }
        }

        ConfigurationSection overrides = plugin.getConfig().getConfigurationSection("ftop.item-values");
        if (overrides != null) {
            for (String key : overrides.getKeys(false)) {
                Material material = Material.matchMaterial(key);
                if (material == null) continue;
                double value = overrides.getDouble(key, -1D);
                if (Double.isFinite(value) && value >= 0D) worthValues.put(material, value);
            }
        }

        plugin.getLogger().info("Loaded FTop material values for " + worthValues.size() + " material(s).");
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

            EnumMap<EntityType, Integer> placedSpawners = readEntityCounts(chunks.getConfigurationSection(encoded + ".placed-spawners"));
            if (placedSpawners.isEmpty()) {
                placedSpawners = readEntityCounts(chunks.getConfigurationSection(encoded + ".counts"));
            }

            EnumMap<EntityType, Integer> storedSpawners = readEntityCounts(chunks.getConfigurationSection(encoded + ".stored-spawners"));
            EnumMap<Material, Long> placedBlocks = readMaterialCounts(chunks.getConfigurationSection(encoded + ".placed-blocks"));
            EnumMap<Material, Long> storedItems = readMaterialCounts(chunks.getConfigurationSection(encoded + ".stored-items"));

            chunkCache.put(claim, new ChunkSnapshot(placedSpawners, storedSpawners, placedBlocks, storedItems));
        }
    }

    private synchronized void saveCache() {
        YamlConfiguration yaml = new YamlConfiguration();

        Set<String> liveClaims = new HashSet<>();
        for (Faction faction : plugin.factions().all()) liveClaims.addAll(faction.claims());
        chunkCache.keySet().removeIf(key -> !liveClaims.contains(key));

        for (Map.Entry<String, ChunkSnapshot> entry : chunkCache.entrySet()) {
            String encoded = Base64.getUrlEncoder().withoutPadding()
                    .encodeToString(entry.getKey().getBytes(StandardCharsets.UTF_8));
            String root = "chunks." + encoded + ".";

            writeEntityCounts(yaml, root + "placed-spawners", entry.getValue().placedSpawners());
            writeEntityCounts(yaml, root + "stored-spawners", entry.getValue().storedSpawners());
            writeMaterialCounts(yaml, root + "placed-blocks", entry.getValue().placedBlocks());
            writeMaterialCounts(yaml, root + "stored-items", entry.getValue().storedItems());
        }

        try {
            cacheFile.getParentFile().mkdirs();
            yaml.save(cacheFile);
            dirty = false;
        } catch (IOException ex) {
            plugin.getLogger().severe("Failed to save ftop-cache.yml: " + ex.getMessage());
        }
    }

    private static EnumMap<EntityType, Integer> readEntityCounts(ConfigurationSection section) {
        EnumMap<EntityType, Integer> counts = new EnumMap<>(EntityType.class);
        if (section == null) return counts;
        for (String name : section.getKeys(false)) {
            try {
                EntityType type = EntityType.valueOf(name);
                int amount = Math.max(0, section.getInt(name));
                if (amount > 0) counts.put(type, amount);
            } catch (IllegalArgumentException ignored) { }
        }
        return counts;
    }

    private static EnumMap<Material, Long> readMaterialCounts(ConfigurationSection section) {
        EnumMap<Material, Long> counts = new EnumMap<>(Material.class);
        if (section == null) return counts;
        for (String name : section.getKeys(false)) {
            Material material = Material.matchMaterial(name);
            long amount = Math.max(0L, section.getLong(name));
            if (material != null && amount > 0L) counts.put(material, amount);
        }
        return counts;
    }

    private static void writeEntityCounts(YamlConfiguration yaml, String root, Map<EntityType, Integer> counts) {
        for (Map.Entry<EntityType, Integer> entry : counts.entrySet()) {
            yaml.set(root + "." + entry.getKey().name(), entry.getValue());
        }
    }

    private static void writeMaterialCounts(YamlConfiguration yaml, String root, Map<Material, Long> counts) {
        for (Map.Entry<Material, Long> entry : counts.entrySet()) {
            yaml.set(root + "." + entry.getKey().name(), entry.getValue());
        }
    }

    private static void mergeInt(Map<EntityType, Integer> target, Map<EntityType, Integer> source) {
        source.forEach((key, value) -> target.merge(key, value, Integer::sum));
    }

    private static void mergeLong(Map<Material, Long> target, Map<Material, Long> source) {
        source.forEach((key, value) -> target.merge(key, value, Long::sum));
    }

    private static double firstPositive(double... values) {
        for (double value : values) if (Double.isFinite(value) && value >= 0D) return value;
        return -1D;
    }

    private static String claimKey(World world, int x, int z) {
        return world.getUID() + ":" + x + ":" + z;
    }

    private Claim parse(String key, UUID factionId) {
        if (key == null || factionId == null) return null;
        String[] parts = key.split(":");
        if (parts.length != 3) return null;
        try {
            UUID world = UUID.fromString(parts[0]);
            int x = Integer.parseInt(parts[1]);
            int z = Integer.parseInt(parts[2]);
            return new Claim(world, x, z, key, factionId);
        } catch (Exception ignored) {
            return null;
        }
    }

    public record Breakdown(
            double spawnerValue,
            double blockValue,
            double containerValue,
            Map<EntityType, Integer> placedSpawnerCounts,
            Map<EntityType, Integer> storedSpawnerCounts,
            Map<Material, Long> placedBlockCounts,
            Map<Material, Long> storedItemCounts
    ) {
        public double totalValue() { return spawnerValue + blockValue + containerValue; }
        public Map<EntityType, Integer> spawnerCounts() {
            EnumMap<EntityType, Integer> combined = new EnumMap<>(EntityType.class);
            placedSpawnerCounts.forEach((type, amount) -> combined.merge(type, amount, Integer::sum));
            storedSpawnerCounts.forEach((type, amount) -> combined.merge(type, amount, Integer::sum));
            return Collections.unmodifiableMap(combined);
        }
        public int totalPlacedSpawners() { return placedSpawnerCounts.values().stream().mapToInt(Integer::intValue).sum(); }
        public int totalStoredSpawners() { return storedSpawnerCounts.values().stream().mapToInt(Integer::intValue).sum(); }
        public int totalSpawners() { return totalPlacedSpawners() + totalStoredSpawners(); }
    }

    public record ChunkBreakdown(
            double spawnerValue,
            double blockValue,
            double containerValue,
            Map<EntityType, Integer> placedSpawnerCounts,
            Map<EntityType, Integer> storedSpawnerCounts,
            Map<Material, Long> placedBlockCounts,
            Map<Material, Long> storedItemCounts
    ) {
        public double totalValue() { return spawnerValue + blockValue + containerValue; }
        public int totalSpawners() {
            return placedSpawnerCounts.values().stream().mapToInt(Integer::intValue).sum()
                    + storedSpawnerCounts.values().stream().mapToInt(Integer::intValue).sum();
        }
    }

    private record ChunkSnapshot(
            Map<EntityType, Integer> placedSpawners,
            Map<EntityType, Integer> storedSpawners,
            Map<Material, Long> placedBlocks,
            Map<Material, Long> storedItems
    ) {
        private ChunkSnapshot {
            placedSpawners = immutableEntityMap(placedSpawners);
            storedSpawners = immutableEntityMap(storedSpawners);
            placedBlocks = immutableMaterialMap(placedBlocks);
            storedItems = immutableMaterialMap(storedItems);
        }

        private ChunkBreakdown breakdown(FactionLandValueService service) {
            double spawners = service.priceSpawners(placedSpawners) + service.priceSpawners(storedSpawners);
            return new ChunkBreakdown(
                    spawners,
                    service.priceMaterials(placedBlocks),
                    service.priceMaterials(storedItems),
                    placedSpawners,
                    storedSpawners,
                    placedBlocks,
                    storedItems
            );
        }

        private static Map<EntityType, Integer> immutableEntityMap(Map<EntityType, Integer> source) {
            EnumMap<EntityType, Integer> copy = new EnumMap<>(EntityType.class);
            if (source != null) copy.putAll(source);
            return Collections.unmodifiableMap(copy);
        }

        private static Map<Material, Long> immutableMaterialMap(Map<Material, Long> source) {
            EnumMap<Material, Long> copy = new EnumMap<>(Material.class);
            if (source != null) copy.putAll(source);
            return Collections.unmodifiableMap(copy);
        }
    }

    private record Claim(UUID world, int x, int z, String key, UUID factionId) { }
}
