package com.mira.factions.command;

import com.mira.factions.MiraFactionsPlugin;
import com.mira.factions.service.FactionService;
import org.bukkit.command.*;

public final class FactionAdminCommand implements CommandExecutor {
    private final MiraFactionsPlugin plugin;
    private final FactionService service;

    public FactionAdminCommand(MiraFactionsPlugin plugin, FactionService service) {
        this.plugin = plugin;
        this.service = service;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0 || args[0].equalsIgnoreCase("help")) {
            plugin.msg(sender, "&d/fadmin reload &7- reload config");
            plugin.msg(sender, "&d/fadmin save &7- force-save faction data");
            return true;
        }
        if (args[0].equalsIgnoreCase("reload")) {
            plugin.reloadConfig();
            plugin.msg(sender, "&aMiraFactions config reloaded.");
            return true;
        }
        if (args[0].equalsIgnoreCase("save")) {
            service.save();
            plugin.msg(sender, "&aFaction data saved.");
            return true;
        }
        return true;
    }
}
