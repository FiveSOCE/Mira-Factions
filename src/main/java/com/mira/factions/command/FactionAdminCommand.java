package com.mira.factions.command;

import com.mira.factions.MiraFactionsPlugin;
import com.mira.factions.model.Faction;
import com.mira.factions.model.TerritoryType;
import com.mira.factions.service.FactionService;
import org.bukkit.Bukkit;
import org.bukkit.command.*;
import org.bukkit.entity.Player;

import java.util.Locale;

public final class FactionAdminCommand implements TabExecutor {
    private final MiraFactionsPlugin plugin;
    private final FactionService service;

    public FactionAdminCommand(MiraFactionsPlugin plugin, FactionService service) {
        this.plugin = plugin;
        this.service = service;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("mirafactions.admin")) return true;
        if (args.length == 0 || args[0].equalsIgnoreCase("help")) { help(sender); return true; }
        switch (args[0].toLowerCase(Locale.ROOT)) {
            case "reload" -> { plugin.reloadConfig(); plugin.msg(sender, "&aMiraFactions config reloaded."); }
            case "save" -> { service.save(); plugin.msg(sender, "&aFaction data saved."); }
            case "bypass" -> {
                if (!(sender instanceof Player player)) { plugin.msg(sender, "&cPlayers only."); break; }
                service.toggleBypass(player.getUniqueId());
                plugin.msg(sender, service.bypass(player.getUniqueId()) ? "&aFaction bypass enabled." : "&7Faction bypass disabled.");
            }
            case "power" -> power(sender, args);
            case "disband" -> {
                Faction faction = args.length > 1 ? service.byName(args[1]) : null;
                respond(sender, faction == null ? FactionService.Result.fail("Faction not found.") : service.forceDisband(faction));
            }
            case "claim" -> {
                if (!(sender instanceof Player player) || args.length < 2) { plugin.msg(sender, "&cUsage: /fa claim <safezone|warzone|wilderness>"); break; }
                try { respond(sender, service.setSpecialClaim(player.getLocation(), TerritoryType.valueOf(args[1].toUpperCase(Locale.ROOT)))); }
                catch (IllegalArgumentException ex) { plugin.msg(sender, "&cUse safezone, warzone or wilderness."); }
            }
            case "grace" -> grace(sender, args);
            case "peaceful" -> toggleFactionFlag(sender, args, true);
            case "permanent" -> toggleFactionFlag(sender, args, false);
            case "money" -> factionMoney(sender, args);
            case "tnt" -> factionTnt(sender, args);
            default -> help(sender);
        }
        return true;
    }

    private void power(CommandSender sender, String[] args) {
        if (args.length < 4) { plugin.msg(sender, "&cUsage: /fa power <set|add> <player> <amount>"); return; }
        Player target = Bukkit.getPlayerExact(args[2]);
        if (target == null) { plugin.msg(sender, "&cPlayer not found."); return; }
        double amount;
        try { amount = Double.parseDouble(args[3]); } catch (NumberFormatException ex) { plugin.msg(sender, "&cInvalid amount."); return; }
        if (args[1].equalsIgnoreCase("set")) service.setPower(target.getUniqueId(), amount); else if (args[1].equalsIgnoreCase("add")) service.addPower(target.getUniqueId(), amount); else { plugin.msg(sender, "&cUse set or add."); return; }
        plugin.msg(sender, "&aUpdated " + target.getName() + " power to " + String.format(Locale.US, "%.1f", service.power(target.getUniqueId())) + ".");
    }

    private void grace(CommandSender sender, String[] args) {
        if (args.length < 2) { plugin.msg(sender, "&eGrace active: &f" + service.graceActive()); return; }
        if (args[1].equalsIgnoreCase("stop")) { respond(sender, service.stopGrace()); return; }
        if (args[1].equalsIgnoreCase("start") && args.length > 2) {
            try { respond(sender, service.startGrace(Long.parseLong(args[2]))); }
            catch (NumberFormatException ex) { plugin.msg(sender, "&cMinutes must be a number."); }
            return;
        }
        plugin.msg(sender, "&cUsage: /fa grace <start <minutes>|stop>");
    }

    private void toggleFactionFlag(CommandSender sender, String[] args, boolean peaceful) {
        Faction faction = args.length > 1 ? service.byName(args[1]) : null;
        if (faction == null) { plugin.msg(sender, "&cFaction not found."); return; }
        if (peaceful) {
            faction.peaceful(!faction.peaceful());
            plugin.msg(sender, "&a" + faction.name() + " peaceful: " + faction.peaceful());
        } else {
            faction.permanent(!faction.permanent());
            plugin.msg(sender, "&a" + faction.name() + " permanent: " + faction.permanent());
        }
        service.save();
    }

    private void factionMoney(CommandSender sender, String[] args) {
        if (args.length < 4) { plugin.msg(sender, "&cUsage: /fa money <set|add> <faction> <amount>"); return; }
        Faction faction = service.byName(args[2]);
        if (faction == null) { plugin.msg(sender, "&cFaction not found."); return; }
        double amount;
        try { amount = Double.parseDouble(args[3]); } catch (NumberFormatException ex) { plugin.msg(sender, "&cInvalid amount."); return; }
        if (args[1].equalsIgnoreCase("set")) faction.bankBalance(amount); else if (args[1].equalsIgnoreCase("add")) faction.bankBalance(faction.bankBalance() + amount); else { plugin.msg(sender, "&cUse set or add."); return; }
        service.save();
        plugin.msg(sender, "&aFaction bank updated to " + plugin.economy().format(faction.bankBalance()) + ".");
    }

    private void factionTnt(CommandSender sender, String[] args) {
        if (args.length < 4) { plugin.msg(sender, "&cUsage: /fa tnt <set|add> <faction> <amount>"); return; }
        Faction faction = service.byName(args[2]);
        if (faction == null) { plugin.msg(sender, "&cFaction not found."); return; }
        int amount;
        try { amount = Integer.parseInt(args[3]); } catch (NumberFormatException ex) { plugin.msg(sender, "&cInvalid amount."); return; }
        if (args[1].equalsIgnoreCase("set")) faction.tntBalance(amount); else if (args[1].equalsIgnoreCase("add")) faction.tntBalance(faction.tntBalance() + amount); else { plugin.msg(sender, "&cUse set or add."); return; }
        service.save();
        plugin.msg(sender, "&aFaction TNT updated to " + faction.tntBalance() + ".");
    }

    private void respond(CommandSender sender, FactionService.Result result) {
        plugin.msg(sender, (result.success() ? "&a" : "&c") + result.message());
    }

    private void help(CommandSender sender) {
        plugin.msg(sender, "&d/fa reload &7| save &7| bypass");
        plugin.msg(sender, "&d/fa power <set|add> <player> <amount>");
        plugin.msg(sender, "&d/fa disband <faction> &7| claim <safezone|warzone|wilderness>");
        plugin.msg(sender, "&d/fa grace <start <minutes>|stop> &7| peaceful <faction> &7| permanent <faction>");
        plugin.msg(sender, "&d/fa money <set|add> <faction> <amount> &7| tnt <set|add> <faction> <amount>");
    }

    @Override
    public java.util.List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) return java.util.List.of("help","reload","save","bypass","power","disband","claim","grace","peaceful","permanent","money","tnt").stream().filter(v -> v.startsWith(args[0].toLowerCase(Locale.ROOT))).toList();
        if (args.length == 2 && java.util.List.of("disband","peaceful","permanent").contains(args[0].toLowerCase(Locale.ROOT))) return service.all().stream().map(Faction::name).filter(v -> v.toLowerCase(Locale.ROOT).startsWith(args[1].toLowerCase(Locale.ROOT))).toList();
        return java.util.List.of();
    }
}
