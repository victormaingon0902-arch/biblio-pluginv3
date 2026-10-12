package fr.bibliotheque;

import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;

public final class Bibliotheque extends JavaPlugin {

    private DataStore data;
    private QuestManager quests;
    private Gui gui;
    private LecternManager lecterns;
    private ScoreboardManager scoreboard;
    private EiHook ei;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        if (!new File(getDataFolder(), "quests.yml").exists()) {
            saveResource("quests.yml", false);
        }

        data = new DataStore(this);
        data.load();
        quests = new QuestManager(this);
        quests.load();
        gui = new Gui(this);
        lecterns = new LecternManager(this);
        scoreboard = new ScoreboardManager(this);
        ei = new EiHook(this);

        getServer().getPluginManager().registerEvents(gui, this);
        getServer().getPluginManager().registerEvents(lecterns, this);
        getServer().getPluginManager().registerEvents(scoreboard, this);
        getServer().getPluginManager().registerEvents(new QuestListener(this), this);

        scoreboard.start();
        // Les kills sont sauvegardés par lot toutes les 30 secondes.
        getServer().getScheduler().runTaskTimer(this, () -> data.saveIfDirty(), 600L, 600L);

        PluginCommand cmd = getCommand("bibliotheque");
        if (cmd != null) {
            BiblioCommand bc = new BiblioCommand(this);
            cmd.setExecutor(bc);
            cmd.setTabCompleter(bc);
        }
    }

    @Override
    public void onDisable() {
        if (scoreboard != null) scoreboard.shutdown();
        if (data != null) data.save();
    }

    public void reloadAll() {
        reloadConfig();
        quests.load();
    }

    public DataStore data() { return data; }
    public QuestManager quests() { return quests; }
    public Gui gui() { return gui; }
    public LecternManager lecterns() { return lecterns; }
    public ScoreboardManager scoreboard() { return scoreboard; }
    public EiHook ei() { return ei; }
}
