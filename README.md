# Bibliotheque (Paper 26.2 / Java 25)

## Compilation
```
mvn clean package
```
Le jar se trouve dans `target/Bibliotheque-1.0.0.jar` → à mettre dans `plugins/`.

## Utilisation
1. `/biblio pupitre` : vous recevez un pupitre spécial. Posez-le : il reçoit un livre et ouvre la bibliothèque au clic droit.
   Seuls les admins (`bibliotheque.admin`) peuvent le poser ou le casser. `/biblio` ouvre aussi le menu.
2. **Histoire** (commandes) :
   - `/biblio histoire chapitre creer chapitre1 Chapitre I - Les origines`
   - Écrivez et **signez** un livre, tenez-le en main : `/biblio histoire livre ajouter chapitre1`
   - `/biblio histoire livre liste|retirer <chapitre> [n]`, `chapitre renommer|icone|supprimer`
3. **Quêtes** : éditez `plugins/Bibliotheque/quests.yml` puis `/biblio reload`
   (prérequis, permission requise, objets à livrer, récompenses : commandes, permissions LuckPerms, chapitres débloqués).
   Progression **commune** : une quête accomplie l'est pour tout le serveur.
   - `tuer: ["ZOMBIE:20"]` : mobs à tuer. Progression **commune** : les kills de tous les joueurs comptent, dès que la quête est débloquée.
     `validation-auto: true` valide la quête au dernier kill (sans livraison ni objet spécial).
   - `objets-speciaux: ["ei:Trace_civilisation:1"]` : objet **ExecutableItems**, reconnu par son id (`/biblio objet ei` avec l'objet en main affiche l'id).
     Le nom affiché est le vrai nom de l'ExecutableItem ; un id inexistant s'affiche en rouge et bloque la validation.
     `/biblio objet donner ei:Trace_civilisation 3` vous donne l'objet pour tester (auto-complétion des ids).
     Alternative pour n'importe quel objet : `/biblio objet creer relique` puis `objets-speciaux: ["relique:1"]` (reconnu s'il est identique).
4. **Scoreboard** : quête en cours + progression, puis morts, temps de jeu, joueurs tués. Réglages dans `config.yml`,
   chaque joueur peut le masquer avec `/biblio scoreboard`.
5. **Règlement** et **Réseaux** : `plugins/Bibliotheque/config.yml` puis `/biblio reload`.

## Notes
- Les permissions de récompense sont données via `lp user <joueur> permission set <perm> true` (LuckPerms requis pour ça uniquement).
- Les objets à livrer doivent être des objets « normaux » (sans nom ni enchantement personnalisé).
- Si un objet ExecutableItems change d'un exemplaire à l'autre (lore dynamique, compteur d'utilisations), la comparaison
  « identique » peut échouer : voir la note dans la réponse du chat.
- Jusqu'à 45 chapitres / livres / quêtes par écran.

## ExecutableItems
Pont vérifié sur SCore 5.26.10.4 / ExecutableItems 7.26.10.4 (API `com.ssomar.score.api.executableitems`). Aucune dépendance de compilation : le plugin utilise l'API par réflexion.
