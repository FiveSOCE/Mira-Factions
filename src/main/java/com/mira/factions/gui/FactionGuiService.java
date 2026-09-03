package com.mira.factions.gui;

import com.mira.factions.MiraFactionsPlugin;
import com.mira.factions.model.Faction;
import com.mira.factions.service.FactionService;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.*;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.*;

public final class FactionGuiService {
    public record Holder() implements InventoryHolder {
        @Override public Inventory getInventory() { return null; }
    }

    private final MiraFactionsPlugin plugin;
    private final FactionService service;

    public FactionGuiService(MiraFactionsPlugin plugin, FactionService service) {
        this.plugin = plugin;
        this.service = service;
    }

    public void open(Player player) {
        Inventory inventory = Bukkit.createInventory(new Holder(), 27, plugin.component("&5Mira Factions"));
        ItemStack filler = item(Material.GRAY_STAINED_GLASS_PANE, " ", List.of());
        filler.editMeta(meta -> meta.setEnchantmentGlintOverride(true));
        for (int slot = 0; slot < inventory.getSize(); slot++) inventory.setItem(slot, filler.clone());

        Faction faction = service.of(player.getUniqueId());
        if (faction == null) {
            inventory.setItem(11, item(Material.EMERALD, "&aCreate Faction", List.of("&7Click, then type the faction name in chat")));
            inventory.setItem(15, item(Material.PAPER, "&eJoin Faction", List.of("&7Use &f/f join <name>", "&7after receiving an invite")));
        } else {
            inventory.setItem(4, item(Material.PLAYER_HEAD, "&d" + faction.name(), List.of(
                    "&7Rank: &f" + faction.rank(player.getUniqueId()),
                    "&7Faction Power: &f" + String.format(Locale.US, "%.1f", service.factionPower(faction)),
                    "&7Your Power: &f" + String.format(Locale.US, "%.1f", service.power(player.getUniqueId())),
                    "&7Claims: &f" + faction.claims().size() + "/" + service.maxClaims(faction))));
            inventory.setItem(10, item(Material.GOLDEN_SHOVEL, "&aClaim Current Chunk", List.of("&7Officer+")));
            inventory.setItem(11, item(Material.FLINT, "&cUnclaim Current Chunk", List.of("&7Officer+")));
            inventory.setItem(12, item(Material.ENDER_PEARL, "&bFaction Home", List.of("&7Teleport to your faction home")));
            inventory.setItem(13, item(Material.RECOVERY_COMPASS, "&bSet Faction Home", List.of("&7Officer+", "&7Must be inside faction land")));
            inventory.setItem(14, item(Material.WRITABLE_BOOK,
                    service.factionChat(player.getUniqueId()) ? "&aFaction Chat: ON" : "&7Faction Chat: OFF",
                    List.of("&7Click to toggle")));
            inventory.setItem(15, item(Material.IRON_CHESTPLATE, "&dMembers", memberLore(faction)));
            inventory.setItem(16, item(Material.DIAMOND_SWORD, "&cDiplomacy", List.of(
                    "&7/f ally <faction>",
                    "&7/f enemy <faction>",
                    "&7/f neutral <faction>")));
            inventory.setItem(22, item(Material.OAK_DOOR, "&cLeave Faction", List.of("&7Leader with members must transfer or disband")));
        }
        player.openInventory(inventory);
    }

    private List<String> memberLore(Faction faction) {
        List<String> lore = new ArrayList<>();
        faction.members().entrySet().stream()
                .sorted((a, b) -> Integer.compare(b.getValue().weight(), a.getValue().weight()))
                .limit(8)
                .forEach(entry -> {
                    String name = Optional.ofNullable(Bukkit.getOfflinePlayer(entry.getKey()).getName())
                            .orElse(entry.getKey().toString().substring(0, 8));
                    lore.add("&7" + name + " &8- &f" + entry.getValue());
                });
        if (faction.members().size() > 8) lore.add("&8...and " + (faction.members().size() - 8) + " more");
        return lore;
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
