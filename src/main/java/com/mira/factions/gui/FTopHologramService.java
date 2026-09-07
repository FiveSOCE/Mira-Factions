package com.mira.factions.gui;

import com.mira.factions.MiraFactionsPlugin;
import com.mira.factions.model.Faction;
import com.mira.factions.service.FactionLandValueService;
import com.mira.factions.service.FactionService;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Display;
import org.bukkit.entity.TextDisplay;

import java.io.File;
import java.io.IOException;
import java.util.*;

public final class FTopHologramService {
    private static final String TAG = "mirafactions_ftop_hologram";

    private final MiraFactionsPlugin plugin;
    private final FactionService factions;
    private final FactionLandValueService landValue;
    private final File file;
    private Location location;

    public FTopHologramService(MiraFactionsPlugin plugin, FactionService factions, FactionLandValueService landValue) {
        this.plugin = plugin;
        this.factions = factions;
        this.landValue = landValue;
        this.file = new File(plugin.getDataFolder(), "ftop-hologram.yml");
        load();
    }

    public boolean spawn(Location location) {
        if (location == null || location.getWorld() == null) return false;
        this.location = location.clone();
        save();
        refresh();
        return true;
    }

    public boolean remove() {
        boolean existed = location != null || !entities().isEmpty();
        location = null;
        if (file.isFile()) file.delete();
        entities().forEach(TextDisplay::remove);
        return existed;
    }

    public void refresh() {
        if (location == null || location.getWorld() == null) return;

        List<String> lines = lines();
        List<TextDisplay> displays = entities();
        while (displays.size() > lines.size()) displays.removeLast().remove();

        double spacing = 0.30D;
        for (int i = 0; i < lines.size(); i++) {
            TextDisplay display;
            if (i < displays.size()) display = displays.get(i);
            else {
                display = location.getWorld().spawn(location, TextDisplay.class);
                display.addScoreboardTag(TAG);
                display.setBillboard(Display.Billboard.CENTER);
                display.setSeeThrough(true);
                display.setShadowed(true);
                display.setPersistent(true);
                displays.add(display);
            }
            display.teleport(location.clone().add(0, (lines.size() - 1 - i) * spacing, 0));
            display.text(plugin.component(lines.get(i)));
        }
    }

    private List<String> lines() {
        List<String> out = new ArrayList<>();
        out.add("&5&l✦ &d&lFACTION TOP 10 &5&l✦");
        out.add("&8Competitive faction wealth");

        List<Faction> sorted = factions.all().stream()
                .sorted(Comparator.comparingDouble((Faction f) -> landValue.value(f) + f.bankBalance()).reversed()
                        .thenComparing(Faction::name, String.CASE_INSENSITIVE_ORDER))
                .limit(10)
                .toList();

        int place = 1;
        for (Faction faction : sorted) {
            double value = landValue.value(faction) + faction.bankBalance();
            out.add("&d#" + place++ + " &f" + faction.name() + " &8- &a$" + String.format(Locale.US, "%,.0f", value));
        }
        if (sorted.isEmpty()) out.add("&7No factions ranked yet.");
        out.add("&8Updates automatically");
        return out;
    }

    private List<TextDisplay> entities() {
        List<TextDisplay> out = new ArrayList<>();
        for (World world : Bukkit.getWorlds()) {
            for (TextDisplay display : world.getEntitiesByClass(TextDisplay.class)) {
                if (display.getScoreboardTags().contains(TAG)) out.add(display);
            }
        }
        out.sort(Comparator.comparingDouble(TextDisplay::getY).reversed());
        return out;
    }

    private void load() {
        if (!file.isFile()) return;
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
        World world = Bukkit.getWorld(yaml.getString("world", ""));
        if (world == null) return;
        location = new Location(world,
                yaml.getDouble("x"), yaml.getDouble("y"), yaml.getDouble("z"),
                (float) yaml.getDouble("yaw"), (float) yaml.getDouble("pitch"));
    }

    private void save() {
        if (location == null || location.getWorld() == null) return;
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.set("world", location.getWorld().getName());
        yaml.set("x", location.getX());
        yaml.set("y", location.getY());
        yaml.set("z", location.getZ());
        yaml.set("yaw", location.getYaw());
        yaml.set("pitch", location.getPitch());
        try {
            file.getParentFile().mkdirs();
            yaml.save(file);
        } catch (IOException ex) {
            plugin.getLogger().warning("Could not save FTop hologram location: " + ex.getMessage());
        }
    }
}
