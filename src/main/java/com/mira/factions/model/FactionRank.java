package com.mira.factions.model;

public enum FactionRank {
    MEMBER(1), OFFICER(2), COLEADER(3), LEADER(4);

    private final int weight;

    FactionRank(int weight) {
        this.weight = weight;
    }

    public int weight() {
        return weight;
    }

    public boolean atLeast(FactionRank other) {
        return weight >= other.weight;
    }
}
