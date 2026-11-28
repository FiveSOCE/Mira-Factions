package com.mira.factions.gui;

import com.mira.factions.MiraFactionsPlugin;
import com.mira.factions.model.Faction;
import com.mira.factions.service.FactionService;
import io.papermc.paper.event.player.AsyncChatEvent;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.concurrent.ConcurrentHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

public final class FactionBankGui implements Listener {
    private final MiraFactionsPlugin plugin;
    private final FactionService service;
    private final Map<UUID, Pending> pending = new ConcurrentHashMap<>();

    private enum Transaction { MONEY_WITHDRAW, MONEY_DEPOSIT, XP_WITHDRAW, XP_DEPOSIT }
    private record Pending(UUID factionId, Transaction transaction) { }
    private record BankHolder(UUID factionId) implements InventoryHolder {
        @Override public Inventory getInventory() { return null; }
    }

    public FactionBankGui(MiraFactionsPlugin plugin, FactionService service) {
        this.plugin = plugin;
        this.service = service;
    }

    public boolean awaitingInput(UUID playerId) { return pending.containsKey(playerId); }

    public void open(Player player) {
        Faction faction = service.of(player.getUniqueId());
        if (faction == null) { plugin.msg(player, "&cYou are not in a faction."); return; }
        Inventory inventory = Bukkit.createInventory(new BankHolder(faction.id()), 27, plugin.component("&6Faction Bank"));
        // Display slots 10-18 (Bukkit's zero-based slots 9-17).
        inventory.setItem(9, icon(Material.IRON_NUGGET, "&fWithdraw", "&7Withdraw money"));
        inventory.setItem(10, icon(Material.GOLD_BLOCK, "&6Faction Money",
                "&7Balance: &e" + plugin.economy().format(faction.bankBalance())));
        inventory.setItem(11, icon(Material.GOLD_NUGGET, "&6Deposit", "&7Deposit money"));
        inventory.setItem(13, icon(Material.NETHER_STAR, "&dFaction Bank", "&7Money & XP level storage"));
        inventory.setItem(15, icon(Material.IRON_NUGGET, "&fWithdraw", "&7Withdraw XP levels"));
        inventory.setItem(16, icon(Material.EXPERIENCE_BOTTLE, "&aFaction XP",
                "&7Balance: &f" + faction.xpBankLevels() + " levels"));
        inventory.setItem(17, icon(Material.GOLD_NUGGET, "&6Deposit", "&7Deposit XP levels"));
        player.openInventory(inventory);
    }

    private ItemStack icon(Material material, String name, String lore) {
        ItemStack stack = new ItemStack(material);
        ItemMeta meta = stack.getItemMeta();
        meta.displayName(plugin.component(name));
        meta.lore(List.of(plugin.component(lore)));
        stack.setItemMeta(meta);
        return stack;
    }

    @EventHandler
    public void click(InventoryClickEvent event) {
        if (!(event.getView().getTopInventory().getHolder() instanceof BankHolder holder)) return;
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player)) return;
        if (service.of(player.getUniqueId()) == null ||
                !service.of(player.getUniqueId()).id().equals(holder.factionId())) {
            player.closeInventory();
            return;
        }
        Transaction operation = switch (event.getRawSlot()) {
            case 9 -> Transaction.MONEY_WITHDRAW;
            case 11 -> Transaction.MONEY_DEPOSIT;
            case 15 -> Transaction.XP_WITHDRAW;
            case 17 -> Transaction.XP_DEPOSIT;
            default -> null;
        };
        if (operation == null) return;
        player.closeInventory();
        pending.put(player.getUniqueId(), new Pending(holder.factionId(), operation));
        plugin.msg(player, "&eEnter how much to " +
                (operation == Transaction.MONEY_DEPOSIT || operation == Transaction.XP_DEPOSIT ? "deposit" : "withdraw") +
                (operation == Transaction.XP_DEPOSIT || operation == Transaction.XP_WITHDRAW ? " (XP levels)" : " (money)") +
                " &ein chat, or type &fcancel&e. Your message will not appear publicly.");
    }

    @EventHandler
    public void drag(InventoryDragEvent event) {
        if (event.getView().getTopInventory().getHolder() instanceof BankHolder) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void chat(AsyncChatEvent event) {
        UUID id = event.getPlayer().getUniqueId();
        Pending check = pending.get(id);
        if (check == null) return;
        // Never expose the response to global/faction chat.
        event.setCancelled(true);
        String input = net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer
                .plainText().serialize(event.message()).trim();
        Bukkit.getScheduler().runTask(plugin, () -> {
            Pending request = pending.remove(id);
            if (request == null) return;
            Player player = Bukkit.getPlayer(id);
            if (player == null) return;
            Faction faction = service.of(id);
            if (faction == null || !faction.id().equals(request.factionId())) {
                plugin.msg(player, "&cYour faction changed. Transaction cancelled.");
                return;
            }
            if (input.equalsIgnoreCase("cancel")) {
                plugin.msg(player, "&7Bank transaction cancelled.");
                open(player);
                return;
            }
            FactionService.Result result;
            try {
                if (request.transaction() == Transaction.XP_DEPOSIT || request.transaction() == Transaction.XP_WITHDRAW) {
                    int levels = Integer.parseInt(input);
                    result = request.transaction() == Transaction.XP_DEPOSIT
                            ? service.depositXp(player, levels) : service.withdrawXp(player, levels);
                } else {
                    double amount = Double.parseDouble(input);
                    if (!Double.isFinite(amount) || amount <= 0) throw new NumberFormatException();
                    result = request.transaction() == Transaction.MONEY_DEPOSIT
                            ? service.deposit(player, amount) : service.withdraw(player, amount);
                }
            } catch (NumberFormatException ex) {
                plugin.msg(player, "&cInvalid amount. Please enter a positive " +
                        (request.transaction() == Transaction.XP_DEPOSIT || request.transaction() == Transaction.XP_WITHDRAW
                                ? "whole number of levels." : "money amount."));
                open(player);
                return;
            }
            plugin.msg(player, (result.success() ? "&a" : "&c") + result.message());
            open(player);
        });
    }

    @EventHandler
    public void quit(PlayerQuitEvent event) {
        pending.remove(event.getPlayer().getUniqueId());
    }
}
