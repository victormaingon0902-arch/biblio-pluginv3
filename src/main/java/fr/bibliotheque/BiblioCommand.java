package fr.bibliotheque;

import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.BookMeta;

import java.util.*;

public final class BiblioCommand implements CommandExecutor, TabCompleter {

    private static final MiniMessage MM = MiniMessage.miniMessage();
    private static final String PREFIX = "<gold>[Bibliothèque]</gold> ";
    private static final String ID_REGEX = "[a-z0-9_-]+";

    private final Bibliotheque plugin;

    public BiblioCommand(Bibliotheque plugin) {
        this.plugin = plugin;
    }

    private void msg(CommandSender s, String text) {
        s.sendMessage(MM.deserialize(PREFIX + text));
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0 || args[0].equalsIgnoreCase("ouvrir")) {
            if (sender instanceof Player p) {
                if (!p.hasPermission("bibliotheque.use")) {
                    msg(sender, "<red>Permission refusée.</red>");
                    return true;
                }
                plugin.gui().openMain(p);
            } else {
                msg(sender, "Commande réservée aux joueurs.");
            }
            return true;
        }

        if (args[0].equalsIgnoreCase("scoreboard")) {
            if (!(sender instanceof Player p)) {
                msg(sender, "Commande réservée aux joueurs.");
                return true;
            }
            boolean now = !plugin.data().isScoreboardEnabled(p.getUniqueId());
            plugin.data().setScoreboardEnabled(p.getUniqueId(), now);
            plugin.scoreboard().update(p);
            msg(sender, now ? "<green>Scoreboard activé.</green>" : "<yellow>Scoreboard désactivé.</yellow>");
            return true;
        }

        if (!sender.hasPermission("bibliotheque.admin")) {
            msg(sender, "<red>Permission refusée.</red>");
            return true;
        }

        switch (args[0].toLowerCase(Locale.ROOT)) {
            case "pupitre" -> {
                if (!(sender instanceof Player p)) {
                    msg(sender, "Commande réservée aux joueurs.");
                    return true;
                }
                p.getInventory().addItem(plugin.lecterns().createItem());
                msg(sender, "Vous avez reçu un <yellow>pupitre de la bibliothèque</yellow>. Posez-le où vous voulez.");
            }
            case "reload" -> {
                plugin.reloadAll();
                msg(sender, "<green>Configuration et quêtes rechargées.</green>");
            }
            case "histoire" -> histoire(sender, args);
            case "quetes" -> quetes(sender, args);
            case "objet" -> objet(sender, args);
            default -> help(sender);
        }
        return true;
    }

    private void help(CommandSender s) {
        msg(s, "Commandes :");
        String[] lines = {
                "/biblio scoreboard <gray>- active/désactive votre scoreboard (tous les joueurs)",
                "/biblio pupitre <gray>- reçoit un pupitre de bibliothèque",
                "/biblio reload <gray>- recharge config.yml et quests.yml",
                "/biblio histoire chapitre creer <id> <nom...>",
                "/biblio histoire chapitre renommer <id> <nom...>",
                "/biblio histoire chapitre icone <id> <MATERIAL>",
                "/biblio histoire chapitre supprimer <id>",
                "/biblio histoire livre ajouter <chapitre> <gray>- livre signé en main",
                "/biblio histoire livre retirer <chapitre> <numéro>",
                "/biblio histoire livre liste <chapitre>",
                "/biblio quetes debloquer|verrouiller <chapitre>",
                "/biblio quetes valider|reset <quête>",
                "/biblio objet ei <gray>- affiche l'id ExecutableItems de l'objet en main",
                "/biblio objet creer <id> <gray>- enregistre l'objet en main (alternative)",
                "/biblio objet donner <id|ei:<idEI>> [nombre] | supprimer <id> | liste"
        };
        for (String l : lines) s.sendMessage(MM.deserialize("<gray> • <white>" + l));
    }

    // ---------- Histoire ----------

    private void histoire(CommandSender s, String[] a) {
        if (a.length < 3) { help(s); return; }
        DataStore data = plugin.data();
        String sub = a[1].toLowerCase(Locale.ROOT);
        String action = a[2].toLowerCase(Locale.ROOT);

        if (sub.equals("chapitre")) {
            if (a.length < 4) { help(s); return; }
            String id = a[3].toLowerCase(Locale.ROOT);
            switch (action) {
                case "creer" -> {
                    if (a.length < 5) { msg(s, "<red>Précisez un nom.</red>"); return; }
                    if (!id.matches(ID_REGEX)) { msg(s, "<red>Id invalide (a-z, 0-9, _ et - uniquement).</red>"); return; }
                    String name = String.join(" ", Arrays.copyOfRange(a, 4, a.length));
                    if (data.createChapter(id, name)) msg(s, "<green>Chapitre '" + id + "' créé.</green>");
                    else msg(s, "<red>Ce chapitre existe déjà.</red>");
                }
                case "renommer" -> {
                    DataStore.Chapter c = data.chapter(id);
                    if (c == null || a.length < 5) { msg(s, "<red>Chapitre introuvable ou nom manquant.</red>"); return; }
                    c.name = String.join(" ", Arrays.copyOfRange(a, 4, a.length));
                    data.save();
                    msg(s, "<green>Chapitre renommé.</green>");
                }
                case "icone" -> {
                    DataStore.Chapter c = data.chapter(id);
                    Material m = a.length > 4 ? Material.matchMaterial(a[4]) : null;
                    if (c == null || m == null || !m.isItem() || m.isAir()) {
                        msg(s, "<red>Chapitre ou matériau invalide.</red>");
                        return;
                    }
                    c.icon = m;
                    data.save();
                    msg(s, "<green>Icône mise à jour.</green>");
                }
                case "supprimer" -> {
                    if (data.deleteChapter(id)) msg(s, "<green>Chapitre supprimé.</green>");
                    else msg(s, "<red>Chapitre introuvable.</red>");
                }
                default -> help(s);
            }
        } else if (sub.equals("livre")) {
            if (a.length < 4) { help(s); return; }
            String id = a[3].toLowerCase(Locale.ROOT);
            DataStore.Chapter c = data.chapter(id);
            if (c == null) { msg(s, "<red>Chapitre introuvable.</red>"); return; }
            switch (action) {
                case "ajouter" -> {
                    if (!(s instanceof Player p)) { msg(s, "Commande réservée aux joueurs."); return; }
                    ItemStack hand = p.getInventory().getItemInMainHand();
                    if (hand.getType() != Material.WRITTEN_BOOK || !(hand.getItemMeta() instanceof BookMeta)) {
                        msg(s, "<red>Tenez en main un livre <u>signé</u> (écrit et signé).</red>");
                        return;
                    }
                    data.addBook(id, hand);
                    msg(s, "<green>Livre ajouté au chapitre '" + id + "'.</green>");
                }
                case "retirer" -> {
                    int n;
                    try { n = Integer.parseInt(a.length > 4 ? a[4] : "") - 1; }
                    catch (NumberFormatException ex) { msg(s, "<red>Numéro invalide.</red>"); return; }
                    if (data.removeBook(id, n)) msg(s, "<green>Livre retiré.</green>");
                    else msg(s, "<red>Numéro introuvable.</red>");
                }
                case "liste" -> {
                    if (c.books.isEmpty()) { msg(s, "Ce chapitre est vide."); return; }
                    int i = 1;
                    for (ItemStack b : c.books) {
                        String title = (b.getItemMeta() instanceof BookMeta bm && bm.title() != null)
                                ? MM.escapeTags(PlainTextComponentSerializer.plainText().serialize(bm.title()))
                                : "?";
                        s.sendMessage(MM.deserialize("<gray> " + i++ + ". <white>" + title));
                    }
                }
                default -> help(s);
            }
        } else {
            help(s);
        }
    }

    // ---------- Quêtes ----------

    private void quetes(CommandSender s, String[] a) {
        if (a.length < 3) { help(s); return; }
        QuestManager qm = plugin.quests();
        DataStore data = plugin.data();
        String action = a[1].toLowerCase(Locale.ROOT);
        String id = a[2];

        switch (action) {
            case "debloquer" -> {
                if (qm.chapter(id) == null) { msg(s, "<red>Chapitre de quêtes introuvable.</red>"); return; }
                data.setChapterUnlocked(id, true);
                msg(s, "<green>Chapitre débloqué pour tout le monde.</green>");
            }
            case "verrouiller" -> {
                if (qm.chapter(id) == null) { msg(s, "<red>Chapitre de quêtes introuvable.</red>"); return; }
                data.setChapterUnlocked(id, false);
                msg(s, "<green>Déblocage manuel retiré (les chapitres 'par défaut' restent visibles).</green>");
            }
            case "valider" -> {
                QuestManager.Quest q = qm.quest(id);
                if (q == null) { msg(s, "<red>Quête introuvable.</red>"); return; }
                data.setQuestDone(id, true);
                for (String ch : q.unlockChapters()) {
                    if (qm.chapter(ch) != null) data.setChapterUnlocked(ch, true);
                }
                msg(s, "<green>Quête marquée comme accomplie (sans récompenses).</green>");
            }
            case "reset" -> {
                if (qm.quest(id) == null) { msg(s, "<red>Quête introuvable.</red>"); return; }
                data.setQuestDone(id, false);
                data.resetKills(id);
                msg(s, "<green>Quête et compteurs de kills remis à zéro pour tout le monde.</green>");
            }
            default -> help(s);
        }
    }

    // ---------- Objets spéciaux ----------

    private void objet(CommandSender s, String[] a) {
        if (a.length < 2) { help(s); return; }
        DataStore data = plugin.data();
        String action = a[1].toLowerCase(Locale.ROOT);

        if (action.equals("liste")) {
            if (data.specialItems().isEmpty()) { msg(s, "Aucun objet spécial enregistré."); return; }
            for (Map.Entry<String, ItemStack> e : data.specialItems().entrySet()) {
                s.sendMessage(MM.deserialize("<gray> • <white>" + e.getKey() + " <gray>(" +
                        QuestManager.pretty(e.getValue().getType()) + ")"));
            }
            return;
        }
        if (action.equals("ei")) {
            if (!(s instanceof Player p)) { msg(s, "Commande réservée aux joueurs."); return; }
            String problem = plugin.ei().status();
            if (problem != null) { msg(s, "<red>" + problem + "</red>"); return; }
            String eiId = plugin.ei().idOf(p.getInventory().getItemInMainHand());
            if (eiId == null) {
                msg(s, "<red>L'objet en main n'est pas un ExecutableItem.</red>");
            } else {
                msg(s, "Id ExecutableItems : <yellow>" + eiId + "</yellow> → dans la quête : <yellow>objets-speciaux: [\"ei:"
                        + eiId + ":1\"]</yellow>");
            }
            return;
        }
        if (a.length < 3) { help(s); return; }
        if (action.equals("donner") && a[2].toLowerCase(Locale.ROOT).startsWith("ei:")) {
            if (!(s instanceof Player p)) { msg(s, "Commande réservée aux joueurs."); return; }
            String problem = plugin.ei().status();
            if (problem != null) { msg(s, "<red>" + problem + "</red>"); return; }
            String eiId = a[2].substring(3);
            int n = 1;
            if (a.length > 3) {
                try { n = Math.max(1, Integer.parseInt(a[3])); } catch (NumberFormatException ignored) { }
            }
            if (plugin.ei().give(eiId, p, n) < 0) msg(s, "<red>Id ExecutableItems inconnu : " + eiId + "</red>");
            else msg(s, "<green>ExecutableItem donné.</green>");
            return;
        }
        String id = a[2].toLowerCase(Locale.ROOT);

        switch (action) {
            case "creer" -> {
                if (!(s instanceof Player p)) { msg(s, "Commande réservée aux joueurs."); return; }
                if (!id.matches(ID_REGEX)) { msg(s, "<red>Id invalide (a-z, 0-9, _ et - uniquement).</red>"); return; }
                ItemStack hand = p.getInventory().getItemInMainHand();
                if (hand.getType().isAir()) { msg(s, "<red>Tenez l'objet en main.</red>"); return; }
                data.registerSpecialItem(id, hand);
                msg(s, "<green>Objet '" + id + "' enregistré.</green> Il sera reconnu dans les quêtes (<yellow>objets-speciaux: [\""
                        + id + ":1\"]</yellow>) s'il est <u>identique</u> (nom, lore, données).");
            }
            case "supprimer" -> {
                if (data.deleteSpecialItem(id)) msg(s, "<green>Objet supprimé.</green>");
                else msg(s, "<red>Objet introuvable.</red>");
            }
            case "donner" -> {
                if (!(s instanceof Player p)) { msg(s, "Commande réservée aux joueurs."); return; }
                ItemStack ref = data.specialItem(id);
                if (ref == null) { msg(s, "<red>Objet introuvable.</red>"); return; }
                int n = 1;
                if (a.length > 3) {
                    try { n = Math.max(1, Integer.parseInt(a[3])); } catch (NumberFormatException ignored) { }
                }
                ItemStack give = ref.clone();
                give.setAmount(Math.min(n, give.getMaxStackSize()));
                p.getInventory().addItem(give);
                msg(s, "<green>Objet donné.</green>");
            }
            default -> help(s);
        }
    }

    // ---------- Auto-complétion ----------

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] a) {
        if (!sender.hasPermission("bibliotheque.admin")) {
            return a.length == 1 ? filter(List.of("ouvrir", "scoreboard"), a[0]) : List.of();
        }
        if (a.length == 1) {
            return filter(List.of("ouvrir", "scoreboard", "pupitre", "reload", "histoire", "quetes", "objet"), a[0]);
        }

        String first = a[0].toLowerCase(Locale.ROOT);
        if (first.equals("histoire")) {
            if (a.length == 2) return filter(List.of("chapitre", "livre"), a[1]);
            if (a.length == 3) {
                return a[1].equalsIgnoreCase("chapitre")
                        ? filter(List.of("creer", "renommer", "icone", "supprimer"), a[2])
                        : filter(List.of("ajouter", "retirer", "liste"), a[2]);
            }
            if (a.length == 4 && !a[2].equalsIgnoreCase("creer")) {
                return filter(plugin.data().chapters().stream().map(c -> c.id).toList(), a[3]);
            }
            if (a.length == 5 && a[1].equalsIgnoreCase("chapitre") && a[2].equalsIgnoreCase("icone")) {
                return filter(Arrays.stream(Material.values()).filter(Material::isItem)
                        .map(m -> m.name().toLowerCase(Locale.ROOT)).toList(), a[4]);
            }
        } else if (first.equals("quetes")) {
            if (a.length == 2) return filter(List.of("debloquer", "verrouiller", "valider", "reset"), a[1]);
            if (a.length == 3) {
                if (a[1].equalsIgnoreCase("debloquer") || a[1].equalsIgnoreCase("verrouiller")) {
                    return filter(plugin.quests().allChapters().stream().map(QuestManager.QChapter::id).toList(), a[2]);
                }
                return filter(plugin.quests().allQuests().stream().map(QuestManager.Quest::id).toList(), a[2]);
            }
        } else if (first.equals("objet")) {
            if (a.length == 2) return filter(List.of("ei", "creer", "donner", "supprimer", "liste"), a[1]);
            if (a.length == 3 && (a[1].equalsIgnoreCase("donner") || a[1].equalsIgnoreCase("supprimer"))) {
                List<String> options = new ArrayList<>(plugin.data().specialItems().keySet());
                if (a[1].equalsIgnoreCase("donner")) {
                    for (String eiId : plugin.ei().ids()) options.add("ei:" + eiId);
                }
                return filter(options, a[2]);
            }
        }
        return List.of();
    }

    private static List<String> filter(List<String> options, String prefix) {
        String p = prefix.toLowerCase(Locale.ROOT);
        List<String> out = new ArrayList<>();
        for (String o : options) if (o.toLowerCase(Locale.ROOT).startsWith(p)) out.add(o);
        return out;
    }
}
