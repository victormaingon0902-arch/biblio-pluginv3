package fr.bibliotheque;

import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.inventory.ItemStack;

import java.io.File;
import java.io.IOException;
import java.util.*;

/** Données persistantes (data.yml) : histoire, progression commune des quêtes, objets spéciaux, pupitres. */
public final class DataStore {

    public static final class Chapter {
        public final String id;
        public String name;
        public Material icon = Material.BOOK;
        public final List<ItemStack> books = new ArrayList<>();

        Chapter(String id, String name) {
            this.id = id;
            this.name = name;
        }
    }

    private final Bibliotheque plugin;
    private final File file;
    private final Map<String, Chapter> chapters = new LinkedHashMap<>();
    private final Set<String> completedQuests = new HashSet<>();
    private final Set<String> unlockedChapters = new HashSet<>();
    private final Set<String> lecterns = new HashSet<>();
    /** "idQuete;typeMob" -> nombre de kills (progression commune). */
    private final Map<String, Integer> kills = new HashMap<>();
    private final Map<String, ItemStack> specialItems = new LinkedHashMap<>();
    private final Set<UUID> scoreboardOff = new HashSet<>();
    private boolean dirty;

    public DataStore(Bibliotheque plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "data.yml");
    }

    public void load() {
        chapters.clear();
        completedQuests.clear();
        unlockedChapters.clear();
        lecterns.clear();
        kills.clear();
        specialItems.clear();
        scoreboardOff.clear();
        if (!file.exists()) return;

        YamlConfiguration yml = YamlConfiguration.loadConfiguration(file);

        ConfigurationSection h = yml.getConfigurationSection("histoire");
        if (h != null) {
            for (String id : h.getKeys(false)) {
                ConfigurationSection s = h.getConfigurationSection(id);
                if (s == null) continue;
                Chapter c = new Chapter(id, s.getString("nom", id));
                Material m = Material.matchMaterial(s.getString("icone", "BOOK"));
                if (m != null && m.isItem()) c.icon = m;
                for (Object o : s.getList("livres", List.of())) {
                    if (o instanceof ItemStack it) c.books.add(it);
                }
                chapters.put(id, c);
            }
        }

        completedQuests.addAll(yml.getStringList("quetes.terminees"));
        unlockedChapters.addAll(yml.getStringList("quetes.chapitres-debloques"));
        lecterns.addAll(yml.getStringList("pupitres"));

        for (String raw : yml.getStringList("quetes.kills")) {
            int i = raw.lastIndexOf(';');
            if (i < 0) continue;
            try {
                kills.put(raw.substring(0, i), Integer.parseInt(raw.substring(i + 1)));
            } catch (NumberFormatException ignored) { }
        }

        ConfigurationSection o = yml.getConfigurationSection("objets");
        if (o != null) {
            for (String id : o.getKeys(false)) {
                ItemStack it = o.getItemStack(id);
                if (it != null) specialItems.put(id, it);
            }
        }

        for (String raw : yml.getStringList("scoreboard-desactive")) {
            try { scoreboardOff.add(UUID.fromString(raw)); } catch (IllegalArgumentException ignored) { }
        }
    }

    public void save() {
        dirty = false;
        YamlConfiguration yml = new YamlConfiguration();
        for (Chapter c : chapters.values()) {
            String p = "histoire." + c.id;
            yml.set(p + ".nom", c.name);
            yml.set(p + ".icone", c.icon.name());
            yml.set(p + ".livres", c.books);
        }
        yml.set("quetes.terminees", new ArrayList<>(completedQuests));
        yml.set("quetes.chapitres-debloques", new ArrayList<>(unlockedChapters));
        List<String> killList = new ArrayList<>();
        kills.forEach((k, v) -> killList.add(k + ";" + v));
        yml.set("quetes.kills", killList);
        yml.set("pupitres", new ArrayList<>(lecterns));
        for (Map.Entry<String, ItemStack> e : specialItems.entrySet()) {
            yml.set("objets." + e.getKey(), e.getValue());
        }
        List<String> off = new ArrayList<>();
        for (UUID u : scoreboardOff) off.add(u.toString());
        yml.set("scoreboard-desactive", off);
        try {
            plugin.getDataFolder().mkdirs();
            yml.save(file);
        } catch (IOException e) {
            plugin.getLogger().severe("Impossible de sauvegarder data.yml : " + e.getMessage());
        }
    }

    /** Sauvegarde différée (utilisée pour les kills, très fréquents). */
    public void saveIfDirty() {
        if (dirty) save();
    }

    // ---------- Histoire ----------

    public Collection<Chapter> chapters() { return chapters.values(); }

    public Chapter chapter(String id) { return chapters.get(id); }

    public boolean createChapter(String id, String name) {
        if (chapters.containsKey(id)) return false;
        chapters.put(id, new Chapter(id, name));
        save();
        return true;
    }

    public boolean deleteChapter(String id) {
        boolean ok = chapters.remove(id) != null;
        if (ok) save();
        return ok;
    }

    public void addBook(String chapterId, ItemStack book) {
        Chapter c = chapters.get(chapterId);
        if (c == null) return;
        ItemStack copy = book.clone();
        copy.setAmount(1);
        c.books.add(copy);
        save();
    }

    public boolean removeBook(String chapterId, int index) {
        Chapter c = chapters.get(chapterId);
        if (c == null || index < 0 || index >= c.books.size()) return false;
        c.books.remove(index);
        save();
        return true;
    }

    // ---------- Quêtes (progression commune) ----------

    public boolean isQuestDone(String id) { return completedQuests.contains(id); }

    public void setQuestDone(String id, boolean done) {
        if (done) completedQuests.add(id); else completedQuests.remove(id);
        save();
    }

    public boolean isChapterUnlocked(String id) { return unlockedChapters.contains(id); }

    public void setChapterUnlocked(String id, boolean unlocked) {
        if (unlocked) unlockedChapters.add(id); else unlockedChapters.remove(id);
        save();
    }

    public int getKills(String questId, String type) {
        return kills.getOrDefault(questId + ";" + type, 0);
    }

    public void addKill(String questId, String type) {
        kills.merge(questId + ";" + type, 1, Integer::sum);
        dirty = true;
    }

    public void resetKills(String questId) {
        kills.keySet().removeIf(k -> k.startsWith(questId + ";"));
        save();
    }

    // ---------- Objets spéciaux ----------

    public Map<String, ItemStack> specialItems() { return specialItems; }

    public ItemStack specialItem(String id) { return specialItems.get(id); }

    public void registerSpecialItem(String id, ItemStack item) {
        ItemStack copy = item.clone();
        copy.setAmount(1);
        specialItems.put(id, copy);
        save();
    }

    public boolean deleteSpecialItem(String id) {
        boolean ok = specialItems.remove(id) != null;
        if (ok) save();
        return ok;
    }

    // ---------- Scoreboard ----------

    public boolean isScoreboardEnabled(UUID id) { return !scoreboardOff.contains(id); }

    public void setScoreboardEnabled(UUID id, boolean on) {
        if (on) scoreboardOff.remove(id); else scoreboardOff.add(id);
        save();
    }

    // ---------- Pupitres ----------

    private static String key(Block b) {
        return b.getWorld().getName() + ";" + b.getX() + ";" + b.getY() + ";" + b.getZ();
    }

    public void addLectern(Block b) { lecterns.add(key(b)); save(); }

    public void removeLectern(Block b) { lecterns.remove(key(b)); save(); }

    public boolean isLectern(Block b) {
        return b.getType() == Material.LECTERN && lecterns.contains(key(b));
    }
}
