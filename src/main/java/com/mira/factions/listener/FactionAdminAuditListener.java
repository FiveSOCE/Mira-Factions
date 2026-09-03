package com.mira.factions.listener;

import com.mira.factions.MiraFactionsPlugin;
import com.mira.factions.model.Faction;
import com.mira.factions.model.UpgradeType;
import com.mira.factions.service.FactionHistoryService;
import com.mira.factions.service.FactionService;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;

import java.util.*;

public final class FactionAdminAuditListener implements Listener {
    private final MiraFactionsPlugin plugin;
    private final FactionService service;
    private final FactionHistoryService history;

    public FactionAdminAuditListener(MiraFactionsPlugin plugin, FactionService service, FactionHistoryService history) {
        this.plugin = plugin;
        this.service = service;
        this.history = history;
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onCommand(PlayerCommandPreprocessEvent event) {
        if (!event.getPlayer().hasPermission("mirafactions.admin")) return;
        String raw = event.getMessage();
        if (raw == null || raw.length() < 2) return;
        String[] args = raw.substring(1).trim().split("\\s+");
        if (args.length < 2 || !isAdminAlias(args[0])) return;

        Faction target = targetFaction(args);
        if (target == null) return;
        AdminSnapshot before = AdminSnapshot.capture(target);
        UUID actor = event.getPlayer().getUniqueId();
        String command = raw.substring(1);
        plugin.getServer().getScheduler().runTask(plugin, () -> auditAfter(target.id(), before, actor, command));
    }

    private void auditAfter(UUID factionId, AdminSnapshot before, UUID actor, String command) {
        Faction now = service.byId(factionId);
        if (now == null) {
            history.logAudit(factionId, before.name(), actor, "ADMIN_DISBAND", "Operator executed /" + command + ".");
            return;
        }
        if (!now.name().equals(before.name())) history.logAudit(now, actor, "ADMIN_RENAME", before.name() + " renamed to " + now.name() + ".");
        if (Math.abs(now.bankBalance() - before.bank()) > 0.0001D) {
            history.logBank(now, actor, "ADMIN", now.bankBalance() - before.bank(), now.bankBalance());
            history.acknowledgeBank(now);
        }
        if (now.tntBalance() != before.tnt()) history.logAudit(now, actor, "ADMIN_TNT", "TNT changed from " + before.tnt() + " to " + now.tntBalance() + ".");
        if (now.peaceful() != before.peaceful()) history.logAudit(now, actor, "ADMIN_SETTING", "Peaceful changed to " + now.peaceful() + ".");
        if (now.permanent() != before.permanent()) history.logAudit(now, actor, "ADMIN_SETTING", "Permanent changed to " + now.permanent() + ".");
        if (now.rentExempt() != before.rentExempt()) history.logAudit(now, actor, "ADMIN_SETTING", "Rent exempt changed to " + now.rentExempt() + ".");
        if (now.shieldUntil() != before.shieldUntil()) history.logAudit(now, actor, "ADMIN_SHIELD", "Shield state changed by operator.");
        if (Double.compare(now.powerBoost(), before.powerBoost()) != 0) history.logAudit(now, actor, "ADMIN_POWER", "Faction power boost changed from " + before.powerBoost() + " to " + now.powerBoost() + ".");
        for (UpgradeType type : UpgradeType.values()) {
            int old = before.upgrades().getOrDefault(type, 0);
            int level = now.upgrade(type);
            if (old != level) history.logAudit(now, actor, "ADMIN_UPGRADE", type.name() + " changed from level " + old + " to " + level + ".");
        }
    }

    private Faction targetFaction(String[] args) {
        String sub = args[1].toLowerCase(Locale.ROOT);
        String name = null;
        if (Set.of("disband", "rename", "peaceful", "permanent", "rentexempt").contains(sub) && args.length >= 3) name = args[2];
        else if (Set.of("money", "tnt", "upgrade", "powerboost", "permanentpower", "shield").contains(sub) && args.length >= 4) name = args[3];
        if (name == null) return null;
        return service.byName(name);
    }

    private static boolean isAdminAlias(String raw) {
        return raw.equalsIgnoreCase("fa") || raw.equalsIgnoreCase("fadmin") || raw.equalsIgnoreCase("factionadmin");
    }

    private record AdminSnapshot(String name, double bank, int tnt, boolean peaceful, boolean permanent,
                                 boolean rentExempt, long shieldUntil, double powerBoost, Map<UpgradeType, Integer> upgrades) {
        static AdminSnapshot capture(Faction faction) {
            Map<UpgradeType, Integer> upgrades = new EnumMap<>(UpgradeType.class);
            upgrades.putAll(faction.upgrades());
            return new AdminSnapshot(faction.name(), faction.bankBalance(), faction.tntBalance(), faction.peaceful(), faction.permanent(),
                    faction.rentExempt(), faction.shieldUntil(), faction.powerBoost(), upgrades);
        }
    }
}
