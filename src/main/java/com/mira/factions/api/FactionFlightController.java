package com.mira.factions.api;

import org.bukkit.entity.Player;

import java.util.UUID;

/**
 * Implemented by MiraFly. MiraFactions owns entitlement only; MiraFly owns runtime flight state.
 */
public interface FactionFlightController {
    ToggleResult toggle(Player player);
    boolean active(UUID playerId);
    void refresh(Player player);

    record ToggleResult(boolean success, boolean enabled, String message) {
        public static ToggleResult enabled(String message) { return new ToggleResult(true, true, message); }
        public static ToggleResult disabled(String message) { return new ToggleResult(true, false, message); }
        public static ToggleResult failed(String message) { return new ToggleResult(false, false, message); }
    }
}
