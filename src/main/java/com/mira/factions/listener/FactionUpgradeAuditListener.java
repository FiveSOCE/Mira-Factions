package com.mira.factions.listener;

import com.mira.factions.MiraFactionsPlugin;
import com.mira.factions.gui.FactionGuiService;
import com.mira.factions.model.Faction;
import com.mira.factions.model.UpgradeType;
import com.mira.factions.service.FactionHistoryService;
import com.mira.factions.service.FactionService;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;

public final class FactionUpgradeAuditListener implements Listener {
    private static final int[] SLOTS = {10,11,12,13,14,15,16,19,20,21,22,23,24,25,28,29,30};
    private final MiraFactionsPlugin plugin;
    private final FactionService service;
    private final FactionHistoryService history;

    public FactionUpgradeAuditListener(MiraFactionsPlugin plugin, FactionService service, FactionHistoryService history) {
        this.plugin = plugin;
        this.service = service;
        this.history = history;
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        if (!(event.getView().getTopInventory().getHolder() instanceof FactionGuiService.Holder holder)) return;
        if (holder.menu() != FactionGuiService.Menu.UPGRADES) return;
        UpgradeType type = typeForSlot(event.getRawSlot());
        if (type == null) return;
        Faction faction = service.byId(holder.factionId());
        if (faction == null) return;
        int beforeLevel = faction.upgrade(type);
        double beforeBank = faction.bankBalance();
        plugin.getServer().getScheduler().runTask(plugin, () -> {
            Faction now = service.byId(holder.factionId());
            if (now == null) return;
            int afterLevel = now.upgrade(type);
            if (afterLevel == beforeLevel) return;
            history.logAudit(now, player.getUniqueId(), "UPGRADE", type.name() + " upgraded from level " + beforeLevel + " to " + afterLevel + ".");
            double delta = now.bankBalance() - beforeBank;
            if (Math.abs(delta) > 0.0001D) history.logBank(now, player.getUniqueId(), "UPGRADE_" + type.name(), delta, now.bankBalance());
            history.acknowledgeBank(now);
        });
    }

    private UpgradeType typeForSlot(int slot) {
        UpgradeType[] types = UpgradeType.values();
        for (int i = 0; i < Math.min(SLOTS.length, types.length); i++) if (SLOTS[i] == slot) return types[i];
        return null;
    }
}
