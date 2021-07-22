package net.coreprotect.listener.entity;

import java.sql.Connection;
import java.sql.Statement;
import java.util.Locale;

import org.bukkit.Material;
import org.bukkit.block.BlockState;
import org.bukkit.entity.Entity;
import org.bukkit.entity.ItemFrame;
import org.bukkit.entity.Painting;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.hanging.HangingBreakByEntityEvent;

import net.coreprotect.bukkit.BukkitAdapter;
import net.coreprotect.config.Config;
import net.coreprotect.config.ConfigHandler;
import net.coreprotect.consumer.Queue;
import net.coreprotect.database.Database;
import net.coreprotect.database.lookup.BlockLookup;
import net.coreprotect.language.Phrase;
import net.coreprotect.utility.Chat;
import net.coreprotect.utility.Color;
import net.coreprotect.utility.Util;

public final class HangingBreakByEntityListener extends Queue implements Listener {

    static void inspectItemFrame(final BlockState block, final Player player) {
        // block check
        class BasicThread implements Runnable {
            @Override
            public void run() {
                try {
                    Connection connection = Database.getConnection(true);
                    if (connection != null) {
                        Statement statement = connection.createStatement();
                        String blockData = BlockLookup.performLookup(null, statement, block, player, 0, 1, 7);

                        if (blockData.contains("\n")) {
                            for (String b : blockData.split("\n")) {
                                Chat.sendComponent(player, b);
                            }
                        }
                        else if (blockData.length() > 0) {
                            Chat.sendComponent(player, blockData);
                        }

                        statement.close();
                        connection.close();
                    }
                    else {
                        Chat.sendMessage(player, Color.DARK_AQUA + "CoreProtect " + Color.WHITE + "- " + Phrase.build(Phrase.DATABASE_BUSY));
                    }
                }
                catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }
        Runnable runnable = new BasicThread();
        Thread thread = new Thread(runnable);
        thread.start();
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    protected void onHangingBreakByEntity(HangingBreakByEntityEvent event) {
        Entity entity = event.getEntity();
        Entity remover = event.getRemover();
        BlockState blockEvent = event.getEntity().getLocation().getBlock().getState();

        boolean inspecting = false;
        if (event.getRemover() instanceof Player) {
            Player player = (Player) event.getRemover();

            if (ConfigHandler.inspecting.get(player.getName()) != null) {
                if (ConfigHandler.inspecting.get(player.getName())) {
                    // block check
                    inspectItemFrame(blockEvent, player);
                    event.setCancelled(true);
                    inspecting = true;
                }
            }
        }

        if (entity instanceof ItemFrame || entity instanceof Painting) {
            String culprit = "#entity";
            if (remover != null) {
                if (remover instanceof Player) {
                    Player player = (Player) remover;
                    culprit = player.getName();
                }
                else if (remover.getType() != null) {
                    culprit = "#" + remover.getType().name().toLowerCase(Locale.ROOT);
                }
            }

            Material material;
            int itemData = 0;
            if (entity instanceof ItemFrame) {
                material = BukkitAdapter.ADAPTER.getFrameType(entity);
                ItemFrame itemframe = (ItemFrame) entity;

                if (itemframe.getItem() != null) {
                    itemData = Util.getBlockId(itemframe.getItem().getType());
                }
            }
            else {
                material = Material.PAINTING;
                Painting painting = (Painting) entity;
                itemData = Util.getArtId(painting.getArt().toString(), true);
            }

            if (!event.isCancelled() && Config.getConfig(blockEvent.getWorld()).BLOCK_BREAK && !inspecting) {
                Queue.queueBlockBreak(culprit, blockEvent, material, null, itemData);
            }
        }
    }

}