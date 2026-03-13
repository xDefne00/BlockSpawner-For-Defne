package com.defne.blockspawner.service;

import com.defne.blockspawner.BlockSpawnerPlugin;
import org.bukkit.ChatColor;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Map;

public class MessageService {
    private final BlockSpawnerPlugin plugin;
    private YamlConfiguration activeMessages;
    private YamlConfiguration fallbackMessages;

    public MessageService(BlockSpawnerPlugin plugin) {
        this.plugin = plugin;
    }

    public void load(String language) {
        saveResourceIfAbsent("messages.yml");
        saveResourceIfAbsent("messages_tr.yml");
        saveResourceIfAbsent("messages_en.yml");

        fallbackMessages = loadBundled("messages_en.yml");
        String fileName = switch (language.toLowerCase()) {
            case "en" -> "messages_en.yml";
            case "tr" -> "messages_tr.yml";
            default -> "messages.yml";
        };

        YamlConfiguration chosen = YamlConfiguration.loadConfiguration(new java.io.File(plugin.getDataFolder(), fileName));
        activeMessages = chosen;
    }

    public String get(String key) {
        String value = activeMessages != null ? activeMessages.getString(key) : null;
        if (value == null && fallbackMessages != null) {
            value = fallbackMessages.getString(key);
        }
        if (value == null) {
            return "§cMissing message: " + key;
        }
        return ChatColor.translateAlternateColorCodes('&', value);
    }

    public String format(String key, Map<String, String> replacements) {
        String value = get(key);
        for (Map.Entry<String, String> entry : replacements.entrySet()) {
            value = value.replace("%" + entry.getKey() + "%", entry.getValue());
        }
        return value;
    }

    private YamlConfiguration loadBundled(String path) {
        try (InputStream in = plugin.getResource(path)) {
            if (in == null) {
                return new YamlConfiguration();
            }
            return YamlConfiguration.loadConfiguration(new InputStreamReader(in, StandardCharsets.UTF_8));
        } catch (Exception ex) {
            return new YamlConfiguration();
        }
    }

    private void saveResourceIfAbsent(String name) {
        java.io.File file = new java.io.File(plugin.getDataFolder(), name);
        if (!file.exists()) {
            plugin.saveResource(name, false);
        }
    }
}
