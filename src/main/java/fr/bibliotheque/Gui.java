package fr.bibliotheque;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.BookMeta;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.*;
import java.util.function.Consumer;

public final class Gui implements Listener {

    private static final MiniMessage MM = MiniMessage.miniMessage();
    private static final PlainTextComponentSerializer PLAIN = PlainTextComponentSerializer.plainText();

    private final Bibliotheque plugin;

    public Gui(Bibliotheque plugin) {
        this.plugin = plugin;
    }

    /** Menu = inventaire + actions associées aux slots. */
    static final class Menu implements InventoryHolder {
        Inventory inv;
        final Map<Integer, Consumer<Player>> actions = new HashMap<>();

        @Override
        public Inventory getInventory() { return inv; }
    }

    // ---------- Helpers ----------

    public static Component c(String s) {
        return MM.deserialize("<!italic>" + s);
    }

    private Menu menu(String title, int rows) {
        Menu m = new Menu();
        m.inv = Bukkit.createInventory(m, rows * 9, c(title));
        return m;
    }

    private ItemStack item(Material mat, String name, List<String> lore, boolean glow) {
        ItemStack s = new ItemStack(mat);
        ItemMeta meta = s.getItemMeta();
        meta.displayName(c(name));
        if (!lore.isEmpty()) meta.lore(lore.stream().map(Gui::c).toList());
        if (glow) meta.setEnchantmentGlintOverride(true);
        s.setItemMeta(meta);
        return s;
    }

    private void set(Menu m, int slot, ItemStack it, Consumer<Player> action) {
        m.inv.setItem(slot, it);
        if (action != null) m.actions.put(slot, action);
    }

    private void fill(Menu m) {
        ItemStack filler = item(Material.GRAY_STAINED_GLASS_PANE, " ", List.of(), false);
        for (int i = 0; i < m.inv.getSize(); i++) m.inv.setItem(i, filler);
    }

    private void back(Menu m, int slot, Consumer<Player> target) {
        set(m, slot, item(Material.ARROW, "<red>« Retour", List.of(), false), target);
    }

    private static String plain(Component comp) {
        return MM.escapeTags(PLAIN.serialize(comp));
    }

    // ---------- Menu principal ----------

    public void openMain(Player p) {
        Menu m = menu("<dark_gray>Bibliothèque", 3);
        fill(m);
        set(m, 10, item(Material.BOOK, "<gold><bold>Histoire",
                List.of("<gray>Les livres qui racontent", "<gray>l'histoire du monde."), false), this::openHistory);
        set(m, 12, item(Material.COMPASS, "<green><bold>Quêtes",
                List.of("<gray>Les quêtes communes", "<gray>à tout le serveur."), false), this::openQuests);
        set(m, 14, item(Material.OAK_SIGN, "<red><bold>Règlement",
                List.of("<gray>Les règles du serveur."), false), this::openRules);
        set(m, 16, item(Material.ENDER_EYE, "<aqua><bold>Réseaux",
                List.of("<gray>Nos liens utiles."), false), this::openLinks);
        p.openInventory(m.inv);
    }

    // ---------- Histoire ----------

    public void openHistory(Player p) {
        Menu m = menu("<dark_gray>Histoire", 6);
        int i = 0;
        for (DataStore.Chapter ch : plugin.data().chapters()) {
            if (i >= 45) break;
            String id = ch.id;
            set(m, i++, item(ch.icon, "<gold>" + ch.name,
                    List.of("<gray>" + ch.books.size() + " livre(s)", "", "<yellow>▶ Clic pour ouvrir"), false),
                    pl -> openHistoryChapter(pl, id));
        }
        if (i == 0) {
            set(m, 22, item(Material.BARRIER, "<gray>Aucun chapitre pour l'instant", List.of(), false), null);
        }
        back(m, 49, this::openMain);
        p.openInventory(m.inv);
    }

    public void openHistoryChapter(Player p, String chapterId) {
        DataStore.Chapter ch = plugin.data().chapter(chapterId);
        if (ch == null) {
            openHistory(p);
            return;
        }
        Menu m = menu("<dark_gray>Histoire › " + ch.name, 6);
        int i = 0;
        for (ItemStack book : ch.books) {
            if (i >= 45) break;
            String title = "Livre sans titre";
            String author = "Inconnu";
            if (book.getItemMeta() instanceof BookMeta bm) {
                if (bm.title() != null) title = plain(bm.title());
                if (bm.author() != null) author = plain(bm.author());
            }
            set(m, i++, item(Material.WRITTEN_BOOK, "<gold>" + title,
                    List.of("<gray>Auteur : " + author, "", "<yellow>▶ Clic pour lire"), false),
                    pl -> {
                        pl.closeInventory();
                        pl.openBook(book.clone());
                    });
        }
        if (i == 0) {
            set(m, 22, item(Material.BARRIER, "<gray>Ce chapitre est vide", List.of(), false), null);
        }
        back(m, 49, this::openHistory);
        p.openInventory(m.inv);
    }

    // ---------- Quêtes ----------

    public void openQuests(Player p) {
        QuestManager qm = plugin.quests();
        Menu m = menu("<dark_gray>Quêtes", 6);
        int i = 0;
        for (QuestManager.QChapter ch : qm.visibleChapters()) {
            if (i >= 45) break;
            List<QuestManager.Quest> list = qm.questsOf(ch.id());
            long done = list.stream().filter(q -> plugin.data().isQuestDone(q.id())).count();
            String id = ch.id();
            set(m, i++, item(ch.icon(), ch.name(),
                    List.of("<gray>Progression : <white>" + done + "/" + list.size(), "",
                            "<yellow>▶ Clic pour ouvrir"), done == list.size() && !list.isEmpty()),
                    pl -> openQuestChapter(pl, id));
        }
        if (i == 0) {
            set(m, 22, item(Material.BARRIER, "<gray>Aucun chapitre débloqué", List.of(), false), null);
        }
        back(m, 49, this::openMain);
        p.openInventory(m.inv);
    }

    public void openQuestChapter(Player p, String chapterId) {
        QuestManager qm = plugin.quests();
        QuestManager.QChapter ch = qm.chapter(chapterId);
        if (ch == null || !qm.isChapterUnlocked(ch)) {
            openQuests(p);
            return;
        }
        Menu m = menu("<dark_gray>Quêtes › " + ch.name(), 6);
        int i = 0;
        for (QuestManager.Quest q : qm.questsOf(chapterId)) {
            if (i >= 45) break;
            QuestManager.State st = qm.state(p, q);

            List<String> lore = new ArrayList<>();
            for (String d : q.description()) lore.add("<gray>" + d);
            List<String> objectives = qm.objectiveLines(p, q);
            if (!objectives.isEmpty()) {
                lore.add("");
                lore.add("<gold>Objectifs :");
                lore.addAll(objectives);
            }
            if (!q.rewardLore().isEmpty()) {
                lore.add("");
                lore.add("<green>Récompenses :");
                for (String r : q.rewardLore()) lore.add(" <white>" + r);
            }
            lore.add("");

            Material icon = q.icon();
            String name;
            boolean glow = false;
            Consumer<Player> action = null;
            switch (st) {
                case DONE -> {
                    name = "<green>✔ " + q.name();
                    lore.add("<green>Quête accomplie (pour tout le serveur)");
                    glow = true;
                }
                case AVAILABLE -> {
                    name = "<yellow>" + q.name();
                    lore.add("<yellow>▶ Clic pour valider");
                    action = pl -> {
                        qm.complete(pl, q);
                        openQuestChapter(pl, chapterId);
                    };
                }
                case LOCKED -> {
                    name = "<gray>" + q.name();
                    icon = Material.GRAY_DYE;
                    lore.add("<red>✖ Prérequis manquants :");
                    for (String pre : q.prerequisites()) {
                        if (!plugin.data().isQuestDone(pre)) {
                            QuestManager.Quest pq = qm.quest(pre);
                            lore.add(" <red>- " + (pq != null ? pq.name() : pre));
                        }
                    }
                }
                default -> {
                    name = "<gray>" + q.name();
                    icon = Material.BARRIER;
                    lore.add("<red>✖ Vous n'avez pas la permission requise");
                }
            }
            set(m, i++, item(icon, name, lore, glow), action);
        }
        if (i == 0) {
            set(m, 22, item(Material.BARRIER, "<gray>Aucune quête dans ce chapitre", List.of(), false), null);
        }
        back(m, 49, this::openQuests);
        p.openInventory(m.inv);
    }

    // ---------- Règlement ----------

    public void openRules(Player p) {
        List<String> pages = plugin.getConfig().getStringList("reglement.pages");
        if (pages.isEmpty()) pages = List.of("Aucun règlement défini.");
        ItemStack book = new ItemStack(Material.WRITTEN_BOOK);
        BookMeta bm = (BookMeta) book.getItemMeta();
        bm.title(Component.text(plugin.getConfig().getString("reglement.titre", "Règlement")));
        bm.author(Component.text("Serveur"));
        for (String page : pages) bm.addPages(MM.deserialize(page));
        book.setItemMeta(bm);
        p.closeInventory();
        p.openBook(book);
    }

    // ---------- Réseaux ----------

    public void openLinks(Player p) {
        Menu m = menu("<dark_gray>Réseaux", 3);
        fill(m);
        ConfigurationSection cs = plugin.getConfig().getConfigurationSection("reseaux");
        int slot = 9;
        if (cs != null) {
            for (String key : cs.getKeys(false)) {
                if (slot >= 18) break;
                ConfigurationSection s = cs.getConfigurationSection(key);
                if (s == null) continue;
                String name = s.getString("nom", key);
                String url = s.getString("url", "");
                Material mat = Material.matchMaterial(s.getString("icone", "PAPER"));
                if (mat == null || !mat.isItem() || mat.isAir()) mat = Material.PAPER;
                set(m, slot++, item(mat, name, List.of("<gray>" + url, "", "<yellow>▶ Clic pour recevoir le lien"), false),
                        pl -> {
                            pl.closeInventory();
                            pl.sendMessage(MM.deserialize(name + "<gray> : </gray><click:open_url:'" + url
                                    + "'><aqua><u>[Cliquez ici pour ouvrir]</u></aqua></click>"));
                        });
            }
        }
        back(m, 22, this::openMain);
        p.openInventory(m.inv);
    }

    // ---------- Événements ----------

    @EventHandler
    public void onClick(InventoryClickEvent e) {
        Inventory top = e.getView().getTopInventory();
        if (!(top.getHolder() instanceof Menu menu)) return;
        e.setCancelled(true);
        if (!top.equals(e.getClickedInventory())) return;
        if (!(e.getWhoClicked() instanceof Player p)) return;
        Consumer<Player> action = menu.actions.get(e.getSlot());
        if (action != null) action.accept(p);
    }

    @EventHandler
    public void onDrag(InventoryDragEvent e) {
        if (e.getView().getTopInventory().getHolder() instanceof Menu) e.setCancelled(true);
    }
}
