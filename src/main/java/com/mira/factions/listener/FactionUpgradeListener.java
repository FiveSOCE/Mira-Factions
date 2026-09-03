package com.mira.factions.listener;

import com.mira.factions.MiraFactionsPlugin;
import com.mira.factions.model.Faction;
import com.mira.factions.model.UpgradeType;
import com.mira.factions.service.FactionService;
import org.bukkit.block.CreatureSpawner;
import org.bukkit.block.data.Ageable;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockDropItemEvent;
import org.bukkit.event.block.BlockGrowEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.SpawnerSpawnEvent;

import java.util.concurrent.ThreadLocalRandom;

public final class FactionUpgradeListener implements Listener {
    private final MiraFactionsPlugin plugin;
    private final FactionService service;

    public FactionUpgradeListener(MiraFactionsPlugin plugin, FactionService service) {
        this.plugin = plugin;
        this.service = service;
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.NORMAL)
    public void onTerritoryCombat(EntityDamageByEntityEvent event) {
        Player attacker = attackingPlayer(event);
        if (attacker != null) {
            Faction faction = service.of(attacker.getUniqueId());
            if (faction != null && service.owner(attacker.getLocation()) == faction) {
                int level = faction.upgrade(UpgradeType.DAMAGE);
                if (level > 0) {
                    double perLevel = plugin.getConfig().getDouble("upgrades.damage.bonus-per-level", 0.05);
                    event.setDamage(event.getDamage() * (1.0 + level * perLevel));
                }
            }
        }

        if (event.getEntity() instanceof Player victim) {
            Faction faction = service.of(victim.getUniqueId());
            if (faction != null && service.owner(victim.getLocation()) == faction) {
                int level = faction.upgrade(UpgradeType.DEFENSE);
                if (level > 0) {
                    double perLevel = plugin.getConfig().getDouble("upgrades.defense.reduction-per-level", 0.05);
                    double multiplier = Math.max(0.25, 1.0 - level * perLevel);
                    event.setDamage(event.getDamage() * multiplier);
                }
            }
        }
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.MONITOR)
    public void onMobDeath(EntityDeathEvent event) {
        Player killer = event.getEntity().getKiller();
        if (killer == null || event.getEntity() instanceof Player) return;
        Faction faction = service.of(killer.getUniqueId());
        if (faction == null) return;

        int xpLevel = faction.upgrade(UpgradeType.MOB_XP);
        if (xpLevel > 0) {
            double bonus = plugin.getConfig().getDouble("upgrades.mob-xp.bonus-per-level", 0.10);
            event.setDroppedExp((int) Math.round(event.getDroppedExp() * (1.0 + xpLevel * bonus)));
        }

        int dropLevel = faction.upgrade(UpgradeType.MOB_DROPS);
        if (dropLevel <= 0 || event.getDrops().isEmpty()) return;
        double chance = Math.min(1.0, dropLevel * plugin.getConfig().getDouble("upgrades.mob-drops.extra-roll-chance-per-level", 0.10));
        if (ThreadLocalRandom.current().nextDouble() >= chance) return;

        var original = event.getDrops().stream().map(item -> item.clone()).toList();
        event.getDrops().addAll(original);
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.MONITOR)
    public void onCropDrops(BlockDropItemEvent event) {
        if (!(event.getBlockState().getBlockData() instanceof Ageable ageable) || ageable.getAge() < ageable.getMaximumAge()) return;
        Player player = event.getPlayer();
        Faction faction = service.of(player.getUniqueId());
        if (faction == null || service.owner(event.getBlock().getLocation()) != faction) return;
        int level = faction.upgrade(UpgradeType.CROP_YIELD);
        if (level <= 0) return;
        double chance = Math.min(1.0, level * plugin.getConfig().getDouble("upgrades.crop-yield.extra-roll-chance-per-level", 0.10));
        if (ThreadLocalRandom.current().nextDouble() >= chance) return;
        for (Item item : event.getItems()) item.getItemStack().setAmount(Math.min(item.getItemStack().getMaxStackSize(), item.getItemStack().getAmount() * 2));
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGH)
    public void onCropGrow(BlockGrowEvent event) {
        Faction faction = service.owner(event.getBlock().getLocation());
        if (faction == null) return;
        int level = faction.upgrade(UpgradeType.CROP_GROWTH);
        if (level <= 0) return;
        if (!(event.getNewState().getBlockData() instanceof Ageable ageable)) return;
        double chance = Math.min(1.0, level * plugin.getConfig().getDouble("upgrades.crop-growth.extra-stage-chance-per-level", 0.10));
        if (ThreadLocalRandom.current().nextDouble() >= chance || ageable.getAge() >= ageable.getMaximumAge()) return;
        ageable.setAge(Math.min(ageable.getMaximumAge(), ageable.getAge() + 1));
        event.getNewState().setBlockData(ageable);
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.MONITOR)
    public void onSpawnerSpawn(SpawnerSpawnEvent event) {
        CreatureSpawner spawner = event.getSpawner();
        Faction faction = service.owner(spawner.getLocation());
        if (faction == null) return;
        int level = faction.upgrade(UpgradeType.SPAWNER_RATE);
        if (level <= 0) return;
        double bonus = Math.max(0.0, level * plugin.getConfig().getDouble("upgrades.spawner-rate.speed-bonus-per-level", 0.10));
        plugin.getServer().getScheduler().runTask(plugin, () -> {
            if (!spawner.getBlock().getChunk().isLoaded()) return;
            int current = Math.max(1, spawner.getDelay());
            int accelerated = Math.max(1, (int) Math.round(current / (1.0 + bonus)));
            spawner.setDelay(accelerated);
            spawner.update(true, false);
        });
    }

    private Player attackingPlayer(EntityDamageByEntityEvent event) {
        if (event.getDamager() instanceof Player player) return player;
        if (event.getDamager() instanceof Projectile projectile && projectile.getShooter() instanceof Player player) return player;
        return null;
    }
}
