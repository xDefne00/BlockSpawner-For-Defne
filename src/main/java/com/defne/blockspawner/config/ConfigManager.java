package com.defne.blockspawner.config;

import com.defne.blockspawner.BlockSpawnerPlugin;
import com.defne.blockspawner.model.SpawnerType;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class ConfigManager {
    public enum DropMode {
        NATURAL,
        INVENTORY,
        HYBRID
    }

    private final BlockSpawnerPlugin plugin;
    private Map<String, SpawnerType> spawnerTypes = Map.of();

    public ConfigManager(BlockSpawnerPlugin plugin) {
        this.plugin = plugin;
    }

    public void load() {
        plugin.reloadConfig();
        ConfigurationSection section = plugin.getConfig().getConfigurationSection("blockspawners");
        Map<String, SpawnerType> parsed = new HashMap<>();
        if (section != null) {
            for (String key : section.getKeys(false)) {
                String typeId = key.toLowerCase(Locale.ROOT);
                String materialName = section.getString(key + ".material", "STONE");
                Material material = Material.matchMaterial(materialName);
                if (material == null || !material.isItem()) {
                    plugin.getLogger().warning("Invalid material for spawner type " + typeId + ": " + materialName);
                    continue;
                }
                int interval = Math.max(1, section.getInt(key + ".interval", 10));
                int amount = Math.max(1, section.getInt(key + ".amount", 1));
                parsed.put(typeId, new SpawnerType(typeId, material, interval, amount));
            }
        }
        spawnerTypes = Collections.unmodifiableMap(parsed);
    }

    public Map<String, SpawnerType> getSpawnerTypes() {
        return spawnerTypes;
    }

    public SpawnerType getType(String id) {
        if (id == null) {
            return null;
        }
        return spawnerTypes.get(id.toLowerCase(Locale.ROOT));
    }

    public int getMaxSpawnPerCycle() {
        return Math.max(1, plugin.getConfig().getInt("global.max-spawn-per-cycle", 500));
    }

    public int getPerPlayerLimit() {
        return Math.max(1, plugin.getConfig().getInt("limits.per-player", 200));
    }

    public int getPerChunkLimit() {
        return Math.max(1, plugin.getConfig().getInt("limits.per-chunk", 50));
    }

    public boolean isDebug() {
        return plugin.getConfig().getBoolean("debug", false);
    }

    public String getStorageFile() {
        return plugin.getConfig().getString("storage.file", "blockspawners.db");
    }

    public String getLanguage() {
        return plugin.getConfig().getString("language", "tr").toLowerCase(Locale.ROOT);
    }

    public DropMode getDropMode() {
        String raw = plugin.getConfig().getString("drop.mode", "NATURAL");
        try {
            return DropMode.valueOf(raw.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            return DropMode.NATURAL;
        }
    }

    public double getDropHeight() {
        return plugin.getConfig().getDouble("drop.height", 1.2D);
    }

    public int getPickupDelay() {
        return Math.max(0, plugin.getConfig().getInt("drop.pickup-delay", 10));
    }

    public boolean isRandomOffsetEnabled() {
        return plugin.getConfig().getBoolean("drop.random-offset", true);
    }

    public boolean isRequireSilkTouch() {
        return plugin.getConfig().getBoolean("break.require-silk-touch", true);
    }

    public boolean isAllowOwnerOnly() {
        return plugin.getConfig().getBoolean("break.allow-owner-only", true);
    }

    public boolean isDropItemOnBreak() {
        return plugin.getConfig().getBoolean("break.drop-item", true);
    }

    public double getCreateSpawnerCost() {
        return Math.max(0.0D, plugin.getConfig().getDouble("gui.create-cost", 15000.0D));
    }

    public int getMaxLevel() {
        return Math.max(1, plugin.getConfig().getInt("levels.max-level", 5));
    }

    public double getIntervalMultiplier() {
        return Math.max(0.01D, plugin.getConfig().getDouble("levels.scaling.interval-multiplier", 0.85D));
    }

    public double getAmountMultiplier() {
        return Math.max(1.0D, plugin.getConfig().getDouble("levels.scaling.amount-multiplier", 1.2D));
    }

    public double getUpgradeCost(int level) {
        return Math.max(0.0D, plugin.getConfig().getDouble("levels.upgrade-cost." + level, 0.0D));
    }

    public List<String> getHologramFormat() {
        List<String> lines = plugin.getConfig().getStringList("hologram");
        if (lines.isEmpty()) {
            lines = plugin.getConfig().getStringList("hologram.lines");
        }
        if (lines.isEmpty()) {
            return List.of("&b%type% Spawner", "&7Level: &f%level%", "&a+%amount% / %interval%s");
        }
        return new ArrayList<>(lines);
    }
}
