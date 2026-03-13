package com.defne.blockspawner.command;

import com.defne.blockspawner.BlockSpawnerPlugin;
import com.defne.blockspawner.model.SpawnerInstance;
import com.defne.blockspawner.model.SpawnerType;
import com.defne.blockspawner.service.GuiService;
import com.defne.blockspawner.service.MessageService;
import com.defne.blockspawner.service.SpawnerItemService;
import com.defne.blockspawner.service.SpawnerService;
import org.bukkit.Bukkit;
import org.bukkit.FluidCollisionMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class BlockSpawnerCommand implements CommandExecutor, TabCompleter {
    private final BlockSpawnerPlugin plugin;
    private final SpawnerService spawnerService;
    private final SpawnerItemService itemService;
    private final MessageService messageService;
    private final GuiService guiService;

    public BlockSpawnerCommand(BlockSpawnerPlugin plugin, SpawnerService spawnerService,
                               SpawnerItemService itemService, MessageService messageService, GuiService guiService) {
        this.plugin = plugin;
        this.spawnerService = spawnerService;
        this.itemService = itemService;
        this.messageService = messageService;
        this.guiService = guiService;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            if (sender instanceof Player player) {
                guiService.openMain(player);
                return true;
            }
            sender.sendMessage("§e/blockspawner give <player> <type> <amount>");
            sender.sendMessage("§e/blockspawner reload");
            sender.sendMessage("§e/blockspawner remove <radius>");
            sender.sendMessage("§e/blockspawner list");
            sender.sendMessage("§e/blockspawner upgrade");
            return true;
        }

        String sub = args[0].toLowerCase(Locale.ROOT);
        return switch (sub) {
            case "give" -> handleGive(sender, args);
            case "reload" -> handleReload(sender);
            case "remove" -> handleRemove(sender, args);
            case "list" -> handleList(sender);
            case "upgrade" -> handleUpgrade(sender);
            default -> false;
        };
    }

    private boolean handleGive(CommandSender sender, String[] args) {
        if (!sender.hasPermission("blockspawner.admin")) {
            sender.sendMessage(messageService.get("no-permission"));
            return true;
        }
        if (args.length < 4) {
            return false;
        }
        Player target = Bukkit.getPlayer(args[1]);
        if (target == null) {
            sender.sendMessage(messageService.get("player-not-found"));
            return true;
        }
        SpawnerType type = plugin.getConfigManager().getType(args[2]);
        if (type == null) {
            sender.sendMessage(messageService.get("unknown-type"));
            return true;
        }

        int amount;
        try {
            amount = Math.max(1, Integer.parseInt(args[3]));
        } catch (NumberFormatException ex) {
            sender.sendMessage(messageService.get("amount-numeric"));
            return true;
        }

        target.getInventory().addItem(itemService.createSpawnerItem(type, amount, 1));
        sender.sendMessage(messageService.format("give-success", Map.of("amount", String.valueOf(amount), "type", type.id(), "player", target.getName())));
        return true;
    }

    private boolean handleReload(CommandSender sender) {
        if (!sender.hasPermission("blockspawner.admin")) {
            sender.sendMessage(messageService.get("no-permission"));
            return true;
        }
        plugin.getConfigManager().load();
        plugin.getMessageService().load(plugin.getConfigManager().getLanguage());
        spawnerService.refreshHolograms();
        sender.sendMessage(messageService.get("reload-success"));
        return true;
    }

    private boolean handleRemove(CommandSender sender, String[] args) {
        if (!sender.hasPermission("blockspawner.admin")) {
            sender.sendMessage(messageService.get("no-permission"));
            return true;
        }
        if (!(sender instanceof Player player)) {
            sender.sendMessage(messageService.get("player-only"));
            return true;
        }
        if (args.length < 2) {
            return false;
        }
        int radius;
        try {
            radius = Math.max(1, Integer.parseInt(args[1]));
        } catch (NumberFormatException ex) {
            sender.sendMessage(messageService.get("radius-numeric"));
            return true;
        }

        Location center = player.getLocation();
        int removed = 0;
        for (SpawnerInstance instance : new ArrayList<>(spawnerService.getLoadedSpawners())) {
            if (!instance.world().equals(center.getWorld().getName())) {
                continue;
            }
            if (center.distanceSquared(new Location(center.getWorld(), instance.x(), instance.y(), instance.z())) > (radius * radius)) {
                continue;
            }
            spawnerService.removeSpawner(new Location(center.getWorld(), instance.x(), instance.y(), instance.z()));
            removed++;
        }

        sender.sendMessage(messageService.format("remove-success", Map.of("amount", String.valueOf(removed), "radius", String.valueOf(radius))));
        return true;
    }

    private boolean handleList(CommandSender sender) {
        Map<String, SpawnerType> types = plugin.getConfigManager().getSpawnerTypes();
        if (types.isEmpty()) {
            sender.sendMessage(messageService.get("no-types"));
            return true;
        }
        sender.sendMessage(messageService.get("list-header"));
        for (SpawnerType type : types.values()) {
            sender.sendMessage("§7- §f" + type.id() + " §8(§f" + type.dropMaterial() + "§8, §f" + type.intervalSeconds() + "s§8, §fx" + type.amountPerCycle() + "§8)");
        }
        return true;
    }

    private boolean handleUpgrade(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(messageService.get("player-only"));
            return true;
        }

        Block block = player.getTargetBlockExact(6, FluidCollisionMode.NEVER);
        if (block == null || block.getType() != Material.SPAWNER) {
            player.sendMessage(messageService.get("look-at-spawner"));
            return true;
        }

        SpawnerInstance instance = spawnerService.getSpawner(block.getLocation());
        if (instance == null) {
            player.sendMessage(messageService.get("not-blockspawner"));
            return true;
        }

        spawnerService.tryUpgrade(player, instance);
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            return List.of("give", "reload", "remove", "list", "upgrade");
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("give")) {
            return Bukkit.getOnlinePlayers().stream().map(Player::getName).toList();
        }
        if (args.length == 3 && args[0].equalsIgnoreCase("give")) {
            return new ArrayList<>(plugin.getConfigManager().getSpawnerTypes().keySet());
        }
        return List.of();
    }
}
