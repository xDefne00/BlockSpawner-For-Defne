package com.defne.blockspawner.listener;

import com.defne.blockspawner.BlockSpawnerPlugin;
import com.defne.blockspawner.model.SpawnerInstance;
import com.defne.blockspawner.model.SpawnerType;
import com.defne.blockspawner.service.SpawnerItemService;
import com.defne.blockspawner.service.SpawnerService;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.block.BlockPistonExtendEvent;
import org.bukkit.event.block.BlockPistonRetractEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.inventory.ItemStack;

public class SpawnerBlockListener implements Listener {
    private final BlockSpawnerPlugin plugin;
    private final SpawnerService spawnerService;
    private final SpawnerItemService itemService;

    public SpawnerBlockListener(BlockSpawnerPlugin plugin, SpawnerService spawnerService, SpawnerItemService itemService) {
        this.plugin = plugin;
        this.spawnerService = spawnerService;
        this.itemService = itemService;
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent event) {
        if (event.getBlockPlaced().getType() != Material.SPAWNER) {
            return;
        }

        ItemStack used = event.getItemInHand();
        String typeId = itemService.getTypeId(used).orElse(null);
        if (typeId == null) {
            return;
        }
        SpawnerType type = plugin.getConfigManager().getType(typeId);
        if (type == null) {
            event.setCancelled(true);
            event.getPlayer().sendMessage("§cInvalid spawner type in item.");
            return;
        }

        Player player = event.getPlayer();
        int chunkX = event.getBlockPlaced().getX() >> 4;
        int chunkZ = event.getBlockPlaced().getZ() >> 4;

        if (spawnerService.getSpawnerCountByOwner(player.getUniqueId()) >= plugin.getConfigManager().getPerPlayerLimit()) {
            event.setCancelled(true);
            player.sendMessage("§cYou reached your spawner limit (" + plugin.getConfigManager().getPerPlayerLimit() + ").");
            return;
        }

        if (spawnerService.getSpawnerCountByChunk(event.getBlockPlaced().getWorld().getName(), chunkX, chunkZ) >= plugin.getConfigManager().getPerChunkLimit()) {
            event.setCancelled(true);
            player.sendMessage("§cThis chunk reached the spawner limit (" + plugin.getConfigManager().getPerChunkLimit() + ").");
            return;
        }

        boolean created = spawnerService.registerSpawner(event.getBlockPlaced().getLocation(), type.id(), player.getUniqueId(), itemService.getLevel(used));
        if (!created) {
            event.setCancelled(true);
            player.sendMessage("§cCould not place this block spawner.");
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent event) {
        Block block = event.getBlock();
        if (block.getType() != Material.SPAWNER) {
            return;
        }

        SpawnerInstance instance = spawnerService.getSpawner(block.getLocation());
        if (instance == null) {
            return;
        }

        Player player = event.getPlayer();
        if (!spawnerService.canBreak(player, instance)) {
            event.setCancelled(true);
            player.sendMessage("§cYou do not own this block spawner.");
            return;
        }

        event.setDropItems(false);
        spawnerService.removeSpawner(block.getLocation());
        block.getWorld().dropItemNaturally(block.getLocation().add(0.5, 0.5, 0.5), spawnerService.toItem(instance, itemService));
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPistonExtend(BlockPistonExtendEvent event) {
        for (Block block : event.getBlocks()) {
            if (spawnerService.getSpawner(block.getLocation()) != null) {
                event.setCancelled(true);
                return;
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPistonRetract(BlockPistonRetractEvent event) {
        for (Block block : event.getBlocks()) {
            if (spawnerService.getSpawner(block.getLocation()) != null) {
                event.setCancelled(true);
                return;
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onEntityExplode(EntityExplodeEvent event) {
        if (event.blockList().stream().anyMatch(block -> spawnerService.getSpawner(block.getLocation()) != null)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBlockExplode(BlockExplodeEvent event) {
        if (event.blockList().stream().anyMatch(block -> spawnerService.getSpawner(block.getLocation()) != null)) {
            event.setCancelled(true);
        }
    }
}
