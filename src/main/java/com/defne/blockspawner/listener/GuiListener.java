package com.defne.blockspawner.listener;

import com.defne.blockspawner.BlockSpawnerPlugin;
import com.defne.blockspawner.service.GuiService;
import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;

public class GuiListener implements Listener {
    private final BlockSpawnerPlugin plugin;
    private final GuiService guiService;

    public GuiListener(BlockSpawnerPlugin plugin, GuiService guiService) {
        this.plugin = plugin;
        this.guiService = guiService;
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        String title = PlainTextComponentSerializer.plainText().serialize(event.getView().title());
        if (!title.startsWith("BlockSpawner")
                && !title.equals("Create Spawner")
                && !title.equals("Upgrade Spawner")
                && !title.equals("My Spawners")
                && !title.equals("Settings")) {
            return;
        }

        event.setCancelled(true);
        if (event.getClickedInventory() == null) {
            return;
        }

        if (title.equals("BlockSpawner")) {
            guiService.clickMain(player, event.getRawSlot());
        } else if (title.equals("Create Spawner")) {
            guiService.clickCreate(player, event.getRawSlot(), event.isRightClick());
        } else if (title.equals("Upgrade Spawner")) {
            guiService.clickUpgrade(player, event.getRawSlot());
        } else if (title.equals("Settings")) {
            guiService.clickSettings(player, event.getRawSlot());
        }
    }

    @EventHandler
    public void onChat(AsyncChatEvent event) {
        Player player = event.getPlayer();
        if (!guiService.isAwaitingNameInput(player.getUniqueId())) {
            return;
        }
        event.setCancelled(true);
        String plain = PlainTextComponentSerializer.plainText().serialize(event.message());
        Bukkit.getScheduler().runTask(plugin, () -> guiService.submitName(player, plain));
    }
}
