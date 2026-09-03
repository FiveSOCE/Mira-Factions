package com.mira.factions.command;

import com.mira.factions.MiraFactionsPlugin;
import com.mira.factions.gui.FactionGuiService;
import com.mira.factions.model.Faction;
import com.mira.factions.model.Relation;
import com.mira.factions.service.FactionService;
import org.bukkit.Bukkit;
import org.bukkit.command.*;
import org.bukkit.entity.Player;

import java.util.Locale;

public final class FactionCommand implements CommandExecutor {
    private final MiraFactionsPlugin plugin;
    private final FactionService service;
    private final FactionGuiService gui;

    public FactionCommand(MiraFactionsPlugin plugin, FactionService service, FactionGuiService gui) {
        this.plugin = plugin;
        this.service = service;
        this.gui = gui;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Players only.");
            return true;
        }
        if (!player.hasPermission("mirafactions.use")) return true;
        if (args.length == 0) {
            gui.open(player);
            return true;
        }

        String sub = args[0].toLowerCase(Locale.ROOT);
        switch (sub) {
            case "create" -> {
                if (args.length < 2) return respond(player, FactionService.Result.fail("Usage: /f create <name>"));
                return respond(player, service.create(player, args[1]));
            }
            case "invite" -> {
                Player target = args.length > 1 ? Bukkit.getPlayerExact(args[1]) : null;
                return respond(player, target == null ? FactionService.Result.fail("Player not found.") : service.invite(player, target));
            }
            case "join" -> {
                return respond(player, args.length < 2 ? FactionService.Result.fail("Usage: /f join <faction>") : service.join(player, args[1]));
            }
            case "leave" -> { return respond(player, service.leave(player)); }
            case "disband" -> { return respond(player, service.disband(player)); }
            case "kick" -> {
                Player target = args.length > 1 ? Bukkit.getPlayerExact(args[1]) : null;
                return respond(player, target == null ? FactionService.Result.fail("Player not found.") : service.kick(player, target));
            }
            case "promote" -> {
                Player target = args.length > 1 ? Bukkit.getPlayerExact(args[1]) : null;
                return respond(player, target == null ? FactionService.Result.fail("Player not found.") : service.promote(player, target));
            }
            case "demote" -> {
                Player target = args.length > 1 ? Bukkit.getPlayerExact(args[1]) : null;
                return respond(player, target == null ? FactionService.Result.fail("Player not found.") : service.demote(player, target));
            }
            case "transfer" -> {
                Player target = args.length > 1 ? Bukkit.getPlayerExact(args[1]) : null;
                return respond(player, target == null ? FactionService.Result.fail("Player not found.") : service.transfer(player, target));
            }
            case "claim" -> { return respond(player, service.claim(player)); }
            case "unclaim" -> { return respond(player, service.unclaim(player)); }
            case "sethome" -> { return respond(player, service.setHome(player)); }
            case "home" -> { return respond(player, service.home(player)); }
            case "chat", "c" -> {
                service.toggleFactionChat(player.getUniqueId());
                plugin.msg(player, service.factionChat(player.getUniqueId()) ? "&aFaction chat enabled." : "&7Faction chat disabled.");
                return true;
            }
            case "ally" -> { return respond(player, args.length < 2 ? FactionService.Result.fail("Usage: /f ally <faction>") : service.relation(player, args[1], Relation.ALLY)); }
            case "enemy" -> { return respond(player, args.length < 2 ? FactionService.Result.fail("Usage: /f enemy <faction>") : service.relation(player, args[1], Relation.ENEMY)); }
            case "neutral" -> { return respond(player, args.length < 2 ? FactionService.Result.fail("Usage: /f neutral <faction>") : service.relation(player, args[1], Relation.NEUTRAL)); }
            case "power" -> {
                Faction faction = service.of(player.getUniqueId());
                if (faction == null) plugin.msg(player, "&cYou are not in a faction.");
                else plugin.msg(player, "&dPower: &f" + String.format(Locale.US, "%.1f", service.factionPower(faction))
                        + " &7| Your Power: &f" + String.format(Locale.US, "%.1f", service.power(player.getUniqueId()))
                        + " &7| Claims: &f" + faction.claims().size() + "/" + service.maxClaims(faction));
                return true;
            }
            case "info" -> {
                Faction faction = args.length > 1 ? service.byName(args[1]) : service.of(player.getUniqueId());
                if (faction == null) plugin.msg(player, "&cFaction not found.");
                else plugin.msg(player, "&d" + faction.name() + " &7| Members: &f" + faction.members().size()
                        + " &7| Power: &f" + String.format(Locale.US, "%.1f", service.factionPower(faction))
                        + " &7| Claims: &f" + faction.claims().size());
                return true;
            }
            default -> {
                help(player);
                return true;
            }
        }
    }

    private boolean respond(Player player, FactionService.Result result) {
        if (!result.message().isBlank()) plugin.msg(player, (result.success() ? "&a" : "&c") + result.message());
        return true;
    }

    private void help(Player player) {
        plugin.msg(player, "&d/f &7opens the faction GUI");
        plugin.msg(player, "&d/f create <name> &7| &d/f invite <player> &7| &d/f join <faction>");
        plugin.msg(player, "&d/f claim &7| &d/f unclaim &7| &d/f home &7| &d/f sethome &7| &d/f chat");
        plugin.msg(player, "&d/f promote|demote|kick|transfer <player>");
        plugin.msg(player, "&d/f ally|enemy|neutral <faction> &7| &d/f power &7| &d/f info [faction]");
    }
}
