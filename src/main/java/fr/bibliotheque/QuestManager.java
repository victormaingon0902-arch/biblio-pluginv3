package fr.bibliotheque;

import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;

import java.io.File;
import java.util.*;
import java.util.function.Predicate;

public final class QuestManager {

    public record QChapter(String id, String name, Material icon, boolean defaultUnlocked, List<String> requires) {}

    public record Quest(String id, String chapter, String name, List<String> description, Material icon,
                        List<String> prerequisites, String permission,
                        Map<Material, Integer> deliver, Map<String, Integer> kills,
                        Map<String, Integer> specialItems, boolean autoValidate,
                        List<String> rewardLore, List<String> commands, List<String> permissions,
                        List<String> unlockChapters) {}

    public enum State { DONE, AVAILABLE, LOCKED, NO_PERMISSION }

    private static final MiniMessage MM = MiniMessage.miniMessage();
    private static final PlainTextComponentSerializer PLAIN = PlainTextComponentSerializer.plainText();

    private final Bibliotheque plugin;
    private final Map<String, QChapter> chapters = new LinkedHashMap<>();
    private final Map<String, Quest> quests = new LinkedHashMap<>();

    public QuestManager(Bibliotheque plugin) {
        this.plugin = plugin;
    }

    // ---------- Chargement ----------

    public void load() {
        chapters.clear();
        quests.clear();
        File f = new File(plugin.getDataFolder(), "quests.yml");
        YamlConfiguration y = YamlConfiguration.loadConfiguration(f);

        ConfigurationSection cs = y.getConfigurationSection("chapitres");
        if (cs != null) {
            for (String id : cs.getKeys(false)) {
                ConfigurationSection s = cs.getConfigurationSection(id);
                if (s == null) continue;
                chapters.put(id, new QChapter(id, s.getString("nom", id),
                        icon(s.getString("icone"), Material.BOOK),
                        s.getBoolean("debloque-par-defaut", false),
                        s.getStringList("necessite-quetes")));
            }
        }

        ConfigurationSection qs = y.getConfigurationSection("quetes");
        if (qs != null) {
            for (String id : qs.getKeys(false)) {
                ConfigurationSection s = qs.getConfigurationSection(id);
                if (s == null) continue;
                String chapter = s.getString("chapitre", "");
                if (!chapters.containsKey(chapter)) {
                    plugin.getLogger().warning("Quête '" + id + "' : chapitre inconnu '" + chapter + "'.");
                    continue;
                }

                Map<String, Integer> specials = new LinkedHashMap<>();
                Map<Material, Integer> deliver = new LinkedHashMap<>();
                for (String raw : s.getStringList("livraison")) {
                    String[] parts = raw.split(":");
                    if (parts[0].trim().equalsIgnoreCase("ei") && parts.length >= 2) {
                        // "ei:<id>:<n>" accepté aussi dans livraison
                        specials.merge("ei:" + parts[1].trim(), amountAt(parts, 2), Integer::sum);
                        continue;
                    }
                    Material m = Material.matchMaterial(parts[0].trim());
                    if (m == null || !m.isItem()) {
                        plugin.getLogger().warning("Quête '" + id + "' : matériau inconnu '" + raw + "'.");
                        // entrée invalide : la quête reste impossible à valider (et l'erreur est visible en jeu)
                        specials.merge("?" + raw.trim(), 1, Integer::sum);
                        continue;
                    }
                    deliver.merge(m, amount(parts), Integer::sum);
                }

                Map<String, Integer> kills = new LinkedHashMap<>();
                for (String raw : s.getStringList("tuer")) {
                    String[] parts = raw.split(":");
                    EntityType et = entityType(parts[0]);
                    if (et == null) {
                        plugin.getLogger().warning("Quête '" + id + "' : type de mob inconnu '" + raw + "'.");
                        kills.merge("?" + raw.trim(), 1, Integer::sum); // impossible à valider, visible en jeu
                        continue;
                    }
                    kills.merge(et.getKey().getKey(), amount(parts), Integer::sum);
                }

                for (String raw : s.getStringList("objets-speciaux")) {
                    String[] parts = raw.split(":");
                    if (parts[0].trim().equalsIgnoreCase("ei") && parts.length >= 2) {
                        // ex. "ei:Trace_civilisation:2" -> id ExecutableItems (casse conservée)
                        specials.merge("ei:" + parts[1].trim(), amountAt(parts, 2), Integer::sum);
                    } else {
                        specials.merge(parts[0].trim().toLowerCase(Locale.ROOT), amountAt(parts, 1), Integer::sum);
                    }
                }

                ConfigurationSection r = s.getConfigurationSection("recompenses");
                List<String> lore = r == null ? List.of() : r.getStringList("affichage");
                List<String> cmds = r == null ? List.of() : r.getStringList("commandes");
                List<String> perms = r == null ? List.of() : r.getStringList("permissions");
                List<String> unlocks = r == null ? List.of() : r.getStringList("debloque-chapitres");

                quests.put(id, new Quest(id, chapter, s.getString("nom", id), s.getStringList("description"),
                        icon(s.getString("icone"), Material.PAPER), s.getStringList("prerequis"),
                        s.getString("permission", ""), deliver, kills, specials,
                        s.getBoolean("validation-auto", false), lore, cmds, perms, unlocks));
            }
        }
        plugin.getLogger().info(chapters.size() + " chapitre(s) de quêtes et " + quests.size() + " quête(s) chargés.");
    }

    private static int amount(String[] parts) {
        return amountAt(parts, 1);
    }

    private static int amountAt(String[] parts, int index) {
        if (parts.length > index) {
            try { return Math.max(1, Integer.parseInt(parts[index].trim())); } catch (NumberFormatException ignored) { }
        }
        return 1;
    }

    private static EntityType entityType(String name) {
        try {
            return Registry.ENTITY_TYPE.get(NamespacedKey.minecraft(name.trim().toLowerCase(Locale.ROOT)));
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private static Material icon(String name, Material def) {
        Material m = name == null ? null : Material.matchMaterial(name);
        return (m != null && m.isItem() && !m.isAir()) ? m : def;
    }

    public static String pretty(Material m) {
        return m.name().toLowerCase(Locale.ROOT).replace('_', ' ');
    }

    public static String prettyKey(String key) {
        return key.toLowerCase(Locale.ROOT).replace('_', ' ');
    }

    private static String plainName(ItemStack it) {
        if (it.hasItemMeta() && it.getItemMeta().hasDisplayName()) {
            return MM.escapeTags(PLAIN.serialize(it.getItemMeta().displayName()));
        }
        return pretty(it.getType());
    }

    // ---------- Accès ----------

    public QChapter chapter(String id) { return chapters.get(id); }

    public Quest quest(String id) { return quests.get(id); }

    public Collection<QChapter> allChapters() { return chapters.values(); }

    public Collection<Quest> allQuests() { return quests.values(); }

    public List<Quest> questsOf(String chapterId) {
        List<Quest> list = new ArrayList<>();
        for (Quest q : quests.values()) if (q.chapter().equals(chapterId)) list.add(q);
        return list;
    }

    public boolean isChapterUnlocked(QChapter c) {
        if (c.defaultUnlocked() || plugin.data().isChapterUnlocked(c.id())) return true;
        if (c.requires().isEmpty()) return false;
        for (String q : c.requires()) if (!plugin.data().isQuestDone(q)) return false;
        return true;
    }

    public List<QChapter> visibleChapters() {
        List<QChapter> list = new ArrayList<>();
        for (QChapter c : chapters.values()) if (isChapterUnlocked(c)) list.add(c);
        return list;
    }

    public State state(Player p, Quest q) {
        if (plugin.data().isQuestDone(q.id())) return State.DONE;
        for (String pre : q.prerequisites()) {
            if (!plugin.data().isQuestDone(pre)) return State.LOCKED;
        }
        if (!q.permission().isEmpty() && !p.hasPermission(q.permission())) return State.NO_PERMISSION;
        return State.AVAILABLE;
    }

    /** Quête en cours (non accomplie, prérequis remplis, chapitre débloqué) : sert aussi à compter les kills. */
    public boolean isActive(Quest q) {
        if (plugin.data().isQuestDone(q.id())) return false;
        for (String pre : q.prerequisites()) {
            if (!plugin.data().isQuestDone(pre)) return false;
        }
        QChapter c = chapters.get(q.chapter());
        return c != null && isChapterUnlocked(c);
    }

    /** Première quête que ce joueur peut faire maintenant (ordre du fichier quests.yml). */
    public Quest currentQuest(Player p) {
        for (Quest q : quests.values()) {
            if (isActive(q) && state(p, q) == State.AVAILABLE) return q;
        }
        return null;
    }

    public boolean killsDone(Quest q) {
        for (Map.Entry<String, Integer> e : q.kills().entrySet()) {
            if (plugin.data().getKills(q.id(), e.getKey()) < e.getValue()) return false;
        }
        return true;
    }

    // ---------- Inventaire ----------

    private static int countSimilar(PlayerInventory inv, ItemStack ref) {
        return count(inv, st -> st.isSimilar(ref));
    }

    private static void removeSimilar(PlayerInventory inv, ItemStack ref, int amount) {
        remove(inv, st -> st.isSimilar(ref), amount);
    }

    /** Reconnaissance d'un objet spécial : "ei:<id>" = ExecutableItems, sinon objet enregistré (/biblio objet creer). */
    private Predicate<ItemStack> matcher(String key) {
        if (key.startsWith("ei:")) {
            String id = key.substring(3);
            if (!plugin.ei().isValidId(id)) return null; // API indisponible ou id inexistant
            return st -> id.equalsIgnoreCase(plugin.ei().idOf(st));
        }
        ItemStack ref = plugin.data().specialItem(key);
        return ref == null ? null : st -> st.isSimilar(ref);
    }

    private String specialLabel(String key) {
        if (key.startsWith("ei:")) {
            String id = key.substring(3);
            ItemStack icon = plugin.ei().icon(id);
            return icon != null ? plainName(icon) : prettyKey(id);
        }
        ItemStack ref = plugin.data().specialItem(key);
        return ref == null ? key : plainName(ref);
    }

    private static int count(PlayerInventory inv, Predicate<ItemStack> match) {
        int n = 0;
        for (ItemStack st : inv.getStorageContents()) {
            if (st != null && match.test(st)) n += st.getAmount();
        }
        return n;
    }

    private static void remove(PlayerInventory inv, Predicate<ItemStack> match, int amount) {
        ItemStack[] contents = inv.getStorageContents();
        int left = amount;
        for (int i = 0; i < contents.length && left > 0; i++) {
            ItemStack st = contents[i];
            if (st == null || !match.test(st)) continue;
            if (st.getAmount() <= left) {
                left -= st.getAmount();
                contents[i] = null;
            } else {
                st.setAmount(st.getAmount() - left);
                left = 0;
            }
        }
        inv.setStorageContents(contents);
    }

    // ---------- Affichage des objectifs (GUI + scoreboard) ----------

    private static String line(boolean ok, String label, int cur, int req) {
        return (ok ? "<green>✔ " : "<gray>• <white>") + label + " <gray>" + cur + "/" + req;
    }

    public List<String> objectiveLines(Player p, Quest q) {
        List<String> out = new ArrayList<>();
        if (plugin.data().isQuestDone(q.id())) return out;

        for (Map.Entry<String, Integer> e : q.kills().entrySet()) {
            int cur = Math.min(plugin.data().getKills(q.id(), e.getKey()), e.getValue());
            out.add(line(cur >= e.getValue(), "Tuer " + prettyKey(e.getKey()), cur, e.getValue()));
        }
        for (Map.Entry<Material, Integer> e : q.deliver().entrySet()) {
            int cur = Math.min(countSimilar(p.getInventory(), new ItemStack(e.getKey())), e.getValue());
            out.add(line(cur >= e.getValue(), "Apporter " + pretty(e.getKey()), cur, e.getValue()));
        }
        for (Map.Entry<String, Integer> e : q.specialItems().entrySet()) {
            Predicate<ItemStack> m = matcher(e.getKey());
            if (m == null) {
                out.add("<red>• Objet spécial inconnu : " + e.getKey());
                continue;
            }
            int cur = Math.min(count(p.getInventory(), m), e.getValue());
            out.add(line(cur >= e.getValue(), "Apporter " + specialLabel(e.getKey()), cur, e.getValue()));
        }
        return out;
    }

    // ---------- Kills (progression commune) ----------

    public void onKill(Player killer, EntityType type) {
        String key = type.getKey().getKey();
        for (Quest q : quests.values()) {
            Integer req = q.kills().get(key);
            if (req == null || !isActive(q)) continue;
            int cur = plugin.data().getKills(q.id(), key);
            if (cur >= req) continue;

            plugin.data().addKill(q.id(), key);
            cur++;
            killer.sendActionBar(MM.deserialize("<gold>" + q.name() + " <gray>» <white>" + prettyKey(key)
                    + " <green>" + cur + "/" + req));

            if (killsDone(q)) {
                if (q.autoValidate() && q.deliver().isEmpty() && q.specialItems().isEmpty()) {
                    complete(killer, q);
                } else if (cur == req && q.kills().size() == 1) {
                    killer.sendMessage(MM.deserialize("<gold>[Quête]</gold> <green>Objectif de chasse atteint pour « "
                            + q.name() + " » !</green> <gray>Rendez-vous au pupitre pour valider.</gray>"));
                }
            }
        }
    }

    // ---------- Validation ----------

    /** Valide la quête pour tout le serveur. Retourne true si elle a été accomplie. */
    public boolean complete(Player p, Quest q) {
        if (state(p, q) != State.AVAILABLE || !isActive(q)) return false;

        if (!killsDone(q)) {
            p.sendMessage(MM.deserialize("<red>Il reste des monstres à tuer pour cette quête.</red>"));
            return false;
        }

        PlayerInventory inv = p.getInventory();
        for (Map.Entry<Material, Integer> e : q.deliver().entrySet()) {
            if (countSimilar(inv, new ItemStack(e.getKey())) < e.getValue()) {
                p.sendMessage(MM.deserialize("<red>Il vous manque des objets : " + e.getValue() + "× "
                        + pretty(e.getKey()) + "</red>"));
                return false;
            }
        }
        for (Map.Entry<String, Integer> e : q.specialItems().entrySet()) {
            Predicate<ItemStack> m = matcher(e.getKey());
            if (m == null) {
                p.sendMessage(MM.deserialize("<red>Objet spécial inconnu : " + e.getKey()
                        + " (prévenez un admin).</red>"));
                return false;
            }
            if (count(inv, m) < e.getValue()) {
                p.sendMessage(MM.deserialize("<red>Il vous manque l'objet spécial : " + e.getValue() + "× "
                        + specialLabel(e.getKey()) + "</red>"));
                return false;
            }
        }

        for (Map.Entry<Material, Integer> e : q.deliver().entrySet()) {
            removeSimilar(inv, new ItemStack(e.getKey()), e.getValue());
        }
        for (Map.Entry<String, Integer> e : q.specialItems().entrySet()) {
            remove(inv, matcher(e.getKey()), e.getValue());
        }

        Set<String> visibleBefore = new HashSet<>();
        for (QChapter c : visibleChapters()) visibleBefore.add(c.id());

        plugin.data().setQuestDone(q.id(), true);
        for (String ch : q.unlockChapters()) {
            if (chapters.containsKey(ch)) plugin.data().setChapterUnlocked(ch, true);
        }

        var console = Bukkit.getConsoleSender();
        for (String cmd : q.commands()) {
            Bukkit.dispatchCommand(console, cmd.replace("%player%", p.getName()));
        }
        for (String perm : q.permissions()) {
            Bukkit.dispatchCommand(console, "lp user " + p.getName() + " permission set " + perm + " true");
        }

        Bukkit.broadcast(MM.deserialize("<gold>[Quête]</gold> <yellow>" + p.getName()
                + "</yellow> a accompli <green>" + q.name() + "</green><gray> (pour tout le serveur)</gray> !"));
        for (QChapter c : visibleChapters()) {
            if (!visibleBefore.contains(c.id())) {
                Bukkit.broadcast(MM.deserialize("<aqua>Nouveau chapitre débloqué pour tous : </aqua>" + c.name()));
            }
        }
        return true;
    }
}
