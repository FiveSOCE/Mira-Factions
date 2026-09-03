package com.mira.factions.service;

import com.mira.factions.MiraFactionsPlugin;
import com.mira.factions.model.*;
import net.kyori.adventure.text.Component;
import org.bukkit.*;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public final class FactionService {
    private final MiraFactionsPlugin plugin;
    private final EconomyHook economy;
    private final File file;

    private final Map<UUID, Faction> factions = new LinkedHashMap<>();
    private final Map<UUID, UUID> playerFaction = new HashMap<>();
    private final Map<String, UUID> claimOwner = new HashMap<>();
    private final Set<String> safeZoneClaims = new HashSet<>();
    private final Set<String> warZoneClaims = new HashSet<>();
    private final Map<UUID, Double> power = new ConcurrentHashMap<>();
    private final Map<UUID, ChatMode> chatModes = new ConcurrentHashMap<>();
    private final Set<UUID> autoClaim = ConcurrentHashMap.newKeySet();
    private final Set<UUID> seeChunk = ConcurrentHashMap.newKeySet();
    private final Set<UUID> factionFlight = ConcurrentHashMap.newKeySet();
    private final Set<UUID> bypass = ConcurrentHashMap.newKeySet();
    private final Map<UUID, Long> homeCooldown = new ConcurrentHashMap<>();
    private final Map<UUID, Long> warpCooldown = new ConcurrentHashMap<>();
    private long graceUntil;
    private long lastDailyCollection;

    public FactionService(MiraFactionsPlugin plugin, EconomyHook economy) {
        this.plugin = plugin;
        this.economy = economy;
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
        if (name == null) return null;
        return factions.values().stream().filter(f -> f.name().equalsIgnoreCase(name)).findFirst().orElse(null);
    }

    public double power(UUID player) { return power.getOrDefault(player, startingPower()); }
    public double factionPower(Faction faction) {
        if (faction == null) return 0.0;
        if (faction.permanentPower() != null) return Math.max(0.0, faction.permanentPower());
        double raw = faction.members().keySet().stream().mapToDouble(uuid -> Math.max(0.0, power(uuid))).sum();
        raw += faction.powerBoost();
        raw += faction.upgrade(UpgradeType.POWER) * plugin.getConfig().getDouble("upgrades.power.per-level", 10.0);
        return Math.max(0.0, raw);
    }
    public int maxClaims(Faction faction) { return Math.max(0, (int) Math.floor(factionPower(faction))); }
    public boolean raidable(Faction faction) { return faction != null && !faction.peaceful() && faction.claims().size() > maxClaims(faction); }
    public boolean shielded(Faction faction) { return faction != null && faction.shieldUntil() > System.currentTimeMillis(); }
    public boolean graceActive() { return graceUntil > System.currentTimeMillis(); }
    public long graceUntil() { return graceUntil; }

    public int memberLimit(Faction faction) {
        int base = plugin.getConfig().getInt("faction.member-limit", 20);
        return Math.max(1, base + faction.upgrade(UpgradeType.MEMBER_LIMIT) * plugin.getConfig().getInt("upgrades.member-limit.per-level", 5));
    }
    public int warpLimit(Faction faction) {
        int base = plugin.getConfig().getInt("warps.base-limit", 1);
        return Math.max(0, base + faction.upgrade(UpgradeType.WARP_LIMIT) * plugin.getConfig().getInt("upgrades.warp-limit.per-level", 1));
    }
    public int tntCapacity(Faction faction) {
        int base = plugin.getConfig().getInt("tnt.base-capacity", 1728);
        return Math.max(0, base + faction.upgrade(UpgradeType.TNT_CAPACITY) * plugin.getConfig().getInt("upgrades.tnt-capacity.per-level", 1728));
    }
    public int zoneLimit(Faction faction) {
        int base = plugin.getConfig().getInt("zones.base-limit", 1);
        return Math.max(0, base + faction.upgrade(UpgradeType.ZONE_LIMIT) * plugin.getConfig().getInt("upgrades.zone-limit.per-level", 1));
    }
    public int vaultSlots(Faction faction) {
        int base = plugin.getConfig().getInt("vault.base-slots", 9);
        int per = plugin.getConfig().getInt("upgrades.vault-size.per-level", 9);
        return Math.min(54, Math.max(9, base + faction.upgrade(UpgradeType.VAULT_SIZE) * per));
    }

    public ChatMode chatMode(UUID player) { return chatModes.getOrDefault(player, ChatMode.PUBLIC); }
    public void chatMode(UUID player, ChatMode mode) { chatModes.put(player, mode == null ? ChatMode.PUBLIC : mode); }
    public boolean autoClaim(UUID player) { return autoClaim.contains(player); }
    public void toggleAutoClaim(UUID player) { if (!autoClaim.remove(player)) autoClaim.add(player); }
    public boolean seeChunk(UUID player) { return seeChunk.contains(player); }
    public void toggleSeeChunk(UUID player) { if (!seeChunk.remove(player)) seeChunk.add(player); }
    public boolean factionFlight(UUID player) { return factionFlight.contains(player); }
    public void toggleFactionFlight(UUID player) { if (!factionFlight.remove(player)) factionFlight.add(player); }
    public boolean bypass(UUID player) { return bypass.contains(player); }
    public void toggleBypass(UUID player) { if (!bypass.remove(player)) bypass.add(player); }

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

    public Result invite(Player actor, OfflinePlayer target) {
        Faction faction = of(actor.getUniqueId());
        if (faction == null) return Result.fail("You are not in a faction.");
        if (!hasPermission(actor, FactionPermission.INVITE)) return Result.fail("You do not have faction permission to invite.");
        if (target == null || target.getName() == null) return Result.fail("Player not found.");
        if (of(target.getUniqueId()) != null) return Result.fail("That player is already in a faction.");
        if (faction.bans().contains(target.getUniqueId())) return Result.fail("That player is banned from your faction.");
        long expiry = System.currentTimeMillis() + Math.max(1, plugin.getConfig().getLong("invites.expire-minutes", 30)) * 60_000L;
        faction.invites().put(target.getUniqueId(), expiry);
        save();
        if (target.isOnline() && target.getPlayer() != null) {
            plugin.msg(target.getPlayer(), "&eYou were invited to &f" + faction.name() + "&e. Use &f/f join " + faction.name());
        }
        return Result.ok("Invited " + target.getName() + ".");
    }

    public Result revokeInvite(Player actor, OfflinePlayer target) {
        Faction faction = of(actor.getUniqueId());
        if (faction == null) return Result.fail("You are not in a faction.");
        if (!hasPermission(actor, FactionPermission.INVITE)) return Result.fail("You do not have faction permission to manage invites.");
        if (target == null || faction.invites().remove(target.getUniqueId()) == null) return Result.fail("That player does not have a pending invite.");
        save();
        return Result.ok("Invite revoked.");
    }

    public Result clearInvites(Player actor) {
        Faction faction = of(actor.getUniqueId());
        if (faction == null) return Result.fail("You are not in a faction.");
        if (!hasPermission(actor, FactionPermission.INVITE)) return Result.fail("You do not have faction permission to manage invites.");
        faction.invites().clear();
        save();
        return Result.ok("All pending invites cleared.");
    }

    public Result join(Player player, String factionName) {
        if (of(player.getUniqueId()) != null) return Result.fail("You are already in a faction.");
        Faction faction = byName(factionName);
        if (faction == null) return Result.fail("Faction not found.");
        if (faction.bans().contains(player.getUniqueId())) return Result.fail("You are banned from that faction.");
        if (faction.members().size() >= memberLimit(faction)) return Result.fail("That faction is at its member limit.");
        Long invite = faction.invites().get(player.getUniqueId());
        boolean validInvite = invite != null && invite >= System.currentTimeMillis();
        if (!faction.open() && !validInvite) return Result.fail("You do not have a valid invite to that faction.");
        faction.invites().remove(player.getUniqueId());
        faction.members().put(player.getUniqueId(), FactionRank.RECRUIT);
        playerFaction.put(player.getUniqueId(), faction.id());
        power.putIfAbsent(player.getUniqueId(), startingPower());
        save();
        announce(faction, "&a" + player.getName() + " joined the faction.");
        return Result.ok("Joined " + faction.name() + " as Recruit.");
    }

    public Result leave(Player player) {
        Faction faction = of(player.getUniqueId());
        if (faction == null) return Result.fail("You are not in a faction.");
        if (faction.rank(player.getUniqueId()) == FactionRank.LEADER && faction.members().size() > 1) return Result.fail("Transfer leadership or disband first.");
        if (faction.members().size() == 1 && !faction.permanent()) return disband(player);
        faction.members().remove(player.getUniqueId());
        faction.titles().remove(player.getUniqueId());
        playerFaction.remove(player.getUniqueId());
        clearTransient(player.getUniqueId());
        save();
        announce(faction, "&c" + player.getName() + " left the faction.");
        return Result.ok("You left " + faction.name() + ".");
    }

    public Result disband(Player player) {
        Faction faction = of(player.getUniqueId());
        if (faction == null) return Result.fail("You are not in a faction.");
        if (!hasPermission(player, FactionPermission.DISBAND)) return Result.fail("Only the faction leader may disband.");
        if (faction.permanent()) return Result.fail("This faction is permanent and cannot be disbanded normally.");
        return forceDisband(faction);
    }

    public Result forceDisband(Faction faction) {
        if (faction == null) return Result.fail("Faction not found.");
        for (UUID member : new HashSet<>(faction.members().keySet())) {
            playerFaction.remove(member);
            clearTransient(member);
        }
        for (String claim : new HashSet<>(faction.claims())) claimOwner.remove(claim);
        factions.remove(faction.id());
        for (Faction other : factions.values()) {
            other.relations().remove(faction.id());
            other.relationRequests().remove(faction.id());
        }
        save();
        return Result.ok("Faction " + faction.name() + " disbanded.");
    }

    public Result kick(Player actor, OfflinePlayer target) {
        Faction faction = of(actor.getUniqueId());
        if (faction == null || target == null || of(target.getUniqueId()) != faction) return Result.fail("That player is not in your faction.");
        if (!hasPermission(actor, FactionPermission.KICK)) return Result.fail("You do not have faction permission to kick.");
        if (actor.getUniqueId().equals(target.getUniqueId())) return Result.fail("Use /f leave instead.");
        FactionRank actorRank = faction.rank(actor.getUniqueId());
        FactionRank targetRank = faction.rank(target.getUniqueId());
        if (actorRank == null || targetRank == null || actorRank.weight() <= targetRank.weight()) return Result.fail("You cannot kick that member.");
        faction.members().remove(target.getUniqueId());
        faction.titles().remove(target.getUniqueId());
        playerFaction.remove(target.getUniqueId());
        clearTransient(target.getUniqueId());
        save();
        announce(faction, "&c" + Optional.ofNullable(target.getName()).orElse("A member") + " was kicked.");
        return Result.ok("Member kicked.");
    }

    public Result ban(Player actor, OfflinePlayer target) {
        Faction faction = of(actor.getUniqueId());
        if (faction == null) return Result.fail("You are not in a faction.");
        if (!hasPermission(actor, FactionPermission.BAN)) return Result.fail("You do not have faction permission to ban.");
        if (target == null) return Result.fail("Player not found.");
        if (of(target.getUniqueId()) == faction) {
            Result kicked = kick(actor, target);
            if (!kicked.success()) return kicked;
        }
        faction.invites().remove(target.getUniqueId());
        faction.bans().add(target.getUniqueId());
        save();
        return Result.ok("Banned " + Optional.ofNullable(target.getName()).orElse(target.getUniqueId().toString()) + " from the faction.");
    }

    public Result unban(Player actor, OfflinePlayer target) {
        Faction faction = of(actor.getUniqueId());
        if (faction == null) return Result.fail("You are not in a faction.");
        if (!hasPermission(actor, FactionPermission.BAN)) return Result.fail("You do not have faction permission to manage bans.");
        if (target == null || !faction.bans().remove(target.getUniqueId())) return Result.fail("That player is not banned.");
        save();
        return Result.ok("Player unbanned.");
    }

    public Result promote(Player actor, OfflinePlayer target) {
        Faction faction = of(actor.getUniqueId());
        if (faction == null || target == null || of(target.getUniqueId()) != faction) return Result.fail("That player is not in your faction.");
        if (!hasPermission(actor, FactionPermission.PROMOTE)) return Result.fail("You do not have faction permission to promote.");
        FactionRank actorRank = faction.rank(actor.getUniqueId());
        FactionRank current = faction.rank(target.getUniqueId());
        if (current == null || current == FactionRank.LEADER) return Result.fail("That member cannot be promoted further.");
        FactionRank next = current.promote();
        if (next.weight() >= actorRank.weight()) return Result.fail("You cannot promote a member to your rank or higher.");
        faction.members().put(target.getUniqueId(), next);
        save();
        return Result.ok("Promoted " + Optional.ofNullable(target.getName()).orElse("member") + " to " + next + ".");
    }

    public Result demote(Player actor, OfflinePlayer target) {
        Faction faction = of(actor.getUniqueId());
        if (faction == null || target == null || of(target.getUniqueId()) != faction) return Result.fail("That player is not in your faction.");
        if (!hasPermission(actor, FactionPermission.PROMOTE)) return Result.fail("You do not have faction permission to demote.");
        FactionRank actorRank = faction.rank(actor.getUniqueId());
        FactionRank current = faction.rank(target.getUniqueId());
        if (current == null || current == FactionRank.LEADER || actorRank.weight() <= current.weight()) return Result.fail("You cannot demote that member.");
        if (current == FactionRank.RECRUIT) return Result.fail("That member is already Recruit.");
        FactionRank next = current.demote();
        faction.members().put(target.getUniqueId(), next);
        save();
        return Result.ok("Demoted " + Optional.ofNullable(target.getName()).orElse("member") + " to " + next + ".");
    }

    public Result setRank(Player actor, OfflinePlayer target, FactionRank rank) {
        Faction faction = of(actor.getUniqueId());
        if (faction == null || target == null || of(target.getUniqueId()) != faction) return Result.fail("That player is not in your faction.");
        if (!hasPermission(actor, FactionPermission.PROMOTE)) return Result.fail("You do not have faction permission to manage ranks.");
        FactionRank actorRank = faction.rank(actor.getUniqueId());
        FactionRank targetRank = faction.rank(target.getUniqueId());
        if (targetRank == FactionRank.LEADER || rank == FactionRank.LEADER) return Result.fail("Use /f transfer for leadership.");
        if (actorRank.weight() <= targetRank.weight() || actorRank.weight() <= rank.weight()) return Result.fail("You cannot assign that rank.");
        faction.members().put(target.getUniqueId(), rank);
        save();
        return Result.ok("Set " + Optional.ofNullable(target.getName()).orElse("member") + " to " + rank + ".");
    }

    public Result transfer(Player actor, OfflinePlayer target) {
        Faction faction = of(actor.getUniqueId());
        if (faction == null || target == null || of(target.getUniqueId()) != faction) return Result.fail("That player is not in your faction.");
        if (faction.rank(actor.getUniqueId()) != FactionRank.LEADER) return Result.fail("Only the leader can transfer leadership.");
        if (actor.getUniqueId().equals(target.getUniqueId())) return Result.fail("You are already the leader.");
        faction.members().put(actor.getUniqueId(), FactionRank.COLEADER);
        faction.members().put(target.getUniqueId(), FactionRank.LEADER);
        save();
        announce(faction, "&6Leadership transferred to " + Optional.ofNullable(target.getName()).orElse("new leader") + ".");
        return Result.ok("Leadership transferred.");
    }

    public Result rename(Player actor, String name) {
        Faction faction = of(actor.getUniqueId());
        if (faction == null) return Result.fail("You are not in a faction.");
        if (faction.rank(actor.getUniqueId()) != FactionRank.LEADER) return Result.fail("Only the leader can rename the faction.");
        String clean = name == null ? "" : name.trim();
        if (!clean.matches("[A-Za-z0-9_]{3,16}")) return Result.fail("Faction names must be 3-16 letters, numbers or underscores.");
        Faction existing = byName(clean);
        if (existing != null && existing != faction) return Result.fail("That faction name is already taken.");
        faction.name(clean);
        save();
        return Result.ok("Faction renamed to " + clean + ".");
    }

    public Result setDescription(Player actor, String description) {
        Faction faction = of(actor.getUniqueId());
        if (faction == null) return Result.fail("You are not in a faction.");
        if (!rankAtLeast(actor, FactionRank.OFFICER)) return Result.fail("Officer rank or higher required.");
        faction.description(description);
        save();
        return Result.ok("Faction description updated.");
    }

    public Result setLink(Player actor, String link) {
        Faction faction = of(actor.getUniqueId());
        if (faction == null) return Result.fail("You are not in a faction.");
        if (!rankAtLeast(actor, FactionRank.OFFICER)) return Result.fail("Officer rank or higher required.");
        faction.link(link);
        save();
        return Result.ok("Faction link updated.");
    }

    public Result setOpen(Player actor, boolean open) {
        Faction faction = of(actor.getUniqueId());
        if (faction == null) return Result.fail("You are not in a faction.");
        if (!rankAtLeast(actor, FactionRank.COLEADER)) return Result.fail("Coleader rank or higher required.");
        faction.open(open);
        save();
        return Result.ok("Faction is now " + (open ? "open" : "invite-only") + ".");
    }

    public Result setTitle(Player actor, OfflinePlayer target, String title) {
        Faction faction = of(actor.getUniqueId());
        if (faction == null || target == null || of(target.getUniqueId()) != faction) return Result.fail("That player is not in your faction.");
        if (!rankAtLeast(actor, FactionRank.OFFICER)) return Result.fail("Officer rank or higher required.");
        String clean = title == null ? "" : title.trim();
        if (clean.length() > plugin.getConfig().getInt("faction.max-title-length", 20)) return Result.fail("That title is too long.");
        if (clean.isBlank()) faction.titles().remove(target.getUniqueId()); else faction.titles().put(target.getUniqueId(), clean);
        save();
        return Result.ok("Member title updated.");
    }

    public Result claim(Player player) { return claim(player, player.getLocation()); }

    public Result claim(Player player, Location location) {
        Faction faction = of(player.getUniqueId());
        if (faction == null) return Result.fail("You are not in a faction.");
        if (!hasPermission(player, FactionPermission.CLAIM)) return Result.fail("You do not have faction permission to claim.");
        if (!claimsAllowed(location.getWorld())) return Result.fail("Claiming is disabled in this world.");
        String key = claimKey(location);
        if (safeZoneClaims.contains(key) || warZoneClaims.contains(key)) return Result.fail("This chunk is reserved territory.");
        UUID existingId = claimOwner.get(key);
        if (existingId != null) {
            Faction existing = factions.get(existingId);
            if (existing == faction) return Result.fail("Your faction already owns this chunk.");
            if (existing == null) {
                claimOwner.remove(key);
            } else {
                Relation relation = relation(faction, existing);
                if (relation != Relation.ENEMY) return Result.fail("Only enemy territory can be overclaimed.");
                if (!raidable(existing)) return Result.fail(existing.name() + " has enough power to protect this land.");
                if (existing.peaceful()) return Result.fail("Peaceful factions cannot be overclaimed.");
                if (graceActive()) return Result.fail("Server grace is active. Enemy land cannot be overclaimed.");
                if (shielded(existing)) return Result.fail("That faction's shield is active.");
                if (faction.claims().size() >= maxClaims(faction)) return Result.fail("Your faction needs more power to claim more land.");
                existing.claims().remove(key);
                existing.claimZones().remove(key);
                faction.claims().add(key);
                claimOwner.put(key, faction.id());
                save();
                announce(existing, "&cEnemy faction " + faction.name() + " overclaimed one of your chunks!");
                announce(faction, "&aOverclaimed a chunk from " + existing.name() + ".");
                return Result.ok("Overclaimed enemy territory from " + existing.name() + ".");
            }
        }
        if (faction.claims().size() >= maxClaims(faction)) return Result.fail("Your faction needs more power to claim more land.");
        faction.claims().add(key);
        claimOwner.put(key, faction.id());
        save();
        return Result.ok("Claimed this chunk for " + faction.name() + ".");
    }

    public Result claimRadius(Player player, int radius) {
        Faction faction = of(player.getUniqueId());
        if (faction == null) return Result.fail("You are not in a faction.");
        if (!hasPermission(player, FactionPermission.CLAIM)) return Result.fail("You do not have faction permission to claim.");
        int maxRadius = Math.max(0, plugin.getConfig().getInt("claims.max-radius", 5));
        radius = Math.max(0, Math.min(maxRadius, radius));
        int success = 0;
        Chunk center = player.getLocation().getChunk();
        for (int x = -radius; x <= radius; x++) {
            for (int z = -radius; z <= radius; z++) {
                Location location = new Location(player.getWorld(), (center.getX() + x) * 16.0, player.getY(), (center.getZ() + z) * 16.0);
                if (claim(player, location).success()) success++;
            }
        }
        return Result.ok("Claimed " + success + " chunk" + (success == 1 ? "" : "s") + ".");
    }

    public Result unclaim(Player player) {
        Faction faction = of(player.getUniqueId());
        if (faction == null) return Result.fail("You are not in a faction.");
        if (!hasPermission(player, FactionPermission.UNCLAIM)) return Result.fail("You do not have faction permission to unclaim.");
        String key = claimKey(player.getLocation());
        if (!faction.id().equals(claimOwner.get(key))) return Result.fail("Your faction does not own this chunk.");
        faction.claims().remove(key);
        faction.claimZones().remove(key);
        claimOwner.remove(key);
        save();
        return Result.ok("Unclaimed this chunk.");
    }

    public Result unclaimAll(Player player) {
        Faction faction = of(player.getUniqueId());
        if (faction == null) return Result.fail("You are not in a faction.");
        if (!hasPermission(player, FactionPermission.UNCLAIM)) return Result.fail("You do not have faction permission to unclaim.");
        int count = faction.claims().size();
        for (String claim : new HashSet<>(faction.claims())) claimOwner.remove(claim);
        faction.claims().clear();
        faction.claimZones().clear();
        save();
        return Result.ok("Unclaimed all " + count + " faction chunks.");
    }

    public Faction owner(Location location) {
        UUID id = claimOwner.get(claimKey(location));
        return id == null ? null : factions.get(id);
    }

    public TerritoryType territoryType(Location location) {
        String key = claimKey(location);
        if (safeZoneClaims.contains(key)) return TerritoryType.SAFEZONE;
        if (warZoneClaims.contains(key)) return TerritoryType.WARZONE;
        return claimOwner.containsKey(key) ? TerritoryType.FACTION : TerritoryType.WILDERNESS;
    }

    public Result setSpecialClaim(Location location, TerritoryType type) {
        String key = claimKey(location);
        UUID old = claimOwner.remove(key);
        if (old != null) {
            Faction faction = factions.get(old);
            if (faction != null) {
                faction.claims().remove(key);
                faction.claimZones().remove(key);
            }
        }
        safeZoneClaims.remove(key);
        warZoneClaims.remove(key);
        if (type == TerritoryType.SAFEZONE) safeZoneClaims.add(key);
        if (type == TerritoryType.WARZONE) warZoneClaims.add(key);
        save();
        return Result.ok("Set chunk to " + type + ".");
    }

    public boolean hasPermission(Player player, FactionPermission permission) {
        Faction faction = of(player.getUniqueId());
        if (faction == null) return false;
        FactionRank rank = faction.rank(player.getUniqueId());
        return rank != null && rank.atLeast(faction.minimum(permission));
    }

    public boolean can(Player player, Location location, FactionPermission permission) {
        if (player.hasPermission("mirafactions.bypass") || bypass(player.getUniqueId())) return true;
        TerritoryType type = territoryType(location);
        if (type == TerritoryType.WILDERNESS) return true;
        if (type == TerritoryType.SAFEZONE) return false;
        if (type == TerritoryType.WARZONE) return permission == FactionPermission.USE || permission == FactionPermission.DOOR || permission == FactionPermission.BUTTON || permission == FactionPermission.LEVER;
        Faction owner = owner(location);
        if (owner == null) return true;
        String zoneName = owner.zoneForClaim(claimKey(location));
        FactionZone zone = owner.zone(zoneName);
        if (owner.isMember(player.getUniqueId())) {
            FactionRank rank = owner.rank(player.getUniqueId());
            FactionRank minimum = zone == null ? owner.minimum(permission) : zone.minimum(permission);
            return rank != null && rank.atLeast(minimum);
        }
        Faction mine = of(player.getUniqueId());
        Relation relation = relation(mine, owner);
        if (owner.relationAllowed(permission, relation)) return true;
        if (relation == Relation.ENEMY && raidable(owner) && !owner.peaceful() && !graceActive() && !shielded(owner)) {
            return raidPermission(permission);
        }
        return false;
    }

    private boolean raidPermission(FactionPermission permission) {
        return switch (permission) {
            case DESTROY, BUILD -> plugin.getConfig().getBoolean("raiding.allow-build-break", true);
            case CONTAINER -> plugin.getConfig().getBoolean("raiding.allow-containers", true);
            case USE, DOOR, BUTTON, LEVER, PRESSURE_PLATE -> plugin.getConfig().getBoolean("raiding.allow-interactions", true);
            default -> false;
        };
    }

    public boolean canPvp(Player attacker, Player victim, Location location) {
        if (attacker.hasPermission("mirafactions.bypass") || bypass(attacker.getUniqueId())) return true;
        if (territoryType(location) == TerritoryType.SAFEZONE) return false;
        Faction a = of(attacker.getUniqueId());
        Faction v = of(victim.getUniqueId());
        if (a == null || v == null) return true;
        if (a.peaceful() || v.peaceful()) return false;
        if (a == v) return plugin.getConfig().getBoolean("combat.friendly-fire", false);
        Relation relation = relation(a, v);
        if (relation == Relation.ALLY) return plugin.getConfig().getBoolean("combat.ally-friendly-fire", false);
        if (relation == Relation.TRUCE) return plugin.getConfig().getBoolean("combat.truce-friendly-fire", false);
        return true;
    }

    public Result setPermission(Player actor, FactionPermission permission, FactionRank rank) {
        Faction faction = of(actor.getUniqueId());
        if (faction == null) return Result.fail("You are not in a faction.");
        if (faction.rank(actor.getUniqueId()) != FactionRank.LEADER) return Result.fail("Only the leader can edit faction permissions.");
        faction.minimumRanks().put(permission, rank);
        save();
        return Result.ok(permission + " now requires " + rank + ".");
    }

    public Result setRelationPermission(Player actor, FactionPermission permission, Relation relation, boolean allow) {
        Faction faction = of(actor.getUniqueId());
        if (faction == null) return Result.fail("You are not in a faction.");
        if (faction.rank(actor.getUniqueId()) != FactionRank.LEADER) return Result.fail("Only the leader can edit faction permissions.");
        Set<Relation> allowed = faction.relationAccess().computeIfAbsent(permission, ignored -> EnumSet.noneOf(Relation.class));
        if (allow) allowed.add(relation); else allowed.remove(relation);
        save();
        return Result.ok(permission + " relation access for " + relation + " is now " + (allow ? "allowed" : "denied") + ".");
    }

    public Result setHome(Player player) {
        Faction faction = of(player.getUniqueId());
        if (faction == null) return Result.fail("You are not in a faction.");
        if (!hasPermission(player, FactionPermission.SETHOME)) return Result.fail("You do not have faction permission to set home.");
        if (owner(player.getLocation()) != faction) return Result.fail("Faction home must be inside your claimed land.");
        faction.home(player.getLocation().clone());
        save();
        return Result.ok("Faction home set.");
    }

    public Result delHome(Player player) {
        Faction faction = of(player.getUniqueId());
        if (faction == null) return Result.fail("You are not in a faction.");
        if (!hasPermission(player, FactionPermission.SETHOME)) return Result.fail("You do not have faction permission to remove home.");
        faction.home(null);
        save();
        return Result.ok("Faction home removed.");
    }

    public Result home(Player player) {
        Faction faction = of(player.getUniqueId());
        if (faction == null) return Result.fail("You are not in a faction.");
        if (!hasPermission(player, FactionPermission.HOME)) return Result.fail("You do not have faction permission to use home.");
        if (faction.home() == null) return Result.fail("Your faction has no home.");
        return warmupTeleport(player, faction.home(), homeCooldown, "Faction home");
    }

    public Result setWarp(Player player, String name) {
        Faction faction = of(player.getUniqueId());
        if (faction == null) return Result.fail("You are not in a faction.");
        if (!hasPermission(player, FactionPermission.SETWARP)) return Result.fail("You do not have faction permission to set warps.");
        String clean = normalizeName(name, 16);
        if (clean == null) return Result.fail("Warp name must be 1-16 letters, numbers, underscores or hyphens.");
        if (!faction.warps().containsKey(clean) && faction.warps().size() >= warpLimit(faction)) return Result.fail("Your faction has reached its warp limit.");
        if (owner(player.getLocation()) != faction) return Result.fail("Faction warps must be inside your claimed land.");
        faction.warps().put(clean, player.getLocation().clone());
        save();
        return Result.ok("Faction warp " + clean + " set.");
    }

    public Result delWarp(Player player, String name) {
        Faction faction = of(player.getUniqueId());
        if (faction == null) return Result.fail("You are not in a faction.");
        if (!hasPermission(player, FactionPermission.SETWARP)) return Result.fail("You do not have faction permission to remove warps.");
        String clean = name == null ? "" : name.toLowerCase(Locale.ROOT);
        if (faction.warps().remove(clean) == null) return Result.fail("Warp not found.");
        save();
        return Result.ok("Faction warp removed.");
    }

    public Result warp(Player player, String name) {
        Faction faction = of(player.getUniqueId());
        if (faction == null) return Result.fail("You are not in a faction.");
        if (!hasPermission(player, FactionPermission.WARP)) return Result.fail("You do not have faction permission to use warps.");
        Location target = faction.warps().get(name == null ? "" : name.toLowerCase(Locale.ROOT));
        if (target == null) return Result.fail("Warp not found.");
        return warmupTeleport(player, target, warpCooldown, "Faction warp");
    }

    private Result warmupTeleport(Player player, Location target, Map<UUID, Long> cooldowns, String label) {
        long now = System.currentTimeMillis();
        long cooldown = cooldowns.getOrDefault(player.getUniqueId(), 0L);
        if (cooldown > now) return Result.fail(label + " is on cooldown for " + Math.max(1, (cooldown - now + 999) / 1000) + "s.");
        int warmup = Math.max(0, plugin.getConfig().getInt("teleports.warmup-seconds", plugin.getConfig().getInt("home.warmup-seconds", 5)));
        Location start = player.getLocation().clone();
        plugin.msg(player, "&eTeleporting in &f" + warmup + "&e seconds. Do not move.");
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (!player.isOnline()) return;
            if (player.getWorld() != start.getWorld() || player.getLocation().distanceSquared(start) > 0.09) {
                plugin.msg(player, "&cTeleport cancelled because you moved.");
                return;
            }
            player.teleportAsync(target);
            cooldowns.put(player.getUniqueId(), System.currentTimeMillis() + plugin.getConfig().getLong("teleports.cooldown-seconds", plugin.getConfig().getLong("home.cooldown-seconds", 30)) * 1000L);
        }, warmup * 20L);
        return Result.ok("");
    }

    public Result relation(Player actor, String otherName, Relation wanted) {
        Faction mine = of(actor.getUniqueId());
        Faction other = byName(otherName);
        if (mine == null) return Result.fail("You are not in a faction.");
        if (!hasPermission(actor, FactionPermission.DIPLOMACY)) return Result.fail("You do not have faction permission to manage diplomacy.");
        if (other == null || other == mine) return Result.fail("Faction not found.");
        if (wanted == Relation.ENEMY || wanted == Relation.NEUTRAL) {
            setRelationBoth(mine, other, wanted);
            mine.relationRequests().remove(other.id());
            other.relationRequests().remove(mine.id());
            save();
            return Result.ok("Relations with " + other.name() + " are now " + wanted + ".");
        }
        Relation reciprocal = other.relationRequests().get(mine.id());
        if (reciprocal == wanted) {
            other.relationRequests().remove(mine.id());
            mine.relationRequests().remove(other.id());
            setRelationBoth(mine, other, wanted);
            save();
            announce(mine, "&aRelation with " + other.name() + " is now " + wanted + ".");
            announce(other, "&aRelation with " + mine.name() + " is now " + wanted + ".");
            return Result.ok(wanted + " relation formed with " + other.name() + ".");
        }
        mine.relationRequests().put(other.id(), wanted);
        save();
        announce(other, "&e" + mine.name() + " requested a " + wanted + " relation. Use /f " + wanted.name().toLowerCase(Locale.ROOT) + " " + mine.name() + " to accept.");
        return Result.ok(wanted + " request sent to " + other.name() + ".");
    }

    private void setRelationBoth(Faction a, Faction b, Relation relation) {
        if (relation == Relation.NEUTRAL) {
            a.relations().remove(b.id());
            b.relations().remove(a.id());
        } else {
            a.relations().put(b.id(), relation);
            b.relations().put(a.id(), relation);
        }
    }

    public Relation relation(Faction first, Faction second) {
        if (first == null || second == null) return Relation.NEUTRAL;
        if (first == second) return Relation.ALLY;
        return first.relations().getOrDefault(second.id(), Relation.NEUTRAL);
    }

    public Result deposit(Player player, double amount) {
        Faction faction = of(player.getUniqueId());
        if (faction == null) return Result.fail("You are not in a faction.");
        if (!economy.available()) return Result.fail("Vault economy is not available.");
        if (amount <= 0) return Result.fail("Amount must be positive.");
        if (!economy.withdraw(player, amount)) return Result.fail("You do not have enough money.");
        faction.bankBalance(faction.bankBalance() + amount);
        save();
        return Result.ok("Deposited " + economy.format(amount) + " into the faction bank.");
    }

    public Result withdraw(Player player, double amount) {
        Faction faction = of(player.getUniqueId());
        if (faction == null) return Result.fail("You are not in a faction.");
        if (!hasPermission(player, FactionPermission.ECONOMY)) return Result.fail("You do not have faction permission to withdraw money.");
        if (!economy.available()) return Result.fail("Vault economy is not available.");
        if (amount <= 0 || faction.bankBalance() < amount) return Result.fail("The faction bank does not have that much money.");
        if (!economy.deposit(player, amount)) return Result.fail("Economy deposit failed.");
        faction.bankBalance(faction.bankBalance() - amount);
        save();
        return Result.ok("Withdrew " + economy.format(amount) + " from the faction bank.");
    }

    public Result payFaction(Player player, String otherName, double amount) {
        Faction mine = of(player.getUniqueId());
        Faction other = byName(otherName);
        if (mine == null) return Result.fail("You are not in a faction.");
        if (!hasPermission(player, FactionPermission.ECONOMY)) return Result.fail("You do not have faction permission to transfer money.");
        if (other == null || other == mine) return Result.fail("Faction not found.");
        if (amount <= 0 || mine.bankBalance() < amount) return Result.fail("Your faction bank does not have that much money.");
        mine.bankBalance(mine.bankBalance() - amount);
        other.bankBalance(other.bankBalance() + amount);
        save();
        announce(mine, "&eSent " + economy.format(amount) + " to " + other.name() + ".");
        announce(other, "&aReceived " + economy.format(amount) + " from " + mine.name() + ".");
        return Result.ok("Faction payment sent.");
    }

    public Result depositTnt(Player player, int amount) {
        Faction faction = of(player.getUniqueId());
        if (faction == null) return Result.fail("You are not in a faction.");
        if (!hasPermission(player, FactionPermission.TNT_DEPOSIT)) return Result.fail("You do not have faction permission to deposit TNT.");
        if (amount <= 0) return Result.fail("Amount must be positive.");
        int capacity = tntCapacity(faction);
        int room = capacity - faction.tntBalance();
        int available = countMaterial(player, Material.TNT);
        int moved = Math.min(amount, Math.min(room, available));
        if (moved <= 0) return Result.fail(room <= 0 ? "Faction TNT bank is full." : "You do not have enough TNT.");
        removeMaterial(player, Material.TNT, moved);
        faction.tntBalance(faction.tntBalance() + moved);
        save();
        return Result.ok("Deposited " + moved + " TNT. Bank: " + faction.tntBalance() + "/" + capacity + ".");
    }

    public Result withdrawTnt(Player player, int amount) {
        Faction faction = of(player.getUniqueId());
        if (faction == null) return Result.fail("You are not in a faction.");
        if (!hasPermission(player, FactionPermission.TNT_WITHDRAW)) return Result.fail("You do not have faction permission to withdraw TNT.");
        int moved = Math.min(Math.max(0, amount), faction.tntBalance());
        if (moved <= 0) return Result.fail("Faction TNT bank is empty.");
        faction.tntBalance(faction.tntBalance() - moved);
        giveMaterial(player, Material.TNT, moved);
        save();
        return Result.ok("Withdrew " + moved + " TNT. Bank: " + faction.tntBalance() + "/" + tntCapacity(faction) + ".");
    }

    public Result activateShield(Player player) {
        Faction faction = of(player.getUniqueId());
        if (faction == null) return Result.fail("You are not in a faction.");
        if (!hasPermission(player, FactionPermission.SHIELD)) return Result.fail("You do not have faction permission to activate the shield.");
        int level = faction.upgrade(UpgradeType.SHIELD);
        if (level <= 0) return Result.fail("Your faction has not purchased the Shield upgrade.");
        long now = System.currentTimeMillis();
        if (faction.shieldUntil() > now) return Result.fail("Faction shield is already active.");
        if (faction.shieldCooldownUntil() > now) return Result.fail("Shield is on cooldown for " + formatDuration(faction.shieldCooldownUntil() - now) + ".");
        long durationMinutes = plugin.getConfig().getLong("shield.duration-minutes-per-level", 30L) * level;
        long cooldownMinutes = plugin.getConfig().getLong("shield.cooldown-minutes", 1440L);
        faction.shieldUntil(now + durationMinutes * 60_000L);
        faction.shieldCooldownUntil(faction.shieldUntil() + cooldownMinutes * 60_000L);
        save();
        announce(faction, "&bFaction shield activated for " + durationMinutes + " minutes.");
        return Result.ok("Shield activated.");
    }

    public Result buyUpgrade(Player player, UpgradeType type) {
        Faction faction = of(player.getUniqueId());
        if (faction == null) return Result.fail("You are not in a faction.");
        if (!hasPermission(player, FactionPermission.UPGRADE)) return Result.fail("You do not have faction permission to buy upgrades.");
        int current = faction.upgrade(type);
        if (current >= type.maxLevel()) return Result.fail(type.display() + " is already max level.");
        int next = current + 1;
        double base = plugin.getConfig().getDouble("upgrades.cost-base", 10000.0);
        double multiplier = plugin.getConfig().getDouble("upgrades.cost-multiplier", 2.0);
        double typeMultiplier = plugin.getConfig().getDouble("upgrades.costs." + type.name().toLowerCase(Locale.ROOT), 1.0);
        double cost = base * Math.pow(multiplier, current) * typeMultiplier;
        if (faction.bankBalance() < cost) return Result.fail("Faction bank needs " + economy.format(cost) + " for this upgrade.");
        faction.bankBalance(faction.bankBalance() - cost);
        faction.upgrades().put(type, next);
        save();
        announce(faction, "&dPurchased " + type.display() + " level " + next + " for " + economy.format(cost) + ".");
        return Result.ok(type.display() + " upgraded to level " + next + ".");
    }

    public double upgradeCost(Faction faction, UpgradeType type) {
        int current = faction.upgrade(type);
        double base = plugin.getConfig().getDouble("upgrades.cost-base", 10000.0);
        double multiplier = plugin.getConfig().getDouble("upgrades.cost-multiplier", 2.0);
        double typeMultiplier = plugin.getConfig().getDouble("upgrades.costs." + type.name().toLowerCase(Locale.ROOT), 1.0);
        return base * Math.pow(multiplier, current) * typeMultiplier;
    }

    public Result createZone(Player player, String name) {
        Faction faction = of(player.getUniqueId());
        if (faction == null) return Result.fail("You are not in a faction.");
        if (!hasPermission(player, FactionPermission.ZONE)) return Result.fail("You do not have faction permission to manage zones.");
        String clean = normalizeName(name, 16);
        if (clean == null) return Result.fail("Zone name must be 1-16 letters, numbers, underscores or hyphens.");
        if (faction.zones().containsKey(clean)) return Result.fail("That zone already exists.");
        if (faction.zones().size() >= zoneLimit(faction)) return Result.fail("Your faction has reached its zone limit.");
        faction.zones().put(clean, new FactionZone(clean));
        save();
        return Result.ok("Created zone " + clean + ".");
    }

    public Result deleteZone(Player player, String name) {
        Faction faction = of(player.getUniqueId());
        if (faction == null) return Result.fail("You are not in a faction.");
        if (!hasPermission(player, FactionPermission.ZONE)) return Result.fail("You do not have faction permission to manage zones.");
        String clean = name == null ? "" : name.toLowerCase(Locale.ROOT);
        if (faction.zones().remove(clean) == null) return Result.fail("Zone not found.");
        faction.claimZones().entrySet().removeIf(entry -> entry.getValue().equalsIgnoreCase(clean));
        save();
        return Result.ok("Zone deleted.");
    }

    public Result assignZone(Player player, String name) {
        Faction faction = of(player.getUniqueId());
        if (faction == null) return Result.fail("You are not in a faction.");
        if (!hasPermission(player, FactionPermission.ZONE)) return Result.fail("You do not have faction permission to manage zones.");
        String clean = name == null ? "" : name.toLowerCase(Locale.ROOT);
        if (!faction.zones().containsKey(clean)) return Result.fail("Zone not found.");
        String key = claimKey(player.getLocation());
        if (!faction.claims().contains(key)) return Result.fail("This chunk is not owned by your faction.");
        faction.claimZones().put(key, clean);
        save();
        return Result.ok("Assigned this chunk to zone " + clean + ".");
    }

    public Result zoneGreeting(Player player, String name, String greeting) {
        Faction faction = of(player.getUniqueId());
        if (faction == null) return Result.fail("You are not in a faction.");
        if (!hasPermission(player, FactionPermission.ZONE)) return Result.fail("You do not have faction permission to manage zones.");
        FactionZone zone = faction.zone(name == null ? "" : name.toLowerCase(Locale.ROOT));
        if (zone == null) return Result.fail("Zone not found.");
        zone.greeting(greeting);
        save();
        return Result.ok("Zone greeting updated.");
    }

    public Result zonePermission(Player player, String name, FactionPermission permission, FactionRank rank) {
        Faction faction = of(player.getUniqueId());
        if (faction == null) return Result.fail("You are not in a faction.");
        if (!hasPermission(player, FactionPermission.ZONE)) return Result.fail("You do not have faction permission to manage zones.");
        FactionZone zone = faction.zone(name == null ? "" : name.toLowerCase(Locale.ROOT));
        if (zone == null) return Result.fail("Zone not found.");
        zone.minimumRanks().put(permission, rank);
        save();
        return Result.ok("Zone permission updated.");
    }

    public void death(Player player) {
        Faction faction = of(player.getUniqueId());
        double loss = plugin.getConfig().getDouble("power.death-loss", 2.0);
        if (faction != null) {
            double reduction = Math.min(0.8, faction.upgrade(UpgradeType.POWER_LOSS) * plugin.getConfig().getDouble("upgrades.power-loss.reduction-per-level", 0.1));
            loss *= 1.0 - reduction;
        }
        setPower(player.getUniqueId(), power(player.getUniqueId()) - loss);
    }

    public void regeneratePower() {
        boolean changed = false;
        for (Player player : Bukkit.getOnlinePlayers()) {
            Faction faction = of(player.getUniqueId());
            if (faction == null) continue;
            double add = plugin.getConfig().getDouble("power.regen-amount", 1.0);
            add *= 1.0 + faction.upgrade(UpgradeType.POWER_REGEN) * plugin.getConfig().getDouble("upgrades.power-regen.bonus-per-level", 0.2);
            double maximum = individualMaxPower(faction);
            double before = power(player.getUniqueId());
            double after = Math.min(maximum, before + add);
            if (after != before) {
                power.put(player.getUniqueId(), after);
                changed = true;
            }
        }
        if (changed) save();
    }

    public void setPower(UUID uuid, double value) {
        Faction faction = of(uuid);
        double minimum = plugin.getConfig().getDouble("power.minimum", -10.0);
        double maximum = faction == null ? plugin.getConfig().getDouble("power.maximum", 10.0) : individualMaxPower(faction);
        power.put(uuid, Math.max(minimum, Math.min(maximum, value)));
        save();
    }

    public void addPower(UUID uuid, double value) { setPower(uuid, power(uuid) + value); }

    private double individualMaxPower(Faction faction) {
        return plugin.getConfig().getDouble("power.maximum", 10.0) + faction.upgrade(UpgradeType.POWER) * plugin.getConfig().getDouble("upgrades.power.individual-max-per-level", 0.0);
    }

    private double startingPower() { return plugin.getConfig().getDouble("power.starting", 10.0); }

    public boolean rankAtLeast(Player player, FactionRank rank) {
        Faction faction = of(player.getUniqueId());
        return faction != null && faction.rank(player.getUniqueId()) != null && faction.rank(player.getUniqueId()).atLeast(rank);
    }

    public String claimKey(Location location) {
        return location.getWorld().getUID() + ":" + location.getChunk().getX() + ":" + location.getChunk().getZ();
    }

    public String map(Player viewer, int radius) {
        Chunk center = viewer.getLocation().getChunk();
        StringBuilder out = new StringBuilder();
        out.append("&8---- &dFaction Map &8----\n");
        Faction mine = of(viewer.getUniqueId());
        for (int z = -radius; z <= radius; z++) {
            for (int x = -radius; x <= radius; x++) {
                if (x == 0 && z == 0) { out.append("&f+"); continue; }
                Location loc = new Location(viewer.getWorld(), (center.getX() + x) * 16.0, viewer.getY(), (center.getZ() + z) * 16.0);
                TerritoryType type = territoryType(loc);
                if (type == TerritoryType.SAFEZONE) { out.append("&aS"); continue; }
                if (type == TerritoryType.WARZONE) { out.append("&cW"); continue; }
                Faction owner = owner(loc);
                if (owner == null) { out.append("&7-"); continue; }
                if (owner == mine) out.append("&a#");
                else switch (relation(mine, owner)) {
                    case ALLY -> out.append("&d#");
                    case TRUCE -> out.append("&b#");
                    case ENEMY -> out.append("&c#");
                    default -> out.append("&e#");
                }
            }
            out.append("\n");
        }
        out.append("&7+ You  &a# Yours  &d# Ally  &b# Truce  &c# Enemy  &e# Neutral  &aS Safe  &cW War");
        return out.toString();
    }

    public Result stuck(Player player) {
        int radius = Math.max(1, plugin.getConfig().getInt("stuck.search-radius-chunks", 10));
        Chunk start = player.getLocation().getChunk();
        for (int r = 1; r <= radius; r++) {
            for (int x = -r; x <= r; x++) {
                for (int z = -r; z <= r; z++) {
                    if (Math.abs(x) != r && Math.abs(z) != r) continue;
                    int blockX = (start.getX() + x) * 16 + 8;
                    int blockZ = (start.getZ() + z) * 16 + 8;
                    Location test = new Location(player.getWorld(), blockX, player.getWorld().getHighestBlockYAt(blockX, blockZ) + 1.0, blockZ);
                    if (territoryType(test) == TerritoryType.WILDERNESS) {
                        player.teleportAsync(test);
                        return Result.ok("Moved you to nearby Wilderness.");
                    }
                }
            }
        }
        return Result.fail("No nearby Wilderness location was found.");
    }

    public List<Player> nearbyMembers(Player player, double radius) {
        Faction faction = of(player.getUniqueId());
        if (faction == null) return List.of();
        double max = radius * radius;
        return faction.members().keySet().stream().map(Bukkit::getPlayer).filter(Objects::nonNull)
                .filter(other -> other.getWorld() == player.getWorld() && other.getLocation().distanceSquared(player.getLocation()) <= max).toList();
    }

    public void renderSeeChunk(Player player) {
        if (!seeChunk(player.getUniqueId()) || !player.isOnline()) return;
        Chunk chunk = player.getLocation().getChunk();
        World world = player.getWorld();
        double y = Math.max(world.getMinHeight() + 1, player.getY());
        Particle particle = Particle.valueOf(plugin.getConfig().getString("seechunk.particle", "END_ROD").toUpperCase(Locale.ROOT));
        for (int i = 0; i <= 16; i += 2) {
            world.spawnParticle(particle, chunk.getX() * 16 + i, y, chunk.getZ() * 16, 1, 0, 0, 0, 0);
            world.spawnParticle(particle, chunk.getX() * 16 + i, y, chunk.getZ() * 16 + 16, 1, 0, 0, 0, 0);
            world.spawnParticle(particle, chunk.getX() * 16, y, chunk.getZ() * 16 + i, 1, 0, 0, 0, 0);
            world.spawnParticle(particle, chunk.getX() * 16 + 16, y, chunk.getZ() * 16 + i, 1, 0, 0, 0, 0);
        }
    }

    public void updateFlight(Player player) {
        if (!factionFlight(player.getUniqueId())) return;
        Faction faction = of(player.getUniqueId());
        boolean allowed = faction != null && faction.upgrade(UpgradeType.FLIGHT) > 0 && hasPermission(player, FactionPermission.FLY);
        Faction owner = owner(player.getLocation());
        allowed = allowed && (owner == faction || (owner != null && relation(faction, owner) == Relation.ALLY));
        if (allowed) {
            player.setAllowFlight(true);
        } else {
            factionFlight.remove(player.getUniqueId());
            if (!player.hasPermission("mirafly.permanent")) {
                player.setFlying(false);
                player.setAllowFlight(false);
            }
            plugin.msg(player, "&cFaction flight disabled because you left authorized territory.");
        }
    }

    public Result startGrace(long minutes) {
        graceUntil = System.currentTimeMillis() + Math.max(1, minutes) * 60_000L;
        save();
        return Result.ok("Grace enabled for " + minutes + " minute(s).");
    }
    public Result stopGrace() { graceUntil = 0L; save(); return Result.ok("Grace disabled."); }

    public void processDailyEconomy() {
        long now = System.currentTimeMillis();
        if (now - lastDailyCollection < 86_400_000L) return;
        lastDailyCollection = now;
        if (!economy.available()) { save(); return; }
        double rentPerClaim = plugin.getConfig().getDouble("rent.per-claim", 0.0);
        for (Faction faction : factions.values()) {
            if (faction.dues() > 0) {
                for (UUID member : faction.members().keySet()) {
                    OfflinePlayer offline = Bukkit.getOfflinePlayer(member);
                    if (economy.withdraw(offline, faction.dues())) {
                        faction.bankBalance(faction.bankBalance() + faction.dues());
                        faction.duesDebt().remove(member);
                    } else {
                        faction.duesDebt().merge(member, faction.dues(), Double::sum);
                    }
                }
            }
            if (!faction.rentExempt() && rentPerClaim > 0) {
                double rent = faction.claims().size() * rentPerClaim;
                if (faction.bankBalance() >= rent) faction.bankBalance(faction.bankBalance() - rent);
                else {
                    faction.rentDebt(faction.rentDebt() + Math.max(0, rent - faction.bankBalance()));
                    faction.bankBalance(0);
                    String policy = plugin.getConfig().getString("rent.failure-policy", "debt").toLowerCase(Locale.ROOT);
                    if (policy.equals("unclaim-all")) {
                        for (String claim : new HashSet<>(faction.claims())) claimOwner.remove(claim);
                        faction.claims().clear();
                        faction.claimZones().clear();
                    } else if (policy.equals("disband") && !faction.permanent()) {
                        Bukkit.getScheduler().runTask(plugin, () -> forceDisband(faction));
                    }
                }
            }
        }
        save();
    }

    public void setDues(Player player, double amount) {
        Faction faction = of(player.getUniqueId());
        if (faction != null && faction.rank(player.getUniqueId()) == FactionRank.LEADER) {
            faction.dues(amount);
            save();
        }
    }

    public void saveVault(Faction faction, ItemStack[] contents) {
        if (faction == null) return;
        faction.vault().clear();
        for (int i = 0; i < 54; i++) faction.vault().add(i < contents.length && contents[i] != null ? contents[i].clone() : null);
        save();
    }

    public ItemStack[] vaultContents(Faction faction) {
        ItemStack[] out = new ItemStack[54];
        for (int i = 0; i < Math.min(54, faction.vault().size()); i++) {
            ItemStack item = faction.vault().get(i);
            out[i] = item == null ? null : item.clone();
        }
        return out;
    }

    public void announce(Faction faction, String message) {
        if (faction == null) return;
        for (UUID uuid : faction.members().keySet()) {
            Player player = Bukkit.getPlayer(uuid);
            if (player != null) plugin.msg(player, message);
        }
    }

    public void sendChannel(Player sender, Component message) {
        Faction mine = of(sender.getUniqueId());
        if (mine == null) return;
        ChatMode mode = chatMode(sender.getUniqueId());
        String prefix = switch (mode) {
            case FACTION -> "&d[F] ";
            case ALLY -> "&5[A] ";
            case TRUCE -> "&b[T] ";
            default -> "";
        };
        Component full = plugin.component(prefix + "&f" + sender.getName() + "&7: ").append(message);
        for (Player player : Bukkit.getOnlinePlayers()) {
            Faction theirs = of(player.getUniqueId());
            boolean receive = switch (mode) {
                case FACTION -> theirs == mine;
                case ALLY -> theirs == mine || relation(mine, theirs) == Relation.ALLY;
                case TRUCE -> theirs == mine || relation(mine, theirs) == Relation.TRUCE;
                default -> false;
            };
            if (receive) player.sendMessage(full);
        }
    }

    public String factionTag(Player viewer, Player subject) {
        Faction faction = of(subject.getUniqueId());
        if (faction == null) return "";
        Faction mine = of(viewer.getUniqueId());
        String color = faction == mine ? "&a" : switch (relation(mine, faction)) {
            case ALLY -> "&d";
            case TRUCE -> "&b";
            case ENEMY -> "&c";
            default -> "&e";
        };
        return color + "[" + faction.name() + "]&r";
    }

    public void cleanupExpiredInvites() {
        long now = System.currentTimeMillis();
        boolean changed = false;
        for (Faction faction : factions.values()) changed |= faction.invites().entrySet().removeIf(entry -> entry.getValue() < now);
        if (changed) save();
    }

    private void clearTransient(UUID uuid) {
        chatModes.remove(uuid);
        autoClaim.remove(uuid);
        seeChunk.remove(uuid);
        factionFlight.remove(uuid);
    }

    private boolean claimsAllowed(World world) {
        List<String> worlds = plugin.getConfig().getStringList("claims.worlds");
        return worlds.isEmpty() || worlds.stream().anyMatch(name -> name.equalsIgnoreCase(world.getName()));
    }

    private String normalizeName(String name, int max) {
        if (name == null) return null;
        String clean = name.trim().toLowerCase(Locale.ROOT);
        return clean.matches("[a-z0-9_-]{1," + max + "}") ? clean : null;
    }

    private int countMaterial(Player player, Material material) {
        int total = 0;
        for (ItemStack item : player.getInventory().getStorageContents()) if (item != null && item.getType() == material) total += item.getAmount();
        return total;
    }

    private void removeMaterial(Player player, Material material, int amount) {
        int left = amount;
        for (ItemStack item : player.getInventory().getStorageContents()) {
            if (item == null || item.getType() != material || left <= 0) continue;
            int take = Math.min(left, item.getAmount());
            item.setAmount(item.getAmount() - take);
            left -= take;
        }
        player.updateInventory();
    }

    private void giveMaterial(Player player, Material material, int amount) {
        int left = amount;
        while (left > 0) {
            ItemStack stack = new ItemStack(material, Math.min(material.getMaxStackSize(), left));
            player.getInventory().addItem(stack).values().forEach(item -> player.getWorld().dropItemNaturally(player.getLocation(), item));
            left -= stack.getAmount();
        }
    }

    private String formatDuration(long millis) {
        long seconds = Math.max(0, millis / 1000);
        long hours = seconds / 3600;
        long minutes = (seconds % 3600) / 60;
        long secs = seconds % 60;
        return (hours > 0 ? hours + "h " : "") + (minutes > 0 ? minutes + "m " : "") + secs + "s";
    }

    public void save() {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.set("meta.graceUntil", graceUntil);
        yaml.set("meta.lastDailyCollection", lastDailyCollection);
        yaml.set("special.safezone", new ArrayList<>(safeZoneClaims));
        yaml.set("special.warzone", new ArrayList<>(warZoneClaims));
        for (Map.Entry<UUID, Double> entry : power.entrySet()) yaml.set("power." + entry.getKey(), entry.getValue());
        for (Faction faction : factions.values()) {
            String base = "factions." + faction.id();
            yaml.set(base + ".name", faction.name());
            yaml.set(base + ".description", faction.description());
            yaml.set(base + ".link", faction.link());
            yaml.set(base + ".createdAt", faction.createdAt());
            yaml.set(base + ".open", faction.open());
            yaml.set(base + ".peaceful", faction.peaceful());
            yaml.set(base + ".permanent", faction.permanent());
            yaml.set(base + ".rentExempt", faction.rentExempt());
            yaml.set(base + ".powerBoost", faction.powerBoost());
            yaml.set(base + ".permanentPower", faction.permanentPower());
            yaml.set(base + ".bank", faction.bankBalance());
            yaml.set(base + ".tnt", faction.tntBalance());
            yaml.set(base + ".dues", faction.dues());
            yaml.set(base + ".rentDebt", faction.rentDebt());
            yaml.set(base + ".shieldUntil", faction.shieldUntil());
            yaml.set(base + ".shieldCooldownUntil", faction.shieldCooldownUntil());
            for (Map.Entry<UUID, FactionRank> member : faction.members().entrySet()) yaml.set(base + ".members." + member.getKey(), member.getValue().name());
            for (Map.Entry<UUID, String> title : faction.titles().entrySet()) yaml.set(base + ".titles." + title.getKey(), title.getValue());
            for (Map.Entry<UUID, Long> invite : faction.invites().entrySet()) yaml.set(base + ".invites." + invite.getKey(), invite.getValue());
            yaml.set(base + ".bans", faction.bans().stream().map(UUID::toString).toList());
            for (Map.Entry<UUID, Double> debt : faction.duesDebt().entrySet()) yaml.set(base + ".duesDebt." + debt.getKey(), debt.getValue());
            yaml.set(base + ".claims", new ArrayList<>(faction.claims()));
            for (Map.Entry<UUID, Relation> relation : faction.relations().entrySet()) yaml.set(base + ".relations." + relation.getKey(), relation.getValue().name());
            for (Map.Entry<UUID, Relation> request : faction.relationRequests().entrySet()) yaml.set(base + ".relationRequests." + request.getKey(), request.getValue().name());
            for (Map.Entry<FactionPermission, FactionRank> permission : faction.minimumRanks().entrySet()) yaml.set(base + ".permissions.ranks." + permission.getKey().name(), permission.getValue().name());
            for (Map.Entry<FactionPermission, Set<Relation>> permission : faction.relationAccess().entrySet()) yaml.set(base + ".permissions.relations." + permission.getKey().name(), permission.getValue().stream().map(Enum::name).toList());
            yaml.set(base + ".home", faction.home());
            for (Map.Entry<String, Location> warp : faction.warps().entrySet()) yaml.set(base + ".warps." + warp.getKey(), warp.getValue());
            for (Map.Entry<UpgradeType, Integer> upgrade : faction.upgrades().entrySet()) yaml.set(base + ".upgrades." + upgrade.getKey().name(), upgrade.getValue());
            for (Map.Entry<String, FactionZone> zoneEntry : faction.zones().entrySet()) {
                String zoneBase = base + ".zones." + zoneEntry.getKey();
                FactionZone zone = zoneEntry.getValue();
                yaml.set(zoneBase + ".greeting", zone.greeting());
                for (Map.Entry<FactionPermission, FactionRank> permission : zone.minimumRanks().entrySet()) yaml.set(zoneBase + ".permissions." + permission.getKey().name(), permission.getValue().name());
            }
            for (Map.Entry<String, String> zone : faction.claimZones().entrySet()) yaml.set(base + ".claimZones." + encodeClaimKey(zone.getKey()), zone.getValue());
            yaml.set(base + ".vault", faction.vault());
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
        graceUntil = yaml.getLong("meta.graceUntil", 0L);
        lastDailyCollection = yaml.getLong("meta.lastDailyCollection", System.currentTimeMillis());
        safeZoneClaims.addAll(yaml.getStringList("special.safezone"));
        warZoneClaims.addAll(yaml.getStringList("special.warzone"));
        ConfigurationSection powerSection = yaml.getConfigurationSection("power");
        if (powerSection != null) for (String key : powerSection.getKeys(false)) try { power.put(UUID.fromString(key), powerSection.getDouble(key)); } catch (IllegalArgumentException ignored) { }
        ConfigurationSection factionSection = yaml.getConfigurationSection("factions");
        if (factionSection == null) return;
        for (String idText : factionSection.getKeys(false)) {
            try {
                UUID id = UUID.fromString(idText);
                String base = "factions." + idText;
                Faction faction = new Faction(id, yaml.getString(base + ".name", "Faction"), yaml.getLong(base + ".createdAt", System.currentTimeMillis()));
                faction.description(yaml.getString(base + ".description", ""));
                faction.link(yaml.getString(base + ".link", ""));
                faction.open(yaml.getBoolean(base + ".open", false));
                faction.peaceful(yaml.getBoolean(base + ".peaceful", false));
                faction.permanent(yaml.getBoolean(base + ".permanent", false));
                faction.rentExempt(yaml.getBoolean(base + ".rentExempt", false));
                faction.powerBoost(yaml.getDouble(base + ".powerBoost", 0.0));
                if (yaml.contains(base + ".permanentPower")) faction.permanentPower(yaml.getDouble(base + ".permanentPower"));
                faction.bankBalance(yaml.getDouble(base + ".bank", 0.0));
                faction.tntBalance(yaml.getInt(base + ".tnt", 0));
                faction.dues(yaml.getDouble(base + ".dues", 0.0));
                faction.rentDebt(yaml.getDouble(base + ".rentDebt", 0.0));
                faction.shieldUntil(yaml.getLong(base + ".shieldUntil", 0L));
                faction.shieldCooldownUntil(yaml.getLong(base + ".shieldCooldownUntil", 0L));
                ConfigurationSection members = yaml.getConfigurationSection(base + ".members");
                if (members != null) for (String uuidText : members.getKeys(false)) {
                    UUID uuid = UUID.fromString(uuidText);
                    FactionRank rank;
                    try { rank = FactionRank.valueOf(members.getString(uuidText, "MEMBER")); } catch (Exception ignored) { rank = FactionRank.MEMBER; }
                    faction.members().put(uuid, rank);
                    playerFaction.put(uuid, id);
                }
                ConfigurationSection titles = yaml.getConfigurationSection(base + ".titles");
                if (titles != null) for (String uuidText : titles.getKeys(false)) faction.titles().put(UUID.fromString(uuidText), titles.getString(uuidText, ""));
                ConfigurationSection invites = yaml.getConfigurationSection(base + ".invites");
                if (invites != null) for (String uuidText : invites.getKeys(false)) faction.invites().put(UUID.fromString(uuidText), invites.getLong(uuidText));
                else for (String uuidText : yaml.getStringList(base + ".invites")) faction.invites().put(UUID.fromString(uuidText), System.currentTimeMillis() + 30 * 60_000L);
                for (String uuidText : yaml.getStringList(base + ".bans")) faction.bans().add(UUID.fromString(uuidText));
                ConfigurationSection debts = yaml.getConfigurationSection(base + ".duesDebt");
                if (debts != null) for (String uuidText : debts.getKeys(false)) faction.duesDebt().put(UUID.fromString(uuidText), debts.getDouble(uuidText));
                for (String claim : yaml.getStringList(base + ".claims")) { faction.claims().add(claim); claimOwner.put(claim, id); }
                ConfigurationSection relations = yaml.getConfigurationSection(base + ".relations");
                if (relations != null) for (String uuidText : relations.getKeys(false)) faction.relations().put(UUID.fromString(uuidText), Relation.valueOf(relations.getString(uuidText, "NEUTRAL")));
                ConfigurationSection requests = yaml.getConfigurationSection(base + ".relationRequests");
                if (requests != null) for (String uuidText : requests.getKeys(false)) faction.relationRequests().put(UUID.fromString(uuidText), Relation.valueOf(requests.getString(uuidText, "ALLY")));
                for (String uuidText : yaml.getStringList(base + ".allyRequests")) faction.relationRequests().put(UUID.fromString(uuidText), Relation.ALLY);
                ConfigurationSection rankPerms = yaml.getConfigurationSection(base + ".permissions.ranks");
                if (rankPerms != null) for (String key : rankPerms.getKeys(false)) faction.minimumRanks().put(FactionPermission.valueOf(key), FactionRank.valueOf(rankPerms.getString(key)));
                ConfigurationSection relationPerms = yaml.getConfigurationSection(base + ".permissions.relations");
                if (relationPerms != null) for (String key : relationPerms.getKeys(false)) {
                    Set<Relation> set = EnumSet.noneOf(Relation.class);
                    for (String relation : relationPerms.getStringList(key)) set.add(Relation.valueOf(relation));
                    faction.relationAccess().put(FactionPermission.valueOf(key), set);
                }
                Location home = yaml.getLocation(base + ".home");
                if (home == null) home = loadLegacyLocation(yaml, base + ".home");
                faction.home(home);
                ConfigurationSection warps = yaml.getConfigurationSection(base + ".warps");
                if (warps != null) for (String name : warps.getKeys(false)) {
                    Location location = yaml.getLocation(base + ".warps." + name);
                    if (location != null) faction.warps().put(name.toLowerCase(Locale.ROOT), location);
                }
                ConfigurationSection upgrades = yaml.getConfigurationSection(base + ".upgrades");
                if (upgrades != null) for (String key : upgrades.getKeys(false)) try { faction.upgrades().put(UpgradeType.valueOf(key), upgrades.getInt(key)); } catch (Exception ignored) { }
                ConfigurationSection zones = yaml.getConfigurationSection(base + ".zones");
                if (zones != null) for (String name : zones.getKeys(false)) {
                    FactionZone zone = new FactionZone(name.toLowerCase(Locale.ROOT));
                    zone.greeting(yaml.getString(base + ".zones." + name + ".greeting", ""));
                    ConfigurationSection perms = yaml.getConfigurationSection(base + ".zones." + name + ".permissions");
                    if (perms != null) for (String key : perms.getKeys(false)) zone.minimumRanks().put(FactionPermission.valueOf(key), FactionRank.valueOf(perms.getString(key)));
                    faction.zones().put(name.toLowerCase(Locale.ROOT), zone);
                }
                ConfigurationSection claimZones = yaml.getConfigurationSection(base + ".claimZones");
                if (claimZones != null) for (String encoded : claimZones.getKeys(false)) faction.claimZones().put(decodeClaimKey(encoded), claimZones.getString(encoded));
                List<?> vaultList = yaml.getList(base + ".vault", List.of());
                faction.vault().clear();
                for (Object object : vaultList) faction.vault().add(object instanceof ItemStack item ? item : null);
                while (faction.vault().size() < 54) faction.vault().add(null);
                if (faction.vault().size() > 54) faction.vault().subList(54, faction.vault().size()).clear();
                factions.put(id, faction);
            } catch (Exception exception) {
                plugin.getLogger().warning("Skipped invalid faction record " + idText + ": " + exception.getMessage());
            }
        }
        cleanupExpiredInvites();
    }

    private Location loadLegacyLocation(YamlConfiguration yaml, String base) {
        String worldName = yaml.getString(base + ".world");
        if (worldName == null) return null;
        World world = Bukkit.getWorld(worldName);
        if (world == null) return null;
        return new Location(world, yaml.getDouble(base + ".x"), yaml.getDouble(base + ".y"), yaml.getDouble(base + ".z"), (float) yaml.getDouble(base + ".yaw"), (float) yaml.getDouble(base + ".pitch"));
    }

    private String encodeClaimKey(String key) { return Base64.getUrlEncoder().withoutPadding().encodeToString(key.getBytes(java.nio.charset.StandardCharsets.UTF_8)); }
    private String decodeClaimKey(String key) { return new String(Base64.getUrlDecoder().decode(key), java.nio.charset.StandardCharsets.UTF_8); }

    public record Result(boolean success, String message) {
        public static Result ok(String message) { return new Result(true, message); }
        public static Result fail(String message) { return new Result(false, message); }
    }
}
