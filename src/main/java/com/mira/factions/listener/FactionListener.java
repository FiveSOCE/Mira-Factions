package com.mira.factions.listener;

import com.mira.factions.MiraFactionsPlugin;
import com.mira.factions.gui.FactionGuiService;
import com.mira.factions.model.*;
import com.mira.factions.service.FactionService;
import com.mira.factions.util.CosmeticsBridge;
import io.papermc.paper.event.player.AsyncChatEvent;
import org.bukkit.*;
import org.bukkit.block.*;
import org.bukkit.entity.Player;
import org.bukkit.event.*;
import org.bukkit.event.block.*;
import org.bukkit.event.entity.*;
import org.bukkit.event.inventory.*;
import org.bukkit.event.player.*;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;

import java.util.*;

public final class FactionListener implements Listener {
    private final MiraFactionsPlugin plugin;
    private final FactionService service;
    private final FactionGuiService gui;
    private final Map<UUID, String> territory = new HashMap<>();
    private final Map<UUID, String> zone = new HashMap<>();

    public FactionListener(MiraFactionsPlugin plugin, FactionService service, FactionGuiService gui) {
        this.plugin = plugin;
        this.service = service;
        this.gui = gui;
    }

    @EventHandler
    public void onGuiClick(InventoryClickEvent event) {
        if (!(event.getView().getTopInventory().getHolder() instanceof FactionGuiService.Holder holder)) return;
        if (!(event.getWhoClicked() instanceof Player player)) return;
        Faction faction = service.byId(holder.factionId());
        if (faction == null || service.of(player.getUniqueId()) != faction) { event.setCancelled(true); player.closeInventory(); return; }

        if (holder.menu() == FactionGuiService.Menu.UPGRADES) {
            event.setCancelled(true);
            if (event.getRawSlot() < 0 || event.getRawSlot() >= event.getView().getTopInventory().getSize()) return;
            UpgradeType type = gui.upgradeForSlot(event.getRawSlot());
            if (type == null) return;
            respond(player, service.buyUpgrade(player, type));
            gui.openUpgrades(player);
            return;
        }

        int allowed = service.vaultSlots(faction);
        int raw = event.getRawSlot();
        if (raw >= 0 && raw < 54 && raw >= allowed) event.setCancelled(true);
        if (event.isShiftClick() && event.getClickedInventory() != null && event.getClickedInventory() == player.getInventory()) {
            int firstEmpty = firstEmptyVault(event.getView().getTopInventory(), allowed);
            if (firstEmpty == -1) event.setCancelled(true);
        }
    }

    @EventHandler
    public void onGuiDrag(InventoryDragEvent event) {
        if (!(event.getView().getTopInventory().getHolder() instanceof FactionGuiService.Holder holder) || holder.menu() != FactionGuiService.Menu.VAULT) return;
        Faction faction = service.byId(holder.factionId());
        if (faction == null) { event.setCancelled(true); return; }
        int allowed = service.vaultSlots(faction);
        for (int raw : event.getRawSlots()) if (raw < 54 && raw >= allowed) { event.setCancelled(true); return; }
    }

    @EventHandler
    public void onGuiClose(InventoryCloseEvent event) {
        if (!(event.getInventory().getHolder() instanceof FactionGuiService.Holder holder) || holder.menu() != FactionGuiService.Menu.VAULT) return;
        Faction faction = service.byId(holder.factionId());
        gui.saveVault(event.getInventory(), faction);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onChat(AsyncChatEvent event) {
        Player player = event.getPlayer();
        ChatMode mode = service.chatMode(player.getUniqueId());
        if (mode != ChatMode.PUBLIC) {
            if (service.of(player.getUniqueId()) == null) { service.chatMode(player.getUniqueId(), ChatMode.PUBLIC); return; }
            event.setCancelled(true);
            Bukkit.getScheduler().runTask(plugin, () -> service.sendChannel(player, event.message()));
            return;
        }
        if (plugin.getConfig().getBoolean("chat.public-faction-tag", true) && service.of(player.getUniqueId()) != null) {
            event.message(plugin.component("&7[&d" + service.of(player.getUniqueId()).name() + "&7] &r").append(event.message()));
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onBreak(BlockBreakEvent event) {
        if (!service.can(event.getPlayer(), event.getBlock().getLocation(), FactionPermission.DESTROY)) deny(event, event.getPlayer());
    }

    @EventHandler(ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent event) {
        if (!service.can(event.getPlayer(), event.getBlock().getLocation(), FactionPermission.BUILD)) deny(event, event.getPlayer());
    }

    @EventHandler(ignoreCancelled = true)
    public void onBucketEmpty(PlayerBucketEmptyEvent event) {
        if (!service.can(event.getPlayer(), event.getBlock().getLocation(), FactionPermission.BUILD)) deny(event, event.getPlayer());
    }

    @EventHandler(ignoreCancelled = true)
    public void onBucketFill(PlayerBucketFillEvent event) {
        if (!service.can(event.getPlayer(), event.getBlock().getLocation(), FactionPermission.DESTROY)) deny(event, event.getPlayer());
    }

    @EventHandler(ignoreCancelled = true)
    public void onInteract(PlayerInteractEvent event) {
        Block block = event.getClickedBlock();
        if (block == null || event.getAction() != Action.RIGHT_CLICK_BLOCK) return;

        TerritoryType territory = service.territoryType(block.getLocation());
        if ((territory == TerritoryType.SAFEZONE || territory == TerritoryType.WARZONE)
                && isProtectedZoneUtility(block.getType())) {
            return;
        }

        FactionPermission permission = interactionPermission(block);
        if (!service.can(event.getPlayer(), block.getLocation(), permission)) deny(event, event.getPlayer());
    }

    @EventHandler(ignoreCancelled = true)
    public void onExplosion(EntityExplodeEvent event) {
        event.blockList().removeIf(block -> protectExplosion(block.getLocation()));
    }

    @EventHandler(ignoreCancelled = true)
    public void onBlockExplosion(BlockExplodeEvent event) {
        event.blockList().removeIf(block -> protectExplosion(block.getLocation()));
    }

    private boolean protectExplosion(Location location) {
        TerritoryType type = service.territoryType(location);
        if (type == TerritoryType.SAFEZONE) return true;
        if (type == TerritoryType.WARZONE || type == TerritoryType.WILDERNESS) return false;
        Faction owner = service.owner(location);
        if (owner == null) return false;
        if (owner.peaceful() || service.graceActive() || service.shielded(owner)) return true;
        return !service.raidable(owner) || !plugin.getConfig().getBoolean("raiding.allow-explosions", true);
    }

    @EventHandler(ignoreCancelled = true)
    public void onPistonExtend(BlockPistonExtendEvent event) {
        Location source = event.getBlock().getLocation();
        for (Block block : event.getBlocks()) {
            Location destination = block.getRelative(event.getDirection()).getLocation();
            if (!sameTerritory(source, destination)) { event.setCancelled(true); return; }
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onPistonRetract(BlockPistonRetractEvent event) {
        Location source = event.getBlock().getLocation();
        for (Block block : event.getBlocks()) if (!sameTerritory(source, block.getLocation())) { event.setCancelled(true); return; }
    }

    @EventHandler(ignoreCancelled = true)
    public void onFlow(BlockFromToEvent event) {
        if (!sameTerritory(event.getBlock().getLocation(), event.getToBlock().getLocation())) event.setCancelled(true);
    }

    @EventHandler(ignoreCancelled = true)
    public void onInventoryMove(InventoryMoveItemEvent event) {
        Location source = location(event.getSource());
        Location destination = location(event.getDestination());
        if (source != null && destination != null && !sameTerritory(source, destination)) event.setCancelled(true);
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGH)
    public void onPvp(EntityDamageByEntityEvent event) {
        if (!(event.getEntity() instanceof Player victim)) return;
        Player attacker = null;
        if (event.getDamager() instanceof Player player) attacker = player;
        else if (event.getDamager() instanceof org.bukkit.entity.Projectile projectile && projectile.getShooter() instanceof Player player) attacker = player;
        if (attacker != null && !service.canPvp(attacker, victim, victim.getLocation())) event.setCancelled(true);
    }

    @EventHandler
    public void onDeath(PlayerDeathEvent event) { service.death(event.getPlayer()); }

    @EventHandler
    public void onRespawn(PlayerRespawnEvent event) {
        if (!plugin.getConfig().getBoolean("home.respawn-at-faction-home", false)) return;
        Faction faction = service.of(event.getPlayer().getUniqueId());
        if (faction != null && faction.home() != null) event.setRespawnLocation(faction.home());
    }

    @EventHandler
    public void onMove(PlayerMoveEvent event) {
        if (event.getTo() == null) return;
        if (event.getFrom().getChunk().equals(event.getTo().getChunk())) return;
        Player player = event.getPlayer();
        if (service.autoClaim(player.getUniqueId())) {
            FactionService.Result result = service.claim(player, event.getTo());
            if (!result.success() && !result.message().contains("already owns")) plugin.msg(player, "&cAuto-claim: " + result.message());
        }
        service.updateFlight(player);
        updateTerritory(player, event.getTo());
    }

    @EventHandler
    public void onTeleport(PlayerTeleportEvent event) {
        Bukkit.getScheduler().runTask(plugin, () -> {
            service.updateFlight(event.getPlayer());
            if (event.getTo() != null) updateTerritory(event.getPlayer(), event.getTo());
        });
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        updateTerritory(player, player.getLocation());
        Faction faction = service.of(player.getUniqueId());
        if (faction != null && plugin.getConfig().getBoolean("members.login-notifications", true)) service.announce(faction, "&7" + player.getName() + " joined the server.");
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        Faction faction = service.of(event.getPlayer().getUniqueId());
        if (faction != null && plugin.getConfig().getBoolean("members.login-notifications", true)) service.announce(faction, "&7" + event.getPlayer().getName() + " left the server.");
    }

    private void updateTerritory(Player player, Location location) {
        TerritoryType type = service.territoryType(location);
        Faction owner = service.owner(location);
        String now = switch (type) {
            case SAFEZONE -> "SAFEZONE";
            case WARZONE -> "WARZONE";
            case WILDERNESS -> "WILDERNESS";
            case FACTION -> owner == null ? "WILDERNESS" : owner.id().toString();
        };
        String old = territory.put(player.getUniqueId(), now);
        if (!Objects.equals(old, now)) {
            String raw = switch (type) {
                case SAFEZONE -> plugin.getConfig().getString("messages.territory-safezone", "&aEntering SafeZone");
                case WARZONE -> plugin.getConfig().getString("messages.territory-warzone", "&cEntering WarZone");
                case WILDERNESS -> plugin.getConfig().getString("messages.territory-wilderness", "&7Entering Wilderness");
                case FACTION -> plugin.getConfig().getString("messages.territory-faction", "&aEntering &f%faction% &aterritory").replace("%faction%", owner == null ? "Wilderness" : owner.name());
            };
            player.sendActionBar(plugin.component(raw));
            if (type == TerritoryType.SAFEZONE) {
                CosmeticsBridge.play(player, "safezone_enter", location);
            } else if (type == TerritoryType.WARZONE) {
                CosmeticsBridge.play(player, "warzone_enter", location);
            }
        }
        if (owner != null) {
            String zoneName = owner.zoneForClaim(service.claimKey(location));
            String oldZone = zone.put(player.getUniqueId(), zoneName == null ? "" : zoneName);
            if (!Objects.equals(oldZone, zoneName) && zoneName != null) {
                FactionZone factionZone = owner.zone(zoneName);
                if (factionZone != null && !factionZone.greeting().isBlank()) player.sendActionBar(plugin.component(factionZone.greeting()));
            }
        } else zone.remove(player.getUniqueId());
    }

    private boolean isProtectedZoneUtility(Material type) {
        return switch (type) {
            case CHEST, TRAPPED_CHEST, BARREL, ENDER_CHEST,
                    ANVIL, CHIPPED_ANVIL, DAMAGED_ANVIL,
                    ENCHANTING_TABLE -> true;
            default -> false;
        };
    }

    private FactionPermission interactionPermission(Block block) {
        if (block.getState() instanceof Container) return FactionPermission.CONTAINER;
        String type = block.getType().name();
        if (type.contains("DOOR") || type.contains("TRAPDOOR") || type.contains("FENCE_GATE")) return FactionPermission.DOOR;
        if (type.contains("BUTTON")) return FactionPermission.BUTTON;
        if (type.equals("LEVER")) return FactionPermission.LEVER;
        if (type.contains("PRESSURE_PLATE")) return FactionPermission.PRESSURE_PLATE;
        return FactionPermission.USE;
    }

    private boolean sameTerritory(Location a, Location b) {
        if (service.territoryType(a) != service.territoryType(b)) return false;
        return service.owner(a) == service.owner(b);
    }

    private Location location(Inventory inventory) {
        InventoryHolder holder = inventory.getHolder();
        if (holder instanceof BlockState state) return state.getLocation();
        return null;
    }

    private int firstEmptyVault(Inventory inventory, int allowed) {
        for (int i = 0; i < allowed; i++) if (inventory.getItem(i) == null || inventory.getItem(i).getType().isAir()) return i;
        return -1;
    }

    private void deny(Cancellable event, Player player) {
        event.setCancelled(true);
        plugin.msg(player, "&cYou cannot do that in this territory.");
    }

    private void respond(Player player, FactionService.Result result) {
        if (!result.message().isBlank()) plugin.msg(player, (result.success() ? "&a" : "&c") + result.message());
    }
}
