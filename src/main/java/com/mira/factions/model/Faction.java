package com.mira.factions.model;

import org.bukkit.Location;
import java.util.*;

public final class Faction {
    private final UUID id;
    private String name;
    private final Map<UUID, FactionRank> members = new LinkedHashMap<>();
    private final Set<UUID> invites = new HashSet<>();
    private final Set<String> claims = new HashSet<>();
    private final Map<UUID, Relation> relations = new HashMap<>();
    private final Set<UUID> allyRequests = new HashSet<>();
    private Location home;

    public Faction(UUID id, String name) {
        this.id = id;
        this.name = name;
    }

    public UUID id() { return id; }
    public String name() { return name; }
    public void name(String value) { name = value; }
    public Map<UUID, FactionRank> members() { return members; }
    public Set<UUID> invites() { return invites; }
    public Set<String> claims() { return claims; }
    public Map<UUID, Relation> relations() { return relations; }
    public Set<UUID> allyRequests() { return allyRequests; }
    public Location home() { return home; }
    public void home(Location value) { home = value; }
    public FactionRank rank(UUID player) { return members.get(player); }
    public boolean isMember(UUID player) { return members.containsKey(player); }
}
