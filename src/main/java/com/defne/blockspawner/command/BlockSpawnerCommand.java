package com.defne.blockspawner.command;

import com.defne.blockspawner.BlockSpawnerPlugin;
import com.defne.blockspawner.model.SpawnerInstance;
import com.defne.blockspawner.model.SpawnerType;
import com.defne.blockspawner.service.SpawnerItemService;
import com.defne.blockspawner.service.SpawnerService;
import org.bukkit.Bukkit;
import org.bukkit.Location;
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

    public BlockSpawnerCommand(BlockSpawnerPlugin plugin, SpawnerService spawnerService, SpawnerItemService itemService) {
        this.plugin = plugin;
        this.spawnerService = spawnerService;
        this.itemService = itemService;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            sender.sendMessage("§e/blockspawner give <player> <type> <amount>");
            sender.sendMessage("§e/blockspawner reload");
            sender.sendMessage("§e/blockspawner remove <radius>");
            sender.sendMessage("§e/blockspawner list");
            return true;
        }

        String sub = args[0].toLowerCase(Locale.ROOT);
        return switch (sub) {
            case "give" -> handleGive(sender, args);
            case "reload" -> handleReload(sender);
            case "remove" -> handleRemove(sender, args);
            case "list" -> handleList(sender);
            default -> false;
        };
    }

    private boolean handleGive(CommandSender sender, String[] args) {
        if (!sender.hasPermission("blockspawner.admin")) {
            sender.sendMessage("§cNo permission.");
            return true;
        }
        if (args.length < 4) {
            return false;
        }
        Player target = Bukkit.getPlayer(args[1]);
        if (target == null) {
            sender.sendMessage("§cPlayer not found.");
            return true;
        }
        SpawnerType type = plugin.getConfigManager().getType(args[2]);
        if (type == null) {
            sender.sendMessage("§cUnknown spawner type.");
            return true;
        }

        int amount;
        try {
            amount = Math.max(1, Integer.parseInt(args[3]));
        } catch (NumberFormatException ex) {
            sender.sendMessage("§cAmount must be numeric.");
            return true;
        }

        target.getInventory().addItem(itemService.createSpawnerItem(type, amount, 1));
        sender.sendMessage("§aGiven " + amount + "x " + type.id() + " spawner(s) to " + target.getName() + ".");
        return true;
    }

    private boolean handleReload(CommandSender sender) {
        if (!sender.hasPermission("blockspawner.admin")) {
            sender.sendMessage("§cNo permission.");
            return true;
        }
        plugin.getConfigManager().load();
        sender.sendMessage("§aBlockSpawner config reloaded.");
        return true;
    }

    private boolean handleRemove(CommandSender sender, String[] args) {
        if (!sender.hasPermission("blockspawner.admin")) {
            sender.sendMessage("§cNo permission.");
            return true;
        }
        if (!(sender instanceof Player player)) {
            sender.sendMessage("§cOnly players can use this command.");
            return true;
        }
        if (args.length < 2) {
            return false;
        }
        int radius;
        try {
            radius = Math.max(1, Integer.parseInt(args[1]));
        } catch (NumberFormatException ex) {
            sender.sendMessage("§cRadius must be numeric.");
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

        sender.sendMessage("§aRemoved " + removed + " spawners within radius " + radius + ".");
        return true;
    }

    private boolean handleList(CommandSender sender) {
        Map<String, SpawnerType> types = plugin.getConfigManager().getSpawnerTypes();
        if (types.isEmpty()) {
            sender.sendMessage("§cNo spawner types configured.");
            return true;
        }
        sender.sendMessage("§bAvailable block spawner types:");
        for (SpawnerType type : types.values()) {
            sender.sendMessage("§7- §f" + type.id() + " §8(§f" + type.dropMaterial() + "§8, §f" + type.intervalSeconds() + "s§8, §fx" + type.amountPerCycle() + "§8)");
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            return List.of("give", "reload", "remove", "list");
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
