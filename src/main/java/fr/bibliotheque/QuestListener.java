package fr.bibliotheque;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDeathEvent;

/** Compte les monstres tués pour les quêtes (progression commune à tout le serveur). */
public final class QuestListener implements Listener {

    private final Bibliotheque plugin;

    public QuestListener(Bibliotheque plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onDeath(EntityDeathEvent e) {
        Player killer = e.getEntity().getKiller();
        if (killer == null) return;
        plugin.quests().onKill(killer, e.getEntityType());
    }
}
