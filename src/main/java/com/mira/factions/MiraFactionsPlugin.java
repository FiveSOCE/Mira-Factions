package com.mira.factions;

import com.mira.factions.command.*;
import com.mira.factions.gui.FactionGuiService;
import com.mira.factions.listener.FactionListener;
import com.mira.factions.service.FactionService;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.java.JavaPlugin;

public final class MiraFactionsPlugin extends JavaPlugin {
    private static final LegacyComponentSerializer AMPERSAND = LegacyComponentSerializer.legacyAmpersand();
    private FactionService factions;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        factions = new FactionService(this);
        FactionGuiService gui = new FactionGuiService(this, factions);

        getServer().getPluginManager().registerEvents(new FactionListener(this, factions, gui), this);

        PluginCommand factionCommand = getCommand("faction");
        if (factionCommand == null) throw new IllegalStateException("faction command missing from plugin.yml");
        factionCommand.setExecutor(new FactionCommand(this, factions, gui));

        PluginCommand adminCommand = getCommand("factionadmin");
        if (adminCommand == null) throw new IllegalStateException("factionadmin command missing from plugin.yml");
        adminCommand.setExecutor(new FactionAdminCommand(this, factions));

        long regenTicks = Math.max(1L, getConfig().getLong("power.regen-minutes", 5L)) * 60L * 20L;
        Bukkit.getScheduler().runTaskTimer(this, factions::regeneratePower, regenTicks, regenTicks);

        getLogger().info("MiraFactions v" + getPluginMeta().getVersion() + " enabled.");
    }

    @Override
    public void onDisable() {
        if (factions != null) factions.save();
    }

    public Component component(String raw) {
        return AMPERSAND.deserialize(raw == null ? "" : raw);
    }

    public void msg(CommandSender sender, String raw) {
        sender.sendMessage(component(getConfig().getString("prefix", "&5[MiraFactions]&r ") + raw));
    }
}
