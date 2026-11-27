package com.mira.factions.hook;

import com.mira.factions.MiraFactionsPlugin;
import com.mira.factions.service.FactionLandValueService;
import org.bukkit.Bukkit;
import org.bukkit.entity.EntityType;
import org.bukkit.event.Event;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.plugin.Plugin;
import java.util.EnumMap;
import java.util.Map;

/** Late-bound bridge so modern MiraShop API bytecode is never linked on Java 17. */
public final class MiraShopPriceBridge implements Listener {
    @SuppressWarnings("unchecked")
    public MiraShopPriceBridge(MiraFactionsPlugin plugin, FactionLandValueService landValue) {
        landValue.refreshSpawnerPricesFromService();
        Plugin shop = Bukkit.getPluginManager().getPlugin("MiraShop");
        if (shop == null || !shop.isEnabled()) return;
        try {
            Class<?> eventClass = Class.forName("com.mira.shop.api.SpawnerPriceCacheEvent",
                    false, shop.getClass().getClassLoader());
            if (!Event.class.isAssignableFrom(eventClass)) return;
            Bukkit.getPluginManager().registerEvent((Class<? extends Event>) eventClass, this,
                    EventPriority.MONITOR, (listener, event) -> {
                        try {
                            Object raw = eventClass.getMethod("prices").invoke(event);
                            if (!(raw instanceof Map<?, ?> prices)) return;
                            Map<EntityType, Double> safe = new EnumMap<>(EntityType.class);
                            for (Map.Entry<?, ?> entry : prices.entrySet()) {
                                if (entry.getKey() instanceof EntityType type && entry.getValue() instanceof Number value)
                                    safe.put(type, value.doubleValue());
                            }
                            landValue.updateSpawnerPrices(safe);
                        } catch (ReflectiveOperationException ex) {
                            plugin.getLogger().warning("Unable to receive MiraShop price update: " + ex.getMessage());
                        }
                    }, plugin, true);
        } catch (ClassNotFoundException | LinkageError | IllegalArgumentException ex) {
            plugin.getLogger().warning("MiraShop price cache event unavailable on legacy server: " + ex.getMessage());
        }
    }
}
