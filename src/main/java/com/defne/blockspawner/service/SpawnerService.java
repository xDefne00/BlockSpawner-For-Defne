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
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

public class SpawnerService {
    private static final BlockFace[] PREFERRED_INSERT_ORDER = {
            BlockFace.DOWN, BlockFace.NORTH, BlockFace.SOUTH, BlockFace.EAST, BlockFace.WEST, BlockFace.UP
    };

    private final BlockSpawnerPlugin plugin;
    private final ConfigManager configManager;
    private final StorageService storageService;
    private final HologramService hologramService;
    private final ConcurrentHashMap<LocationKey, SpawnerInstance> loadedSpawners = new ConcurrentHashMap<>();
    private final Set<String> loadedChunks = ConcurrentHashMap.newKeySet();
    private BukkitTask scanTask;

    public SpawnerService(BlockSpawnerPlugin plugin, ConfigManager configManager, StorageService storageService, HologramService hologramService) {
        this.plugin = plugin;
        this.configManager = configManager;
        this.storageService = storageService;
        this.hologramService = hologramService;
    }

    public void start() {
        // Async loop only selects due tasks from in-memory state (no Bukkit world/block calls).
        scanTask = Bukkit.getScheduler().runTaskTimerAsynchronously(plugin, this::scanSpawnersAsync, 20L, 20L);
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
        if (loadedSpawners.containsKey(key)) {
            return false;
        }
        if (getSpawnerCountByOwner(owner) >= configManager.getPerPlayerLimit()) {
            return false;
        }
        if (getSpawnerCountByChunk(key.world(), key.x() >> 4, key.z() >> 4) >= configManager.getPerChunkLimit()) {
            return false;
        }

        SpawnerInstance instance = new SpawnerInstance(key.world(), key.x(), key.y(), key.z(), type.id(), owner, level);
        loadedSpawners.put(key, instance);
        storageService.upsertAsync(instance);
        hologramService.createOrUpdate(instance);
        debug("Registered spawner " + key.serialized());
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
        hologramService.remove(key);
        debug("Removed spawner " + key.serialized());
        return true;
    }

    public boolean setSpawnerLevel(Location location, int level) {
        SpawnerInstance instance = getSpawner(location);
        if (instance == null) {
            return false;
        }
        instance.setLevel(level);
        storageService.upsertAsync(instance);
        hologramService.createOrUpdate(instance);
        return true;
    }

    public int getSpawnerCountByOwner(UUID owner) {
        int total = 0;
        for (SpawnerInstance instance : loadedSpawners.values()) {
            if (owner.equals(instance.owner())) {
                total++;
            }
        }
        return total;
    }

    public int getSpawnerCountByChunk(String world, int chunkX, int chunkZ) {
        int total = 0;
        for (LocationKey key : loadedSpawners.keySet()) {
            if (key.world().equals(world) && (key.x() >> 4) == chunkX && (key.z() >> 4) == chunkZ) {
                total++;
            }
        }
        return total;
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

    public void refreshHolograms() {
        for (SpawnerInstance instance : loadedSpawners.values()) {
            hologramService.createOrUpdate(instance);
        }
    }

    public void onChunkLoaded(String world, int chunkX, int chunkZ) {
        loadedChunks.add(chunkKey(world, chunkX, chunkZ));
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            List<SpawnerInstance> fromDb = storageService.loadChunk(world, chunkX, chunkZ);
            Bukkit.getScheduler().runTask(plugin, () -> {
                for (SpawnerInstance instance : fromDb) {
                    LocationKey key = new LocationKey(instance.world(), instance.x(), instance.y(), instance.z());
                    loadedSpawners.put(key, instance);
                    hologramService.createOrUpdate(instance);
                }
            });
        });
    }

    public void onChunkUnloaded(String world, int chunkX, int chunkZ) {
        loadedChunks.remove(chunkKey(world, chunkX, chunkZ));
        List<LocationKey> toRemove = loadedSpawners.keySet().stream()
                .filter(key -> key.world().equals(world) && (key.x() >> 4) == chunkX && (key.z() >> 4) == chunkZ)
                .toList();

        for (LocationKey key : toRemove) {
            loadedSpawners.remove(key);
            hologramService.remove(key);
        }
    }

    public void clearRuntimeState() {
        loadedSpawners.clear();
        loadedChunks.clear();
        hologramService.clearAll();
    }

    private void scanSpawnersAsync() {
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
            if (instance.nextSpawnMillis() <= now && budget.decrementAndGet() >= 0) {
                due.add(instance);
            }
        }

        due.sort(Comparator.comparingLong(SpawnerInstance::nextSpawnMillis));
        if (!due.isEmpty()) {
            Bukkit.getScheduler().runTask(plugin, () -> processDueSync(due));
        }
    }

    private void processDueSync(List<SpawnerInstance> due) {
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
            ItemStack toSpawn = new ItemStack(type.dropMaterial(), type.amountPerCycle());

            // First pass: prefer hoppers/containers around the spawner to reduce dropped entities.
            ItemStack remainder = insertIntoNearbyContainers(spawnerLocation, toSpawn);
            if (remainder.getAmount() > 0) {
                // Second pass: try owner inventory when online.
                Player owner = Bukkit.getPlayer(instance.owner());
                if (owner != null && owner.isOnline()) {
                    remainder = addToInventory(owner.getInventory(), remainder);
                }
            }
            if (remainder.getAmount() > 0) {
                // Final pass: merge into nearby dropped stacks before creating a new item entity.
                mergeOrDrop(spawnerLocation.clone().add(0.5, 1.2, 0.5), remainder);
            }

            long next = now + Math.max(1000L, (long) (type.intervalSeconds() * 1000L * intervalMultiplier(instance.level())));
            instance.setNextSpawnMillis(next);
            hologramService.createOrUpdate(instance);
        }
    }

    private ItemStack insertIntoNearbyContainers(Location spawnerLocation, ItemStack input) {
        ItemStack remainder = input.clone();
        Block origin = spawnerLocation.getBlock();

        for (BlockFace face : PREFERRED_INSERT_ORDER) {
            if (remainder.getAmount() <= 0) {
                break;
            }
            BlockState state = origin.getRelative(face).getState();
            if (!(state instanceof Container container)) {
                continue;
            }
            remainder = addToInventory(container.getInventory(), remainder);
        }
        return remainder;
    }

    private ItemStack addToInventory(Inventory inventory, ItemStack stack) {
        ItemStack remainder = stack.clone();
        var leftovers = inventory.addItem(remainder);
        if (leftovers.isEmpty()) {
            remainder.setAmount(0);
            return remainder;
        }
        return leftovers.values().iterator().next();
    }

    private void mergeOrDrop(Location dropLocation, ItemStack stack) {
        int maxStack = stack.getMaxStackSize();
        for (Item nearbyItem : dropLocation.getWorld().getNearbyEntitiesByType(Item.class, dropLocation, 1.4, 1.0, 1.4)) {
            ItemStack nearbyStack = nearbyItem.getItemStack();
            if (!nearbyStack.isSimilar(stack) || nearbyStack.getAmount() >= maxStack) {
                continue;
            }

            int movable = Math.min(stack.getAmount(), maxStack - nearbyStack.getAmount());
            nearbyStack.setAmount(nearbyStack.getAmount() + movable);
            nearbyItem.setItemStack(nearbyStack);
            stack.setAmount(stack.getAmount() - movable);
            if (stack.getAmount() <= 0) {
                return;
            }
        }

        dropLocation.getWorld().dropItemNaturally(dropLocation, stack);
    }

    private double intervalMultiplier(int level) {
        return Math.max(0.30D, 1.0D - ((Math.max(1, level) - 1) * 0.15D));
    }

    private String chunkKey(String world, int chunkX, int chunkZ) {
        return world + ":" + chunkX + ":" + chunkZ;
    }

    private void debug(String message) {
        if (configManager.isDebug()) {
            plugin.getLogger().info("[DEBUG] " + message);
        }
    }
}
