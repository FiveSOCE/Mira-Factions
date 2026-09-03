package com.mira.factions.listener;

import com.mira.factions.MiraFactionsPlugin;
import com.mira.factions.model.TerritoryType;
import com.mira.factions.service.FactionService;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.entity.ProjectileLaunchEvent;
import org.bukkit.event.player.PlayerInteractEvent;
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

    private boolean bypass(Player player) {
        return player.hasPermission("mirafactions.protectedzone.bypass")
                || player.hasPermission("mirafactions.bypass")
                || service.bypass(player.getUniqueId());
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onCreatureSpawn(CreatureSpawnEvent event) {
        if (protectedZone(event.getLocation())) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onDamage(EntityDamageEvent event) {
        if (event.getEntity() instanceof Player player
                && service.territoryType(player.getLocation()) == TerritoryType.SAFEZONE
                && !bypass(player)) {
            event.setCancelled(true);
            player.setFireTicks(0);
        }
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
        if (!protectedZone(player.getLocation()) || bypass(player)) return;
        ItemStack item = event.getItem();
        if (item == null || item.getType().isAir()) return;
        Material type = item.getType();
        if (type.name().endsWith("_SPAWN_EGG") || isThrowableOrLaunchItem(type)) {
            event.setCancelled(true);
            plugin.msg(player, "&cYou cannot use that item in this protected zone.");
        }
    }

    private boolean isThrowableOrLaunchItem(Material type) {
        return switch (type) {
            case BOW, CROSSBOW, TRIDENT,
                    SNOWBALL, EGG, ENDER_PEARL, EXPERIENCE_BOTTLE,
                    SPLASH_POTION, LINGERING_POTION, FIREWORK_ROCKET,
                    WIND_CHARGE -> true;
            default -> false;
        };
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
