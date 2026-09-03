package com.mira.factions.model;

import org.bukkit.Location;
import org.bukkit.inventory.ItemStack;

import java.util.*;

public final class Faction {
    private final UUID id;
    private String name;
    private String description = "";
    private String link = "";
    private final long createdAt;
    private boolean open;
    private boolean peaceful;
    private boolean permanent;
    private boolean rentExempt;
    private double powerBoost;
    private Double permanentPower;
    private double bankBalance;
    private int tntBalance;
    private double dues;
    private double rentDebt;
    private long shieldUntil;
    private long shieldCooldownUntil;

    private final Map<UUID, FactionRank> members = new LinkedHashMap<>();
    private final Map<UUID, String> titles = new HashMap<>();
    private final Map<UUID, Long> invites = new HashMap<>();
    private final Set<UUID> bans = new HashSet<>();
    private final Map<UUID, Double> duesDebt = new HashMap<>();
    private final Set<String> claims = new HashSet<>();
    private final Map<UUID, Relation> relations = new HashMap<>();
    private final Map<UUID, Relation> relationRequests = new HashMap<>();
    private final Map<FactionPermission, FactionRank> minimumRanks = new EnumMap<>(FactionPermission.class);
    private final Map<FactionPermission, Set<Relation>> relationAccess = new EnumMap<>(FactionPermission.class);
    private final Map<String, Location> warps = new LinkedHashMap<>();
    private final Map<UpgradeType, Integer> upgrades = new EnumMap<>(UpgradeType.class);
    private final Map<String, FactionZone> zones = new LinkedHashMap<>();
    private final Map<String, String> claimZones = new HashMap<>();
    private final List<ItemStack> vault = new ArrayList<>();
    private Location home;

    public Faction(UUID id, String name) {
        this(id, name, System.currentTimeMillis());
    }

    public Faction(UUID id, String name, long createdAt) {
        this.id = id;
        this.name = name;
        this.createdAt = createdAt;
        for (int i = 0; i < 54; i++) vault.add(null);
    }

    public UUID id() { return id; }
    public String name() { return name; }
    public void name(String value) { name = value; }
    public String description() { return description; }
    public void description(String value) { description = value == null ? "" : value; }
    public String link() { return link; }
    public void link(String value) { link = value == null ? "" : value; }
    public long createdAt() { return createdAt; }
    public boolean open() { return open; }
    public void open(boolean value) { open = value; }
    public boolean peaceful() { return peaceful; }
    public void peaceful(boolean value) { peaceful = value; }
    public boolean permanent() { return permanent; }
    public void permanent(boolean value) { permanent = value; }
    public boolean rentExempt() { return rentExempt; }
    public void rentExempt(boolean value) { rentExempt = value; }
    public double powerBoost() { return powerBoost; }
    public void powerBoost(double value) { powerBoost = value; }
    public Double permanentPower() { return permanentPower; }
    public void permanentPower(Double value) { permanentPower = value; }
    public double bankBalance() { return bankBalance; }
    public void bankBalance(double value) { bankBalance = Math.max(0.0, value); }
    public int tntBalance() { return tntBalance; }
    public void tntBalance(int value) { tntBalance = Math.max(0, value); }
    public double dues() { return dues; }
    public void dues(double value) { dues = Math.max(0.0, value); }
    public double rentDebt() { return rentDebt; }
    public void rentDebt(double value) { rentDebt = Math.max(0.0, value); }
    public long shieldUntil() { return shieldUntil; }
    public void shieldUntil(long value) { shieldUntil = value; }
    public long shieldCooldownUntil() { return shieldCooldownUntil; }
    public void shieldCooldownUntil(long value) { shieldCooldownUntil = value; }
    public Map<UUID, FactionRank> members() { return members; }
    public Map<UUID, String> titles() { return titles; }
    public Map<UUID, Long> invites() { return invites; }
    public Set<UUID> bans() { return bans; }
    public Map<UUID, Double> duesDebt() { return duesDebt; }
    public Set<String> claims() { return claims; }
    public Map<UUID, Relation> relations() { return relations; }
    public Map<UUID, Relation> relationRequests() { return relationRequests; }
    public Map<FactionPermission, FactionRank> minimumRanks() { return minimumRanks; }
    public Map<FactionPermission, Set<Relation>> relationAccess() { return relationAccess; }
    public Map<String, Location> warps() { return warps; }
    public Map<UpgradeType, Integer> upgrades() { return upgrades; }
    public Map<String, FactionZone> zones() { return zones; }
    public Map<String, String> claimZones() { return claimZones; }
    public List<ItemStack> vault() { return vault; }
    public Location home() { return home; }
    public void home(Location value) { home = value; }
    public FactionRank rank(UUID player) { return members.get(player); }
    public boolean isMember(UUID player) { return members.containsKey(player); }
    public String title(UUID player) { return titles.getOrDefault(player, ""); }
    public int upgrade(UpgradeType type) { return upgrades.getOrDefault(type, 0); }
    public FactionRank minimum(FactionPermission permission) { return minimumRanks.getOrDefault(permission, permission.defaultRank()); }
    public boolean relationAllowed(FactionPermission permission, Relation relation) {
        return relationAccess.getOrDefault(permission, Set.of()).contains(relation);
    }
    public String zoneForClaim(String claimKey) { return claimZones.get(claimKey); }
    public FactionZone zone(String name) { return name == null ? null : zones.get(name.toLowerCase(Locale.ROOT)); }
}
