package com.mira.factions.gui;

import com.mira.factions.MiraFactionsPlugin;
import com.mira.factions.model.Faction;
import com.mira.factions.service.FactionLandValueService;
import com.mira.factions.service.FactionSeasonService;
import com.mira.factions.service.FactionService;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.*;

public final class FTopPodiumService implements Listener {
    private record Holder() implements InventoryHolder { @Override public Inventory getInventory() { return null; } }
    private final MiraFactionsPlugin plugin;
    private final FactionService factions;
    private final FactionLandValueService landValue;
    private final FactionSeasonService seasons;

    public FTopPodiumService(MiraFactionsPlugin plugin, FactionService factions, FactionLandValueService landValue, FactionSeasonService seasons) {
        this.plugin = plugin;
        this.factions = factions;
        this.landValue = landValue;
        this.seasons = seasons;
    }

    public void open(Player player) {
        Inventory inv = Bukkit.createInventory(new Holder(), 27, plugin.component("&5FTop Podium"));
        List<Faction> ranked = new ArrayList<>(factions.all());
        ranked.sort(Comparator.comparingDouble(this::wealth).reversed().thenComparing(Faction::name, String.CASE_INSENSITIVE_ORDER));
        int[] slots = {13, 11, 15, 20, 24};
        Material[] icons = {Material.NETHER_STAR, Material.DIAMOND_BLOCK, Material.GOLD_BLOCK, Material.IRON_BLOCK, Material.EMERALD_BLOCK};
        for (int i = 0; i < Math.min(5, ranked.size()); i++) {
            Faction faction = ranked.get(i);
            double land = landValue.value(faction);
            double total = land + faction.bankBalance();
            FactionSeasonService.Stats stats = seasons.stats(faction);
            List<String> lore = List.of(
                    "&7Total Wealth: &a" + money(total),
                    "&7Spawner Land: &f" + money(land),
                    "&7Faction Bank: &f" + money(faction.bankBalance()),
                    "&7Power: &f" + String.format(Locale.US, "%.1f", factions.factionPower(faction)),
                    "&7Members: &f" + faction.members().size(),
                    "",
                    "&7Season Peak: &6" + money(stats.peakWealth()),
                    "&7Best FTop Rank: &f#" + (stats.bestFtopRank() <= 0 ? "-" : stats.bestFtopRank()),
                    "&7Raids W/L: &a" + stats.raidsWon() + "&7/&c" + stats.raidsLost());
            inv.setItem(slots[i], item(icons[i], "&d#" + (i + 1) + " &f" + faction.name(), lore));
        }
        inv.setItem(4, item(Material.CLOCK, "&dSeason: &f" + seasons.currentSeason(), List.of("&7Highest recorded wealth: &6" + money(seasons.highestValueEver()), "&7Record holder: &f" + seasons.highestValueFaction())));
        player.openInventory(inv);
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (event.getInventory().getHolder() instanceof Holder) event.setCancelled(true);
    }

    private double wealth(Faction faction) { return landValue.value(faction) + faction.bankBalance(); }
    private String money(double value) { return String.format(Locale.US, "$%,.2f", value); }
    private ItemStack item(Material material, String name, List<String> lore) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(plugin.component(name));
        meta.lore(lore.stream().map(plugin::component).toList());
        item.setItemMeta(meta);
        return item;
    }
}
