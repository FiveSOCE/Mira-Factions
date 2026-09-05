package com.mira.factions.gui;

import com.mira.factions.MiraFactionsPlugin;
import com.mira.factions.model.*;
import com.mira.factions.service.FactionService;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.*;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.*;

public final class FactionGuiService {
    private static final int[] UPGRADE_SLOTS = {
            10,11,12,13,14,15,16,
            19,20,21,22,23,24,25,
            28,29,30,31,32
    };
    private static final int[] PERMISSION_SLOTS = {
            10,11,12,13,14,15,16,
            19,20,21,22,23,24,25,
            28,29,30,31,32,33,34,
            37,38,39,40,41,42,43
    };

    public enum Menu { UPGRADES, VAULT, PERMISSIONS, PERMISSION_RANK, SHIELD }

    public record Holder(Menu menu, UUID factionId, String context) implements InventoryHolder {
        public Holder(Menu menu, UUID factionId) { this(menu, factionId, ""); }
        @Override public Inventory getInventory() { return null; }
    }

    private final MiraFactionsPlugin plugin;
    private final FactionService service;

    public FactionGuiService(MiraFactionsPlugin plugin, FactionService service) {
        this.plugin = plugin;
        this.service = service;
    }

    public void openUpgrades(Player player) {
        Faction faction = service.of(player.getUniqueId());
        if (faction == null) { plugin.msg(player, "&cYou are not in a faction."); return; }

        Inventory inventory = Bukkit.createInventory(new Holder(Menu.UPGRADES, faction.id()), 54,
                plugin.component("&5Faction Upgrades"));
        fill(inventory);

        UpgradeType[] types = UpgradeType.values();
        for (int i = 0; i < Math.min(UPGRADE_SLOTS.length, types.length); i++) {
            UpgradeType type = types[i];
            int level = faction.upgrade(type);
            boolean max = level >= type.maxLevel();
            List<String> lore = new ArrayList<>();
            lore.add("&7Category: &f" + category(type));
            lore.add("&7Level: &f" + level + "/" + type.maxLevel());
            lore.add("&7Current: &f" + effect(type, level));
            if (!max) lore.add("&7Next: &f" + effect(type, level + 1));
            lore.add("&7Faction Bank: &f" + plugin.economy().format(faction.bankBalance()));
            if (max) {
                lore.add("");
                lore.add("&aMAX LEVEL");
            } else {
                lore.add("&7Next Cost: &6" + plugin.economy().format(service.upgradeCost(faction, type)));
                lore.add("");
                lore.add("&aClick to purchase next level");
            }
            inventory.setItem(UPGRADE_SLOTS[i], item(type.icon(), "&d" + type.display(), lore));
        }

        inventory.setItem(49, item(Material.EMERALD, "&aFaction Bank",
                List.of("&7Balance: &f" + plugin.economy().format(faction.bankBalance()),
                        "&7Use &f/f money &7to deposit or withdraw")));
        player.openInventory(inventory);
    }

    public void openPermissions(Player player) {
        Faction faction = service.of(player.getUniqueId());
        if (faction == null) { plugin.msg(player, "&cYou are not in a faction."); return; }
        if (faction.rank(player.getUniqueId()) != FactionRank.LEADER) {
            plugin.msg(player, "&cOnly the faction leader can edit faction permissions.");
            return;
        }

        Inventory inventory = Bukkit.createInventory(new Holder(Menu.PERMISSIONS, faction.id()), 27,
                plugin.component("&5Faction Permissions"));
        fill(inventory);
        inventory.setItem(10, rankItem(faction, FactionRank.RECRUIT, Material.LEATHER_HELMET));
        inventory.setItem(11, rankItem(faction, FactionRank.MEMBER, Material.IRON_HELMET));
        inventory.setItem(15, rankItem(faction, FactionRank.OFFICER, Material.DIAMOND_HELMET));
        inventory.setItem(16, rankItem(faction, FactionRank.COLEADER, Material.NETHERITE_HELMET));
        inventory.setItem(22, item(Material.REDSTONE, "&cReset Permissions",
                List.of("&7Restore every permission to", "&7the MiraFactions default rank.", "", "&eClick to reset.")));
        player.openInventory(inventory);
    }

    public void openPermissionRank(Player player, FactionRank rank) {
        Faction faction = service.of(player.getUniqueId());
        if (faction == null || faction.rank(player.getUniqueId()) != FactionRank.LEADER) {
            player.closeInventory();
            return;
        }
        if (rank == FactionRank.LEADER) rank = FactionRank.COLEADER;

        Inventory inventory = Bukkit.createInventory(new Holder(Menu.PERMISSION_RANK, faction.id(), rank.name()), 54,
                plugin.component("&5Permissions: " + pretty(rank)));
        fill(inventory);

        FactionPermission[] permissions = FactionPermission.values();
        for (int i = 0; i < Math.min(PERMISSION_SLOTS.length, permissions.length); i++) {
            FactionPermission permission = permissions[i];
            boolean allowed = service.permissionAllowed(faction, rank, permission);
            FactionRank minimum = faction.minimum(permission);
            inventory.setItem(PERMISSION_SLOTS[i], item(
                    allowed ? Material.LIME_DYE : Material.GRAY_DYE,
                    (allowed ? "&a" : "&7") + pretty(permission),
                    List.of("&7This rank: " + (allowed ? "&aALLOWED" : "&cDENIED"),
                            "&7Minimum rank: &f" + pretty(minimum),
                            "",
                            allowed
                                    ? "&eClick to deny this rank and lower."
                                    : "&aClick to allow this rank and higher.")));
        }

        inventory.setItem(49, item(Material.ARROW, "&eBack", List.of("&7Return to rank selection.")));
        player.openInventory(inventory);
    }

    public void openShield(Player player) {
        Faction faction = service.of(player.getUniqueId());
        if (faction == null) { plugin.msg(player, "&cYou are not in a faction."); return; }
        if (!service.hasPermission(player, FactionPermission.SHIELD)) {
            plugin.msg(player, "&cYou do not have faction permission to manage the shield.");
            return;
        }

        Inventory inventory = Bukkit.createInventory(new Holder(Menu.SHIELD, faction.id()), 27,
                plugin.component("&5Faction Shield"));
        fill(inventory);

        long now = System.currentTimeMillis();
        boolean active = faction.shieldUntil() > now;
        long activeMs = Math.max(0L, faction.shieldUntil() - now);
        long cooldownMs = Math.max(0L, faction.shieldCooldownUntil() - now);
        int level = faction.upgrade(UpgradeType.SHIELD);
        long minutesPerLevel = Math.max(1L, plugin.getConfig().getLong("shield.duration-minutes-per-level", 30L));

        inventory.setItem(11, item(active ? Material.SHIELD : Material.GRAY_DYE,
                active ? "&bShield ACTIVE" : "&7Shield Inactive",
                List.of(active ? "&7Remaining: &f" + formatDuration(activeMs)
                                : cooldownMs > 0L ? "&7Available in: &f" + formatDuration(cooldownMs)
                                : "&7Ready to activate.")));

        inventory.setItem(13, item(Material.NETHER_STAR, "&dShield Upgrade",
                List.of("&7Level: &f" + level + "/" + UpgradeType.SHIELD.maxLevel(),
                        "&7Activation duration: &f" + (level * minutesPerLevel) + " minutes",
                        "&7Raidable factions cannot activate a shield.")));

        List<String> activateLore = new ArrayList<>();
        if (level <= 0) activateLore.add("&cPurchase the Shield upgrade first.");
        else if (active) activateLore.add("&cShield is already active.");
        else if (cooldownMs > 0L) activateLore.add("&cShield is still on cooldown.");
        else {
            activateLore.add("&7Activate the faction shield for");
            activateLore.add("&f" + (level * minutesPerLevel) + " minutes&7.");
            activateLore.add("");
            activateLore.add("&aClick to activate.");
        }
        inventory.setItem(15, item(Material.LIME_CONCRETE, "&aActivate Shield", activateLore));
        player.openInventory(inventory);
    }

    public void openVault(Player player) {
        Faction faction = service.of(player.getUniqueId());
        if (faction == null) { plugin.msg(player, "&cYou are not in a faction."); return; }
        if (!service.hasPermission(player, FactionPermission.VAULT)) {
            plugin.msg(player, "&cYou do not have faction permission to use the vault.");
            return;
        }
        Inventory inventory = Bukkit.createInventory(new Holder(Menu.VAULT, faction.id()), 54,
                plugin.component("&5" + faction.name() + " Vault"));
        ItemStack[] saved = service.vaultContents(faction);
        int slots = service.vaultSlots(faction);
        for (int i = 0; i < 54; i++) {
            if (i < slots) inventory.setItem(i, saved[i]);
            else inventory.setItem(i, lockedSlot());
        }
        player.openInventory(inventory);
    }

    public void saveVault(Inventory inventory, Faction faction) {
        if (faction == null || !(inventory.getHolder() instanceof Holder holder)
                || holder.menu() != Menu.VAULT) return;
        int allowed = service.vaultSlots(faction);
        ItemStack[] contents = inventory.getContents().clone();
        for (int i = allowed; i < 54; i++) contents[i] = null;
        service.saveVault(faction, contents);
    }

    public UpgradeType upgradeForSlot(int slot) {
        UpgradeType[] types = UpgradeType.values();
        for (int i = 0; i < Math.min(UPGRADE_SLOTS.length, types.length); i++) {
            if (UPGRADE_SLOTS[i] == slot) return types[i];
        }
        return null;
    }

    public FactionPermission permissionForSlot(int slot) {
        FactionPermission[] permissions = FactionPermission.values();
        for (int i = 0; i < Math.min(PERMISSION_SLOTS.length, permissions.length); i++) {
            if (PERMISSION_SLOTS[i] == slot) return permissions[i];
        }
        return null;
    }

    public FactionRank rankForSelectorSlot(int slot) {
        return switch (slot) {
            case 10 -> FactionRank.RECRUIT;
            case 11 -> FactionRank.MEMBER;
            case 15 -> FactionRank.OFFICER;
            case 16 -> FactionRank.COLEADER;
            default -> null;
        };
    }

    public FactionRank rankFromContext(String context) {
        try { return FactionRank.valueOf(context); }
        catch (RuntimeException ignored) { return null; }
    }

    public FactionRank nextHigher(FactionRank rank) {
        return switch (rank) {
            case RECRUIT -> FactionRank.MEMBER;
            case MEMBER -> FactionRank.OFFICER;
            case OFFICER -> FactionRank.COLEADER;
            case COLEADER, LEADER -> FactionRank.LEADER;
        };
    }

    private ItemStack rankItem(Faction faction, FactionRank rank, Material material) {
        long allowed = Arrays.stream(FactionPermission.values())
                .filter(permission -> service.permissionAllowed(faction, rank, permission)).count();
        return item(material, "&d" + pretty(rank),
                List.of("&7Allowed permissions: &f" + allowed + "/" + FactionPermission.values().length,
                        "", "&aClick to edit this rank."));
    }

    private String category(UpgradeType type) {
        return switch (type) {
            case POWER, MEMBER_LIMIT, POWER_REGEN, POWER_LOSS -> "Faction";
            case WARP_LIMIT, ZONE_LIMIT, FLIGHT, SHIELD, DAMAGE, DEFENSE, HOME_WARMUP, WARP_WARMUP -> "Territory";
            case VAULT_SIZE, TNT_CAPACITY -> "Economy";
            case MOB_DROPS, MOB_XP, CROP_YIELD, CROP_GROWTH, SPAWNER_RATE -> "Production";
        };
    }

    private String effect(UpgradeType type, int level) {
        return switch (type) {
            case POWER -> "+" + (level * plugin.getConfig().getDouble("upgrades.power.per-level", 10D)) + " faction power";
            case MEMBER_LIMIT -> "+" + (level * plugin.getConfig().getInt("upgrades.member-limit.per-level", 5)) + " members";
            case WARP_LIMIT -> "+" + (level * plugin.getConfig().getInt("upgrades.warp-limit.per-level", 1)) + " warps";
            case VAULT_SIZE -> "+" + (level * plugin.getConfig().getInt("upgrades.vault-size.per-level", 9)) + " vault slots";
            case TNT_CAPACITY -> "+" + (level * plugin.getConfig().getInt("upgrades.tnt-capacity.per-level", 1728)) + " TNT capacity";
            case ZONE_LIMIT -> "+" + (level * plugin.getConfig().getInt("upgrades.zone-limit.per-level", 1)) + " zones";
            case SHIELD -> (level * plugin.getConfig().getLong("shield.duration-minutes-per-level", 30L)) + "m shield";
            case HOME_WARMUP -> "-" + (level * plugin.getConfig().getInt("upgrades.home-warmup.seconds-reduced-per-level", 1)) + "s home warmup";
            case WARP_WARMUP -> "-" + (level * plugin.getConfig().getInt("upgrades.warp-warmup.seconds-reduced-per-level", 1)) + "s warp warmup";
            case FLIGHT -> level > 0 ? "Faction flight unlocked" : "Faction flight locked";
            case DAMAGE -> "+" + percent(level * plugin.getConfig().getDouble("upgrades.damage.bonus-per-level", .05)) + " territory damage";
            case DEFENSE -> "-" + percent(level * plugin.getConfig().getDouble("upgrades.defense.reduction-per-level", .05)) + " territory damage taken";
            case POWER_REGEN -> "+" + percent(level * plugin.getConfig().getDouble("upgrades.power-regen.bonus-per-level", .20)) + " power regen";
            case POWER_LOSS -> "-" + percent(level * plugin.getConfig().getDouble("upgrades.power-loss.reduction-per-level", .10)) + " death power loss";
            case MOB_DROPS -> "+" + percent(level * plugin.getConfig().getDouble("upgrades.mob-drops.extra-roll-chance-per-level", .10)) + " mob drop chance";
            case MOB_XP -> "+" + percent(level * plugin.getConfig().getDouble("upgrades.mob-xp.bonus-per-level", .10)) + " mob XP";
            case CROP_YIELD -> "+" + percent(level * plugin.getConfig().getDouble("upgrades.crop-yield.extra-roll-chance-per-level", .10)) + " crop yield chance";
            case CROP_GROWTH -> "+" + percent(level * plugin.getConfig().getDouble("upgrades.crop-growth.extra-stage-chance-per-level", .10)) + " crop growth chance";
            case SPAWNER_RATE -> "+" + percent(level * plugin.getConfig().getDouble("upgrades.spawner-rate.speed-bonus-per-level", .10)) + " spawner rate";
        };
    }

    private String percent(double decimal) {
        return String.format(Locale.US, "%.0f%%", decimal * 100D);
    }

    private void fill(Inventory inventory) {
        ItemStack filler = item(Material.GRAY_STAINED_GLASS_PANE, " ", List.of());
        for (int i = 0; i < inventory.getSize(); i++) inventory.setItem(i, filler.clone());
    }

    private ItemStack lockedSlot() {
        ItemStack item = item(Material.BARRIER, "&cLocked Vault Slot",
                List.of("&7Purchase the Vault upgrade", "&7with &f/f upgrades&7."));
        item.editMeta(meta -> meta.setEnchantmentGlintOverride(true));
        return item;
    }

    private ItemStack item(Material material, String name, List<String> lore) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(plugin.component(name));
        meta.lore(lore.stream().map(plugin::component).toList());
        item.setItemMeta(meta);
        return item;
    }

    private static String pretty(Enum<?> value) {
        String[] words = value.name().toLowerCase(Locale.ROOT).split("_");
        StringBuilder out = new StringBuilder();
        for (String word : words) {
            if (!out.isEmpty()) out.append(' ');
            out.append(Character.toUpperCase(word.charAt(0))).append(word.substring(1));
        }
        return out.toString();
    }

    private static String formatDuration(long millis) {
        long seconds = Math.max(0L, millis / 1000L);
        long hours = seconds / 3600L;
        long minutes = (seconds % 3600L) / 60L;
        long secs = seconds % 60L;
        if (hours > 0) return hours + "h " + minutes + "m";
        if (minutes > 0) return minutes + "m " + secs + "s";
        return secs + "s";
    }
}
