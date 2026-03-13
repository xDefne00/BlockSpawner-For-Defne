package com.defne.blockspawner.listener;

import com.defne.blockspawner.service.SpawnerService;
import org.bukkit.Chunk;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.world.ChunkLoadEvent;
import org.bukkit.event.world.ChunkUnloadEvent;

public class ChunkStateListener implements Listener {
    private final SpawnerService spawnerService;

    public ChunkStateListener(SpawnerService spawnerService) {
        this.spawnerService = spawnerService;
    }

    @EventHandler
    public void onChunkLoad(ChunkLoadEvent event) {
        Chunk chunk = event.getChunk();
        spawnerService.onChunkLoaded(chunk.getWorld().getName(), chunk.getX(), chunk.getZ());
    }

    @EventHandler
    public void onChunkUnload(ChunkUnloadEvent event) {
        Chunk chunk = event.getChunk();
        spawnerService.onChunkUnloaded(chunk.getWorld().getName(), chunk.getX(), chunk.getZ());
    }
}
