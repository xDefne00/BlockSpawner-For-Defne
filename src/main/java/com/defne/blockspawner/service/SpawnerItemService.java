package com.defne.blockspawner.service;

import com.defne.blockspawner.BlockSpawnerPlugin;
import com.defne.blockspawner.model.SpawnerType;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.Optional;

public class SpawnerItemService {
    private final NamespacedKey typeKey;
    private final NamespacedKey levelKey;

    public SpawnerItemService(BlockSpawnerPlugin plugin) {
        this.typeKey = new NamespacedKey(plugin, "spawner_type");
        this.levelKey = new NamespacedKey(plugin, "spawner_level");
    }

    public ItemStack createSpawnerItem(SpawnerType type, int amount, int level) {
        ItemStack item = new ItemStack(Material.SPAWNER, amount);
        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return item;
        }
        meta.getPersistentDataContainer().set(typeKey, PersistentDataType.STRING, type.id());
        meta.getPersistentDataContainer().set(levelKey, PersistentDataType.INTEGER, Math.max(1, Math.min(5, level)));
        meta.displayName(net.kyori.adventure.text.Component.text("§b" + type.id().toUpperCase() + " Block Spawner §7[L" + level + "]"));
        item.setItemMeta(meta);
        return item;
    }

    public Optional<String> getTypeId(ItemStack itemStack) {
        if (itemStack == null || itemStack.getType() != Material.SPAWNER) {
            return Optional.empty();
        }
        ItemMeta meta = itemStack.getItemMeta();
        if (meta == null) {
            return Optional.empty();
        }
        String value = meta.getPersistentDataContainer().get(typeKey, PersistentDataType.STRING);
        return Optional.ofNullable(value);
    }

    public int getLevel(ItemStack itemStack) {
        if (itemStack == null || itemStack.getType() != Material.SPAWNER) {
            return 1;
        }
        ItemMeta meta = itemStack.getItemMeta();
        if (meta == null) {
            return 1;
        }
        Integer level = meta.getPersistentDataContainer().get(levelKey, PersistentDataType.INTEGER);
        if (level == null) {
            return 1;
        }
        return Math.max(1, Math.min(5, level));
    }
}
