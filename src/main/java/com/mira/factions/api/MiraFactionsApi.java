package com.mira.factions.api;

import com.mira.factions.model.Relation;
import org.bukkit.Location;
import org.bukkit.entity.Player;

import java.util.Optional;
import java.util.UUID;

public interface MiraFactionsApi {
    Optional<String> factionName(UUID player);
    Optional<UUID> factionId(UUID player);
    Optional<String> territoryFaction(Location location);
    Relation relation(UUID firstPlayer, UUID secondPlayer);
    double playerPower(UUID player);
    double factionPower(UUID factionId);
    boolean isRaidable(UUID factionId);
    boolean isSafeZone(Location location);
    boolean isWarZone(Location location);
    boolean canBuild(Player player, Location location);
}
