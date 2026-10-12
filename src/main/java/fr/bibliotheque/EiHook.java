package fr.bibliotheque;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Pont vers l'API d'ExecutableItems (classes dans SCore), par réflexion : aucune dépendance de compilation.
 * Vérifié sur SCore 5.26.10.4 / ExecutableItems 7.26.10.4 :
 *   com.ssomar.score.api.executableitems.ExecutableItemsAPI.getExecutableItemsManager()
 *     .getExecutableItem(ItemStack) -> Optional&lt;ExecutableItemInterface&gt;   (getId(), getIconItem(), give(Player,int))
 *     .getExecutableItem(String) / isValidID(String) / getExecutableItemIdsList()
 */
public final class EiHook {

    private static final String[] API_CLASSES = {
            "com.ssomar.score.api.executableitems.ExecutableItemsAPI",
            "com.ssomar.executableitems.api.ExecutableItemsAPI"
    };

    private final Bibliotheque plugin;
    private boolean initDone;
    private Object manager;
    private Method byItem, byId, isValid, idList;
    private String error = "ExecutableItems n'est pas installé ou pas encore chargé.";
    private boolean warned;

    public EiHook(Bibliotheque plugin) {
        this.plugin = plugin;
    }

    private void init() {
        if (initDone) return;
        Plugin ei = Bukkit.getPluginManager().getPlugin("ExecutableItems");
        if (ei == null || !ei.isEnabled()) return; // on réessaiera plus tard

        initDone = true;
        ClassLoader[] loaders = {plugin.getClass().getClassLoader(), ei.getClass().getClassLoader()};
        for (String cn : API_CLASSES) {
            for (ClassLoader cl : loaders) {
                try {
                    Class<?> api = Class.forName(cn, true, cl);
                    Method gm = api.getMethod("getExecutableItemsManager");
                    Object mgr = gm.invoke(null);
                    Class<?> mi = gm.getReturnType();
                    byItem = mi.getMethod("getExecutableItem", ItemStack.class);
                    byId = mi.getMethod("getExecutableItem", String.class);
                    isValid = mi.getMethod("isValidID", String.class);
                    idList = mi.getMethod("getExecutableItemIdsList");
                    manager = mgr;
                    error = null;
                    plugin.getLogger().info("API ExecutableItems détectée (" + cn + ").");
                    return;
                } catch (ReflectiveOperationException | LinkageError ignored) {
                    byItem = null;
                }
            }
        }
        error = "API ExecutableItems introuvable (SCore/ExecutableItems trop ancien ou différent ?).";
        plugin.getLogger().warning(error);
    }

    /** Message d'erreur si l'API n'est pas utilisable, sinon null. */
    public String status() {
        init();
        return (byItem != null && manager != null) ? null : error;
    }

    private static Object unwrap(Object opt) {
        return (opt instanceof Optional<?> o && o.isPresent()) ? o.get() : null;
    }

    private void warn(Exception e) {
        if (!warned) {
            warned = true;
            plugin.getLogger().warning("Appel à l'API ExecutableItems impossible : " + e);
        }
    }

    /** Id ExecutableItems de l'objet, ou null si ce n'est pas un ExecutableItem. */
    public String idOf(ItemStack it) {
        if (it == null || it.getType().isAir() || status() != null) return null;
        try {
            Object item = unwrap(byItem.invoke(manager, it));
            if (item == null) return null;
            Method m = item.getClass().getMethod("getId");
            try { m.setAccessible(true); } catch (RuntimeException ignored) { }
            Object r = m.invoke(item);
            return r == null ? null : r.toString();
        } catch (ReflectiveOperationException | RuntimeException e) {
            warn(e);
            return null;
        }
    }

    /** L'id existe-t-il dans ExecutableItems ? */
    public boolean isValidId(String id) {
        if (status() != null) return false;
        try {
            return (boolean) isValid.invoke(manager, id);
        } catch (ReflectiveOperationException | RuntimeException e) {
            warn(e);
            return false;
        }
    }

    /** Tous les ids ExecutableItems (pour l'auto-complétion). */
    public List<String> ids() {
        if (status() != null) return List.of();
        try {
            List<String> out = new ArrayList<>();
            if (idList.invoke(manager) instanceof List<?> l) {
                for (Object o : l) out.add(String.valueOf(o));
            }
            return out;
        } catch (ReflectiveOperationException | RuntimeException e) {
            warn(e);
            return List.of();
        }
    }

    private Object itemById(String id) throws ReflectiveOperationException {
        return unwrap(byId.invoke(manager, id));
    }

    /** Objet-icône de l'ExecutableItem (pour récupérer son vrai nom), ou null. */
    public ItemStack icon(String id) {
        if (status() != null) return null;
        try {
            Object item = itemById(id);
            if (item == null) return null;
            Method m = item.getClass().getMethod("getIconItem");
            try { m.setAccessible(true); } catch (RuntimeException ignored) { }
            return m.invoke(item) instanceof ItemStack st ? st : null;
        } catch (ReflectiveOperationException | RuntimeException e) {
            warn(e);
            return null;
        }
    }

    /** Donne l'ExecutableItem au joueur. Retourne -1 si l'id est inconnu / l'API indisponible. */
    public int give(String id, Player p, int amount) {
        if (status() != null) return -1;
        try {
            Object item = itemById(id);
            if (item == null) return -1;
            Method m = item.getClass().getMethod("give", Player.class, int.class);
            try { m.setAccessible(true); } catch (RuntimeException ignored) { }
            Object r = m.invoke(item, p, amount);
            return r instanceof Integer i ? i : 0;
        } catch (ReflectiveOperationException | RuntimeException e) {
            warn(e);
            return -1;
        }
    }
}
