package com.defne.blockspawner;

import com.defne.blockspawner.command.BlockSpawnerCommand;
import com.defne.blockspawner.config.ConfigManager;
import com.defne.blockspawner.listener.ChunkStateListener;
import com.defne.blockspawner.listener.SpawnerBlockListener;
import com.defne.blockspawner.service.SpawnerItemService;
import com.defne.blockspawner.service.SpawnerService;
import com.defne.blockspawner.service.StorageService;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.World;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.java.JavaPlugin;

import net.milkbowl.vault.economy.Economy;

import java.io.File;
import java.sql.SQLException;

public class BlockSpawnerPlugin extends JavaPlugin {
    private ConfigManager configManager;
    private StorageService storageService;
    private SpawnerService spawnerService;
    private SpawnerItemService spawnerItemService;
    private Economy economy;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        configManager = new ConfigManager(this);
        configManager.load();

        File dbFile = new File(getDataFolder(), configManager.getStorageFile());
        if (!dbFile.getParentFile().exists() && !dbFile.getParentFile().mkdirs()) {
            getLogger().warning("Could not create plugin data folder");
        }

        storageService = new StorageService(dbFile);
        try {
            storageService.init();
        } catch (SQLException exception) {
            getLogger().severe("Could not initialize SQLite storage: " + exception.getMessage());
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        spawnerItemService = new SpawnerItemService(this);
        spawnerService = new SpawnerService(this, configManager, storageService);
        spawnerService.start();

        Bukkit.getPluginManager().registerEvents(new SpawnerBlockListener(this, spawnerService, spawnerItemService), this);
        Bukkit.getPluginManager().registerEvents(new ChunkStateListener(spawnerService), this);

        PluginCommand command = getCommand("blockspawner");
        if (command != null) {
            BlockSpawnerCommand executor = new BlockSpawnerCommand(this, spawnerService, spawnerItemService);
            command.setExecutor(executor);
            command.setTabCompleter(executor);
        }

        for (World world : Bukkit.getWorlds()) {
            for (Chunk chunk : world.getLoadedChunks()) {
                spawnerService.onChunkLoaded(world.getName(), chunk.getX(), chunk.getZ());
            }
        }

        setupVault();
        getLogger().info("BlockSpawner enabled.");
    }

    @Override
    public void onDisable() {
        if (spawnerService != null) {
            spawnerService.stop();
        }
        if (storageService != null) {
            storageService.shutdown();
        }
    }

    private void setupVault() {
        if (Bukkit.getPluginManager().getPlugin("Vault") == null) {
            return;
        }
        RegisteredServiceProvider<Economy> registration = getServer().getServicesManager().getRegistration(Economy.class);
        if (registration != null) {
            economy = registration.getProvider();
            getLogger().info("Vault detected. Economy integration ready.");
        }
    }

    public ConfigManager getConfigManager() {
        return configManager;
    }

    public Economy getEconomy() {
        return economy;
    }
}
