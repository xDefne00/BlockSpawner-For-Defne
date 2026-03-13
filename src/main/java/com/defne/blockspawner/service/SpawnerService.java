package com.defne.blockspawner.service;

import com.defne.blockspawner.BlockSpawnerPlugin;
import com.defne.blockspawner.config.ConfigManager;
import com.defne.blockspawner.model.SpawnerInstance;
import com.defne.blockspawner.model.SpawnerType;
import com.defne.blockspawner.util.LocationKey;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.BlockState;
import org.bukkit.block.Container;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

public class SpawnerService {
    private final BlockSpawnerPlugin plugin;
    private final ConfigManager configManager;
    private final StorageService storageService;
    private final ConcurrentHashMap<LocationKey, SpawnerInstance> loadedSpawners = new ConcurrentHashMap<>();
    private final Set<String> loadedChunks = ConcurrentHashMap.newKeySet();
    private BukkitTask scanTask;

    public SpawnerService(BlockSpawnerPlugin plugin, ConfigManager configManager, StorageService storageService) {
        this.plugin = plugin;
        this.configManager = configManager;
        this.storageService = storageService;
    }

    public void start() {
        // async scan to handle large spawner counts efficiently
        scanTask = Bukkit.getScheduler().runTaskTimerAsynchronously(plugin, this::scanSpawners, 20L, 20L);
    }

    public void stop() {
        if (scanTask != null) {
            scanTask.cancel();
        }
    }

    public boolean registerSpawner(Location location, String typeId, UUID owner, int level) {
        SpawnerType type = configManager.getType(typeId);
        if (type == null || location.getWorld() == null) {
            return false;
        }
        LocationKey key = LocationKey.from(location);
        SpawnerInstance instance = new SpawnerInstance(key.world(), key.x(), key.y(), key.z(), type.id(), owner, level);
        if (loadedSpawners.putIfAbsent(key, instance) != null) {
            return false;
        }
        storageService.upsertAsync(instance);
        return true;
    }

    public SpawnerInstance getSpawner(Location location) {
        return loadedSpawners.get(LocationKey.from(location));
    }

    public boolean removeSpawner(Location location) {
        LocationKey key = LocationKey.from(location);
        SpawnerInstance removed = loadedSpawners.remove(key);
        if (removed == null) {
            return false;
        }
        storageService.deleteAsync(key.world(), key.x(), key.y(), key.z());
        return true;
    }

    public boolean canBreak(Player player, SpawnerInstance instance) {
        return player.hasPermission("blockspawner.admin") || player.getUniqueId().equals(instance.owner());
    }

    public ItemStack toItem(SpawnerInstance instance, SpawnerItemService itemService) {
        SpawnerType type = configManager.getType(instance.typeId());
        if (type == null) {
            return new ItemStack(Material.SPAWNER);
        }
        return itemService.createSpawnerItem(type, 1, instance.level());
    }

    public Collection<SpawnerInstance> getLoadedSpawners() {
        return loadedSpawners.values();
    }

    public void onChunkLoaded(String world, int chunkX, int chunkZ) {
        loadedChunks.add(chunkKey(world, chunkX, chunkZ));
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            List<SpawnerInstance> fromDb = storageService.loadChunk(world, chunkX, chunkZ);
            Bukkit.getScheduler().runTask(plugin, () -> {
                for (SpawnerInstance instance : fromDb) {
                    loadedSpawners.put(new LocationKey(instance.world(), instance.x(), instance.y(), instance.z()), instance);
                }
            });
        });
    }

    public void onChunkUnloaded(String world, int chunkX, int chunkZ) {
        loadedChunks.remove(chunkKey(world, chunkX, chunkZ));
        loadedSpawners.keySet().removeIf(key -> key.world().equals(world) && (key.x() >> 4) == chunkX && (key.z() >> 4) == chunkZ);
    }

    private void scanSpawners() {
        long now = System.currentTimeMillis();
        List<SpawnerInstance> due = new ArrayList<>();
        AtomicInteger budget = new AtomicInteger(configManager.getMaxSpawnPerCycle());

        for (SpawnerInstance instance : loadedSpawners.values()) {
            if (budget.get() <= 0) {
                break;
            }
            if (!loadedChunks.contains(chunkKey(instance.world(), instance.x() >> 4, instance.z() >> 4))) {
                continue;
            }
            if (instance.nextSpawnMillis() > now) {
                continue;
            }
            if (budget.decrementAndGet() >= 0) {
                due.add(instance);
            }
        }

        if (!due.isEmpty()) {
            Bukkit.getScheduler().runTask(plugin, () -> processDue(due));
        }
    }

    private void processDue(List<SpawnerInstance> due) {
        long now = System.currentTimeMillis();
        for (SpawnerInstance instance : due) {
            SpawnerType type = configManager.getType(instance.typeId());
            if (type == null) {
                continue;
            }
            World world = Bukkit.getWorld(instance.world());
            if (world == null || !world.isChunkLoaded(instance.x() >> 4, instance.z() >> 4)) {
                continue;
            }

            Location spawnerLocation = new Location(world, instance.x(), instance.y(), instance.z());
            Block topBlock = spawnerLocation.clone().add(0, 1, 0).getBlock();
            ItemStack stack = new ItemStack(type.dropMaterial(), type.amountPerCycle());
            boolean inserted = false;

            if (!topBlock.isPassable() || topBlock.getType().isSolid()) {
                inserted = tryInsertIntoNearbyInventories(spawnerLocation, stack);
                if (!inserted) {
                    Player owner = Bukkit.getPlayer(instance.owner());
                    if (owner != null && owner.isOnline()) {
                        inserted = owner.getInventory().addItem(stack).isEmpty();
                    }
                }
            }

            if (!inserted) {
                world.dropItemNaturally(spawnerLocation.clone().add(0.5, 1.2, 0.5), stack);
            }

            double multiplier = Math.max(0.30D, 1.0D - ((instance.level() - 1) * 0.15D));
            long next = now + Math.max(1000L, (long) (type.intervalSeconds() * 1000L * multiplier));
            instance.setNextSpawnMillis(next);
        }
    }

    private boolean tryInsertIntoNearbyInventories(Location location, ItemStack stack) {
        Block origin = location.getBlock();
        for (BlockFace face : BlockFace.values()) {
            if (face == BlockFace.SELF) {
                continue;
            }
            BlockState state = origin.getRelative(face).getState();
            if (!(state instanceof Container container)) {
                continue;
            }
            Inventory inventory = container.getInventory();
            if (inventory.firstEmpty() == -1) {
                continue;
            }
            inventory.addItem(stack);
            return true;
        }
        return false;
    }

    private String chunkKey(String world, int chunkX, int chunkZ) {
        return world + ":" + chunkX + ":" + chunkZ;
    }
}
