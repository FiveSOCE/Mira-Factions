package com.mira.factions.model;

import java.util.EnumMap;
import java.util.Map;

public final class FactionZone {
    private final String name;
    private String greeting;
    private final Map<FactionPermission, FactionRank> minimumRanks = new EnumMap<>(FactionPermission.class);

    public FactionZone(String name) {
        this.name = name;
        this.greeting = "";
    }

    public String name() { return name; }
    public String greeting() { return greeting; }
    public void greeting(String greeting) { this.greeting = greeting == null ? "" : greeting; }
    public Map<FactionPermission, FactionRank> minimumRanks() { return minimumRanks; }

    public FactionRank minimum(FactionPermission permission) {
        return minimumRanks.getOrDefault(permission, permission.defaultRank());
    }
}
