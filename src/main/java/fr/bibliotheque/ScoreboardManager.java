package fr.bibliotheque;

import io.papermc.paper.scoreboard.numbers.NumberFormat;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.Statistic;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.scoreboard.Criteria;
import org.bukkit.scoreboard.DisplaySlot;
import org.bukkit.scoreboard.Objective;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.Team;

import java.util.*;

/** Scoreboard latéral : quête en cours, puis statistiques du joueur. */
public final class ScoreboardManager implements Listener {

    private static final MiniMessage MM = MiniMessage.miniMessage();
    private static final int MAX_LINES = 15;

    private static final class Board {
        final Scoreboard sb;
        final Objective obj;
        final Team[] teams = new Team[MAX_LINES];

        Board(Scoreboard sb, Objective obj) {
            this.sb = sb;
            this.obj = obj;
        }
    }

    private final Bibliotheque plugin;
    private final Map<UUID, Board> boards = new HashMap<>();

    public ScoreboardManager(Bibliotheque plugin) {
        this.plugin = plugin;
    }

    public void start() {
        Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            for (Player p : Bukkit.getOnlinePlayers()) update(p);
        }, 20L, 20L);
    }

    public void shutdown() {
        for (Player p : Bukkit.getOnlinePlayers()) remove(p);
        boards.clear();
    }

    private static String entry(int i) {
        return "\u00a7" + Integer.toHexString(i) + "\u00a7r";
    }

    private Board create(Player p) {
        Scoreboard sb = Bukkit.getScoreboardManager().getNewScoreboard();
        Objective obj = sb.registerNewObjective("biblio", Criteria.DUMMY,
                MM.deserialize(plugin.getConfig().getString("scoreboard.titre", "<gold><bold>Serveur")));
        obj.setDisplaySlot(DisplaySlot.SIDEBAR);
        obj.numberFormat(NumberFormat.blank());

        Board b = new Board(sb, obj);
        for (int i = 0; i < MAX_LINES; i++) {
            Team t = sb.registerNewTeam("l" + i);
            t.addEntry(entry(i));
            b.teams[i] = t;
        }
        p.setScoreboard(sb);
        return b;
    }

    private void remove(Player p) {
        if (boards.remove(p.getUniqueId()) != null) {
            p.setScoreboard(Bukkit.getScoreboardManager().getMainScoreboard());
        }
    }

    /** Met à jour (ou crée / retire) le scoreboard d'un joueur. */
    public void update(Player p) {
        if (!plugin.getConfig().getBoolean("scoreboard.actif", true)
                || !plugin.data().isScoreboardEnabled(p.getUniqueId())) {
            remove(p);
            return;
        }
        Board b = boards.computeIfAbsent(p.getUniqueId(), k -> create(p));
        if (p.getScoreboard() != b.sb) p.setScoreboard(b.sb);

        List<String> lines = buildLines(p);
        int n = Math.min(lines.size(), MAX_LINES);
        for (int i = 0; i < n; i++) {
            b.teams[i].prefix(MM.deserialize(lines.get(i)));
            b.obj.getScore(entry(i)).setScore(n - i);
        }
        for (int i = n; i < MAX_LINES; i++) {
            b.sb.resetScores(entry(i));
        }
    }

    private List<String> buildLines(Player p) {
        QuestManager qm = plugin.quests();
        List<String> l = new ArrayList<>();

        l.add("");
        l.add("<gold><bold>Quête en cours");
        QuestManager.Quest q = qm.currentQuest(p);
        if (q == null) {
            l.add(" <gray>Aucune pour l'instant");
        } else {
            l.add(" <yellow>" + q.name());
            List<String> obj = qm.objectiveLines(p, q);
            if (obj.isEmpty()) {
                l.add(" <gray>Validez-la au pupitre");
            } else {
                for (int i = 0; i < Math.min(3, obj.size()); i++) l.add(" " + obj.get(i));
                if (obj.size() > 3) l.add(" <gray>…");
            }
        }

        l.add("");
        l.add("<gold><bold>Statistiques");
        l.add(" <gray>Morts : <white>" + p.getStatistic(Statistic.DEATHS));
        l.add(" <gray>Temps de jeu : <white>" + formatTime(p.getStatistic(Statistic.PLAY_ONE_MINUTE)));
        l.add(" <gray>Joueurs tués : <white>" + p.getStatistic(Statistic.PLAYER_KILLS));
        return l;
    }

    private static String formatTime(int ticks) {
        long minutes = ticks / 20L / 60L;
        long h = minutes / 60;
        long m = minutes % 60;
        return h > 0 ? h + "h " + m + "min" : m + "min";
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent e) {
        Bukkit.getScheduler().runTask(plugin, () -> update(e.getPlayer()));
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        boards.remove(e.getPlayer().getUniqueId());
    }
}
