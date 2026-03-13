package com.defne.blockspawner.service;

import com.defne.blockspawner.BlockSpawnerPlugin;
import com.defne.blockspawner.config.ConfigManager;
import com.defne.blockspawner.model.SpawnerInstance;
import com.defne.blockspawner.model.SpawnerType;
import com.defne.blockspawner.util.CustomSpawnerCodec;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.*;

public class GuiService {
    public static final String MAIN_TITLE = "§8BlockSpawner";
    public static final String CREATE_TITLE = "§8Create Spawner";
    public static final String UPGRADE_TITLE = "§8Upgrade Spawner";
    public static final String MY_TITLE = "§8My Spawners";
    public static final String SETTINGS_TITLE = "§8Settings";

    private static final List<Material> CREATE_MATERIALS = List.of(Material.DIAMOND, Material.GOLD_INGOT, Material.IRON_INGOT, Material.EMERALD, Material.COPPER_INGOT);

    private final BlockSpawnerPlugin plugin;
    private final ConfigManager configManager;
    private final SpawnerService spawnerService;
    private final SpawnerItemService itemService;
    private final MessageService messageService;

    private final Map<UUID, BuilderState> builderState = new HashMap<>();
    private final Set<UUID> awaitingNameInput = new HashSet<>();

    public GuiService(BlockSpawnerPlugin plugin, ConfigManager configManager, SpawnerService spawnerService,
                      SpawnerItemService itemService, MessageService messageService) {
        this.plugin = plugin;
        this.configManager = configManager;
        this.spawnerService = spawnerService;
        this.itemService = itemService;
        this.messageService = messageService;
    }

    public void openMain(Player player) {
        Inventory inv = Bukkit.createInventory(null, 27, Component.text(MAIN_TITLE));
        inv.setItem(10, item(Material.CHEST, messageService.get("gui-my-spawners")));
        inv.setItem(12, item(Material.ANVIL, messageService.get("gui-upgrade-spawner")));
        inv.setItem(14, item(Material.CRAFTING_TABLE, messageService.get("gui-create-spawner")));
        inv.setItem(16, item(Material.COMPARATOR, messageService.get("gui-settings")));
        player.openInventory(inv);
    }

    public void openMySpawners(Player player) {
        Inventory inv = Bukkit.createInventory(null, 54, Component.text(MY_TITLE));
        int slot = 0;
        for (SpawnerInstance instance : spawnerService.getLoadedSpawners()) {
            if (!instance.owner().equals(player.getUniqueId())) {
                continue;
            }
            if (slot >= inv.getSize()) {
                break;
            }
            Material material = Optional.ofNullable(spawnerService.resolveType(instance.typeId())).map(SpawnerType::dropMaterial).orElse(Material.SPAWNER);
            ItemStack stack = new ItemStack(material);
            ItemMeta meta = stack.getItemMeta();
            if (meta != null) {
                meta.displayName(Component.text("§b" + spawnerService.getDisplayName(instance.typeId()) + " §7[L" + instance.level() + "]"));
                meta.lore(List.of(
                        Component.text("§7World: §f" + instance.world()),
                        Component.text("§7XYZ: §f" + instance.x() + ", " + instance.y() + ", " + instance.z())
                ));
                stack.setItemMeta(meta);
            }
            inv.setItem(slot++, stack);
        }
        player.openInventory(inv);
    }

    public void openUpgrade(Player player) {
        SpawnerInstance target = getTargetedSpawner(player);
        if (target == null) {
            player.sendMessage(messageService.get("look-at-spawner"));
            return;
        }

        SpawnerType type = spawnerService.resolveType(target.typeId());
        if (type == null) {
            player.sendMessage(messageService.get("not-blockspawner"));
            return;
        }

        int amount = spawnerService.getSpawnAmount(type, target.level());
        int interval = (int) Math.round(spawnerService.getSpawnIntervalSeconds(type, target.level()));
        double cost = configManager.getUpgradeCost(target.level());

        Inventory inv = Bukkit.createInventory(null, 27, Component.text(UPGRADE_TITLE));
        ItemStack info = new ItemStack(Material.PAPER);
        ItemMeta meta = info.getItemMeta();
        if (meta != null) {
            meta.displayName(Component.text("§b" + spawnerService.getDisplayName(target.typeId())));
            meta.lore(List.of(
                    Component.text("§7Level: §f" + target.level()),
                    Component.text("§7Interval: §f" + interval + "s"),
                    Component.text("§7Amount: §f" + amount)
            ));
            info.setItemMeta(meta);
        }
        inv.setItem(11, info);

        ItemStack upgrade = item(Material.EMERALD_BLOCK, messageService.format("gui-upgrade-button", Map.of("cost", String.valueOf((long) cost))));
        inv.setItem(15, upgrade);
        player.openInventory(inv);
    }

    public void openCreate(Player player) {
        BuilderState state = builderState.computeIfAbsent(player.getUniqueId(), uuid -> new BuilderState());
        Inventory inv = Bukkit.createInventory(null, 27, Component.text(CREATE_TITLE));

        inv.setItem(10, item(state.material, messageService.format("gui-create-material", Map.of("material", state.material.name()))));
        inv.setItem(12, item(Material.CLOCK, messageService.format("gui-create-interval", Map.of("interval", String.valueOf(state.interval)))));
        inv.setItem(14, item(Material.DROPPER, messageService.format("gui-create-amount", Map.of("amount", String.valueOf(state.amount)))));
        inv.setItem(16, item(Material.NAME_TAG, messageService.format("gui-create-name", Map.of("name", state.name))));
        inv.setItem(22, item(Material.LIME_CONCRETE, messageService.format("gui-create-confirm", Map.of("cost", String.valueOf((long) configManager.getCreateSpawnerCost())))));

        player.openInventory(inv);
    }

    public void openSettings(Player player) {
        Inventory inv = Bukkit.createInventory(null, 27, Component.text(SETTINGS_TITLE));
        inv.setItem(13, item(Material.GLOBE_BANNER_PATTERN, messageService.format("gui-language", Map.of("lang", configManager.getLanguage().toUpperCase(Locale.ROOT)))));
        player.openInventory(inv);
    }

    public void clickCreate(Player player, int slot, boolean rightClick) {
        BuilderState state = builderState.computeIfAbsent(player.getUniqueId(), uuid -> new BuilderState());
        switch (slot) {
            case 10 -> {
                int index = CREATE_MATERIALS.indexOf(state.material);
                if (index < 0) index = 0;
                state.material = CREATE_MATERIALS.get((index + 1) % CREATE_MATERIALS.size());
                openCreate(player);
            }
            case 12 -> {
                state.interval = Math.max(1, state.interval + (rightClick ? -1 : 1));
                openCreate(player);
            }
            case 14 -> {
                state.amount = Math.max(1, state.amount + (rightClick ? -1 : 1));
                openCreate(player);
            }
            case 16 -> {
                awaitingNameInput.add(player.getUniqueId());
                player.closeInventory();
                player.sendMessage(messageService.get("gui-enter-name"));
            }
            case 22 -> createSpawnerItem(player, state);
            default -> {
            }
        }
    }

    public void clickMain(Player player, int slot) {
        switch (slot) {
            case 10 -> openMySpawners(player);
            case 12 -> openUpgrade(player);
            case 14 -> openCreate(player);
            case 16 -> openSettings(player);
            default -> {
            }
        }
    }

    public void clickUpgrade(Player player, int slot) {
        if (slot != 15) {
            return;
        }
        SpawnerInstance target = getTargetedSpawner(player);
        if (target == null) {
            player.sendMessage(messageService.get("look-at-spawner"));
            return;
        }
        spawnerService.tryUpgrade(player, target);
        openUpgrade(player);
    }

    public void clickSettings(Player player, int slot) {
        if (slot != 13) {
            return;
        }
        String newLang = configManager.getLanguage().equalsIgnoreCase("tr") ? "en" : "tr";
        plugin.getConfig().set("language", newLang);
        plugin.saveConfig();
        configManager.load();
        messageService.load(configManager.getLanguage());
        spawnerService.refreshHolograms();
        openSettings(player);
    }

    public boolean isAwaitingNameInput(UUID uuid) {
        return awaitingNameInput.contains(uuid);
    }

    public void submitName(Player player, String name) {
        if (!awaitingNameInput.remove(player.getUniqueId())) {
            return;
        }
        BuilderState state = builderState.computeIfAbsent(player.getUniqueId(), id -> new BuilderState());
        state.name = name.length() > 24 ? name.substring(0, 24) : name;
        openCreate(player);
    }

    private void createSpawnerItem(Player player, BuilderState state) {
        if (plugin.getEconomy() == null) {
            player.sendMessage(messageService.get("economy-unavailable"));
            return;
        }
        double cost = configManager.getCreateSpawnerCost();
        if (plugin.getEconomy().getBalance(player) < cost) {
            player.sendMessage(messageService.format("not-enough-money", Map.of("cost", String.valueOf((long) cost))));
            return;
        }
        var response = plugin.getEconomy().withdrawPlayer(player, cost);
        if (!response.transactionSuccess()) {
            player.sendMessage(messageService.get("upgrade-failed"));
            return;
        }

        String encoded = CustomSpawnerCodec.encode(state.material, state.interval, state.amount, state.name);
        player.getInventory().addItem(itemService.createSpawnerItem(encoded, state.name, 1, 1));
        player.sendMessage(messageService.get("gui-create-success"));
    }

    private SpawnerInstance getTargetedSpawner(Player player) {
        var block = player.getTargetBlockExact(6);
        if (block == null || block.getType() != Material.SPAWNER) {
            return null;
        }
        return spawnerService.getSpawner(block.getLocation());
    }

    private ItemStack item(Material material, String name) {
        ItemStack stack = new ItemStack(material);
        ItemMeta meta = stack.getItemMeta();
        if (meta != null) {
            meta.displayName(Component.text(name));
            stack.setItemMeta(meta);
        }
        return stack;
    }

    private static class BuilderState {
        private Material material = Material.DIAMOND;
        private int interval = 10;
        private int amount = 2;
        private String name = "Custom";
    }
}
