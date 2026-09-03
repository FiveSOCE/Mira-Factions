package com.mira.factions.gui;

import com.mira.factions.MiraFactionsPlugin;
import com.mira.factions.model.Faction;
import com.mira.factions.model.UpgradeType;
import com.mira.factions.service.FactionService;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.*;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.*;

public final class FactionGuiService {
    public enum Menu { UPGRADES, VAULT }
    public record Holder(Menu menu, UUID factionId) implements InventoryHolder {
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
        Inventory inventory = Bukkit.createInventory(new Holder(Menu.UPGRADES, faction.id()), 54, plugin.component("&5Faction Upgrades"));
        ItemStack filler = item(Material.GRAY_STAINED_GLASS_PANE, " ", List.of());
        filler.editMeta(meta -> meta.setEnchantmentGlintOverride(true));
        for (int i = 0; i < inventory.getSize(); i++) inventory.setItem(i, filler.clone());

        int[] slots = {10,11,12,13,14,15,16,19,20,21,22,23,24,25,28,29,30};
        UpgradeType[] types = UpgradeType.values();
        for (int i = 0; i < Math.min(slots.length, types.length); i++) {
            UpgradeType type = types[i];
            int level = faction.upgrade(type);
            boolean max = level >= type.maxLevel();
            List<String> lore = new ArrayList<>();
            lore.add("&7Level: &f" + level + "/" + type.maxLevel());
            lore.add("&7Faction Bank: &f" + plugin.economy().format(faction.bankBalance()));
            if (max) lore.add("&aMAX LEVEL");
            else {
                lore.add("&7Next Cost: &6" + plugin.economy().format(service.upgradeCost(faction, type)));
                lore.add("");
                lore.add("&aClick to purchase next level");
            }
            inventory.setItem(slots[i], item(type.icon(), "&d" + type.display(), lore));
        }
        inventory.setItem(49, item(Material.EMERALD, "&aFaction Bank", List.of("&7Balance: &f" + plugin.economy().format(faction.bankBalance()), "&7Use &f/f money &7to deposit or withdraw")));
        player.openInventory(inventory);
    }

    public void openVault(Player player) {
        Faction faction = service.of(player.getUniqueId());
        if (faction == null) { plugin.msg(player, "&cYou are not in a faction."); return; }
        if (!service.hasPermission(player, com.mira.factions.model.FactionPermission.VAULT)) { plugin.msg(player, "&cYou do not have faction permission to use the vault."); return; }
        Inventory inventory = Bukkit.createInventory(new Holder(Menu.VAULT, faction.id()), 54, plugin.component("&5" + faction.name() + " Vault"));
        ItemStack[] saved = service.vaultContents(faction);
        int slots = service.vaultSlots(faction);
        for (int i = 0; i < 54; i++) {
            if (i < slots) inventory.setItem(i, saved[i]);
            else inventory.setItem(i, lockedSlot());
        }
        player.openInventory(inventory);
    }

    public void saveVault(Inventory inventory, Faction faction) {
        if (faction == null || !(inventory.getHolder() instanceof Holder holder) || holder.menu() != Menu.VAULT) return;
        int allowed = service.vaultSlots(faction);
        ItemStack[] contents = inventory.getContents().clone();
        for (int i = allowed; i < 54; i++) contents[i] = null;
        service.saveVault(faction, contents);
    }

    public UpgradeType upgradeForSlot(int slot) {
        int[] slots = {10,11,12,13,14,15,16,19,20,21,22,23,24,25,28,29,30};
        UpgradeType[] types = UpgradeType.values();
        for (int i = 0; i < Math.min(slots.length, types.length); i++) if (slots[i] == slot) return types[i];
        return null;
    }

    private ItemStack lockedSlot() {
        ItemStack item = item(Material.BARRIER, "&cLocked Vault Slot", List.of("&7Purchase the Vault upgrade", "&7with &f/f upgrades&7."));
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
}
