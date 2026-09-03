package com.mira.factions.model;

public enum FactionPermission {
    BUILD(FactionRank.MEMBER),
    DESTROY(FactionRank.MEMBER),
    CONTAINER(FactionRank.MEMBER),
    USE(FactionRank.RECRUIT),
    DOOR(FactionRank.RECRUIT),
    BUTTON(FactionRank.RECRUIT),
    LEVER(FactionRank.RECRUIT),
    PRESSURE_PLATE(FactionRank.RECRUIT),
    INVITE(FactionRank.OFFICER),
    KICK(FactionRank.OFFICER),
    BAN(FactionRank.OFFICER),
    PROMOTE(FactionRank.COLEADER),
    CLAIM(FactionRank.OFFICER),
    UNCLAIM(FactionRank.OFFICER),
    SETHOME(FactionRank.OFFICER),
    HOME(FactionRank.RECRUIT),
    SETWARP(FactionRank.OFFICER),
    WARP(FactionRank.RECRUIT),
    ECONOMY(FactionRank.COLEADER),
    TNT_DEPOSIT(FactionRank.RECRUIT),
    TNT_WITHDRAW(FactionRank.OFFICER),
    FLY(FactionRank.MEMBER),
    SHIELD(FactionRank.COLEADER),
    UPGRADE(FactionRank.COLEADER),
    VAULT(FactionRank.MEMBER),
    ZONE(FactionRank.COLEADER),
    DIPLOMACY(FactionRank.COLEADER),
    ANNOUNCE(FactionRank.OFFICER),
    DISBAND(FactionRank.LEADER);

    private final FactionRank defaultRank;

    FactionPermission(FactionRank defaultRank) {
        this.defaultRank = defaultRank;
    }

    public FactionRank defaultRank() { return defaultRank; }
}
