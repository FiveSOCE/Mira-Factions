package com.mira.factions.gui;

import com.mira.factions.MiraFactionsPlugin;
import com.mira.factions.model.Faction;
import com.mira.factions.service.FactionLandValueService;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.List;
import java.util.Locale;
import java.util.UUID;

public final class FactionTopBreakdownGui implements Listener {
    private final MiraFactionsPlugin plugin;
    private record ViewHolder(UUID factionId) implements InventoryHolder {
        @Override public Inventory getInventory() { return null; }
    }
    public FactionTopBreakdownGui(MiraFactionsPlugin plugin) { this.plugin = plugin; }

    public void open(Player player, Faction faction) {
        if (faction == null) { plugin.msg(player, "&cThat faction no longer exists."); return; }
        FactionLandValueService.Breakdown data = plugin.landValue().breakdown(faction);
        Inventory menu = Bukkit.createInventory(new ViewHolder(faction.id()), 27,
                plugin.component("&6" + faction.name() + " - Value"));
        double total = data.totalValue() + faction.bankBalance();
        menu.setItem(10, icon(Material.SPAWNER, "&dSpawners",
                "&7Placed: &f" + data.totalPlacedSpawners(),
                "&7Stored: &f" + data.totalStoredSpawners(),
                "&7Value: &e" + money(data.spawnerValue())));
        menu.setItem(11, icon(Material.DIAMOND_BLOCK, "&bPlaced Blocks",
                "&7Tracked types: &f" + data.placedBlockCounts().size(),
                "&7Value: &e" + money(data.blockValue())));
        menu.setItem(12, icon(Material.CHEST, "&6Chest / Container Items",
                "&7Tracked types: &f" + data.storedItemCounts().size(),
                "&7Value: &e" + money(data.containerValue())));
        menu.setItem(14, icon(Material.GOLD_BLOCK, "&6Faction Bank",
                "&7Balance: &e" + money(faction.bankBalance())));
        menu.setItem(16, icon(Material.NETHER_STAR, "&eTotal Wealth",
                "&7Combined: &e" + money(total),
                "&7Claim value + faction bank"));
        player.openInventory(menu);
    }

    private String money(double value) { return String.format(Locale.US, "$%,.2f", value); }

    private ItemStack icon(Material material, String title, String... lines) {
        ItemStack stack = new ItemStack(material);
        ItemMeta meta = stack.getItemMeta();
        meta.displayName(plugin.component(title));
        meta.lore(java.util.Arrays.stream(lines).map(plugin::component).toList());
        stack.setItemMeta(meta);
        return stack;
    }

    @EventHandler
    public void click(InventoryClickEvent event) {
        if (event.getView().getTopInventory().getHolder() instanceof ViewHolder)
            event.setCancelled(true);
    }

    @EventHandler
    public void drag(InventoryDragEvent event) {
        if (event.getView().getTopInventory().getHolder() instanceof ViewHolder)
            event.setCancelled(true);
    }
}
