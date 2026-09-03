package com.mira.factions.listener;

import com.mira.factions.MiraFactionsPlugin;
import com.mira.factions.gui.FactionGuiService;
import com.mira.factions.model.*;
import com.mira.factions.service.FactionService;
import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.*;
import org.bukkit.block.Container;
import org.bukkit.entity.Player;
import org.bukkit.event.*;
import org.bukkit.event.block.*;
import org.bukkit.event.entity.*;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.*;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public final class FactionListener implements Listener {
    private final MiraFactionsPlugin plugin;
    private final FactionService service;
    private final FactionGuiService gui;
    private final Map<UUID, String> territory = new HashMap<>();
    private final Set<UUID> createPending = ConcurrentHashMap.newKeySet();

    public FactionListener(MiraFactionsPlugin plugin, FactionService service, FactionGuiService gui) {
        this.plugin = plugin;
        this.service = service;
        this.gui = gui;
    }

    @EventHandler
    public void onGui(InventoryClickEvent event) {
        if (!(event.getView().getTopInventory().getHolder() instanceof FactionGuiService.Holder)) return;
        if (!(event.getWhoClicked() instanceof Player player)) return;
        event.setCancelled(true);
        int slot = event.getRawSlot();
        Faction faction = service.of(player.getUniqueId());

        if (faction == null) {
            if (slot == 11) {
                createPending.add(player.getUniqueId());
                player.closeInventory();
                plugin.msg(player, "&eType your new faction name in chat, or &ccancel&e.");
            }
            return;
        }

        switch (slot) {
            case 10 -> respond(player, service.claim(player));
            case 11 -> respond(player, service.unclaim(player));
            case 12 -> respond(player, service.home(player));
            case 13 -> respond(player, service.setHome(player));
            case 14 -> {
                service.toggleFactionChat(player.getUniqueId());
                gui.open(player);
            }
            case 22 -> respond(player, service.leave(player));
            default -> { }
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onChat(AsyncChatEvent event) {
        Player player = event.getPlayer();
        if (createPending.remove(player.getUniqueId())) {
            event.setCancelled(true);
            String text = PlainTextComponentSerializer.plainText().serialize(event.message()).trim();
            Bukkit.getScheduler().runTask(plugin, () -> {
                if (text.equalsIgnoreCase("cancel")) {
                    plugin.msg(player, "&cFaction creation cancelled.");
                    gui.open(player);
                } else {
                    respond(player, service.create(player, text));
                }
            });
            return;
        }

        if (!service.factionChat(player.getUniqueId())) return;
        Faction faction = service.of(player.getUniqueId());
        if (faction == null) return;
        event.setCancelled(true);
        Component message = plugin.component("&d[F] &f" + player.getName() + "&7: ").append(event.message());
        Bukkit.getScheduler().runTask(plugin, () -> {
            for (UUID uuid : faction.members().keySet()) {
                Player member = Bukkit.getPlayer(uuid);
                if (member != null) member.sendMessage(message);
            }
        });
    }

    @EventHandler(ignoreCancelled = true)
    public void onBreak(BlockBreakEvent event) {
        if (plugin.getConfig().getBoolean("claims.protect-build", true)
                && !service.canBuild(event.getPlayer(), event.getBlock().getLocation())) deny(event, event.getPlayer());
    }

    @EventHandler(ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent event) {
        if (plugin.getConfig().getBoolean("claims.protect-build", true)
                && !service.canBuild(event.getPlayer(), event.getBlock().getLocation())) deny(event, event.getPlayer());
    }

    @EventHandler(ignoreCancelled = true)
    public void onBucketEmpty(PlayerBucketEmptyEvent event) {
        if (!service.canBuild(event.getPlayer(), event.getBlock().getLocation())) deny(event, event.getPlayer());
    }

    @EventHandler(ignoreCancelled = true)
    public void onBucketFill(PlayerBucketFillEvent event) {
        if (!service.canBuild(event.getPlayer(), event.getBlock().getLocation())) deny(event, event.getPlayer());
    }

    @EventHandler(ignoreCancelled = true)
    public void onInteract(PlayerInteractEvent event) {
        if (event.getClickedBlock() == null || service.canBuild(event.getPlayer(), event.getClickedBlock().getLocation())) return;
        if (event.getClickedBlock().getState() instanceof Container
                && plugin.getConfig().getBoolean("claims.protect-containers", true)) {
            deny(event, event.getPlayer());
        } else if (plugin.getConfig().getBoolean("claims.protect-interactions", true)
                && event.getAction() == Action.RIGHT_CLICK_BLOCK) {
            deny(event, event.getPlayer());
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onExplosion(EntityExplodeEvent event) {
        if (!plugin.getConfig().getBoolean("claims.protect-explosions", true)) return;
        event.blockList().removeIf(block -> service.owner(block.getLocation()) != null);
    }

    @EventHandler(ignoreCancelled = true)
    public void onBlockExplosion(BlockExplodeEvent event) {
        if (!plugin.getConfig().getBoolean("claims.protect-explosions", true)) return;
        event.blockList().removeIf(block -> service.owner(block.getLocation()) != null);
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGH)
    public void onPvp(EntityDamageByEntityEvent event) {
        if (!(event.getEntity() instanceof Player victim)) return;
        Player attacker = null;
        if (event.getDamager() instanceof Player player) attacker = player;
        else if (event.getDamager() instanceof org.bukkit.entity.Projectile projectile
                && projectile.getShooter() instanceof Player player) attacker = player;
        if (attacker == null) return;

        Faction attackFaction = service.of(attacker.getUniqueId());
        Faction victimFaction = service.of(victim.getUniqueId());
        if (attackFaction == null || victimFaction == null) return;

        if (attackFaction == victimFaction && !plugin.getConfig().getBoolean("combat.friendly-fire", false)) {
            event.setCancelled(true);
            return;
        }
        if (attackFaction != victimFaction
                && service.relation(attackFaction, victimFaction) == Relation.ALLY
                && !plugin.getConfig().getBoolean("combat.ally-friendly-fire", false)) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onDeath(PlayerDeathEvent event) {
        service.death(event.getPlayer());
    }

    @EventHandler
    public void onMove(PlayerMoveEvent event) {
        if (event.getTo() == null) return;
        if (event.getFrom().getChunk().equals(event.getTo().getChunk())) return;
        updateTerritory(event.getPlayer(), event.getTo());
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        updateTerritory(event.getPlayer(), event.getPlayer().getLocation());
    }

    private void updateTerritory(Player player, Location location) {
        Faction owner = service.owner(location);
        String now = owner == null ? "" : owner.id().toString();
        String old = territory.put(player.getUniqueId(), now);
        if (Objects.equals(old, now)) return;
        String raw = owner == null
                ? plugin.getConfig().getString("messages.territory-leave", "&7Entering Wilderness")
                : plugin.getConfig().getString("messages.territory-enter", "&aEntering &f%faction% &aterritory")
                    .replace("%faction%", owner.name());
        player.sendActionBar(plugin.component(raw));
    }

    private void deny(Cancellable event, Player player) {
        event.setCancelled(true);
        plugin.msg(player, "&cYou cannot do that in another faction's territory.");
    }

    private void respond(Player player, FactionService.Result result) {
        if (!result.message().isBlank()) plugin.msg(player, (result.success() ? "&a" : "&c") + result.message());
        if (result.success()) Bukkit.getScheduler().runTask(plugin, () -> gui.open(player));
    }
}
