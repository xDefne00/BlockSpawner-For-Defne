package com.defne.blockspawner.service;

import com.defne.blockspawner.BlockSpawnerPlugin;
import com.defne.blockspawner.config.ConfigManager;
import com.defne.blockspawner.model.SpawnerInstance;
import com.defne.blockspawner.model.SpawnerType;
import com.defne.blockspawner.util.LocationKey;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.plugin.Plugin;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Optional DecentHolograms integration.
 * Uses reflection so the plugin remains fully functional when DecentHolograms is absent.
 */
public class HologramService {
    private static final String DH_PLUGIN_NAME = "DecentHolograms";

    private final BlockSpawnerPlugin plugin;
    private final ConfigManager configManager;
    private final Map<LocationKey, String> activeHolograms = new ConcurrentHashMap<>();

    private boolean available;
    private Method createMethod;
    private Method getMethod;
    private Method removeMethod;
    private Method setLinesMethod;

    public HologramService(BlockSpawnerPlugin plugin, ConfigManager configManager) {
        this.plugin = plugin;
        this.configManager = configManager;
    }

    public void initialize() {
        Plugin decent = Bukkit.getPluginManager().getPlugin(DH_PLUGIN_NAME);
        if (decent == null || !decent.isEnabled()) {
            available = false;
            return;
        }

        try {
            Class<?> dhApiClass = Class.forName("eu.decentsoftware.holograms.api.DHAPI");
            Class<?> hologramClass = Class.forName("eu.decentsoftware.holograms.api.holograms.Hologram");

            createMethod = dhApiClass.getMethod("createHologram", String.class, Location.class, boolean.class, List.class);
            getMethod = dhApiClass.getMethod("getHologram", String.class);
            removeMethod = dhApiClass.getMethod("removeHologram", String.class);
            setLinesMethod = dhApiClass.getMethod("setHologramLines", hologramClass, List.class);
            available = true;
            debug("DecentHolograms integration enabled.");
        } catch (ReflectiveOperationException ex) {
            available = false;
            plugin.getLogger().warning("DecentHolograms detected but API could not be linked safely: " + ex.getMessage());
        }
    }

    public boolean isAvailable() {
        return available;
    }

    public void createOrUpdate(SpawnerInstance instance) {
        if (!available) {
            return;
        }
        LocationKey key = new LocationKey(instance.world(), instance.x(), instance.y(), instance.z());
        String hologramId = activeHolograms.computeIfAbsent(key, this::hologramId);
        Location location = hologramLocation(key);
        if (location == null) {
            return;
        }

        List<String> lines = buildLines(instance);
        try {
            Object hologram = getMethod.invoke(null, hologramId);
            if (hologram == null) {
                createMethod.invoke(null, hologramId, location, false, lines);
                debug("Created hologram " + hologramId);
                return;
            }
            setLinesMethod.invoke(null, hologram, lines);
            debug("Updated hologram " + hologramId);
        } catch (ReflectiveOperationException ex) {
            plugin.getLogger().warning("Failed to create/update hologram " + hologramId + ": " + ex.getMessage());
        }
    }

    public void remove(LocationKey key) {
        if (!available) {
            return;
        }
        String id = activeHolograms.remove(key);
        if (id == null) {
            id = hologramId(key);
        }
        try {
            removeMethod.invoke(null, id);
            debug("Removed hologram " + id);
        } catch (ReflectiveOperationException ex) {
            plugin.getLogger().warning("Failed to remove hologram " + id + ": " + ex.getMessage());
        }
    }

    public void clearAll() {
        if (!available) {
            activeHolograms.clear();
            return;
        }
        for (LocationKey key : new ArrayList<>(activeHolograms.keySet())) {
            remove(key);
        }
        activeHolograms.clear();
    }

    private List<String> buildLines(SpawnerInstance instance) {
        SpawnerType type = configManager.getType(instance.typeId());
        if (type == null) {
            return List.of("§cUnknown Spawner", "§7Level: " + instance.level(), "§7+0 / 0s");
        }
        return List.of(
                "§b" + type.id().toUpperCase() + " Spawner",
                "§eLevel: " + instance.level(),
                "§a+" + type.amountPerCycle() + " / " + type.intervalSeconds() + "s"
        );
    }

    private Location hologramLocation(LocationKey key) {
        World world = Bukkit.getWorld(key.world());
        if (world == null) {
            return null;
        }
        return new Location(world, key.x() + 0.5, key.y() + 1.8, key.z() + 0.5);
    }

    private String hologramId(LocationKey key) {
        return "blockspawner_" + key.serialized().replace(':', '_');
    }

    private void debug(String message) {
        if (configManager.isDebug()) {
            plugin.getLogger().info("[DEBUG] " + message);
        }
    }
}
