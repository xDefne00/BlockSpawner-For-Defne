package com.defne.blockspawner;

import com.defne.blockspawner.command.BlockSpawnerCommand;
import com.defne.blockspawner.config.ConfigManager;
import com.defne.blockspawner.listener.ChunkStateListener;
import com.defne.blockspawner.listener.SpawnerBlockListener;
import com.defne.blockspawner.service.HologramService;
import com.defne.blockspawner.service.MessageService;
import com.defne.blockspawner.service.SpawnerItemService;
import com.defne.blockspawner.service.SpawnerService;
import com.defne.blockspawner.service.StorageService;
import net.milkbowl.vault.economy.Economy;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.World;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.sql.SQLException;

public class BlockSpawnerPlugin extends JavaPlugin {
    private ConfigManager configManager;
    private StorageService storageService;
    private SpawnerService spawnerService;
    private SpawnerItemService spawnerItemService;
    private HologramService hologramService;
    private MessageService messageService;
    private Economy economy;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        configManager = new ConfigManager(this);
        configManager.load();

        messageService = new MessageService(this);
        messageService.load(configManager.getLanguage());

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
        hologramService = new HologramService(this, configManager);
        hologramService.initialize();
        spawnerService = new SpawnerService(this, configManager, storageService, hologramService, messageService);
        spawnerService.start();

        Bukkit.getPluginManager().registerEvents(new SpawnerBlockListener(this, spawnerService, spawnerItemService, messageService), this);
        Bukkit.getPluginManager().registerEvents(new ChunkStateListener(spawnerService), this);

        PluginCommand command = getCommand("blockspawner");
        if (command != null) {
            BlockSpawnerCommand executor = new BlockSpawnerCommand(this, spawnerService, spawnerItemService, messageService);
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
            spawnerService.clearRuntimeState();
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

    public MessageService getMessageService() {
        return messageService;
    }

    public Economy getEconomy() {
        return economy;
    }
}
