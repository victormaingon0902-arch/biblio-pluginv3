package fr.bibliotheque;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.block.Block;
import org.bukkit.block.Lectern;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerTakeLecternBookEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.BookMeta;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.List;

public final class LecternManager implements Listener {

    private static final MiniMessage MM = MiniMessage.miniMessage();

    private final Bibliotheque plugin;
    private final NamespacedKey key;

    public LecternManager(Bibliotheque plugin) {
        this.plugin = plugin;
        this.key = new NamespacedKey(plugin, "pupitre_bibliotheque");
    }

    /** Item spécial : pupitre de la bibliothèque. */
    public ItemStack createItem() {
        ItemStack it = new ItemStack(Material.LECTERN);
        ItemMeta meta = it.getItemMeta();
        meta.displayName(Gui.c("<gold><bold>Pupitre de la Bibliothèque"));
        meta.lore(List.of(Gui.c("<gray>Posez-le pour créer un accès"), Gui.c("<gray>à la bibliothèque commune.")));
        meta.getPersistentDataContainer().set(key, PersistentDataType.BYTE, (byte) 1);
        it.setItemMeta(meta);
        return it;
    }

    private boolean isLecternItem(ItemStack it) {
        return it != null && it.hasItemMeta()
                && it.getItemMeta().getPersistentDataContainer().has(key, PersistentDataType.BYTE);
    }

    /** Pose un livre sur le pupitre (visuel). */
    private void decorate(Block block) {
        if (block.getType() != Material.LECTERN) return;

        ItemStack book = new ItemStack(Material.WRITTEN_BOOK);
        BookMeta bm = (BookMeta) book.getItemMeta();
        bm.title(Component.text("Bibliothèque"));
        bm.author(Component.text("Serveur"));
        bm.addPages(Component.text("Bibliothèque"));
        book.setItemMeta(bm);

        org.bukkit.block.data.type.Lectern data = (org.bukkit.block.data.type.Lectern) block.getBlockData();
        data.setHasBook(true);
        block.setBlockData(data, false);

        Lectern state = (Lectern) block.getState();
        state.getSnapshotInventory().setItem(0, book);
        state.update(true, false);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent e) {
        if (!isLecternItem(e.getItemInHand())) return;
        Block block = e.getBlockPlaced();
        plugin.data().addLectern(block);
        plugin.getServer().getScheduler().runTask(plugin, () -> decorate(block));
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onPlaceCheck(BlockPlaceEvent e) {
        if (isLecternItem(e.getItemInHand()) && !e.getPlayer().hasPermission("bibliotheque.admin")) {
            e.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onInteract(PlayerInteractEvent e) {
        if (e.getAction() != Action.RIGHT_CLICK_BLOCK || e.getHand() != EquipmentSlot.HAND) return;
        Block block = e.getClickedBlock();
        if (block == null || block.getType() != Material.LECTERN || !plugin.data().isLectern(block)) return;

        e.setUseInteractedBlock(Event.Result.DENY);
        e.setUseItemInHand(Event.Result.DENY);
        e.setCancelled(true);

        Player p = e.getPlayer();
        if (!p.hasPermission("bibliotheque.use")) {
            p.sendMessage(MM.deserialize("<red>Vous n'avez pas accès à la bibliothèque.</red>"));
            return;
        }
        plugin.gui().openMain(p);
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onTakeBook(PlayerTakeLecternBookEvent e) {
        if (plugin.data().isLectern(e.getLectern().getBlock())) e.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onBreak(BlockBreakEvent e) {
        Block block = e.getBlock();
        if (!plugin.data().isLectern(block)) return;

        Player p = e.getPlayer();
        if (!p.hasPermission("bibliotheque.admin")) {
            e.setCancelled(true);
            p.sendMessage(MM.deserialize("<red>Seul un administrateur peut retirer ce pupitre.</red>"));
            return;
        }
        plugin.data().removeLectern(block);
        e.setDropItems(false);
        if (p.getGameMode() != GameMode.CREATIVE) {
            block.getWorld().dropItemNaturally(block.getLocation().add(0.5, 0.5, 0.5), createItem());
        }
        p.sendMessage(MM.deserialize("<yellow>Pupitre de la bibliothèque retiré.</yellow>"));
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onEntityExplode(EntityExplodeEvent e) {
        e.blockList().removeIf(b -> plugin.data().isLectern(b));
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onBlockExplode(BlockExplodeEvent e) {
        e.blockList().removeIf(b -> plugin.data().isLectern(b));
    }
}
