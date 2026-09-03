package com.mira.factions.model;

public enum FactionRank {
    RECRUIT(0),
    MEMBER(1),
    OFFICER(2),
    COLEADER(3),
    LEADER(4);

    private final int weight;

    FactionRank(int weight) {
        this.weight = weight;
    }

    public int weight() { return weight; }
    public boolean atLeast(FactionRank other) { return weight >= other.weight; }

    public FactionRank promote() {
        return switch (this) {
            case RECRUIT -> MEMBER;
            case MEMBER -> OFFICER;
            case OFFICER -> COLEADER;
            default -> this;
        };
    }

    public FactionRank demote() {
        return switch (this) {
            case COLEADER -> OFFICER;
            case OFFICER -> MEMBER;
            case MEMBER -> RECRUIT;
            default -> this;
        };
    }
}
