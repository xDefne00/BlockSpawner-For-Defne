package com.defne.blockspawner.service;

import com.defne.blockspawner.BlockSpawnerPlugin;
import com.defne.blockspawner.config.ConfigManager;
import com.defne.blockspawner.model.SpawnerInstance;
import com.defne.blockspawner.model.SpawnerType;
import com.defne.blockspawner.util.CustomSpawnerCodec;
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
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

public class SpawnerService {
    private static final BlockFace[] CONTAINER_FACES = {
            BlockFace.DOWN, BlockFace.NORTH, BlockFace.SOUTH, BlockFace.EAST, BlockFace.WEST, BlockFace.UP
    };

    private final BlockSpawnerPlugin plugin;
    private final ConfigManager configManager;
    private final StorageService storageService;
    private final HologramService hologramService;
    private final MessageService messageService;

    private final ConcurrentHashMap<LocationKey, SpawnerInstance> loadedSpawners = new ConcurrentHashMap<>();
    private final Set<String> loadedChunks = ConcurrentHashMap.newKeySet();
    private BukkitTask scanTask;

    public SpawnerService(BlockSpawnerPlugin plugin, ConfigManager configManager, StorageService storageService,
                          HologramService hologramService, MessageService messageService) {
        this.plugin = plugin;
        this.configManager = configManager;
        this.storageService = storageService;
        this.hologramService = hologramService;
        this.messageService = messageService;
    }

    public void start() {
        // Async loop only selects due tasks from in-memory data.
        scanTask = Bukkit.getScheduler().runTaskTimerAsynchronously(plugin, this::scanSpawnersAsync, 20L, 20L);
    }

    public void stop() {
        if (scanTask != null) {
            scanTask.cancel();
        }
    }

    public boolean registerSpawner(Location location, String typeId, UUID owner, int level) {
        SpawnerType type = resolveType(typeId);
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

        SpawnerInstance instance = new SpawnerInstance(
                key.world(), key.x(), key.y(), key.z(), typeId, owner,
                Math.max(1, Math.min(level, configManager.getMaxLevel()))
        );
        loadedSpawners.put(key, instance);
        storageService.upsertAsync(instance);
        hologramService.createOrUpdate(instance);
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
        return true;
    }

    public boolean setSpawnerLevel(Location location, int level) {
        SpawnerInstance instance = getSpawner(location);
        if (instance == null) {
            return false;
        }
        instance.setLevel(Math.max(1, Math.min(level, configManager.getMaxLevel())));
        storageService.upsertAsync(instance);
        hologramService.createOrUpdate(instance);
        return true;
    }

    public boolean tryUpgrade(Player player, SpawnerInstance instance) {
        if (instance.level() >= configManager.getMaxLevel()) {
            player.sendMessage(messageService.get("max-level"));
            return false;
        }

        if (configManager.isAllowOwnerOnly() && !canBreak(player, instance)) {
            player.sendMessage(messageService.get("not-owner"));
            return false;
        }

        if (plugin.getEconomy() == null) {
            player.sendMessage(messageService.get("economy-unavailable"));
            return false;
        }

        double cost = configManager.getUpgradeCost(instance.level());
        if (plugin.getEconomy().getBalance(player) < cost) {
            player.sendMessage(messageService.format("not-enough-money", java.util.Map.of("cost", String.valueOf((long) cost))));
            return false;
        }

        var response = plugin.getEconomy().withdrawPlayer(player, cost);
        if (!response.transactionSuccess()) {
            player.sendMessage(messageService.get("upgrade-failed"));
            return false;
        }

        World world = Bukkit.getWorld(instance.world());
        if (world == null) {
            player.sendMessage(messageService.get("upgrade-failed"));
            return false;
        }

        setSpawnerLevel(new Location(world, instance.x(), instance.y(), instance.z()), instance.level() + 1);
        player.sendMessage(messageService.format("upgrade-success", java.util.Map.of("level", String.valueOf(instance.level()))));
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
        SpawnerType type = resolveType(instance.typeId());
        if (type == null) {
            return new ItemStack(Material.SPAWNER);
        }
        return itemService.createSpawnerItem(instance.typeId(), getDisplayName(instance.typeId()), 1, instance.level());
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
                    instance.setLevel(Math.min(instance.level(), configManager.getMaxLevel()));
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
            SpawnerType type = resolveType(instance.typeId());
            if (type == null) {
                continue;
            }

            World world = Bukkit.getWorld(instance.world());
            if (world == null || !world.isChunkLoaded(instance.x() >> 4, instance.z() >> 4)) {
                continue;
            }

            Location spawnerLocation = new Location(world, instance.x(), instance.y(), instance.z());
            int spawnAmount = getSpawnAmount(type, instance.level());
            ItemStack stack = new ItemStack(type.dropMaterial(), spawnAmount);

            ConfigManager.DropMode mode = configManager.getDropMode();
            if (mode == ConfigManager.DropMode.NATURAL) {
                dropNaturally(spawnerLocation, stack);
            } else if (mode == ConfigManager.DropMode.INVENTORY) {
                ItemStack left = addToOwnerInventory(instance.owner(), stack);
                if (left.getAmount() > 0) {
                    dropNaturally(spawnerLocation, left);
                }
            } else {
                ItemStack left = insertIntoNearbyContainers(spawnerLocation, stack);
                if (left.getAmount() > 0) {
                    dropNaturally(spawnerLocation, left);
                }
            }

            long next = now + (long) (getSpawnIntervalSeconds(type, instance.level()) * 1000L);
            instance.setNextSpawnMillis(Math.max(now + 1000L, next));
            hologramService.createOrUpdate(instance);
        }
    }

    private ItemStack addToOwnerInventory(UUID ownerId, ItemStack stack) {
        Player owner = Bukkit.getPlayer(ownerId);
        if (owner == null || !owner.isOnline()) {
            return stack;
        }
        return addToInventory(owner.getInventory(), stack);
    }

    private ItemStack insertIntoNearbyContainers(Location spawnerLocation, ItemStack input) {
        ItemStack remainder = input.clone();
        Block origin = spawnerLocation.getBlock();

        for (BlockFace face : CONTAINER_FACES) {
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

    private void dropNaturally(Location spawnerLocation, ItemStack stack) {
        Location dropLocation = spawnerLocation.clone().add(0.5, configManager.getDropHeight(), 0.5);
        if (configManager.isRandomOffsetEnabled()) {
            dropLocation.add((Math.random() - 0.5D) * 0.30D, 0, (Math.random() - 0.5D) * 0.30D);
        }

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

        Item dropped = dropLocation.getWorld().dropItemNaturally(dropLocation, stack);
        dropped.setPickupDelay(configManager.getPickupDelay());
        dropped.setVelocity(new Vector(0, 0, 0));
    }

    public int getSpawnAmount(SpawnerType type, int level) {
        return (int) Math.max(1, Math.floor(type.amountPerCycle() * Math.pow(configManager.getAmountMultiplier(), Math.max(0, level - 1))));
    }

    public double getSpawnIntervalSeconds(SpawnerType type, int level) {
        return Math.max(1.0D, type.intervalSeconds() * Math.pow(configManager.getIntervalMultiplier(), Math.max(0, level - 1)));
    }


    public SpawnerType resolveType(String typeId) {
        SpawnerType configured = configManager.getType(typeId);
        if (configured != null) {
            return configured;
        }
        return CustomSpawnerCodec.toSpawnerType(typeId, "custom");
    }

    public String getDisplayName(String typeId) {
        return CustomSpawnerCodec.decode(typeId)
                .map(CustomSpawnerCodec.DecodedCustomSpawner::name)
                .orElseGet(() -> {
                    if (typeId == null || typeId.isEmpty()) {
                        return "Spawner";
                    }
                    return typeId.substring(0, 1).toUpperCase() + typeId.substring(1).toLowerCase();
                });
    }

    private String chunkKey(String world, int chunkX, int chunkZ) {
        return world + ":" + chunkX + ":" + chunkZ;
    }
}
