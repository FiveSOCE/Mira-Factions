package com.mira.factions.listener;

import com.mira.factions.MiraFactionsPlugin;
import com.mira.factions.api.FactionFlightController;
import com.mira.factions.model.Faction;
import com.mira.factions.model.FactionPermission;
import com.mira.factions.model.UpgradeType;
import com.mira.factions.service.FactionService;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;

import java.util.Locale;

/**
 * Intercepts /f fly and delegates runtime flight state to MiraFly.
 * MiraFactions remains the authority for faction entitlement only.
 */
public final class FactionFlightDelegationListener implements Listener {
    private final MiraFactionsPlugin plugin;
    private final FactionService factions;

    public FactionFlightDelegationListener(MiraFactionsPlugin plugin, FactionService factions) {
        this.plugin = plugin;
        this.factions = factions;
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onFactionFly(PlayerCommandPreprocessEvent event) {
        String raw = event.getMessage().trim().toLowerCase(Locale.ROOT);
        if (!raw.equals("/f fly") && !raw.equals("/faction fly") && !raw.equals("/factions fly")) return;
        event.setCancelled(true);

        var player = event.getPlayer();
        Faction faction = factions.of(player.getUniqueId());
        if (faction == null) {
            plugin.msg(player, "&cYou are not in a faction.");
            return;
        }
        if (faction.upgrade(UpgradeType.FLIGHT) <= 0) {
            plugin.msg(player, "&cYour faction has not unlocked Faction Flight.");
            return;
        }
        if (!factions.hasPermission(player, FactionPermission.FLY)) {
            plugin.msg(player, "&cYou do not have faction permission to fly.");
            return;
        }

        FactionFlightController controller = plugin.getServer().getServicesManager().load(FactionFlightController.class);
        if (controller == null) {
            plugin.msg(player, "&cFaction flight requires MiraFly.");
            return;
        }

        FactionFlightController.ToggleResult result = controller.toggle(player);
        plugin.msg(player, (result.success() ? (result.enabled() ? "&a" : "&7") : "&c") + result.message());
    }
}
