package com.mira.factions.hook;

import com.mira.factions.service.FactionLandValueService;
import com.mira.spawners.api.event.SpawnerStackChangeEvent;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;

public final class MiraSpawnersValueBridge implements Listener {
    private final FactionLandValueService landValue;

    public MiraSpawnersValueBridge(FactionLandValueService landValue) {
        this.landValue = landValue;
    }

    @EventHandler
    public void onSpawnerStackChange(SpawnerStackChangeEvent event) {
        landValue.applySpawnerChange(
                event.location(),
                event.entityType(),
                event.oldAmount(),
                event.newAmount()
        );
    }
}
