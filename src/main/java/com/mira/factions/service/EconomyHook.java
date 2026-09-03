package com.mira.factions.service;

import net.milkbowl.vault.economy.Economy;
import org.bukkit.OfflinePlayer;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.java.JavaPlugin;

public final class EconomyHook {
    private final Economy economy;

    public EconomyHook(JavaPlugin plugin) {
        RegisteredServiceProvider<Economy> registration = plugin.getServer().getServicesManager().getRegistration(Economy.class);
        economy = registration == null ? null : registration.getProvider();
    }

    public boolean available() { return economy != null; }
    public double balance(OfflinePlayer player) { return economy == null ? 0.0 : economy.getBalance(player); }
    public boolean withdraw(OfflinePlayer player, double amount) {
        return economy != null && amount >= 0.0 && economy.withdrawPlayer(player, amount).transactionSuccess();
    }
    public boolean deposit(OfflinePlayer player, double amount) {
        return economy != null && amount >= 0.0 && economy.depositPlayer(player, amount).transactionSuccess();
    }
    public String format(double amount) { return economy == null ? String.format("$%,.2f", amount) : economy.format(amount); }
}
