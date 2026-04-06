# Wiki — VillageDefensePro

Documentation complète du plugin. Pour l'installation et les commandes rapides, voir [README.md](README.md).

---

## Table des matières

1. [Architecture](#1-architecture)
2. [Créer et configurer une arène](#2-créer-et-configurer-une-arène)
3. [Cycle de vie d'une partie](#3-cycle-de-vie-dune-partie)
4. [Kits](#4-kits)
5. [Tours](#5-tours)
6. [Pièges](#6-pièges)
7. [Atouts (Perks)](#7-atouts-perks)
8. [Power-ups](#8-power-ups)
9. [Shop & Enchantements](#9-shop--enchantements)
10. [Système Gameplay Avancé](#10-système-gameplay-avancé)
11. [Système Waypoints](#11-système-waypoints)
12. [Base de données](#12-base-de-données)
13. [Réseau / BungeeCord](#13-réseau--bungeecord)
14. [Achievements](#14-achievements)
15. [Leaderboard & Statistiques](#15-leaderboard--statistiques)
16. [API publique](#16-api-publique)
17. [PlaceholderAPI](#17-placeholderapi)
18. [Langues](#18-langues)
19. [Référence des permissions](#19-référence-des-permissions)
20. [Référence des commandes](#20-référence-des-commandes)
21. [Dépannage](#21-dépannage)

---

## 1. Architecture

Le plugin est organisé en packages fonctionnels indépendants :

```
villagedefense/
├── arena/          Modèle Arena, ArenaManager, ArenaSetup, chemins
├── game/           GameManager (logique de partie), ScoreboardManager, WaypointGuideManager
├── wave/           WaveManager, Wave, WaveType (système legacy)
├── gameplay/       Système avancé : modèles, providers, services (voir §10)
├── kit/            Kits joueur et leurs capacités
├── tower/          Tours, schematics, spécialisations
├── trap/           Pièges
├── perk/           Atouts achetables en jeu
├── powerup/        Power-ups dropant pendant les vagues
├── shop/           Shop in-game
├── player/         Données joueur (VDPlayer, PlayerManager)
├── database/       H2 local / MySQL
├── achievement/    Système d'achievements
├── leaderboard/    Affichage du classement
├── network/        Sync BungeeCord
├── hook/           Intégrations Vault, PAPI, ProtocolLib
├── gui/            Tous les GUIs Inventory
├── command/        /vd et /vdadmin
└── api/            API publique + events
```

**Point d'entrée** : `VillageDefensePlugin` (extends JavaPlugin). Tous les managers sont instanciés dans `onEnable()` et accessibles via des getters sur le plugin.

**Persistance** :

| Donnée | Stockage |
|---|---|
| Configuration des arènes | `arenas/<id>.yml` |
| Settings Gameplay avancé | `arenas/<id>_gameplay.yml` |
| Config générale | `config.yml` |
| Messages / traductions | `lang/<code>.yml` |
| Données joueur, stats, achievements | Base de données (H2 local ou MySQL) |
| Difficulté de session | Runtime uniquement (non persisté) |

---

## 2. Créer et configurer une arène

### Étape 1 — Créer l'arène

```
/vdadmin create <id>
```

L'`id` est l'identifiant interne (minuscule, sans espaces). Le nom d'affichage est configurable ensuite dans le GUI.

### Étape 2 — Entrer en mode setup

```
/vdadmin setup <id>
```

Vous recevez une **baguette de configuration** et le GUI principal de l'arène s'ouvre.

### Étape 3 — Définir les positions

Utilisez la baguette pour définir les positions en cliquant sur les blocs ou dans l'air. Le mode actif de la baguette est affiché dans l'action bar.

| Position | Description | Obligatoire |
|---|---|---|
| Lobby | Salle d'attente avant la partie | Oui |
| Spawn joueurs | Point d'apparition au démarrage de la partie | Oui |
| Villageois | Un ou plusieurs PNJ à protéger | Oui |
| Spawners mobs | Points d'apparition des ennemis | Oui (min. 1) |
| Emplacements tours | Cases où les joueurs construisent des tours | Non |
| Emplacements pièges | Cases où les joueurs posent des pièges | Non |
| Spectateurs | Point d'apparition des spectateurs | Non |

**Modes de la baguette** :

| Mode | Action | Effet |
|---|---|---|
| `DEFAULT` | Shift+Clic gauche | Ajouter un spawner de mob |
| `DEFAULT` | Shift+Clic droit | Ajouter un emplacement de tour |
| `BOUNDARY` | Clic gauche / droit | Définir Pos1 / Pos2 (zone de l'arène) |
| `REMOVE` | Shift+Clic gauche/droit | Supprimer spawner / emplacement de tour |
| `TRAP` | Shift+Clic droit/gauche | Ajouter / supprimer emplacement de piège |
| `VILLAGER_SPAWN` | Shift+Clic droit/gauche | Ajouter / supprimer spawn de villageois |
| `CHEST` | Clic droit sur coffre | Enregistrer le coffre shop |
| `SIGN` | Clic droit sur panneau | Enregistrer le panneau d'arène |
| `SHOP_VILLAGER` | Shift+Clic droit | Définir la position du marchand |
| `ENCHANT_VILLAGER` | Shift+Clic droit | Définir la position de l'enchanteur |
| `SCHEMATIC` | Clic gauche/droit | Capturer une structure |

### Étape 4 — Options avancées (GUI)

Depuis le GUI Setup → **Options** :

| Option | Description |
|---|---|
| Nom d'affichage | Texte avec codes couleur `&` |
| Joueurs min / max | Entre 1 et 20 |
| Vies du village | Nombre de villageois pouvant mourir avant la défaite |
| Disponibilité | `ACTIVE`, `TEST`, `DISABLED` |
| Mode de limite | `PUSH_BACK` (repousse) ou `TP_CENTER` (téléporte au village) |
| Limite de vague | Victoire automatique à la vague N (0 = infini) |
| Limite de tentatives | N essais par fenêtre de X heures par joueur |
| Récompenses | Commandes console et dépôt Vault en fin de partie |

### Étape 5 — Emplacements Gameplay

GUI Setup → **Emplacements Gameplay** : configurez les spawns mobs, spawns villageois, marchand, enchanteur, tours, pièges et chemins. Les chemins de waypoints se gèrent depuis ce sous-GUI. Voir §11.

### Étape 6 — Customisation Vague

GUI Setup → **Customisation Vague** : configurez le système de vagues avancé avec difficulté, composition, catégories, scaling et overrides. Voir §10.

### Ordre de setup recommandé

1. Nom, Min, Max
2. Pos1 et Pos2 (zone)
3. Lobby spawn
4. Game spawn
5. Spawners mobs
6. Spawners villageois
7. Emplacements tours
8. Emplacements pièges
9. Coffre shop, Marchand, Enchanteur
10. Emplacements Gameplay
11. Customisation Vague
12. Disponibilité → `TEST`
13. Sauvegarder → tester → passer en `ACTIVE`

---

## 3. Cycle de vie d'une partie

```
SETUP → WAITING → STARTING → RUNNING ⇄ BETWEEN_WAVES → ENDING → RESETTING → WAITING
```

| État | Description |
|---|---|
| `SETUP` | Arène en cours de configuration par un admin |
| `WAITING` | En attente du nombre minimum de joueurs |
| `STARTING` | Compte à rebours (5 → 1) avant le début |
| `RUNNING` | Vague en cours — mobs actifs |
| `BETWEEN_WAVES` | Intervalle entre deux vagues — les joueurs récupèrent, achètent, construisent |
| `ENDING` | Partie terminée — affichage des scores et récompenses |
| `RESETTING` | Réinitialisation de l'arène (restauration snapshot, nettoyage entités) |

### Déclencheurs de fin de partie

- **Défaite** : toutes les vies du village épuisées
- **Victoire** : limite de vague atteinte (si configurée)
- **Admin** : `/vdadmin forcestop <id>`

### Mort et spectateur

Quand un joueur meurt pendant la partie :
- Il respawn en **mode spectateur custom** : invisible, vol autorisé, interactions bloquées
- Il dispose d'un item dédié pour quitter la partie
- Si une partie est en attente, il peut rejoindre en spectateur via `/vd join <id>`

### Rejoin après déconnexion

Un **token de rejoin** est conservé tant que la partie dure. `/vd rejoin` permet de réintégrer la partie depuis n'importe où.

---

## 4. Kits

Les kits se sélectionnent via `/vd kit` avant ou pendant la phase d'attente. Chaque kit se débloque à un niveau minimal (configurable dans `config.yml`).

| Kit | Niveau par défaut | Rôle | Capacité passive | Capacité active |
|---|---|---|---|---|
| **Guardian** | 1 | Tank | Réduction des dégâts reçus, bonus bouclier | Provocation — force les mobs proches à cibler le Guardian |
| **Ranger** | 3 | DPS distance | Bonus de portée et précision arc | Flèche perforante / explosive à recharge |
| **Berserker** | 5 | DPS mêlée | Dégâts augmentés à basse vie | Rage — vitesse d'attaque temporaire, vol de vie |
| **Medic** | 7 | Support soin | Aura de régénération autour des alliés proches | Impulsion de soin de zone instantanée |
| **Engineer** | 10 | Support défensif | Réduction des coûts de construction de tours | Surcadence + réparation des tours adjacentes |

Les niveaux requis sont modifiables via GUI Admin → **Niveaux des Kits** (clic pour modifier, Shift+Clic pour réinitialiser).

---

## 5. Tours

Les tours se construisent sur des **emplacements dédiés** définis pendant le setup. Elles tirent automatiquement sur les mobs à portée.

### Types de tours

| Tour | Style | Description |
|---|---|---|
| **Archer** | Longue portée | Flèches rapides, dégâts modérés, cible unique |
| **Trébuchet** | Zone | TNT à longue portée, explosion de zone, cadence lente |
| **Mage** | Effets | Projectiles magiques à portée moyenne avec effets de statut |
| **Glace** | Ralentissement | Boules de glace — ralentit les mobs touchés |
| **Foudre** | Dégâts élevés | Éclairs aléatoires dans la zone, dégâts importants |
| **Canon** | Pénétration | Boulets perforants en ligne droite |

Chaque tour possède plusieurs **niveaux d'amélioration** (achetables avec les coins). Les stats (dégâts, portée, cadence de tir) progressent à chaque niveau.

### Spécialisations

Au niveau maximum, chaque tour débloque **2 spécialisations mutuellement exclusives** — choisissez-en une seule par tour.

| Tour | Spécialisation A | Spécialisation B |
|---|---|---|
| Archer | **Multi-Shot** — salve de flèches | **Incendiary Arrow** — flèches enflammées |
| Trébuchet | **Incendiary TNT** — explosion incendiaire | **Cluster** — fragmentation à l'impact |
| Mage | **Fire** — dégâts feu persistants | **Poison** — venin sur durée |
| Glace | **Blizzard** — zone de gel élargie | **Frost Nova** — gel instantané au contact |
| Foudre | **Overload** — chaîne d'éclairs | **Stun** — étourdissement |
| Canon | **Explosive Shot** — explosion à l'impact | **Grapeshot** — dispersion latérale |

### Configuration par arène

GUI Setup → **Config Tours** : activer/désactiver chaque type de tour, modifier son coût et son niveau requis pour cette arène.

### Schematics

Les tours sont construites à partir de schematics YAML. Gérez-les via GUI Admin → **Schematics**. Catégories disponibles : `ARCHER`, `TREBUCHET`, `MAGE`, `ICE`, `LIGHTNING`, `CANNON`, `TOWER_SLOT`, `SPAWN_MONSTER`.

Les schematics peuvent être **globaux** (partagés entre arènes) ou **locaux** (spécifiques à une arène).

---

## 6. Pièges

Les pièges se posent sur des **emplacements dédiés** et s'activent automatiquement au passage des mobs.

| Piège | Coût | Effet | Rechargement |
|---|---|---|---|
| **Piège ralentisseur** | 30 coins | Applique Slowness à tous les mobs dans le rayon | 15 secondes |
| **Mine explosive** | 50 coins | Explosion de zone (usage unique — détruit après activation) | Aucun |
| **Filet** | 40 coins | Immobilise les mobs (toile de cobweb) | 25 secondes |

Un indicateur d'état (actif / rechargement) est visible sur l'emplacement.

---

## 7. Atouts (Perks)

Les perks s'achètent en cours de partie depuis le GUI des atouts, accessible depuis le shop ou automatiquement proposé en inter-vague.

| Atout | Coût | Niveau requis | Max stacks | Effet |
|---|---|---|---|---|
| **Vitalité** | 150 coins | 1 | 3× | +4 cœurs max par stack |
| **Force** | 100 coins | 1 | 3× | +2 dégâts d'attaque par stack |
| **Compagnon Loup** | 300 coins | 3 | Illimité* | Invoque un loup allié |
| **Compagnon Golem** | 500 coins | 6 | Illimité* | Invoque un golem de fer allié |
| **Wave Skip** | 800 coins | 5 | 1× | Saute la vague en cours immédiatement |
| **Acheter Villageois** | 2 000 coins | 5 | 2× | Ajoute un villageois protégé |
| **Village Guard** | 1 500 coins | 8 | 1× | Invoque un garde permanent |

*Le nombre de compagnons simultanés est plafonné globalement (configurable dans `config.yml`).

---

## 8. Power-ups

Des power-ups apparaissent aléatoirement sur la carte pendant les vagues. Les joueurs les ramassent en marchant dessus. Ils disparaissent après 45 secondes si non ramassés.

| Power-up | Durée | Effet |
|---|---|---|
| **Résistance** | 20 secondes | Résistance III (réduction des dégâts) |
| **Vitesse** | 30 secondes | Vitesse I |
| **Force** | 30 secondes | Force I |
| **Régénération** | 15 secondes | Régénération I |
| **Zombie Clear** | Instantané | Élimine tous les mobs actifs **sauf le boss de vague** |
| **Instant Kill** | 10 secondes | Chaque attaque tue en un coup |
| **Double Damage** | 30 secondes | Dégâts ×2 |

La fréquence et les probabilités de drop de chaque power-up sont configurables dans `config.yml`.

---

## 9. Shop & Enchantements

### Shop

Accessible via `/vd shop` en cours de partie. Les joueurs dépensent leurs **coins** gagnés en tuant des mobs.

Le shop peut fonctionner via :
- Un **coffre d'arène** configuré (contenu personnalisable)
- Le **shop interne** de fallback (liste configurée dans `config.yml`)

Les prix du shop sont modifiables via GUI Admin → **Prix**.

### Enchantements

Un GUI d'enchantement séparé (villageois enchanteur) permet d'améliorer les équipements du kit avec :

`Sharpness` · `Fire Aspect` · `Unbreaking` · `Power` · `Flame` · `Infinity` · `Protection` · `Feather Falling` · `Mending`

Les prix des enchantements sont configurables par arène via GUI Setup → **Prix Enchantements**.

---

## 10. Système Gameplay Avancé

Le système avancé de vagues remplace le système legacy par une architecture entièrement configurable par arène. Il se configure via GUI Admin → Setup arène → **Customisation Vague**.

### Accès au GUI

Le `GameplayMainGUI` expose directement :

- **Difficulté**
- **Scaling des vagues**
- **Catégories de vagues**
- **Composition**
- **Overrides de vague**

Le système avancé est désormais activé par défaut et n'est plus exposé comme une option de bascule dans le GUI principal.

### Difficulté

Quatre niveaux disponibles :

| Niveau | Description |
|---|---|
| `EASY` | Mobs moins nombreux, multiplicateurs réduits |
| `NORMAL` | Valeurs par défaut |
| `HARD` | Budget de mobs plus élevé, multiplicateurs accrus |
| `CUSTOM` | Profil personnalisé via les settings de difficulté |

La difficulté par défaut est définie dans les settings de l'arène et assignée à la session au démarrage. Elle détermine les **multiplicateurs HP, dégâts et vitesse** des mobs, ainsi que le **budget** de mobs par vague.

### WaveCategories

Chaque vague est classée dans une catégorie :

| Catégorie | Description |
|---|---|
| `NORMAL` | Vague standard |
| `ELITE` | Mobs plus résistants, moins nombreux |
| `BOSS` | Un ou quelques boss très puissants — boss bar affichée |
| `SPECIAL` | Vague thématique (loups, araignées, sorcières, etc.) |

La catégorie est résolue automatiquement par le `WaveCategoryResolver` selon le numéro de vague et la configuration de l'arène.

### Mob Pools

Un **Mob Pool** est une liste de `MobEntry` configurée via GUI Gameplay → **Composition**.

Chaque `MobEntry` définit :

| Champ | Description |
|---|---|
| `id` | Identifiant du mob (EntityType Bukkit ou nom interne MythicMobs) |
| `source` | `VANILLA` ou `MYTHICMOBS` |
| `weight` | Poids pour le tirage pondéré |
| `threat` | Coût en budget par unité (contrôle la densité) |
| `minAmount` / `maxAmount` | Fourchette de quantité par vague |
| `minWave` / `maxWave` | Plage de vagues où ce mob peut apparaître (0 = illimité) |
| `enabled` | Activer / désactiver sans supprimer |

### Modes de composition

| Mode | Comportement |
|---|---|
| `WEIGHTED` | Tirage pondéré — les mobs avec un poids plus élevé apparaissent plus souvent |
| `RANDOM` | Sélection aléatoire pure parmi les entrées valides |
| `FIXED` | Ordre fixe, quantités minimales — composition déterministe |

### Wave Overrides

Une **override** force une configuration spécifique pour une vague précise.

Exemple : vague 25 = toujours une vague BOSS avec un pool dédié, indépendamment du resolver.

Se configure via GUI Gameplay → **Overrides de vague**.

### Wave Scaling

Définit l'évolution des multiplicateurs (HP, dégâts, vitesse, budget) en fonction du numéro de vague. Configurable via GUI Gameplay → **Scaling des vagues**.

### Intégration MythicMobs

Si MythicMobs 5.x est installé, ses mobs apparaissent dans la liste de sélection des Mob Pools avec le badge `[MYTHICMOBS]`.

**Fonctionnement du `MythicMobsProvider`** :
1. Liste tous les types définis dans MythicMobs (`MythicBukkit.inst().getMobManager().getMobTypes()`)
2. Spawne via l'API native MythicMobs — skills, IA et display name MythicMobs sont préservés
3. Applique **par-dessus** les multiplicateurs HP/dégâts/vitesse du profil de difficulté de l'arène
4. Tag le mob avec les métadonnées de l'arène pour le tracking (mort, waypoint, boss bar)

**Prérequis** : MythicMobs 5.x dans `plugins/`. Le log de démarrage doit afficher : `[Gameplay] MythicMobs détecté — provider enregistré.`

---

## 11. Système Waypoints

Le système Waypoints guide les mobs le long de chemins prédéfinis vers les villageois, évitant les blocages sur les obstacles de la map.

### Comment ça fonctionne

1. **Configuration** : l'admin définit un ou plusieurs **chemins** (séquence ordonnée de points) associés aux spawners via GUI Setup → **Emplacements Gameplay** → **Chemins**.
2. **Au spawn** : chaque mob reçoit un `PathGuide` basé sur le chemin de son spawner.
3. **Tick (toutes les 5 ticks / 250 ms)** : le `WaypointGuideManager` déplace un **ArmorStand invisible** de 0,3 blocs vers le prochain waypoint du chemin.
4. **Ciblage IA** : le mob cible l'ArmorStand. Comme ce dernier avance pas à pas, le mob le suit naturellement le long du chemin.
5. **Fin du chemin** : une fois le dernier waypoint atteint, les mobs sont reciblés vers le villageois le plus proche. L'ArmorStand est supprimé.

### Sans waypoints

Si un spawner n'a pas de chemin, les mobs sont reciblés directement vers le villageois le plus proche 2 ticks après le spawn (comportement par défaut de l'IA Bukkit).

### Notes techniques

- Chaque mob a son propre ArmorStand guide (indépendants).
- Les ArmorStands sont invisibles, sans gravité, sans collision.
- À la mort du mob, son guide est supprimé immédiatement.
- En cas de `onDisable`, tous les guides sont nettoyés et toutes les ArmorStands supprimées.

---

## 12. Base de données

### Configuration

Dans `config.yml` :

```yaml
database:
  type: SQLITE  # SQLITE (backend local H2) ou MYSQL
  host: localhost
  port: 3306
  name: villagedefense
  username: root
  password: ''
  pool-size: 10
```

Le GUI Admin → **Base de Données** permet de modifier ces paramètres in-game.

### Ce qui est persisté

| Donnée | Stockage |
|---|---|
| Stats joueur (kills, vagues, parties, coins gagnés) | Base de données |
| Niveau et XP | Base de données |
| Kits débloqués | Base de données |
| Achievements complétés | Base de données |
| Tentatives par arène (`vd_arena_attempts`) | Base de données |
| Configuration des arènes | YAML `arenas/<id>.yml` |
| Settings Gameplay avancé | YAML `arenas/<id>_gameplay.yml` |
| Difficulté sélectionnée en session | Runtime uniquement |

### Recommandation

- **H2 local** : parfait pour un serveur standalone. Aucune configuration supplémentaire.
- **MySQL** : recommandé pour un réseau multi-serveurs ou si vous avez besoin de backups centralisés.

### Note importante sur les stats par arène

Les statistiques dédiées au classement **par arène** (`vd_arena_stats`) sont utilisées en mode **MySQL**.

En backend local `SQLITE`/H2 :

- les données joueur globales restent persistées normalement
- les stats spécifiques au leaderboard par arène sont ignorées proprement
- cela évite les warnings SQL sur le backend local

---

## 13. Réseau / BungeeCord

VillageDefensePro supporte la synchronisation d'arènes entre plusieurs serveurs d'un réseau BungeeCord.

### Activation

Dans `config.yml` :

```yaml
network:
  enabled: true
  server-id: "survival-1"
  sync-interval: 10
```

GUI Admin → **Réseau** expose les options de synchronisation.

### Fonctionnement

Le `NetworkSyncManager` publie l'état de chaque arène locale via le canal BungeeCord (`BungeeCord:Forward`). Les autres serveurs reçoivent les mises à jour et les stockent dans des `NetworkArenaEntry` locales.

Les placeholders `%villagedefense_arena_<id>_*%` fonctionnent en cross-serveur grâce à ces entrées synchronisées.

Le `return-server` configurable dans le GUI BungeeCord permet de renvoyer les joueurs vers un serveur lobby précis en fin de partie.

---

## 14. Achievements

### Gestion admin

GUI Admin → **Achievements** : créer, éditer, supprimer des achievements directement en jeu.

Chaque achievement définit :
- **ID** unique
- **Nom & description** (codes couleur `&` supportés)
- **Icône** Material
- **Condition** : type de progression + valeur cible
- **Récompenses** : coins, XP, kit débloqué

### Types de conditions

| Type | Description |
|---|---|
| `TOTAL_KILLS` | Nombre total de mobs tués |
| `TOTAL_WAVES` | Nombre total de vagues complétées |
| `TOTAL_GAMES` | Nombre total de parties jouées |
| `WAVE_REACHED` | Atteindre la vague N dans une seule partie |
| `COINS_EARNED` | Coins gagnés au total |
| `LEVEL_REACHED` | Atteindre le niveau N |

### Déclenchement

Les achievements sont vérifiés à chaque `VillageDefenseProgressEvent`. Si la condition est remplie, l'achievement est accordé **une seule fois** par joueur.

---

## 15. Leaderboard & Statistiques

### Statistiques joueur

Accessibles via `/vd stats` ou les [placeholders PAPI](#17-placeholderapi).

| Stat | Description |
|---|---|
| Niveau | Progression globale basée sur l'XP cumulée |
| Kills totaux | Mobs tués toutes parties confondues |
| Vagues complétées | Vagues terminées sans défaite |
| Parties jouées | Nombre total de parties |
| Coins gagnés | Coins cumulés depuis la création du compte |
| Kills de session | Kills de la partie en cours |
| Coins de session | Coins gagnés dans la partie en cours |

### Leaderboard global

Accessible via `/vd top`. Classe les joueurs par kills totaux par défaut.

Affichage :
- **GUI Inventory** paginé (`/vd top`)
- **Hologramme flottant global** (configurable dans ArenaOptionsGUI — classement toutes arènes confondues, rotation automatique kills/vagues/parties)
- **Hologramme flottant par arène** (classement propre à l'arène, disponible en pratique avec le backend MySQL)

---

## 16. API publique

L'API est accessible depuis d'autres plugins via le service Bukkit.

### Récupérer l'API

```java
VillageDefenseApi api = Bukkit.getServicesManager().load(VillageDefenseApi.class);
```

### Snapshots (lecture)

```java
// Arène
VillageDefenseArenaSnapshot arena = api.getArenaSnapshot("village_01");
String state   = arena.getState();        // "running", "waiting", "between_waves"...
int wave       = arena.getCurrentWave();
int players    = arena.getPlayerCount();
int lives      = arena.getVillageLives();

// Joueur
VillageDefensePlayerSnapshot player = api.getPlayerSnapshot(uuid);
int level      = player.getLevel();
int kills      = player.getTotalKills();
String kitId   = player.getSelectedKitId();  // "guardian", "ranger"...

// Liste des arènes
List<String> ids = api.getArenaIds();

// Version
String ver = api.getApiVersion();
```

### Événements

```java
@EventHandler
public void onProgress(VillageDefenseProgressEvent event) {
    VillageDefenseProgressType type = event.getType();
    // KILL, WAVE_COMPLETED, GAME_PLAYED, COINS_EARNED, LEVEL_UP...
    Player player  = event.getPlayer();
    int newValue   = event.getNewValue();
}
```

Cas d'usage typiques : battle pass, quêtes, missions, succès externes.

---

## 17. PlaceholderAPI

Identifiant de l'expansion : `villagedefense`

Format : `%villagedefense_<placeholder>%`

### Placeholders globaux

| Placeholder | Valeur |
|---|---|
| `plugin_version` | Version du plugin |
| `api_version` | Version de l'API publique |
| `arenas_total` | Nombre total d'arènes |
| `arenas_joinable` | Arènes en phase d'attente (rejoignables) |
| `arenas_running` | Arènes avec partie en cours |
| `arenas_spectatable` | Arènes en cours acceptant des spectateurs |
| `arenas_active` | Arènes en disponibilité ACTIVE |
| `arenas_test` | Arènes en mode TEST |
| `arenas_disabled` | Arènes désactivées |

### Placeholders joueur

| Placeholder | Valeur |
|---|---|
| `player_in_arena` | `true` / `false` |
| `player_spectating` | `true` / `false` |
| `player_coins` | Coins actuels |
| `player_level` | Niveau |
| `player_xp` | XP total |
| `player_total_kills` | Kills totaux |
| `player_total_waves` | Vagues complétées totales |
| `player_total_games` | Parties jouées |
| `player_total_coins_earned` | Coins gagnés au total |
| `player_session_kills` | Kills de la session en cours |
| `player_session_coins` | Coins de la session en cours |
| `player_selected_kit` | ID du kit sélectionné (`guardian`, `ranger`...) |
| `player_selected_kit_name` | Nom traduit du kit |
| `player_current_arena` | ID de l'arène actuelle |
| `player_current_arena_name` | Nom d'affichage de l'arène actuelle |
| `player_unlocked_kits_count` | Nombre de kits débloqués |
| `player_unlocked_achievements_count` | Achievements complétés |

### Placeholders d'arène (joueur dans une arène)

| Placeholder | Valeur |
|---|---|
| `arena_id` | ID de l'arène |
| `arena_display_name` | Nom d'affichage |
| `arena_state` | État (`running`, `waiting`, `between_waves`...) |
| `arena_wave` | Vague actuelle |
| `arena_players` | Joueurs actifs |
| `arena_spectators` | Spectateurs |
| `arena_max_players` | Capacité max |
| `arena_village_lives` | Vies restantes du village |
| `arena_active_mobs` | Mobs actuellement en vie |
| `arena_remaining_mobs` | Mobs restants à éliminer dans la vague |
| `arena_joinable` | `true` / `false` |
| `arena_spectatable` | `true` / `false` |

### Placeholders d'arène par ID (cross-serveur)

Format : `%villagedefense_arena_<arenaId>_<champ>%`

Exemple : `%villagedefense_arena_village01_wave%` → vague actuelle de l'arène `village01`.

Champs disponibles : `id`, `name`, `display_name`, `state`, `wave`, `players`, `spectators`, `max_players`, `village_lives`, `active_mobs`, `remaining_mobs`, `joinable`, `spectatable`.

---

## 18. Langues

### Fichiers disponibles

| Code | Langue |
|---|---|
| `fr` | Français (défaut) |
| `en` | Anglais |
| `de` | Allemand |

### Configuration

Dans `config.yml` :

```yaml
language: fr
```

Changeable in-game via GUI Admin → **Langue** (permission `villagedefense.admin.lang`).

### Personnalisation

Copiez et éditez le fichier dans `plugins/VillageDefensePro/lang/`. Les codes couleur `&` sont supportés partout. Une clé manquante affiche la clé brute en jeu.

---

## 19. Référence des permissions

| Permission | Par défaut | Description |
|---|---|---|
| `villagedefense.admin` | OP | Parent — accorde tous les nœuds admin |
| `villagedefense.admin.arena` | OP | `/vdadmin create` et `delete` |
| `villagedefense.admin.setup` | OP | `/vdadmin setup` + baguette de configuration |
| `villagedefense.admin.schematic` | OP | GUI gestion schematics |
| `villagedefense.admin.lang` | OP | Changer la langue du plugin |
| `villagedefense.admin.database` | OP | GUI configuration base de données |
| `villagedefense.admin.prices` | OP | GUI prix shop et enchantements |
| `villagedefense.admin.reload` | OP | `/vdadmin reload` |
| `villagedefense.admin.game` | OP | forcestart, forcestop, setwave, tp, info |
| `villagedefense.admin.player` | OP | coins, setlevel |
| `villagedefense.play` | Tous | Accès aux commandes joueur `/vd` |
| `villagedefense.test` | Aucun | Rejoindre les arènes en mode TEST |
| `villagedefense.bypass` | OP | Bypass certaines vérifications |

---

## 20. Référence des commandes

### /vd — Commandes joueur

Alias : `/villagedefense`, `/vdef`

| Sous-commande | Usage complet | Description |
|---|---|---|
| `join` | `/vd join [id]` | Rejoindre une arène. Si `id` omis et une seule arène disponible, rejoint automatiquement. |
| `rejoin` | `/vd rejoin` | Réintégrer une partie après déconnexion (token valide tant que la partie dure). |
| `leave` | `/vd leave` | Quitter l'arène ou le mode spectateur. |
| `kit` | `/vd kit` | Ouvrir le GUI de sélection de kit. |
| `shop` | `/vd shop` | Ouvrir le shop (uniquement pendant une partie). |
| `stats` | `/vd stats` | Afficher ses statistiques personnelles. |
| `list` | `/vd list` | Lister les arènes visibles avec leur état. |
| `top` | `/vd top` | Ouvrir le GUI du leaderboard. |
| `help` | `/vd help` | Afficher l'aide des commandes joueur. |

### /vdadmin — Commandes admin

Alias : `/vda`

Sans sous-commande : ouvre le GUI d'administration principal (joueur uniquement).

| Sous-commande | Usage complet | Permission | Description |
|---|---|---|---|
| `create` | `/vda create <id>` | `admin.arena` | Créer une nouvelle arène. |
| `delete` | `/vda delete <id>` | `admin.arena` | Supprimer une arène et ses données. |
| `setup` | `/vda setup <id>` | `admin.setup` | Ouvrir le GUI de configuration + donner la baguette. |
| `setup exit` | `/vda setup exit` | `admin.setup` | Quitter le mode configuration. |
| `reload` | `/vda reload` | `admin.reload` | Recharger config.yml, messages et schematics. |
| `forcestart` | `/vda forcestart <id>` | `admin.game` | Démarrer le compte à rebours immédiatement. |
| `forcestop` | `/vda forcestop <id>` | `admin.game` | Terminer la partie en cours (compte comme défaite). |
| `setwave` | `/vda setwave <id> <n>` | `admin.game` | Passer à la vague n (les mobs en cours sont tués). |
| `tp` | `/vda tp <id>` | `admin.game` | Se téléporter au lobby de l'arène. |
| `info` | `/vda info <id>` | `admin.game` | Afficher l'état complet de l'arène (état, joueurs, vague, vies, mobs, tours). |
| `coins` | `/vda coins <joueur> <montant>` | `admin.player` | Ajouter des coins à un joueur connecté. |
| `setlevel` | `/vda setlevel <joueur> <niveau>` | `admin.player` | Définir le niveau d'un joueur et débloquer automatiquement les kits associés. |

---

## 21. Dépannage

### Les mobs restent bloqués à leur spawner

**Cause** : aucun chemin waypoint défini pour ce spawner, ou le `WaypointGuideManager` n'a pas démarré.

**Solution** :
1. Définissez des chemins via GUI Setup → **Emplacements Gameplay** → **Chemins** et associez-les aux spawners.
2. Vérifiez les logs de démarrage — la ligne `[WaypointGuideManager] Démarré` doit être présente.
3. Évitez `/reload` — préférez un redémarrage complet du serveur.

---

### Le système avancé de vagues n'est pas utilisé

**Cause** : ancienne configuration d'arène, mauvais setup gameplay, ou problème de données chargées.

**Solution** :
1. Ouvrez GUI Admin → Setup arène → **Customisation Vague**.
2. Vérifiez que la difficulté, la composition et les catégories sont bien configurées.
3. Si besoin, réinitialisez les settings gameplay de l'arène puis reconfigurez-les.

---

### Les mobs MythicMobs n'apparaissent pas dans la liste de sélection

**Cause** : MythicMobs n'est pas installé ou est en version incompatible (5.x requis).

**Solution** :
1. Vérifiez que MythicMobs 5.x est présent dans `plugins/`.
2. Vérifiez les logs : `[Gameplay] MythicMobs détecté — provider enregistré.` doit apparaître.

---

### L'arène ne démarre pas automatiquement

**Cause** : nombre minimum de joueurs non atteint, ou arène non configurée complètement.

**Solution** :
1. `/vdadmin info <id>` — vérifiez `Setup: ✓`.
2. Si `Setup: ✗` : retournez dans le GUI de setup et définissez toutes les positions obligatoires.
3. Utilisez `/vdadmin forcestart <id>` pour démarrer sans attendre le minimum.

---

### Les données joueur ne sont pas sauvegardées après un crash

Les données sont sauvegardées à la déconnexion et en fin de partie. Un crash serveur peut entraîner la perte des données de la session en cours.

**Solution recommandée** : utiliser MySQL avec des backups automatiques de la base.

---

### Les placeholders ne fonctionnent pas

**Solution** :
1. Vérifiez que PlaceholderAPI est installé.
2. Vérifiez les logs de démarrage : l'expansion `villagedefense` doit être enregistrée.
3. Testez avec `/papi parse me %villagedefense_plugin_version%`.

---

### Le trade vanilla des villageois protégés s'ouvre

**Cause** : les villageois protégés ne sont pas correctement taggés comme entités de l'arène.

**Solution** : assurez-vous de placer les spawns de villageois **via le GUI setup** (pas de spawn d'entités manuel). Les villageois doivent être enregistrés par le plugin pour que leurs interactions soient interceptées.

---

Consultez aussi [FEATURES.md](FEATURES.md) pour la liste structurée des fonctionnalités.

*VillageDefensePro — ObsidianLabs — v1.3*
