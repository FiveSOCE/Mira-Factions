package com.mira.factions.command;

import com.mira.factions.MiraFactionsPlugin;
import com.mira.factions.model.Faction;
import com.mira.factions.model.FactionRank;
import com.mira.factions.model.TerritoryType;
import com.mira.factions.model.UpgradeType;
import com.mira.factions.service.FactionService;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.*;
import org.bukkit.entity.Player;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public final class FactionAdminCommand implements TabExecutor {
    private static final Set<UUID> CHAT_SPIES = ConcurrentHashMap.newKeySet();

    private final MiraFactionsPlugin plugin;
    private final FactionService service;

    public FactionAdminCommand(MiraFactionsPlugin plugin, FactionService service) {
        this.plugin = plugin;
        this.service = service;
    }

    public static boolean chatSpy(UUID player) {
        return CHAT_SPIES.contains(player);
    }

    public static Set<UUID> chatSpies() {
        return Collections.unmodifiableSet(CHAT_SPIES);
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("mirafactions.admin")) return true;
        if (args.length == 0 || args[0].equalsIgnoreCase("help")) {
            help(sender, args.length > 1 ? parseInt(args[1], 1) : 1);
            return true;
        }

        switch (args[0].toLowerCase(Locale.ROOT)) {
            case "reload" -> {
                plugin.reloadConfig();
                plugin.msg(sender, "&aMiraFactions config reloaded.");
            }
            case "save" -> {
                service.save();
                plugin.msg(sender, "&aFaction data saved.");
            }
            case "bypass" -> toggleBypass(sender);
            case "chatspy", "spy" -> toggleChatSpy(sender);
            case "power" -> power(sender, args);
            case "powerboost" -> powerBoost(sender, args);
            case "permanentpower", "permpower" -> permanentPower(sender, args);
            case "disband" -> disband(sender, args);
            case "forcejoin" -> forceJoin(sender, args);
            case "forcekick" -> forceKick(sender, args);
            case "forcerole" -> forceRole(sender, args);
            case "forcehome" -> forceHome(sender, args);
            case "rename", "tag" -> rename(sender, args);
            case "claim" -> specialClaim(sender, args);
            case "grace" -> grace(sender, args);
            case "peaceful" -> toggleFactionFlag(sender, args, Flag.PEACEFUL);
            case "permanent" -> toggleFactionFlag(sender, args, Flag.PERMANENT);
            case "rentexempt", "rent-exempt" -> toggleFactionFlag(sender, args, Flag.RENT_EXEMPT);
            case "money" -> factionMoney(sender, args);
            case "tnt" -> factionTnt(sender, args);
            case "shield" -> shield(sender, args);
            case "upgrade", "upgrades" -> upgrade(sender, args);
            case "info" -> info(sender, args);
            default -> help(sender, 1);
        }
        return true;
    }

    private void toggleBypass(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            plugin.msg(sender, "&cPlayers only.");
            return;
        }
        service.toggleBypass(player.getUniqueId());
        plugin.msg(sender, service.bypass(player.getUniqueId()) ? "&aFaction bypass enabled." : "&7Faction bypass disabled.");
    }

    private void toggleChatSpy(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            plugin.msg(sender, "&cPlayers only.");
            return;
        }
        if (!CHAT_SPIES.remove(player.getUniqueId())) {
            CHAT_SPIES.add(player.getUniqueId());
            plugin.msg(player, "&aFaction chat spy enabled.");
        } else {
            plugin.msg(player, "&7Faction chat spy disabled.");
        }
    }

    private void power(CommandSender sender, String[] args) {
        if (args.length < 4) {
            plugin.msg(sender, "&cUsage: /fa power <set|add> <player> <amount>");
            return;
        }
        OfflinePlayer target = offline(args[2]);
        if (target == null) {
            plugin.msg(sender, "&cPlayer not found.");
            return;
        }
        double amount = parseDouble(args[3], Double.NaN);
        if (Double.isNaN(amount)) {
            plugin.msg(sender, "&cInvalid amount.");
            return;
        }
        if (args[1].equalsIgnoreCase("set")) service.setPower(target.getUniqueId(), amount);
        else if (args[1].equalsIgnoreCase("add")) service.addPower(target.getUniqueId(), amount);
        else {
            plugin.msg(sender, "&cUse set or add.");
            return;
        }
        plugin.msg(sender, "&aUpdated " + name(target) + " power to " + String.format(Locale.US, "%.1f", service.power(target.getUniqueId())) + ".");
    }

    private void powerBoost(CommandSender sender, String[] args) {
        if (args.length < 4) {
            plugin.msg(sender, "&cUsage: /fa powerboost <set|add> <faction> <amount>");
            return;
        }
        Faction faction = service.byName(args[2]);
        if (faction == null) {
            plugin.msg(sender, "&cFaction not found.");
            return;
        }
        double amount = parseDouble(args[3], Double.NaN);
        if (Double.isNaN(amount)) {
            plugin.msg(sender, "&cInvalid amount.");
            return;
        }
        if (args[1].equalsIgnoreCase("set")) faction.powerBoost(amount);
        else if (args[1].equalsIgnoreCase("add")) faction.powerBoost(faction.powerBoost() + amount);
        else {
            plugin.msg(sender, "&cUse set or add.");
            return;
        }
        service.save();
        plugin.msg(sender, "&a" + faction.name() + " power boost: &f" + String.format(Locale.US, "%.1f", faction.powerBoost()));
    }

    private void permanentPower(CommandSender sender, String[] args) {
        if (args.length < 3) {
            plugin.msg(sender, "&cUsage: /fa permanentpower <set|clear> <faction> [amount]");
            return;
        }
        Faction faction = service.byName(args[2]);
        if (faction == null) {
            plugin.msg(sender, "&cFaction not found.");
            return;
        }
        if (args[1].equalsIgnoreCase("clear")) {
            faction.permanentPower(null);
            service.save();
            plugin.msg(sender, "&aPermanent power cleared for " + faction.name() + ".");
            return;
        }
        if (!args[1].equalsIgnoreCase("set") || args.length < 4) {
            plugin.msg(sender, "&cUsage: /fa permanentpower <set|clear> <faction> [amount]");
            return;
        }
        double amount = parseDouble(args[3], Double.NaN);
        if (Double.isNaN(amount) || amount < 0) {
            plugin.msg(sender, "&cInvalid amount.");
            return;
        }
        faction.permanentPower(amount);
        service.save();
        plugin.msg(sender, "&aPermanent faction power set to &f" + String.format(Locale.US, "%.1f", amount) + "&a.");
    }

    private void disband(CommandSender sender, String[] args) {
        Faction faction = args.length > 1 ? service.byName(args[1]) : null;
        respond(sender, faction == null ? FactionService.Result.fail("Faction not found.") : service.forceDisband(faction));
    }

    private void forceJoin(CommandSender sender, String[] args) {
        if (args.length < 3) {
            plugin.msg(sender, "&cUsage: /fa forcejoin <player> <faction>");
            return;
        }
        Player target = Bukkit.getPlayerExact(args[1]);
        Faction faction = service.byName(args[2]);
        if (target == null) {
            plugin.msg(sender, "&cTarget must be online.");
            return;
        }
        if (faction == null) {
            plugin.msg(sender, "&cFaction not found.");
            return;
        }
        Faction current = service.of(target.getUniqueId());
        if (current != null) {
            FactionRank oldRank = current.rank(target.getUniqueId());
            if (oldRank == FactionRank.LEADER && current.members().size() > 1) current.members().put(target.getUniqueId(), FactionRank.RECRUIT);
            FactionService.Result left = service.leave(target);
            if (!left.success()) {
                plugin.msg(sender, "&cCould not remove player from current faction: " + left.message());
                return;
            }
        }
        boolean wasOpen = faction.open();
        faction.open(true);
        FactionService.Result joined = service.join(target, faction.name());
        faction.open(wasOpen);
        service.save();
        respond(sender, joined.success() ? FactionService.Result.ok("Force-joined " + target.getName() + " to " + faction.name() + ".") : joined);
    }

    private void forceKick(CommandSender sender, String[] args) {
        if (args.length < 2) {
            plugin.msg(sender, "&cUsage: /fa forcekick <player>");
            return;
        }
        Player target = Bukkit.getPlayerExact(args[1]);
        if (target == null) {
            plugin.msg(sender, "&cTarget must be online.");
            return;
        }
        Faction faction = service.of(target.getUniqueId());
        if (faction == null) {
            plugin.msg(sender, "&cThat player is not in a faction.");
            return;
        }
        if (faction.rank(target.getUniqueId()) == FactionRank.LEADER && faction.members().size() > 1) faction.members().put(target.getUniqueId(), FactionRank.RECRUIT);
        FactionService.Result result = service.leave(target);
        respond(sender, result.success() ? FactionService.Result.ok("Force-kicked " + target.getName() + " from their faction.") : result);
    }

    private void forceRole(CommandSender sender, String[] args) {
        if (args.length < 3) {
            plugin.msg(sender, "&cUsage: /fa forcerole <player> <recruit|member|officer|coleader|leader>");
            return;
        }
        OfflinePlayer target = offline(args[1]);
        if (target == null) {
            plugin.msg(sender, "&cPlayer not found.");
            return;
        }
        Faction faction = service.of(target.getUniqueId());
        if (faction == null) {
            plugin.msg(sender, "&cThat player is not in a faction.");
            return;
        }
        FactionRank rank;
        try {
            rank = FactionRank.valueOf(args[2].toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            plugin.msg(sender, "&cUnknown rank.");
            return;
        }
        if (rank == FactionRank.LEADER) {
            for (Map.Entry<UUID, FactionRank> entry : faction.members().entrySet()) {
                if (entry.getValue() == FactionRank.LEADER && !entry.getKey().equals(target.getUniqueId())) entry.setValue(FactionRank.COLEADER);
            }
        }
        faction.members().put(target.getUniqueId(), rank);
        service.save();
        plugin.msg(sender, "&aSet " + name(target) + " to " + rank + " in " + faction.name() + ".");
    }

    private void forceHome(CommandSender sender, String[] args) {
        if (args.length < 3) {
            plugin.msg(sender, "&cUsage: /fa forcehome <player> <faction>");
            return;
        }
        Player target = Bukkit.getPlayerExact(args[1]);
        Faction faction = service.byName(args[2]);
        if (target == null) {
            plugin.msg(sender, "&cTarget must be online.");
            return;
        }
        if (faction == null || faction.home() == null) {
            plugin.msg(sender, "&cFaction not found or has no home.");
            return;
        }
        target.teleportAsync(faction.home()).thenAccept(success -> {
            if (success) plugin.msg(sender, "&aSent " + target.getName() + " to " + faction.name() + " home.");
            else plugin.msg(sender, "&cTeleport failed.");
        });
    }

    private void rename(CommandSender sender, String[] args) {
        if (args.length < 3) {
            plugin.msg(sender, "&cUsage: /fa rename <faction> <newName>");
            return;
        }
        Faction faction = service.byName(args[1]);
        String name = args[2];
        if (faction == null) {
            plugin.msg(sender, "&cFaction not found.");
            return;
        }
        if (!name.matches("[A-Za-z0-9_]{3,16}")) {
            plugin.msg(sender, "&cFaction names must be 3-16 letters, numbers or underscores.");
            return;
        }
        Faction existing = service.byName(name);
        if (existing != null && existing != faction) {
            plugin.msg(sender, "&cThat name is already taken.");
            return;
        }
        String old = faction.name();
        faction.name(name);
        service.save();
        plugin.msg(sender, "&aRenamed " + old + " to " + name + ".");
    }

    private void specialClaim(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player) || args.length < 2) {
            plugin.msg(sender, "&cUsage: /fa claim <safezone|warzone|wilderness>");
            return;
        }
        try {
            respond(sender, service.setSpecialClaim(player.getLocation(), TerritoryType.valueOf(args[1].toUpperCase(Locale.ROOT))));
        } catch (IllegalArgumentException ex) {
            plugin.msg(sender, "&cUse safezone, warzone or wilderness.");
        }
    }

    private void grace(CommandSender sender, String[] args) {
        if (args.length < 2 || args[1].equalsIgnoreCase("status")) {
            plugin.msg(sender, "&eGrace active: &f" + service.graceActive());
            return;
        }
        if (args[1].equalsIgnoreCase("stop")) {
            respond(sender, service.stopGrace());
            return;
        }
        if (args[1].equalsIgnoreCase("start") && args.length > 2) {
            long minutes = parseLong(args[2], -1);
            if (minutes < 1) plugin.msg(sender, "&cMinutes must be a positive number.");
            else respond(sender, service.startGrace(minutes));
            return;
        }
        plugin.msg(sender, "&cUsage: /fa grace <status|start <minutes>|stop>");
    }

    private void toggleFactionFlag(CommandSender sender, String[] args, Flag flag) {
        Faction faction = args.length > 1 ? service.byName(args[1]) : null;
        if (faction == null) {
            plugin.msg(sender, "&cFaction not found.");
            return;
        }
        switch (flag) {
            case PEACEFUL -> faction.peaceful(!faction.peaceful());
            case PERMANENT -> faction.permanent(!faction.permanent());
            case RENT_EXEMPT -> faction.rentExempt(!faction.rentExempt());
        }
        service.save();
        boolean state = switch (flag) {
            case PEACEFUL -> faction.peaceful();
            case PERMANENT -> faction.permanent();
            case RENT_EXEMPT -> faction.rentExempt();
        };
        plugin.msg(sender, "&a" + faction.name() + " " + flag.label + ": &f" + state);
    }

    private void factionMoney(CommandSender sender, String[] args) {
        if (args.length < 4) {
            plugin.msg(sender, "&cUsage: /fa money <set|add> <faction> <amount>");
            return;
        }
        Faction faction = service.byName(args[2]);
        if (faction == null) {
            plugin.msg(sender, "&cFaction not found.");
            return;
        }
        double amount = parseDouble(args[3], Double.NaN);
        if (Double.isNaN(amount)) {
            plugin.msg(sender, "&cInvalid amount.");
            return;
        }
        if (args[1].equalsIgnoreCase("set")) faction.bankBalance(amount);
        else if (args[1].equalsIgnoreCase("add")) faction.bankBalance(faction.bankBalance() + amount);
        else {
            plugin.msg(sender, "&cUse set or add.");
            return;
        }
        service.save();
        plugin.msg(sender, "&aFaction bank updated to " + plugin.economy().format(faction.bankBalance()) + ".");
    }

    private void factionTnt(CommandSender sender, String[] args) {
        if (args.length < 4) {
            plugin.msg(sender, "&cUsage: /fa tnt <set|add> <faction> <amount>");
            return;
        }
        Faction faction = service.byName(args[2]);
        if (faction == null) {
            plugin.msg(sender, "&cFaction not found.");
            return;
        }
        int amount = parseInt(args[3], Integer.MIN_VALUE);
        if (amount == Integer.MIN_VALUE) {
            plugin.msg(sender, "&cInvalid amount.");
            return;
        }
        if (args[1].equalsIgnoreCase("set")) faction.tntBalance(amount);
        else if (args[1].equalsIgnoreCase("add")) faction.tntBalance(faction.tntBalance() + amount);
        else {
            plugin.msg(sender, "&cUse set or add.");
            return;
        }
        service.save();
        plugin.msg(sender, "&aFaction TNT updated to " + faction.tntBalance() + ".");
    }

    private void shield(CommandSender sender, String[] args) {
        if (args.length < 3) {
            plugin.msg(sender, "&cUsage: /fa shield <clear|reset> <faction>");
            return;
        }
        Faction faction = service.byName(args[2]);
        if (faction == null) {
            plugin.msg(sender, "&cFaction not found.");
            return;
        }
        if (args[1].equalsIgnoreCase("clear")) faction.shieldUntil(0L);
        else if (args[1].equalsIgnoreCase("reset")) {
            faction.shieldUntil(0L);
            faction.shieldCooldownUntil(0L);
        } else {
            plugin.msg(sender, "&cUse clear or reset.");
            return;
        }
        service.save();
        plugin.msg(sender, "&aShield state updated for " + faction.name() + ".");
    }

    private void upgrade(CommandSender sender, String[] args) {
        if (args.length < 5) {
            plugin.msg(sender, "&cUsage: /fa upgrade <set|add> <faction> <upgrade> <level>");
            return;
        }
        Faction faction = service.byName(args[2]);
        if (faction == null) {
            plugin.msg(sender, "&cFaction not found.");
            return;
        }
        UpgradeType type;
        try {
            type = UpgradeType.valueOf(args[3].toUpperCase(Locale.ROOT).replace('-', '_'));
        } catch (IllegalArgumentException ex) {
            plugin.msg(sender, "&cUnknown upgrade. Use tab completion to see valid upgrades.");
            return;
        }
        int amount = parseInt(args[4], Integer.MIN_VALUE);
        if (amount == Integer.MIN_VALUE) {
            plugin.msg(sender, "&cInvalid level.");
            return;
        }
        int level;
        if (args[1].equalsIgnoreCase("set")) level = amount;
        else if (args[1].equalsIgnoreCase("add")) level = faction.upgrade(type) + amount;
        else {
            plugin.msg(sender, "&cUse set or add.");
            return;
        }
        level = Math.max(0, Math.min(type.maxLevel(), level));
        if (level == 0) faction.upgrades().remove(type); else faction.upgrades().put(type, level);
        service.save();
        plugin.msg(sender, "&a" + faction.name() + " " + type.display() + " is now level " + level + "/" + type.maxLevel() + ".");
    }

    private void info(CommandSender sender, String[] args) {
        Faction faction = args.length > 1 ? service.byName(args[1]) : null;
        if (faction == null) {
            plugin.msg(sender, "&cUsage: /fa info <faction>");
            return;
        }
        plugin.msg(sender, "&5&m--------------------------------");
        plugin.msg(sender, "&d" + faction.name() + " &7(&f" + faction.id() + "&7)");
        plugin.msg(sender, "&7Members: &f" + faction.members().size() + " &7Claims: &f" + faction.claims().size() + "/" + service.maxClaims(faction));
        plugin.msg(sender, "&7Power: &f" + String.format(Locale.US, "%.1f", service.factionPower(faction)) + (service.raidable(faction) ? " &cRAIDABLE" : " &aPROTECTED"));
        plugin.msg(sender, "&7Power boost: &f" + String.format(Locale.US, "%.1f", faction.powerBoost()) + " &7Permanent power: &f" + (faction.permanentPower() == null ? "none" : String.format(Locale.US, "%.1f", faction.permanentPower())));
        plugin.msg(sender, "&7Bank: &f" + plugin.economy().format(faction.bankBalance()) + " &7TNT: &f" + faction.tntBalance());
        plugin.msg(sender, "&7Peaceful: &f" + faction.peaceful() + " &7Permanent: &f" + faction.permanent() + " &7Rent exempt: &f" + faction.rentExempt());
        plugin.msg(sender, "&7Shield active: &f" + service.shielded(faction));
    }

    private void respond(CommandSender sender, FactionService.Result result) {
        plugin.msg(sender, (result.success() ? "&a" : "&c") + result.message());
    }

    private void help(CommandSender sender, int page) {
        int p = Math.max(1, Math.min(3, page));
        plugin.msg(sender, "&5&m------------- &d/fa Help " + p + "/3 &5&m-------------");
        if (p == 1) {
            plugin.msg(sender, "&d/fa bypass &7| chatspy &7| reload &7| save");
            plugin.msg(sender, "&d/fa info <faction> &7| disband <faction> &7| rename <faction> <name>");
            plugin.msg(sender, "&d/fa forcejoin <player> <faction> &7| forcekick <player>");
            plugin.msg(sender, "&d/fa forcerole <player> <rank> &7| forcehome <player> <faction>");
        } else if (p == 2) {
            plugin.msg(sender, "&d/fa power <set|add> <player> <amount>");
            plugin.msg(sender, "&d/fa powerboost <set|add> <faction> <amount>");
            plugin.msg(sender, "&d/fa permanentpower <set|clear> <faction> [amount]");
            plugin.msg(sender, "&d/fa money <set|add> <faction> <amount>");
            plugin.msg(sender, "&d/fa tnt <set|add> <faction> <amount>");
            plugin.msg(sender, "&d/fa upgrade <set|add> <faction> <upgrade> <level>");
        } else {
            plugin.msg(sender, "&d/fa claim <safezone|warzone|wilderness>");
            plugin.msg(sender, "&d/fa grace <status|start <minutes>|stop>");
            plugin.msg(sender, "&d/fa peaceful <faction> &7| permanent <faction> &7| rentexempt <faction>");
            plugin.msg(sender, "&d/fa shield <clear|reset> <faction>");
        }
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!sender.hasPermission("mirafactions.admin")) return List.of();
        String current = args.length == 0 ? "" : args[args.length - 1].toLowerCase(Locale.ROOT);
        if (args.length == 1) return filter(List.of("help","reload","save","bypass","chatspy","info","power","powerboost","permanentpower","disband","forcejoin","forcekick","forcerole","forcehome","rename","claim","grace","peaceful","permanent","rentexempt","money","tnt","shield","upgrade"), current);

        String root = args[0].toLowerCase(Locale.ROOT);
        if (args.length == 2) {
            if (Set.of("disband","info","peaceful","permanent","rentexempt","rename").contains(root)) return factions(current);
            if (Set.of("forcejoin","forcekick","forcerole","forcehome").contains(root)) return players(current);
            if (Set.of("power","powerboost","money","tnt","upgrade").contains(root)) return filter(List.of("set","add"), current);
            if (root.equals("permanentpower")) return filter(List.of("set","clear"), current);
            if (root.equals("claim")) return filter(List.of("safezone","warzone","wilderness"), current);
            if (root.equals("grace")) return filter(List.of("status","start","stop"), current);
            if (root.equals("shield")) return filter(List.of("clear","reset"), current);
        }
        if (args.length == 3) {
            if (root.equals("forcejoin") || root.equals("forcehome")) return factions(current);
            if (root.equals("forcerole")) return filter(Arrays.stream(FactionRank.values()).map(v -> v.name().toLowerCase(Locale.ROOT)).toList(), current);
            if (Set.of("powerboost","permanentpower","money","tnt","upgrade").contains(root)) return factions(current);
            if (root.equals("power")) return players(current);
            if (root.equals("shield")) return factions(current);
        }
        if (args.length == 4 && root.equals("upgrade")) return filter(Arrays.stream(UpgradeType.values()).map(v -> v.name().toLowerCase(Locale.ROOT)).toList(), current);
        return List.of();
    }

    private List<String> factions(String current) {
        return filter(service.all().stream().map(Faction::name).toList(), current);
    }

    private List<String> players(String current) {
        return filter(Bukkit.getOnlinePlayers().stream().map(Player::getName).toList(), current);
    }

    private List<String> filter(Collection<String> values, String current) {
        return values.stream().filter(v -> v.toLowerCase(Locale.ROOT).startsWith(current)).sorted(String.CASE_INSENSITIVE_ORDER).toList();
    }

    private OfflinePlayer offline(String name) {
        Player online = Bukkit.getPlayerExact(name);
        if (online != null) return online;
        @SuppressWarnings("deprecation") OfflinePlayer offline = Bukkit.getOfflinePlayer(name);
        return offline.hasPlayedBefore() ? offline : null;
    }

    private String name(OfflinePlayer player) {
        return Optional.ofNullable(player.getName()).orElse(player.getUniqueId().toString());
    }

    private int parseInt(String raw, int fallback) {
        try { return Integer.parseInt(raw); } catch (Exception ignored) { return fallback; }
    }

    private long parseLong(String raw, long fallback) {
        try { return Long.parseLong(raw); } catch (Exception ignored) { return fallback; }
    }

    private double parseDouble(String raw, double fallback) {
        try { return Double.parseDouble(raw); } catch (Exception ignored) { return fallback; }
    }

    private enum Flag {
        PEACEFUL("peaceful"), PERMANENT("permanent"), RENT_EXEMPT("rent exempt");
        private final String label;
        Flag(String label) { this.label = label; }
    }
}
