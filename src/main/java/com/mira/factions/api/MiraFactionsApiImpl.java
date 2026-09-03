package com.mira.factions.api;

import com.mira.factions.model.*;
import com.mira.factions.service.FactionService;
import org.bukkit.Location;
import org.bukkit.entity.Player;

import java.util.Optional;
import java.util.UUID;

public final class MiraFactionsApiImpl implements MiraFactionsApi {
    private final FactionService service;

    public MiraFactionsApiImpl(FactionService service) { this.service = service; }

    @Override public Optional<String> factionName(UUID player) {
        Faction faction = service.of(player);
        return faction == null ? Optional.empty() : Optional.of(faction.name());
    }

    @Override public Optional<UUID> factionId(UUID player) {
        Faction faction = service.of(player);
        return faction == null ? Optional.empty() : Optional.of(faction.id());
    }

    @Override public Optional<String> territoryFaction(Location location) {
        Faction faction = service.owner(location);
        return faction == null ? Optional.empty() : Optional.of(faction.name());
    }

    @Override public Relation relation(UUID firstPlayer, UUID secondPlayer) {
        return service.relation(service.of(firstPlayer), service.of(secondPlayer));
    }

    @Override public double playerPower(UUID player) { return service.power(player); }

    @Override public double factionPower(UUID factionId) {
        Faction faction = service.byId(factionId);
        return faction == null ? 0.0 : service.factionPower(faction);
    }

    @Override public boolean isRaidable(UUID factionId) { return service.raidable(service.byId(factionId)); }
    @Override public boolean isSafeZone(Location location) { return service.territoryType(location) == TerritoryType.SAFEZONE; }
    @Override public boolean isWarZone(Location location) { return service.territoryType(location) == TerritoryType.WARZONE; }
    @Override public boolean canBuild(Player player, Location location) { return service.can(player, location, FactionPermission.BUILD); }
}
