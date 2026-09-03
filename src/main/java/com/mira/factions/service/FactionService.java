package com.mira.factions.service;

import com.mira.factions.MiraFactionsPlugin;
import com.mira.factions.model.*;
import org.bukkit.*;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public final class FactionService {
    private final MiraFactionsPlugin plugin;
    private final File file;
    private final Map<UUID, Faction> factions = new LinkedHashMap<>();
    private final Map<UUID, UUID> playerFaction = new HashMap<>();
    private final Map<String, UUID> claimOwner = new HashMap<>();
    private final Map<UUID, Double> power = new ConcurrentHashMap<>();
    private final Map<UUID, Boolean> factionChat = new ConcurrentHashMap<>();
    private final Map<UUID, Long> homeCooldown = new ConcurrentHashMap<>();

    public FactionService(MiraFactionsPlugin plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "factions.yml");
        load();
    }

    public Collection<Faction> all() { return Collections.unmodifiableCollection(factions.values()); }
    public Faction byId(UUID id) { return factions.get(id); }
    public Faction of(UUID player) {
        UUID id = playerFaction.get(player);
        return id == null ? null : factions.get(id);
    }
    public Faction byName(String name) {
        return factions.values().stream().filter(f -> f.name().equalsIgnoreCase(name)).findFirst().orElse(null);
    }
    public double power(UUID player) { return power.getOrDefault(player, startingPower()); }
    public double factionPower(Faction faction) {
        return faction.members().keySet().stream().mapToDouble(uuid -> Math.max(0.0, power(uuid))).sum();
    }
    public int maxClaims(Faction faction) { return Math.max(0, (int) Math.floor(factionPower(faction))); }
    public boolean factionChat(UUID player) { return factionChat.getOrDefault(player, false); }
    public void toggleFactionChat(UUID player) { factionChat.put(player, !factionChat(player)); }

    public Result create(Player player, String name) {
        if (of(player.getUniqueId()) != null) return Result.fail("You are already in a faction.");
        String clean = name == null ? "" : name.trim();
        if (!clean.matches("[A-Za-z0-9_]{3,16}")) return Result.fail("Faction names must be 3-16 letters, numbers or underscores.");
        if (byName(clean) != null) return Result.fail("That faction name is already taken.");

        Faction faction = new Faction(UUID.randomUUID(), clean);
        faction.members().put(player.getUniqueId(), FactionRank.LEADER);
        factions.put(faction.id(), faction);
        playerFaction.put(player.getUniqueId(), faction.id());
        power.putIfAbsent(player.getUniqueId(), startingPower());
        save();
        return Result.ok("Created faction " + clean + ".");
    }

    public Result invite(Player actor, Player target) {
        Faction faction = of(actor.getUniqueId());
        if (faction == null) return Result.fail("You are not in a faction.");
        if (!rankAtLeast(actor, FactionRank.OFFICER)) return Result.fail("Officer rank or higher required.");
        if (of(target.getUniqueId()) != null) return Result.fail("That player is already in a faction.");
        faction.invites().add(target.getUniqueId());
        save();
        plugin.msg(target, "&eYou were invited to &f" + faction.name() + "&e. Use &f/f join " + faction.name());
        return Result.ok("Invited " + target.getName() + ".");
    }

    public Result join(Player player, String factionName) {
        if (of(player.getUniqueId()) != null) return Result.fail("You are already in a faction.");
        Faction faction = byName(factionName);
        if (faction == null) return Result.fail("Faction not found.");
        if (!faction.invites().remove(player.getUniqueId())) return Result.fail("You do not have an invite to that faction.");
        faction.members().put(player.getUniqueId(), FactionRank.MEMBER);
        playerFaction.put(player.getUniqueId(), faction.id());
        power.putIfAbsent(player.getUniqueId(), startingPower());
        save();
        return Result.ok("Joined " + faction.name() + ".");
    }

    public Result leave(Player player) {
        Faction faction = of(player.getUniqueId());
        if (faction == null) return Result.fail("You are not in a faction.");
        if (faction.rank(player.getUniqueId()) == FactionRank.LEADER && faction.members().size() > 1) {
            return Result.fail("Transfer leadership or disband first.");
        }
        if (faction.members().size() == 1) return disband(player);
        faction.members().remove(player.getUniqueId());
        playerFaction.remove(player.getUniqueId());
        factionChat.remove(player.getUniqueId());
        save();
        return Result.ok("You left " + faction.name() + ".");
    }

    public Result disband(Player player) {
        Faction faction = of(player.getUniqueId());
        if (faction == null) return Result.fail("You are not in a faction.");
        if (faction.rank(player.getUniqueId()) != FactionRank.LEADER) return Result.fail("Only the leader can disband.");
        for (UUID member : faction.members().keySet()) {
            playerFaction.remove(member);
            factionChat.remove(member);
        }
        for (String claim : faction.claims()) claimOwner.remove(claim);
        factions.remove(faction.id());
        for (Faction other : factions.values()) {
            other.relations().remove(faction.id());
            other.allyRequests().remove(faction.id());
        }
        save();
        return Result.ok("Faction " + faction.name() + " disbanded.");
    }

    public Result kick(Player actor, Player target) {
        Faction faction = of(actor.getUniqueId());
        if (faction == null || of(target.getUniqueId()) != faction) return Result.fail("That player is not in your faction.");
        if (actor.equals(target)) return Result.fail("Use /f leave instead.");
        FactionRank actorRank = faction.rank(actor.getUniqueId());
        FactionRank targetRank = faction.rank(target.getUniqueId());
        if (actorRank == null || targetRank == null || !actorRank.atLeast(FactionRank.OFFICER) || actorRank.weight() <= targetRank.weight()) {
            return Result.fail("You cannot kick that member.");
        }
        faction.members().remove(target.getUniqueId());
        playerFaction.remove(target.getUniqueId());
        factionChat.remove(target.getUniqueId());
        save();
        return Result.ok("Kicked " + target.getName() + ".");
    }

    public Result promote(Player actor, Player target) {
        Faction faction = of(actor.getUniqueId());
        if (faction == null || of(target.getUniqueId()) != faction) return Result.fail("That player is not in your faction.");
        if (!rankAtLeast(actor, FactionRank.COLEADER)) return Result.fail("Coleader rank or higher required.");
        FactionRank current = faction.rank(target.getUniqueId());
        FactionRank next = switch (current) {
            case MEMBER -> FactionRank.OFFICER;
            case OFFICER -> FactionRank.COLEADER;
            default -> null;
        };
        if (next == null) return Result.fail("That member cannot be promoted further.");
        faction.members().put(target.getUniqueId(), next);
        save();
        return Result.ok("Promoted " + target.getName() + " to " + next + ".");
    }

    public Result demote(Player actor, Player target) {
        Faction faction = of(actor.getUniqueId());
        if (faction == null || of(target.getUniqueId()) != faction) return Result.fail("That player is not in your faction.");
        if (!rankAtLeast(actor, FactionRank.COLEADER)) return Result.fail("Coleader rank or higher required.");
        FactionRank current = faction.rank(target.getUniqueId());
        if (current == null || current == FactionRank.LEADER || faction.rank(actor.getUniqueId()).weight() <= current.weight()) {
            return Result.fail("You cannot demote that member.");
        }
        FactionRank next = switch (current) {
            case COLEADER -> FactionRank.OFFICER;
            case OFFICER -> FactionRank.MEMBER;
            default -> null;
        };
        if (next == null) return Result.fail("That member cannot be demoted further.");
        faction.members().put(target.getUniqueId(), next);
        save();
        return Result.ok("Demoted " + target.getName() + " to " + next + ".");
    }

    public Result transfer(Player actor, Player target) {
        Faction faction = of(actor.getUniqueId());
        if (faction == null || of(target.getUniqueId()) != faction) return Result.fail("That player is not in your faction.");
        if (faction.rank(actor.getUniqueId()) != FactionRank.LEADER) return Result.fail("Only the leader can transfer leadership.");
        if (actor.equals(target)) return Result.fail("You are already the leader.");
        faction.members().put(actor.getUniqueId(), FactionRank.COLEADER);
        faction.members().put(target.getUniqueId(), FactionRank.LEADER);
        save();
        return Result.ok("Transferred leadership to " + target.getName() + ".");
    }

    public Result claim(Player player) {
        Faction faction = of(player.getUniqueId());
        if (faction == null) return Result.fail("You are not in a faction.");
        if (!rankAtLeast(player, FactionRank.OFFICER)) return Result.fail("Officer rank or higher required.");
        if (!claimsAllowed(player.getWorld())) return Result.fail("Claiming is disabled in this world.");
        String key = claimKey(player.getLocation());
        UUID owner = claimOwner.get(key);
        if (owner != null) return Result.fail(owner.equals(faction.id()) ? "Your faction already owns this chunk." : "This chunk is already claimed.");
        if (faction.claims().size() >= maxClaims(faction)) return Result.fail("Your faction needs more power to claim more land.");
        faction.claims().add(key);
        claimOwner.put(key, faction.id());
        save();
        return Result.ok("Claimed this chunk for " + faction.name() + ".");
    }

    public Result unclaim(Player player) {
        Faction faction = of(player.getUniqueId());
        if (faction == null) return Result.fail("You are not in a faction.");
        if (!rankAtLeast(player, FactionRank.OFFICER)) return Result.fail("Officer rank or higher required.");
        String key = claimKey(player.getLocation());
        if (!faction.id().equals(claimOwner.get(key))) return Result.fail("Your faction does not own this chunk.");
        faction.claims().remove(key);
        claimOwner.remove(key);
        save();
        return Result.ok("Unclaimed this chunk.");
    }

    public Faction owner(Location location) {
        UUID id = claimOwner.get(claimKey(location));
        return id == null ? null : factions.get(id);
    }

    public boolean canBuild(Player player, Location location) {
        Faction owner = owner(location);
        return owner == null || owner.isMember(player.getUniqueId()) || player.hasPermission("mirafactions.bypass");
    }

    public Result setHome(Player player) {
        Faction faction = of(player.getUniqueId());
        if (faction == null) return Result.fail("You are not in a faction.");
        if (!rankAtLeast(player, FactionRank.OFFICER)) return Result.fail("Officer rank or higher required.");
        if (owner(player.getLocation()) != faction) return Result.fail("Faction home must be inside your claimed land.");
        faction.home(player.getLocation().clone());
        save();
        return Result.ok("Faction home set.");
    }

    public Result home(Player player) {
        Faction faction = of(player.getUniqueId());
        if (faction == null) return Result.fail("You are not in a faction.");
        if (faction.home() == null) return Result.fail("Your faction has no home.");
        long now = System.currentTimeMillis();
        long cooldown = homeCooldown.getOrDefault(player.getUniqueId(), 0L);
        if (cooldown > now) return Result.fail("Faction home is on cooldown for " + Math.max(1, (cooldown - now + 999) / 1000) + "s.");

        int warmup = Math.max(0, plugin.getConfig().getInt("home.warmup-seconds", 5));
        Location start = player.getLocation().clone();
        plugin.msg(player, "&eTeleporting to faction home in &f" + warmup + "&e seconds. Do not move.");
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (!player.isOnline()) return;
            if (player.getWorld() != start.getWorld() || player.getLocation().distanceSquared(start) > 0.09) {
                plugin.msg(player, "&cFaction home teleport cancelled because you moved.");
                return;
            }
            player.teleportAsync(faction.home());
            homeCooldown.put(player.getUniqueId(), System.currentTimeMillis() + plugin.getConfig().getLong("home.cooldown-seconds", 30) * 1000L);
        }, warmup * 20L);
        return Result.ok("");
    }

    public Result relation(Player actor, String otherName, Relation wanted) {
        Faction mine = of(actor.getUniqueId());
        Faction other = byName(otherName);
        if (mine == null) return Result.fail("You are not in a faction.");
        if (!rankAtLeast(actor, FactionRank.COLEADER)) return Result.fail("Coleader rank or higher required.");
        if (other == null || other == mine) return Result.fail("Faction not found.");

        if (wanted == Relation.ENEMY) {
            mine.relations().put(other.id(), Relation.ENEMY);
            other.relations().put(mine.id(), Relation.ENEMY);
            mine.allyRequests().remove(other.id());
            other.allyRequests().remove(mine.id());
            save();
            return Result.ok("You are now enemies with " + other.name() + ".");
        }
        if (wanted == Relation.NEUTRAL) {
            mine.relations().remove(other.id());
            other.relations().remove(mine.id());
            mine.allyRequests().remove(other.id());
            other.allyRequests().remove(mine.id());
            save();
            return Result.ok("Relations with " + other.name() + " are now neutral.");
        }
        if (other.allyRequests().remove(mine.id())) {
            mine.relations().put(other.id(), Relation.ALLY);
            other.relations().put(mine.id(), Relation.ALLY);
            save();
            return Result.ok("Alliance formed with " + other.name() + ".");
        }
        mine.allyRequests().add(other.id());
        save();
        return Result.ok("Alliance request sent to " + other.name() + ".");
    }

    public Relation relation(Faction first, Faction second) {
        if (first == null || second == null) return Relation.NEUTRAL;
        if (first == second) return Relation.ALLY;
        return first.relations().getOrDefault(second.id(), Relation.NEUTRAL);
    }

    public void death(Player player) {
        setPower(player.getUniqueId(), power(player.getUniqueId()) - plugin.getConfig().getDouble("power.death-loss", 2.0));
    }

    public void regeneratePower() {
        double add = plugin.getConfig().getDouble("power.regen-amount", 1.0);
        double maximum = plugin.getConfig().getDouble("power.maximum", 10.0);
        boolean changed = false;
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (of(player.getUniqueId()) == null) continue;
            double before = power(player.getUniqueId());
            double after = Math.min(maximum, before + add);
            if (after != before) {
                power.put(player.getUniqueId(), after);
                changed = true;
            }
        }
        if (changed) save();
    }

    private void setPower(UUID uuid, double value) {
        double minimum = plugin.getConfig().getDouble("power.minimum", -10.0);
        double maximum = plugin.getConfig().getDouble("power.maximum", 10.0);
        power.put(uuid, Math.max(minimum, Math.min(maximum, value)));
        save();
    }

    private double startingPower() { return plugin.getConfig().getDouble("power.starting", 10.0); }

    public boolean rankAtLeast(Player player, FactionRank rank) {
        Faction faction = of(player.getUniqueId());
        return faction != null && faction.rank(player.getUniqueId()) != null && faction.rank(player.getUniqueId()).atLeast(rank);
    }

    public String claimKey(Location location) {
        return location.getWorld().getUID() + ":" + location.getChunk().getX() + ":" + location.getChunk().getZ();
    }

    private boolean claimsAllowed(World world) {
        List<String> worlds = plugin.getConfig().getStringList("claims.worlds");
        return worlds.isEmpty() || worlds.stream().anyMatch(name -> name.equalsIgnoreCase(world.getName()));
    }

    public void save() {
        YamlConfiguration yaml = new YamlConfiguration();
        for (Map.Entry<UUID, Double> entry : power.entrySet()) yaml.set("power." + entry.getKey(), entry.getValue());
        for (Faction faction : factions.values()) {
            String base = "factions." + faction.id();
            yaml.set(base + ".name", faction.name());
            for (Map.Entry<UUID, FactionRank> member : faction.members().entrySet()) {
                yaml.set(base + ".members." + member.getKey(), member.getValue().name());
            }
            yaml.set(base + ".invites", faction.invites().stream().map(UUID::toString).toList());
            yaml.set(base + ".claims", new ArrayList<>(faction.claims()));
            for (Map.Entry<UUID, Relation> relation : faction.relations().entrySet()) {
                yaml.set(base + ".relations." + relation.getKey(), relation.getValue().name());
            }
            yaml.set(base + ".allyRequests", faction.allyRequests().stream().map(UUID::toString).toList());
            if (faction.home() != null) {
                yaml.set(base + ".home.world", faction.home().getWorld().getName());
                yaml.set(base + ".home.x", faction.home().getX());
                yaml.set(base + ".home.y", faction.home().getY());
                yaml.set(base + ".home.z", faction.home().getZ());
                yaml.set(base + ".home.yaw", faction.home().getYaw());
                yaml.set(base + ".home.pitch", faction.home().getPitch());
            }
        }
        try {
            file.getParentFile().mkdirs();
            yaml.save(file);
        } catch (IOException exception) {
            plugin.getLogger().severe("Failed to save factions.yml: " + exception.getMessage());
        }
    }

    private void load() {
        if (!file.exists()) return;
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
        ConfigurationSection powerSection = yaml.getConfigurationSection("power");
        if (powerSection != null) {
            for (String key : powerSection.getKeys(false)) {
                try { power.put(UUID.fromString(key), powerSection.getDouble(key)); } catch (IllegalArgumentException ignored) { }
            }
        }
        ConfigurationSection factionSection = yaml.getConfigurationSection("factions");
        if (factionSection == null) return;

        for (String idText : factionSection.getKeys(false)) {
            try {
                UUID id = UUID.fromString(idText);
                String base = "factions." + idText;
                Faction faction = new Faction(id, yaml.getString(base + ".name", "Faction"));
                ConfigurationSection members = yaml.getConfigurationSection(base + ".members");
                if (members != null) {
                    for (String uuidText : members.getKeys(false)) {
                        UUID uuid = UUID.fromString(uuidText);
                        FactionRank rank = FactionRank.valueOf(members.getString(uuidText, "MEMBER"));
                        faction.members().put(uuid, rank);
                        playerFaction.put(uuid, id);
                    }
                }
                for (String uuidText : yaml.getStringList(base + ".invites")) faction.invites().add(UUID.fromString(uuidText));
                for (String claim : yaml.getStringList(base + ".claims")) {
                    faction.claims().add(claim);
                    claimOwner.put(claim, id);
                }
                ConfigurationSection relations = yaml.getConfigurationSection(base + ".relations");
                if (relations != null) {
                    for (String uuidText : relations.getKeys(false)) {
                        faction.relations().put(UUID.fromString(uuidText), Relation.valueOf(relations.getString(uuidText, "NEUTRAL")));
                    }
                }
                for (String uuidText : yaml.getStringList(base + ".allyRequests")) faction.allyRequests().add(UUID.fromString(uuidText));
                String worldName = yaml.getString(base + ".home.world");
                if (worldName != null) {
                    World world = Bukkit.getWorld(worldName);
                    if (world != null) {
                        faction.home(new Location(world,
                                yaml.getDouble(base + ".home.x"),
                                yaml.getDouble(base + ".home.y"),
                                yaml.getDouble(base + ".home.z"),
                                (float) yaml.getDouble(base + ".home.yaw"),
                                (float) yaml.getDouble(base + ".home.pitch")));
                    }
                }
                factions.put(id, faction);
            } catch (Exception exception) {
                plugin.getLogger().warning("Skipped invalid faction record " + idText + ": " + exception.getMessage());
            }
        }
    }

    public record Result(boolean success, String message) {
        public static Result ok(String message) { return new Result(true, message); }
        public static Result fail(String message) { return new Result(false, message); }
    }
}
