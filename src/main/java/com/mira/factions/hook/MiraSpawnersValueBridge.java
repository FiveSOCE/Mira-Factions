package com.mira.factions.hook;

import com.mira.factions.MiraFactionsPlugin;
import com.mira.factions.service.FactionLandValueService;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.EntityType;
import org.bukkit.event.Event;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.plugin.Plugin;

/** Version-neutral live spawner stack changes; legacy FTop continues using cached scans. */
public final class MiraSpawnersValueBridge implements Listener {
    @SuppressWarnings("unchecked")
    public MiraSpawnersValueBridge(MiraFactionsPlugin plugin, FactionLandValueService landValue) {
        Plugin spawners = Bukkit.getPluginManager().getPlugin("MiraSpawners");
        if (spawners == null || !spawners.isEnabled()) return;
        try {
            Class<?> eventClass = Class.forName("com.mira.spawners.api.event.SpawnerStackChangeEvent",
                    false, spawners.getClass().getClassLoader());
            if (!Event.class.isAssignableFrom(eventClass)) return;
            Bukkit.getPluginManager().registerEvent((Class<? extends Event>) eventClass, this,
                    EventPriority.MONITOR, (listener, event) -> {
                        try {
                            Object location = eventClass.getMethod("location").invoke(event);
                            Object type = eventClass.getMethod("entityType").invoke(event);
                            Object oldAmount = eventClass.getMethod("oldAmount").invoke(event);
                            Object newAmount = eventClass.getMethod("newAmount").invoke(event);
                            if (location instanceof Location loc && type instanceof EntityType entity
                                    && oldAmount instanceof Number oldCount && newAmount instanceof Number newCount) {
                                landValue.applySpawnerChange(loc, entity, oldCount.intValue(), newCount.intValue());
                            }
                        } catch (ReflectiveOperationException ex) {
                            plugin.getLogger().warning("MiraSpawners stack event could not be read: " + ex.getMessage());
                        }
                    }, plugin, true);
        } catch (ClassNotFoundException | LinkageError | IllegalArgumentException ex) {
            plugin.getLogger().warning("MiraSpawners live valuation event unavailable: " + ex.getMessage());
        }
    }
}
