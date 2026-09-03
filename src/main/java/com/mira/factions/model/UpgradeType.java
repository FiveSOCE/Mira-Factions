package com.mira.factions.model;

import org.bukkit.Material;

public enum UpgradeType {
    POWER("Power", Material.NETHER_STAR, 5),
    MEMBER_LIMIT("Member Limit", Material.PLAYER_HEAD, 5),
    WARP_LIMIT("Warp Limit", Material.ENDER_PEARL, 5),
    VAULT_SIZE("Vault", Material.ENDER_CHEST, 5),
    TNT_CAPACITY("TNT Bank", Material.TNT, 5),
    SHIELD("Shield", Material.SHIELD, 3),
    FLIGHT("Faction Flight", Material.ELYTRA, 1),
    DAMAGE("Territory Damage", Material.DIAMOND_SWORD, 5),
    DEFENSE("Territory Defense", Material.DIAMOND_CHESTPLATE, 5),
    POWER_REGEN("Power Regen", Material.EXPERIENCE_BOTTLE, 5),
    POWER_LOSS("Power Loss", Material.TOTEM_OF_UNDYING, 5),
    MOB_DROPS("Mob Drops", Material.ROTTEN_FLESH, 5),
    MOB_XP("Mob XP", Material.EXPERIENCE_BOTTLE, 5),
    CROP_YIELD("Crop Yield", Material.WHEAT, 5),
    CROP_GROWTH("Crop Growth", Material.BONE_MEAL, 5),
    SPAWNER_RATE("Spawner Rate", Material.SPAWNER, 5),
    ZONE_LIMIT("Zone Limit", Material.OAK_SIGN, 5);

    private final String display;
    private final Material icon;
    private final int maxLevel;

    UpgradeType(String display, Material icon, int maxLevel) {
        this.display = display;
        this.icon = icon;
        this.maxLevel = maxLevel;
    }

    public String display() { return display; }
    public Material icon() { return icon; }
    public int maxLevel() { return maxLevel; }
}
