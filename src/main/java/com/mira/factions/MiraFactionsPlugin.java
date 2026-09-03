package com.mira.factions;

import com.mira.factions.api.*;
import com.mira.factions.command.*;
import com.mira.factions.gui.FactionGuiService;
import com.mira.factions.hook.MiraFactionsPlaceholderExpansion;
import com.mira.factions.listener.FactionAdminAuditListener;
import com.mira.factions.listener.FactionHistoryListener;
import com.mira.factions.listener.FactionListener;
import com.mira.factions.listener.FactionOperatorListener;
import com.mira.factions.listener.FactionUpgradeAuditListener;
import com.mira.factions.listener.FactionUpgradeListener;
import com.mira.factions.service.*;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.command.PluginCommand;
import org.bukkit.entity.Player;
import org.bukkit.plugin.ServicePriority;
import org.bukkit.plugin.java.JavaPlugin;

public final class MiraFactionsPlugin extends JavaPlugin {
    private static final LegacyComponentSerializer AMPERSAND = LegacyComponentSerializer.legacyAmpersand();
    private FactionService factions;
    private EconomyHook economy;
    private FactionHistoryService history;
    private FactionLandValueService landValue;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        economy = new EconomyHook(this);
        factions = new FactionService(this, economy);
        history = new FactionHistoryService(this);
        landValue = new FactionLandValueService(this);
        history.initializeBanks(factions.all());
        FactionGuiService gui = new FactionGuiService(this, factions);

        FactionHistoryListener historyListener = new FactionHistoryListener(this, factions, history, landValue);
        getServer().getPluginManager().registerEvents(historyListener, this);
        getServer().getPluginManager().registerEvents(new FactionAdminAuditListener(this, factions, history), this);
        getServer().getPluginManager().registerEvents(new FactionListener(this, factions, gui), this);
        getServer().getPluginManager().registerEvents(new FactionUpgradeAuditListener(this, factions, history), this);
        getServer().getPluginManager().registerEvents(new FactionUpgradeListener(this, factions), this);
        getServer().getPluginManager().registerEvents(new FactionOperatorListener(this, factions), this);

        FactionCommand factionExecutor = new FactionCommand(this, factions, gui);
        PluginCommand factionCommand = getCommand("faction");
        if (factionCommand == null) throw new IllegalStateException("faction command missing from plugin.yml");
        factionCommand.setExecutor(factionExecutor);
        factionCommand.setTabCompleter(factionExecutor);

        FactionAdminCommand adminExecutor = new FactionAdminCommand(this, factions);
        PluginCommand adminCommand = getCommand("factionadmin");
        if (adminCommand == null) throw new IllegalStateException("factionadmin command missing from plugin.yml");
        adminCommand.setExecutor(adminExecutor);
        adminCommand.setTabCompleter(adminExecutor);

        MiraFactionsApi api = new MiraFactionsApiImpl(factions);
        getServer().getServicesManager().register(MiraFactionsApi.class, api, this, ServicePriority.Normal);

        if (getServer().getPluginManager().isPluginEnabled("PlaceholderAPI")) {
            try {
                new MiraFactionsPlaceholderExpansion(this, factions, landValue).register();
                getLogger().info("PlaceholderAPI expansion registered.");
            } catch (Throwable throwable) {
                getLogger().warning("Could not register PlaceholderAPI expansion: " + throwable.getMessage());
            }
        }

        long regenTicks = Math.max(1L, getConfig().getLong("power.regen-minutes", 5L)) * 60L * 20L;
        Bukkit.getScheduler().runTaskTimer(this, factions::regeneratePower, regenTicks, regenTicks);
        Bukkit.getScheduler().runTaskTimer(this, factions::cleanupExpiredInvites, 1200L, 1200L);
        Bukkit.getScheduler().runTaskTimer(this, factions::processDailyEconomy, 20L * 60L, 20L * 60L * 60L);
        Bukkit.getScheduler().runTaskTimer(this, () -> history.pollBanks(factions.all()), 20L, 20L);
        long valueHistoryTicks = Math.max(1L, getConfig().getLong("history.value-snapshot-minutes", 10L)) * 60L * 20L;
        Bukkit.getScheduler().runTaskTimer(this, historyListener::snapshotValues, 40L, valueHistoryTicks);
        Bukkit.getScheduler().runTaskTimer(this, () -> {
            for (Player player : Bukkit.getOnlinePlayers()) factions.renderSeeChunk(player);
        }, 10L, 10L);

        getLogger().info("MiraFactions v" + getPluginMeta().getVersion() + " enabled with " + factions.all().size() + " faction(s). Vault economy: " + economy.available());
    }

    @Override
    public void onDisable() {
        if (factions != null) factions.save();
        getServer().getServicesManager().unregisterAll(this);
    }

    public Component component(String raw) {
        return AMPERSAND.deserialize(raw == null ? "" : raw);
    }

    public void msg(CommandSender sender, String raw) {
        sender.sendMessage(component(getConfig().getString("prefix", "&5[MiraFactions]&r ") + raw));
    }

    public FactionService factions() { return factions; }
    public EconomyHook economy() { return economy; }
    public FactionHistoryService history() { return history; }
    public FactionLandValueService landValue() { return landValue; }
}
