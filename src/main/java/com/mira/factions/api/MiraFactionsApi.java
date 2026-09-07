package com.mira.factions.api;

import com.mira.factions.model.Relation;
import org.bukkit.Location;
import org.bukkit.entity.Player;

import java.util.Optional;
import java.util.UUID;

public interface MiraFactionsApi {
    Optional<String> factionName(UUID player);
    Optional<String> factionRank(UUID player);
    Optional<UUID> factionId(UUID player);
    Optional<String> territoryFaction(Location location);
    Relation relation(UUID firstPlayer, UUID secondPlayer);
    double playerPower(UUID player);
    double factionPower(UUID factionId);
    boolean isRaidable(UUID factionId);
    boolean isSafeZone(Location location);
    boolean isWarZone(Location location);
    boolean canBuild(Player player, Location location);

    /** True only when the location is claimed by the player's own faction. */
    boolean isOwnClaim(Player player, Location location);

    /** Current faction bank balance for the player's faction, or 0 when factionless. */
    double factionBankBalance(UUID player);

    /** Atomically withdraws from the player's faction bank when sufficient funds exist. */
    boolean withdrawFactionBank(UUID player, double amount);

    /** True when the player's faction has the FLIGHT upgrade and the player has faction FLY permission. */
    boolean hasFactionFlightEntitlement(Player player);

    /** Describes the player's relationship to the territory at the supplied location for MiraFly policy checks. */
    FlightTerritory flightTerritory(Player player, Location location);
}
