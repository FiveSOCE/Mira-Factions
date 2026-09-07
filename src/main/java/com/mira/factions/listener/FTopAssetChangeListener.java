package com.mira.factions.listener;

import com.mira.factions.service.FactionLandValueService;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.BlockState;
import org.bukkit.block.Container;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryMoveItemEvent;
import org.bukkit.inventory.Inventory;

public final class FTopAssetChangeListener implements Listener {

    private final FactionLandValueService landValue;

    public FTopAssetChangeListener(FactionLandValueService landValue) {
        this.landValue = landValue;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent event) {
        Material material = event.getBlockPlaced().getType();
        BlockState state = event.getBlockPlaced().getState();
        if (material == Material.SPAWNER || landValue.isTrackedMaterial(material) || state instanceof Container) {
            landValue.queueRefresh(event.getBlockPlaced().getLocation());
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent event) {
        Material material = event.getBlock().getType();
        BlockState state = event.getBlock().getState();
        if (material == Material.SPAWNER || landValue.isTrackedMaterial(material) || state instanceof Container) {
            landValue.queueRefresh(event.getBlock().getLocation());
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onInventoryClose(InventoryCloseEvent event) {
        Location location = event.getInventory().getLocation();
        if (location != null) landValue.queueRefresh(location);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onInventoryMove(InventoryMoveItemEvent event) {
        queue(event.getSource());
        queue(event.getDestination());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockExplode(BlockExplodeEvent event) {
        landValue.queueRefresh(event.getBlock().getLocation());
        event.blockList().forEach(block -> landValue.queueRefresh(block.getLocation()));
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onEntityExplode(EntityExplodeEvent event) {
        event.blockList().forEach(block -> landValue.queueRefresh(block.getLocation()));
    }

    private void queue(Inventory inventory) {
        Location location = inventory == null ? null : inventory.getLocation();
        if (location != null) landValue.queueRefresh(location);
    }
}
