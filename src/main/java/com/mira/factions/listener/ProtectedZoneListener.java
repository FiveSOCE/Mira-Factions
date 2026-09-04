package com.mira.factions.listener;

import com.mira.factions.MiraFactionsPlugin;
import com.mira.factions.model.TerritoryType;
import com.mira.factions.service.FactionService;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.BlockState;
import org.bukkit.block.data.Directional;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockBurnEvent;
import org.bukkit.event.block.BlockDispenseEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.block.BlockFromToEvent;
import org.bukkit.event.block.BlockIgniteEvent;
import org.bukkit.event.block.BlockPistonExtendEvent;
import org.bukkit.event.block.BlockPistonRetractEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.AreaEffectCloudApplyEvent;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.entity.EntityChangeBlockEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.entity.EntityPotionEffectEvent;
import org.bukkit.event.entity.ProjectileLaunchEvent;
import org.bukkit.event.hanging.HangingBreakEvent;
import org.bukkit.event.hanging.HangingPlaceEvent;
import org.bukkit.event.inventory.InventoryMoveItemEvent;
import org.bukkit.event.player.PlayerArmorStandManipulateEvent;
import org.bukkit.event.player.PlayerBucketEmptyEvent;
import org.bukkit.event.player.PlayerBucketFillEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;

/**
 * Absolute SafeZone / WarZone rules. Normal faction permissions and relations do not weaken these protections.
 */
public final class ProtectedZoneListener implements Listener {
    private final MiraFactionsPlugin plugin;
    private final FactionService service;

    public ProtectedZoneListener(MiraFactionsPlugin plugin, FactionService service) {
        this.plugin = plugin;
        this.service = service;
    }

    private boolean protectedZone(Location location) {
        TerritoryType type = service.territoryType(location);
        return type == TerritoryType.SAFEZONE || type == TerritoryType.WARZONE;
    }

    private boolean safeZone(Location location) {
        return service.territoryType(location) == TerritoryType.SAFEZONE;
    }

    private boolean bypass(Player player) {
        return player.hasPermission("mirafactions.protectedzone.bypass")
                || player.hasPermission("mirafactions.bypass")
                || service.bypass(player.getUniqueId());
    }

    private void denied(Player player) {
        plugin.msg(player, "&cYou cannot do that in this protected zone.");
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBlockPlace(BlockPlaceEvent event) {
        if (protectedZone(event.getBlockPlaced().getLocation()) && !bypass(event.getPlayer())) {
            event.setCancelled(true);
            denied(event.getPlayer());
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        if (protectedZone(event.getBlock().getLocation()) && !bypass(event.getPlayer())) {
            event.setCancelled(true);
            denied(event.getPlayer());
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onCreatureSpawn(CreatureSpawnEvent event) {
        if (protectedZone(event.getLocation())) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onDamage(EntityDamageEvent event) {
        if (event.getEntity() instanceof Player player && safeZone(player.getLocation()) && !bypass(player)) {
            event.setCancelled(true);
            player.setFireTicks(0);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onProtectedEntityDamage(EntityDamageByEntityEvent event) {
        if (!protectedZone(event.getEntity().getLocation())) return;
        Player player = attackingPlayer(event.getDamager());
        if (player != null && !bypass(player) && !(event.getEntity() instanceof Player)) {
            event.setCancelled(true);
            denied(player);
        }
    }

    private Player attackingPlayer(Entity entity) {
        if (entity instanceof Player player) return player;
        if (entity instanceof Projectile projectile && projectile.getShooter() instanceof Player player) return player;
        return null;
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onProjectileLaunch(ProjectileLaunchEvent event) {
        Projectile projectile = event.getEntity();
        if (!(projectile.getShooter() instanceof Player player)) return;
        if (protectedZone(player.getLocation()) && !bypass(player)) {
            event.setCancelled(true);
            plugin.msg(player, "&cYou cannot launch projectiles in this protected zone.");
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onInteract(PlayerInteractEvent event) {
        Player player = event.getPlayer();
        Location check = event.getClickedBlock() == null ? player.getLocation() : event.getClickedBlock().getLocation();
        if (!protectedZone(check) || bypass(player)) return;

        ItemStack item = event.getItem();
        if (event.getClickedBlock() != null) {
            event.setCancelled(true);
            denied(player);
            return;
        }
        if (item == null || item.getType().isAir()) return;
        Material type = item.getType();
        if (type.name().endsWith("_SPAWN_EGG") || isThrowableOrLaunchItem(type) || isVehicleItem(type)) {
            event.setCancelled(true);
            denied(player);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onInteractEntity(PlayerInteractEntityEvent event) {
        if (protectedZone(event.getRightClicked().getLocation()) && !bypass(event.getPlayer())) {
            event.setCancelled(true);
            denied(event.getPlayer());
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onArmorStandManipulate(PlayerArmorStandManipulateEvent event) {
        if (protectedZone(event.getRightClicked().getLocation()) && !bypass(event.getPlayer())) {
            event.setCancelled(true);
            denied(event.getPlayer());
        }
    }

    private boolean isThrowableOrLaunchItem(Material type) {
        return switch (type) {
            case BOW, CROSSBOW, TRIDENT,
                    SNOWBALL, EGG, ENDER_PEARL, EXPERIENCE_BOTTLE,
                    SPLASH_POTION, LINGERING_POTION, FIREWORK_ROCKET,
                    WIND_CHARGE, END_CRYSTAL, RESPAWN_ANCHOR -> true;
            default -> false;
        };
    }

    private boolean isVehicleItem(Material type) {
        String name = type.name();
        return name.endsWith("_BOAT") || name.endsWith("_RAFT") || name.endsWith("_MINECART") || type == Material.MINECART;
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBucketEmpty(PlayerBucketEmptyEvent event) {
        if (protectedZone(event.getBlockClicked().getRelative(event.getBlockFace()).getLocation()) && !bypass(event.getPlayer())) {
            event.setCancelled(true);
            denied(event.getPlayer());
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBucketFill(PlayerBucketFillEvent event) {
        if (protectedZone(event.getBlockClicked().getLocation()) && !bypass(event.getPlayer())) {
            event.setCancelled(true);
            denied(event.getPlayer());
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPistonExtend(BlockPistonExtendEvent event) {
        if (protectedZone(event.getBlock().getLocation())
                || protectedZone(event.getBlock().getRelative(event.getDirection()).getLocation())
                || event.getBlocks().stream().anyMatch(block -> protectedZone(block.getLocation())
                || protectedZone(block.getRelative(event.getDirection()).getLocation()))) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPistonRetract(BlockPistonRetractEvent event) {
        if (protectedZone(event.getBlock().getLocation())
                || event.getBlocks().stream().anyMatch(block -> protectedZone(block.getLocation())
                || protectedZone(block.getRelative(event.getDirection()).getLocation()))) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onFluidFlow(BlockFromToEvent event) {
        if (protectedZone(event.getBlock().getLocation()) || protectedZone(event.getToBlock().getLocation())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onIgnite(BlockIgniteEvent event) {
        if (protectedZone(event.getBlock().getLocation())) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBurn(BlockBurnEvent event) {
        if (protectedZone(event.getBlock().getLocation())) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onEntityChangeBlock(EntityChangeBlockEvent event) {
        if (protectedZone(event.getBlock().getLocation())) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onDispense(BlockDispenseEvent event) {
        boolean sourceProtected = protectedZone(event.getBlock().getLocation());
        boolean targetProtected = false;
        if (event.getBlock().getBlockData() instanceof Directional directional) {
            targetProtected = protectedZone(event.getBlock().getRelative(directional.getFacing()).getLocation());
        }
        if (sourceProtected || targetProtected) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onHangingPlace(HangingPlaceEvent event) {
        Player player = event.getPlayer();
        if (player != null && protectedZone(event.getEntity().getLocation()) && !bypass(player)) {
            event.setCancelled(true);
            denied(player);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onHangingBreak(HangingBreakEvent event) {
        if (protectedZone(event.getEntity().getLocation())) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onInventoryMove(InventoryMoveItemEvent event) {
        if (protectedInventory(event.getSource().getHolder()) || protectedInventory(event.getDestination().getHolder())) {
            event.setCancelled(true);
        }
    }

    private boolean protectedInventory(InventoryHolder holder) {
        return holder instanceof BlockState state && protectedZone(state.getLocation());
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPotionEffect(EntityPotionEffectEvent event) {
        if (!(event.getEntity() instanceof Player player) || !safeZone(player.getLocation()) || bypass(player)) return;
        if (event.getCause() == EntityPotionEffectEvent.Cause.POTION_SPLASH
                || event.getCause() == EntityPotionEffectEvent.Cause.AREA_EFFECT_CLOUD
                || event.getCause() == EntityPotionEffectEvent.Cause.ARROW) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onAreaEffectCloud(AreaEffectCloudApplyEvent event) {
        event.getAffectedEntities().removeIf(entity ->
                entity instanceof Player player && safeZone(player.getLocation()) && !bypass(player));
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onEntityExplosion(EntityExplodeEvent event) {
        event.blockList().removeIf(block -> protectedZone(block.getLocation()));
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBlockExplosion(BlockExplodeEvent event) {
        event.blockList().removeIf(block -> protectedZone(block.getLocation()));
    }
}
