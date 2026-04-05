# VillageDefensePro

**Plugin PvE coopératif pour Paper/Spigot 1.17 – 1.21**
Défendez le village contre des vagues de monstres de plus en plus puissantes. Construisez des tours, posez des pièges, choisissez votre kit et survivez aussi longtemps que possible.

> Développé par **ObsidianLabs** — Java 17 requis.

---

## Fonctionnalités

- **Arènes multiples** configurables indépendamment via GUI in-game
- **Système de vagues** legacy (config.yml) ou **avancé** (Gameplay System) avec difficulté, pools de mobs et overrides par vague
- **5 kits joueur** avec capacités passives et actives débloquées par niveau
- **6 types de tours** construites sur des emplacements dédiés, chacune avec 2 spécialisations exclusives
- **3 types de pièges** posables sur des emplacements dédiés
- **7 atouts (perks)** achetables en cours de partie dont compagnons loup et golem
- **Power-ups** dropant aléatoirement pendant les vagues (résistance, force, double dégâts, etc.)
- **Shop in-game** avec prix configurables via GUI admin
- **Système Waypoints** : les mobs suivent des chemins définis par ArmorStands invisibles
- **Achievements** entièrement configurables via GUI admin
- **Leaderboard & Stats joueur** persistés en SQLite ou MySQL
- **PlaceholderAPI** avec 30+ placeholders
- **API publique** (events, snapshots) pour intégrations tierces
- **Support MythicMobs** : intégrez vos mobs custom directement dans les vagues
- **Multi-serveur / BungeeCord** : synchronisation des arènes entre serveurs
- **Multiples langues** : FR, EN, DE — fichiers YAML éditables

---

## Dépendances

| Dépendance | Type | Rôle |
|---|---|---|
| Paper / Spigot 1.17+ | **Requise** | Serveur cible |
| Java 17 | **Requise** | Runtime |
| Vault | Optionnelle | Économie pour le shop |
| PlaceholderAPI | Optionnelle | Placeholders dans d'autres plugins |
| ProtocolLib | Optionnelle | Compatibilité affichage avancé |
| MythicMobs 5.x | Optionnelle | Mobs custom dans les vagues |
| OldCombatMechanics | Optionnelle | Combat 1.8-like |

---

## Installation

1. Copiez `VillageDefensePro.jar` dans le dossier `plugins/` du serveur.
2. Démarrez le serveur — les fichiers de configuration sont générés automatiquement.
3. Connectez-vous en tant qu'OP et suivez le **Guide de démarrage rapide** ci-dessous.

---

## Démarrage rapide

```
/vdadmin create <id>       # Créer une arène
/vdadmin setup <id>        # Ouvrir le GUI de configuration
```

Dans le GUI de configuration, définissez dans l'ordre :
1. **Lobby** (point d'attente des joueurs)
2. **Spawn joueurs** (point de téléportation au démarrage)
3. **Villageois** (un ou plusieurs à protéger)
4. **Spawners de mobs** (un ou plusieurs points d'apparition)
5. **Emplacements de tours** (optionnel)
6. **Emplacements de pièges** (optionnel)
7. **Chemins Waypoints** (optionnel — guide les mobs vers les villageois)

Une fois toutes les positions définies, l'arène se marque automatiquement comme prête.

---

## Commandes joueur

| Commande | Description |
|---|---|
| `/vd join [id]` | Rejoindre une arène (auto si une seule disponible) |
| `/vd rejoin` | Revenir dans la partie après une déconnexion |
| `/vd leave` | Quitter l'arène |
| `/vd kit` | Ouvrir le sélecteur de kit |
| `/vd shop` | Ouvrir le shop (pendant une partie) |
| `/vd stats` | Afficher ses statistiques |
| `/vd list` | Lister les arènes disponibles |
| `/vd top` | Classement des meilleurs joueurs |

Alias : `/villagedefense`, `/vdef`

---

## Commandes admin

| Commande | Description |
|---|---|
| `/vdadmin create <id>` | Créer une nouvelle arène |
| `/vdadmin delete <id>` | Supprimer une arène |
| `/vdadmin setup <id>` | Configurer une arène (GUI + baguette) |
| `/vdadmin reload` | Recharger config + messages + schematics |
| `/vdadmin forcestart <id>` | Forcer le démarrage d'une partie |
| `/vdadmin forcestop <id>` | Forcer la fin d'une partie |
| `/vdadmin setwave <id> <n>` | Passer directement à la vague n |
| `/vdadmin tp <id>` | Se téléporter au lobby d'une arène |
| `/vdadmin info <id>` | Afficher l'état détaillé d'une arène |
| `/vdadmin coins <joueur> <montant>` | Donner des coins à un joueur |
| `/vdadmin setlevel <joueur> <niveau>` | Définir le niveau d'un joueur |

Alias : `/vda`

---

## Permissions

| Permission | Par défaut | Description |
|---|---|---|
| `villagedefense.admin` | OP | Accès à toutes les commandes admin (parent) |
| `villagedefense.admin.arena` | OP | Créer / supprimer des arènes |
| `villagedefense.admin.setup` | OP | Configurer une arène |
| `villagedefense.admin.schematic` | OP | Gérer les schematics de tours |
| `villagedefense.admin.lang` | OP | Changer la langue |
| `villagedefense.admin.database` | OP | Configurer la base de données |
| `villagedefense.admin.prices` | OP | Modifier les prix du shop |
| `villagedefense.admin.reload` | OP | Recharger la configuration |
| `villagedefense.admin.game` | OP | Forcestart / forcestop / setwave / info |
| `villagedefense.admin.player` | OP | Gérer coins et niveaux des joueurs |
| `villagedefense.play` | Tous | Jouer à Village Defense Pro |
| `villagedefense.test` | Aucun | Rejoindre les arènes en mode test |
| `villagedefense.bypass` | OP | Bypass certaines restrictions |

---

## Documentation complète

Consultez [Wiki.md](Wiki.md) pour la documentation exhaustive :
systèmes de jeu, gameplay avancé, MythicMobs, waypoints, API, placeholders, base de données, BungeeCord et plus encore.

---

*VillageDefensePro — ObsidianLabs*
