package com.defne.blockspawner.config;

import com.defne.blockspawner.BlockSpawnerPlugin;
import com.defne.blockspawner.model.SpawnerType;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;

import java.util.Collections;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

public class ConfigManager {
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
}
