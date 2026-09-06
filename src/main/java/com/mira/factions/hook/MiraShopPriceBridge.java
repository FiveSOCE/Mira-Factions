package com.mira.factions.hook;

import com.mira.factions.MiraFactionsPlugin;
import com.mira.factions.service.FactionLandValueService;
import com.mira.shop.api.SpawnerPriceCacheEvent;
import com.mira.shop.api.SpawnerPriceService;
import org.bukkit.Bukkit;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;

public final class MiraShopPriceBridge implements Listener {
    private final MiraFactionsPlugin plugin;
    private final FactionLandValueService landValue;

    public MiraShopPriceBridge(MiraFactionsPlugin plugin, FactionLandValueService landValue) {
        this.plugin = plugin;
        this.landValue = landValue;

        SpawnerPriceService service = Bukkit.getServicesManager().load(SpawnerPriceService.class);
        if (service != null) landValue.updateSpawnerPrices(service.buyPrices());
    }

    @EventHandler
    public void onSpawnerPriceCache(SpawnerPriceCacheEvent event) {
        landValue.updateSpawnerPrices(event.prices());
    }
}
