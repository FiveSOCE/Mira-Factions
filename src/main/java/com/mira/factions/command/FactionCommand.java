package com.mira.factions.command;

import com.mira.factions.MiraFactionsPlugin;
import com.mira.factions.gui.FactionGuiService;
import com.mira.factions.model.*;
import com.mira.factions.service.FactionService;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.*;
import org.bukkit.entity.Player;

import java.text.SimpleDateFormat;
import java.util.*;

public final class FactionCommand implements TabExecutor {
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
        if (!(sender instanceof Player player)) { sender.sendMessage("Players only."); return true; }
        if (!player.hasPermission("mirafactions.use")) return true;
        if (args.length == 0 || args[0].equalsIgnoreCase("help")) { help(player, args.length > 1 ? parseInt(args[1], 1) : 1); return true; }

        String sub = args[0].toLowerCase(Locale.ROOT);
        return switch (sub) {
            case "create" -> respond(player, args.length < 2 ? fail("Usage: /f create <name>") : service.create(player, args[1]));
            case "invite" -> invite(player, args);
            case "join" -> respond(player, args.length < 2 ? fail("Usage: /f join <faction>") : service.join(player, args[1]));
            case "leave" -> respond(player, service.leave(player));
            case "disband" -> respond(player, service.disband(player));
            case "kick" -> respond(player, args.length < 2 ? fail("Usage: /f kick <player>") : service.kick(player, offline(args[1])));
            case "ban" -> respond(player, args.length < 2 ? fail("Usage: /f ban <player>") : service.ban(player, offline(args[1])));
            case "unban" -> respond(player, args.length < 2 ? fail("Usage: /f unban <player>") : service.unban(player, offline(args[1])));
            case "bans" -> bans(player);
            case "promote" -> respond(player, args.length < 2 ? fail("Usage: /f promote <player>") : service.promote(player, offline(args[1])));
            case "demote" -> respond(player, args.length < 2 ? fail("Usage: /f demote <player>") : service.demote(player, offline(args[1])));
            case "role" -> role(player, args);
            case "transfer" -> respond(player, args.length < 2 ? fail("Usage: /f transfer <player>") : service.transfer(player, offline(args[1])));
            case "claim" -> claim(player, args);
            case "auto", "autoclaim" -> autoClaim(player);
            case "unclaim" -> args.length > 1 && args[1].equalsIgnoreCase("all") ? respond(player, service.unclaimAll(player)) : respond(player, service.unclaim(player));
            case "map" -> showMap(player);
            case "seechunk", "sc" -> seeChunk(player);
            case "sethome" -> respond(player, service.setHome(player));
            case "delhome" -> respond(player, service.delHome(player));
            case "home" -> respond(player, service.home(player));
            case "setwarp" -> respond(player, args.length < 2 ? fail("Usage: /f setwarp <name>") : service.setWarp(player, args[1]));
            case "delwarp" -> respond(player, args.length < 2 ? fail("Usage: /f delwarp <name>") : service.delWarp(player, args[1]));
            case "warp" -> respond(player, args.length < 2 ? fail("Usage: /f warp <name>") : service.warp(player, args[1]));
            case "warps" -> warps(player);
            case "chat", "c" -> chat(player, args);
            case "ally" -> relation(player, args, Relation.ALLY);
            case "truce" -> relation(player, args, Relation.TRUCE);
            case "enemy" -> relation(player, args, Relation.ENEMY);
            case "neutral" -> relation(player, args, Relation.NEUTRAL);
            case "power" -> power(player, args);
            case "info", "show", "status" -> info(player, args);
            case "set" -> set(player, args);
            case "perms", "permissions" -> {
                if (args.length == 1) { gui.openPermissions(player); yield true; }
                yield permissions(player, args);
            }
            case "money", "bank" -> money(player, args);
            case "tnt" -> tnt(player, args);
            case "shield" -> { gui.openShield(player); yield true; }
            case "fly" -> fly(player);
            case "upgrades", "upgrade" -> { gui.openUpgrades(player); yield true; }
            case "vault", "fvault" -> { gui.openVault(player); yield true; }
            case "zone", "zones" -> zone(player, args);
            case "near" -> near(player);
            case "coords" -> coords(player);
            case "announce" -> announce(player, args);
            case "stuck" -> respond(player, service.stuck(player));
            case "top" -> top(player);
            default -> { help(player, 1); yield true; }
        };
    }

    private boolean invite(Player player, String[] args) {
        if (args.length < 2) return respond(player, fail("Usage: /f invite <player|list|clear|revoke>"));
        if (args[1].equalsIgnoreCase("clear")) return respond(player, service.clearInvites(player));
        if (args[1].equalsIgnoreCase("list")) {
            Faction faction = service.of(player.getUniqueId());
            if (faction == null) return respond(player, fail("You are not in a faction."));
            plugin.msg(player, "&dPending Invites:");
            if (faction.invites().isEmpty()) plugin.msg(player, "&7None");
            long now = System.currentTimeMillis();
            for (var entry : faction.invites().entrySet()) {
                OfflinePlayer target = Bukkit.getOfflinePlayer(entry.getKey());
                long minutes = Math.max(0, (entry.getValue() - now) / 60_000L);
                plugin.msg(player, "&7- &f" + Optional.ofNullable(target.getName()).orElse(entry.getKey().toString()) + " &8(" + minutes + "m remaining)");
            }
            return true;
        }
        if (args[1].equalsIgnoreCase("revoke")) {
            if (args.length < 3) return respond(player, fail("Usage: /f invite revoke <player>"));
            return respond(player, service.revokeInvite(player, offline(args[2])));
        }
        return respond(player, service.invite(player, offline(args[1])));
    }

    private boolean bans(Player player) {
        Faction faction = service.of(player.getUniqueId());
        if (faction == null) return respond(player, fail("You are not in a faction."));
        plugin.msg(player, "&dFaction Bans:");
        if (faction.bans().isEmpty()) plugin.msg(player, "&7None");
        for (UUID uuid : faction.bans()) plugin.msg(player, "&7- &f" + Optional.ofNullable(Bukkit.getOfflinePlayer(uuid).getName()).orElse(uuid.toString()));
        return true;
    }

    private boolean role(Player player, String[] args) {
        if (args.length < 3) return respond(player, fail("Usage: /f role <player> <recruit|member|officer|coleader>"));
        try { return respond(player, service.setRank(player, offline(args[1]), FactionRank.valueOf(args[2].toUpperCase(Locale.ROOT)))); }
        catch (IllegalArgumentException ex) { return respond(player, fail("Unknown role.")); }
    }

    private boolean claim(Player player, String[] args) {
        if (args.length >= 3 && args[1].equalsIgnoreCase("radius")) return respond(player, service.claimRadius(player, parseInt(args[2], 0)));
        return respond(player, service.claim(player));
    }

    private boolean autoClaim(Player player) {
        Faction faction = service.of(player.getUniqueId());
        if (faction == null) return respond(player, fail("You are not in a faction."));
        if (!service.hasPermission(player, FactionPermission.CLAIM)) return respond(player, fail("You do not have faction permission to claim."));
        service.toggleAutoClaim(player.getUniqueId());
        plugin.msg(player, service.autoClaim(player.getUniqueId()) ? "&aAuto-claim enabled." : "&7Auto-claim disabled.");
        return true;
    }

    private boolean showMap(Player player) {
        for (String line : service.map(player, Math.max(2, plugin.getConfig().getInt("map.radius", 4))).split("\\n")) player.sendMessage(plugin.component(line));
        return true;
    }

    private boolean seeChunk(Player player) {
        service.toggleSeeChunk(player.getUniqueId());
        plugin.msg(player, service.seeChunk(player.getUniqueId()) ? "&aChunk boundary particles enabled." : "&7Chunk boundary particles disabled.");
        return true;
    }

    private boolean warps(Player player) {
        Faction faction = service.of(player.getUniqueId());
        if (faction == null) return respond(player, fail("You are not in a faction."));
        plugin.msg(player, "&dFaction Warps: &f" + (faction.warps().isEmpty() ? "None" : String.join(", ", faction.warps().keySet())));
        plugin.msg(player, "&7Slots: &f" + faction.warps().size() + "/" + service.warpLimit(faction));
        return true;
    }

    private boolean chat(Player player, String[] args) {
        ChatMode mode;
        if (args.length < 2) mode = service.chatMode(player.getUniqueId()) == ChatMode.FACTION ? ChatMode.PUBLIC : ChatMode.FACTION;
        else {
            try { mode = ChatMode.valueOf(args[1].toUpperCase(Locale.ROOT)); }
            catch (IllegalArgumentException ex) { return respond(player, fail("Chat modes: public, faction, ally, truce.")); }
        }
        if (mode != ChatMode.PUBLIC && service.of(player.getUniqueId()) == null) return respond(player, fail("You are not in a faction."));
        service.chatMode(player.getUniqueId(), mode);
        plugin.msg(player, "&aChat channel: &f" + mode + ".");
        return true;
    }

    private boolean relation(Player player, String[] args, Relation relation) {
        return respond(player, args.length < 2 ? fail("Usage: /f " + relation.name().toLowerCase(Locale.ROOT) + " <faction>") : service.relation(player, args[1], relation));
    }

    private boolean power(Player player, String[] args) {
        OfflinePlayer target = args.length > 1 ? offline(args[1]) : player;
        Faction faction = service.of(target.getUniqueId());
        plugin.msg(player, "&d" + Optional.ofNullable(target.getName()).orElse("Player") + " Power: &f" + String.format(Locale.US, "%.1f", service.power(target.getUniqueId())));
        if (faction != null) plugin.msg(player, "&7Faction: &f" + faction.name() + " &7Power: &f" + String.format(Locale.US, "%.1f", service.factionPower(faction)) + " &7Claims: &f" + faction.claims().size() + "/" + service.maxClaims(faction) + (service.raidable(faction) ? " &cRAIDABLE" : " &aPROTECTED"));
        return true;
    }

    private boolean info(Player player, String[] args) {
        Faction faction = args.length > 1 ? service.byName(args[1]) : service.of(player.getUniqueId());
        if (faction == null) return respond(player, fail("Faction not found."));
        long online = faction.members().keySet().stream().map(Bukkit::getPlayer).filter(Objects::nonNull).count();
        SimpleDateFormat date = new SimpleDateFormat("yyyy-MM-dd");
        plugin.msg(player, "&5&m--------------------------------");
        plugin.msg(player, "&d" + faction.name() + (service.raidable(faction) ? " &c[RAIDABLE]" : " &a[PROTECTED]"));
        if (!faction.description().isBlank()) plugin.msg(player, "&7" + faction.description());
        if (!faction.link().isBlank()) plugin.msg(player, "&7Link: &f" + faction.link());
        plugin.msg(player, "&7Created: &f" + date.format(new Date(faction.createdAt())) + " &7Open: &f" + faction.open());
        plugin.msg(player, "&7Members: &f" + faction.members().size() + "/" + service.memberLimit(faction) + " &7Online: &f" + online);
        plugin.msg(player, "&7Power: &f" + String.format(Locale.US, "%.1f", service.factionPower(faction)) + " &7Claims: &f" + faction.claims().size() + "/" + service.maxClaims(faction));
        plugin.msg(player, "&7Bank: &f" + plugin.economy().format(faction.bankBalance()) + " &7TNT: &f" + faction.tntBalance() + "/" + service.tntCapacity(faction));
        if (service.shielded(faction)) plugin.msg(player, "&bShield: ACTIVE");
        if (faction.peaceful()) plugin.msg(player, "&aPeaceful faction");
        return true;
    }

    private boolean set(Player player, String[] args) {
        if (args.length < 2) return respond(player, fail("Usage: /f set <tag|description|link|open|title|dues> ..."));
        return switch (args[1].toLowerCase(Locale.ROOT)) {
            case "tag", "name" -> respond(player, args.length < 3 ? fail("Usage: /f set tag <name>") : service.rename(player, args[2]));
            case "description", "desc" -> respond(player, args.length < 3 ? fail("Usage: /f set description <text>") : service.setDescription(player, join(args, 2)));
            case "link" -> respond(player, args.length < 3 ? fail("Usage: /f set link <url|text>") : service.setLink(player, join(args, 2)));
            case "open" -> respond(player, args.length < 3 ? fail("Usage: /f set open <true|false>") : service.setOpen(player, Boolean.parseBoolean(args[2])));
            case "title" -> respond(player, args.length < 4 ? fail("Usage: /f set title <player> <title|clear>") : service.setTitle(player, offline(args[2]), args[3].equalsIgnoreCase("clear") ? "" : join(args, 3)));
            case "dues" -> setDues(player, args);
            default -> respond(player, fail("Unknown faction setting."));
        };
    }

    private boolean setDues(Player player, String[] args) {
        Faction faction = service.of(player.getUniqueId());
        if (faction == null) return respond(player, fail("You are not in a faction."));
        if (faction.rank(player.getUniqueId()) != FactionRank.LEADER) return respond(player, fail("Only the leader can set dues."));
        if (args.length < 3) return respond(player, fail("Usage: /f set dues <amount>"));
        double amount = parseDouble(args[2], -1);
        if (amount < 0) return respond(player, fail("Invalid amount."));
        service.setDues(player, amount);
        return respond(player, FactionService.Result.ok("Daily member dues set to " + plugin.economy().format(amount) + "."));
    }

    private boolean permissions(Player player, String[] args) {
        Faction faction = service.of(player.getUniqueId());
        if (faction == null) return respond(player, fail("You are not in a faction."));
        if (args.length == 1 || args[1].equalsIgnoreCase("list")) {
            plugin.msg(player, "&dFaction Permission Minimum Ranks:");
            for (FactionPermission permission : FactionPermission.values()) plugin.msg(player, "&7" + permission.name().toLowerCase(Locale.ROOT) + ": &f" + faction.minimum(permission));
            return true;
        }
        if (args[1].equalsIgnoreCase("relation")) {
            if (args.length < 5) return respond(player, fail("Usage: /f perms relation <permission> <ally|truce|neutral|enemy> <allow|deny>"));
            try {
                FactionPermission permission = FactionPermission.valueOf(args[2].toUpperCase(Locale.ROOT));
                Relation relation = Relation.valueOf(args[3].toUpperCase(Locale.ROOT));
                return respond(player, service.setRelationPermission(player, permission, relation, args[4].equalsIgnoreCase("allow")));
            } catch (IllegalArgumentException ex) { return respond(player, fail("Unknown permission or relation.")); }
        }
        if (args.length < 3) return respond(player, fail("Usage: /f perms <permission> <rank>"));
        try {
            return respond(player, service.setPermission(player, FactionPermission.valueOf(args[1].toUpperCase(Locale.ROOT)), FactionRank.valueOf(args[2].toUpperCase(Locale.ROOT))));
        } catch (IllegalArgumentException ex) { return respond(player, fail("Unknown permission or rank.")); }
    }

    private boolean money(Player player, String[] args) {
        Faction faction = service.of(player.getUniqueId());
        if (faction == null) return respond(player, fail("You are not in a faction."));
        if (args.length < 2 || args[1].equalsIgnoreCase("balance")) { plugin.msg(player, "&dFaction Bank: &f" + plugin.economy().format(faction.bankBalance())); return true; }
        return switch (args[1].toLowerCase(Locale.ROOT)) {
            case "deposit" -> respond(player, args.length < 3 ? fail("Usage: /f money deposit <amount>") : service.deposit(player, parseDouble(args[2], -1)));
            case "withdraw" -> respond(player, args.length < 3 ? fail("Usage: /f money withdraw <amount>") : service.withdraw(player, parseDouble(args[2], -1)));
            case "pay" -> respond(player, args.length < 4 ? fail("Usage: /f money pay <faction> <amount>") : service.payFaction(player, args[2], parseDouble(args[3], -1)));
            default -> respond(player, fail("Usage: /f money <balance|deposit|withdraw|pay>"));
        };
    }

    private boolean tnt(Player player, String[] args) {
        Faction faction = service.of(player.getUniqueId());
        if (faction == null) return respond(player, fail("You are not in a faction."));
        if (args.length < 2 || args[1].equalsIgnoreCase("balance")) { plugin.msg(player, "&dFaction TNT: &f" + faction.tntBalance() + "/" + service.tntCapacity(faction)); return true; }
        return switch (args[1].toLowerCase(Locale.ROOT)) {
            case "deposit" -> respond(player, args.length < 3 ? fail("Usage: /f tnt deposit <amount>") : service.depositTnt(player, parseInt(args[2], 0)));
            case "withdraw" -> respond(player, args.length < 3 ? fail("Usage: /f tnt withdraw <amount>") : service.withdrawTnt(player, parseInt(args[2], 0)));
            default -> respond(player, fail("Usage: /f tnt <balance|deposit|withdraw>"));
        };
    }

    private boolean shield(Player player) {
        Faction faction = service.of(player.getUniqueId());
        if (faction == null) return respond(player, fail("You are not in a faction."));
        if (service.shielded(faction)) { plugin.msg(player, "&bFaction shield is active."); return true; }
        return respond(player, service.activateShield(player));
    }

    private boolean fly(Player player) {
        Faction faction = service.of(player.getUniqueId());
        if (faction == null) return respond(player, fail("You are not in a faction."));
        if (faction.upgrade(UpgradeType.FLIGHT) <= 0) return respond(player, fail("Your faction has not unlocked Faction Flight."));
        if (!service.hasPermission(player, FactionPermission.FLY)) return respond(player, fail("You do not have faction permission to fly."));
        service.toggleFactionFlight(player.getUniqueId());
        if (service.factionFlight(player.getUniqueId())) service.updateFlight(player);
        else if (!player.hasPermission("mirafly.permanent")) { player.setFlying(false); player.setAllowFlight(false); }
        plugin.msg(player, service.factionFlight(player.getUniqueId()) ? "&aFaction flight enabled." : "&7Faction flight disabled.");
        return true;
    }

    private boolean zone(Player player, String[] args) {
        Faction faction = service.of(player.getUniqueId());
        if (faction == null) return respond(player, fail("You are not in a faction."));
        if (args.length < 2 || args[1].equalsIgnoreCase("list")) {
            plugin.msg(player, "&dFaction Zones: &f" + (faction.zones().isEmpty() ? "None" : String.join(", ", faction.zones().keySet())) + " &7(" + faction.zones().size() + "/" + service.zoneLimit(faction) + ")");
            return true;
        }
        return switch (args[1].toLowerCase(Locale.ROOT)) {
            case "create" -> respond(player, args.length < 3 ? fail("Usage: /f zone create <name>") : service.createZone(player, args[2]));
            case "delete" -> respond(player, args.length < 3 ? fail("Usage: /f zone delete <name>") : service.deleteZone(player, args[2]));
            case "assign" -> respond(player, args.length < 3 ? fail("Usage: /f zone assign <name>") : service.assignZone(player, args[2]));
            case "greeting" -> respond(player, args.length < 4 ? fail("Usage: /f zone greeting <name> <text>") : service.zoneGreeting(player, args[2], join(args, 3)));
            case "perm" -> {
                if (args.length < 5) yield respond(player, fail("Usage: /f zone perm <name> <permission> <rank>"));
                try { yield respond(player, service.zonePermission(player, args[2], FactionPermission.valueOf(args[3].toUpperCase(Locale.ROOT)), FactionRank.valueOf(args[4].toUpperCase(Locale.ROOT)))); }
                catch (IllegalArgumentException ex) { yield respond(player, fail("Unknown permission or rank.")); }
            }
            default -> respond(player, fail("Usage: /f zone <list|create|delete|assign|greeting|perm>"));
        };
    }

    private boolean near(Player player) {
        Faction faction = service.of(player.getUniqueId());
        if (faction == null) return respond(player, fail("You are not in a faction."));
        List<Player> nearby = service.nearbyMembers(player, plugin.getConfig().getDouble("near.radius", 100.0));
        plugin.msg(player, "&dNearby faction members: &f" + (nearby.isEmpty() ? "None" : String.join(", ", nearby.stream().map(Player::getName).toList())));
        return true;
    }

    private boolean coords(Player player) {
        Faction faction = service.of(player.getUniqueId());
        if (faction == null) return respond(player, fail("You are not in a faction."));
        String message = "&b" + player.getName() + "&7: &f" + player.getWorld().getName() + " " + player.getLocation().getBlockX() + ", " + player.getLocation().getBlockY() + ", " + player.getLocation().getBlockZ();
        service.announce(faction, message);
        return true;
    }

    private boolean announce(Player player, String[] args) {
        Faction faction = service.of(player.getUniqueId());
        if (faction == null) return respond(player, fail("You are not in a faction."));
        if (!service.hasPermission(player, FactionPermission.ANNOUNCE)) return respond(player, fail("You do not have faction permission to announce."));
        if (args.length < 2) return respond(player, fail("Usage: /f announce <message>"));
        service.announce(faction, "&6[Announcement] &f" + join(args, 1));
        return true;
    }

    private boolean top(Player player) {
        List<Faction> sorted = service.all().stream().sorted(Comparator.comparingDouble(service::factionPower).reversed()).limit(10).toList();
        plugin.msg(player, "&5&m----&d Faction Top &5&m----");
        int place = 1;
        for (Faction faction : sorted) plugin.msg(player, "&d#" + place++ + " &f" + faction.name() + " &7Power: &f" + String.format(Locale.US, "%.1f", service.factionPower(faction)) + " &7Claims: &f" + faction.claims().size());
        return true;
    }

    private boolean respond(Player player, FactionService.Result result) {
        if (!result.message().isBlank()) plugin.msg(player, (result.success() ? "&a" : "&c") + result.message());
        return true;
    }

    private FactionService.Result fail(String message) { return FactionService.Result.fail(message); }
    private OfflinePlayer offline(String name) { return Bukkit.getOfflinePlayer(name); }
    private String join(String[] args, int start) { return String.join(" ", Arrays.copyOfRange(args, start, args.length)); }
    private int parseInt(String raw, int fallback) { try { return Integer.parseInt(raw); } catch (Exception ignored) { return fallback; } }
    private double parseDouble(String raw, double fallback) { try { return Double.parseDouble(raw); } catch (Exception ignored) { return fallback; } }

    private void help(Player player, int page) {
        plugin.msg(player, "&5&m------&d MiraFactions Commands &5&m------");
        if (page <= 1) {
            plugin.msg(player, "&d/f create <name> &7| invite/join/leave/disband");
            plugin.msg(player, "&d/f promote/demote/role/kick/ban/unban/transfer");
            plugin.msg(player, "&d/f claim [radius <n>] &7| unclaim [all] &7| auto &7| map &7| seechunk");
            plugin.msg(player, "&d/f home/sethome/delhome &7| setwarp/delwarp/warp/warps");
            plugin.msg(player, "&7Use &f/f help 2 &7for diplomacy/economy/war systems.");
        } else if (page == 2) {
            plugin.msg(player, "&d/f ally/truce/enemy/neutral <faction> &7| chat <channel>");
            plugin.msg(player, "&d/f money &7| tnt &7| shield &7| fly &7| upgrades &7| vault");
            plugin.msg(player, "&d/f perms &7| zone &7| set <tag|description|link|open|title|dues>");
            plugin.msg(player, "&7Use &f/f help 3 &7for utilities/status.");
        } else {
            plugin.msg(player, "&d/f info [faction] &7| power [player] &7| top &7| near &7| coords");
            plugin.msg(player, "&d/f announce <message> &7| stuck &7| invite list/revoke/clear &7| bans");
        }
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) return filter(args[0], List.of("help","create","invite","join","leave","disband","kick","ban","unban","bans","promote","demote","role","transfer","claim","auto","unclaim","map","seechunk","sethome","delhome","home","setwarp","delwarp","warp","warps","chat","ally","truce","enemy","neutral","power","info","set","perms","money","tnt","shield","fly","upgrades","vault","zone","near","coords","announce","stuck","top"));
        if (args.length == 2 && List.of("ally","truce","enemy","neutral","join","info").contains(args[0].toLowerCase(Locale.ROOT))) return filter(args[1], service.all().stream().map(Faction::name).toList());
        if (args.length == 2 && List.of("invite","kick","ban","unban","promote","demote","role","transfer","power").contains(args[0].toLowerCase(Locale.ROOT))) return filter(args[1], Bukkit.getOnlinePlayers().stream().map(Player::getName).toList());
        if (args.length == 2 && args[0].equalsIgnoreCase("chat")) return filter(args[1], Arrays.stream(ChatMode.values()).map(v -> v.name().toLowerCase(Locale.ROOT)).toList());
        if (args.length == 2 && args[0].equalsIgnoreCase("set")) return filter(args[1], List.of("tag","description","link","open","title","dues"));
        if (args.length == 2 && args[0].equalsIgnoreCase("money")) return filter(args[1], List.of("balance","deposit","withdraw","pay"));
        if (args.length == 2 && args[0].equalsIgnoreCase("tnt")) return filter(args[1], List.of("balance","deposit","withdraw"));
        if (args.length == 2 && args[0].equalsIgnoreCase("zone")) return filter(args[1], List.of("list","create","delete","assign","greeting","perm"));
        if (args.length == 2 && args[0].equalsIgnoreCase("unclaim")) return filter(args[1], List.of("all"));
        if (args.length == 2 && args[0].equalsIgnoreCase("claim")) return filter(args[1], List.of("radius"));
        if (args.length == 2 && args[0].equalsIgnoreCase("perms")) return filter(args[1], new ArrayList<>() {{ add("list"); add("relation"); addAll(Arrays.stream(FactionPermission.values()).map(v -> v.name().toLowerCase(Locale.ROOT)).toList()); }});
        if (args.length == 3 && args[0].equalsIgnoreCase("role")) return filter(args[2], Arrays.stream(FactionRank.values()).filter(v -> v != FactionRank.LEADER).map(v -> v.name().toLowerCase(Locale.ROOT)).toList());
        return List.of();
    }

    private List<String> filter(String prefix, Collection<String> options) {
        String lower = prefix.toLowerCase(Locale.ROOT);
        return options.stream().filter(value -> value.toLowerCase(Locale.ROOT).startsWith(lower)).sorted().toList();
    }
}
