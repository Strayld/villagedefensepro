# CHANGELOG

Ce fichier sert de base officielle pour les notes de mise a jour.

## Regle De Maintien

- La section `Unreleased` doit etre lue et mise en avant avant chaque nouvelle phase de travail.
- Toute modification livree doit etre ajoutee ici en langage orienté notes de patch.
- Lors d'une release, le contenu de `Unreleased` doit etre deplace dans une nouvelle section versionnee.

Format inspire de Keep a Changelog.

## [Unreleased]

_(rien pour l'instant)_

## [1.1.3] - 2026-04-01

### Added

- **Système de limite de tentatives** (`ArenaAttemptManager`) : les admins peuvent configurer N tentatives par fenêtre de X heures sur chaque arène (0 = désactivé par défaut).
  - Nouvelle table DB `vd_arena_attempts (player_uuid, arena_id, attempts_used, window_start)` — implémentée pour MySQL et H2/SQLite.
  - `ArenaAttemptEntry` : POJO en mémoire (attemptsUsed + windowStart en epoch secondes).
  - `ArenaAttemptManager` : cache `Map<UUID, Map<String, ArenaAttemptEntry>>` chargé à la connexion du joueur (async), libéré à la déconnexion. Méthodes `isBlocked()`, `getRemainingAttempts()`, `getWindowRemainingSeconds()`, `incrementAttempt()`.
  - Chargement async au login dans `PlayerListener.onJoin`, déchargement dans `onQuit`.
  - L'incrément se déclenche au **lancement de la partie** (`GameManager.startGame()`), pas à l'entrée en lobby — les joueurs peuvent rejoindre et partir sans consommer de tentative.
  - Si la fenêtre a expiré, une nouvelle fenêtre s'ouvre automatiquement.
  - Blocage dans `ArenaManager.joinArena()` avec message `arena.attempt-blocked` indiquant le temps restant.
- **Nouveau bouton GUI** dans `ArenaOptionsGUI` (slot 21, HOPPER/BARREL) :
  - Clic gauche → prompt pour définir la limite (entier > 0).
  - Shift+clic gauche → désactiver la limite.
  - Shift+clic droit → prompt pour changer la durée de la fenêtre en heures.
- `Arena` : nouveaux champs `attemptLimit` (défaut 0) et `attemptWindowHours` (défaut 12) + getters/setters/`hasAttemptLimit()`.
- `ArenaManager` : load/save de `attempt-limit` et `attempt-window-hours` dans le fichier YAML de l'arène.
- Nouvelles clés de langue dans fr/en/de : `arena.attempt-blocked`, `admin.options.attempt-limit-*`.

## [1.1.2] - 2026-03-30

### Added

- **Système de chemins waypoint (Waypoint Path System)** : les arènes peuvent maintenant avoir plusieurs chemins de guidage nommés, chacun composé d'une liste ordonnée de waypoints. Chaque chemin peut être assigné à un ou plusieurs spawners de mobs.
  - Nouveau modèle `ArenaPath` : id, liste de waypoints, méthodes add/remove/clear.
  - `Arena` expose `addPath()`, `removePath()`, `getPath()`, `hasPaths()`, `getSpawnerPathAssignments()` et les méthodes d'assignation spawner→chemin.
  - `ArenaSetup` : nouveau mode de baguette `WAYPOINT` + champ `editingPathId`.
- **`WaypointGuideManager`** : nouveau gestionnaire global qui crée un ArmorStand guide invisible, invulnérable, sans gravité, pour chaque chemin actif dans chaque arène. L'ArmorStand se déplace de 0,3 bloc par tick vers le prochain waypoint et les mobs de la vague lui sont assignés via `Creature.setTarget()`. À l'arrivée au dernier waypoint, les mobs sont redirigés vers le villageois le plus proche.
- **`WaveManager`** : après le spawn d'un mob, `registerMob()` est appelé sur le `WaypointGuideManager` ; le retarget automatique vers les villageois est ignoré si le mob est déjà guidé par un chemin.
- **`ArenaPathsGUI`** : GUI 54 slots listant tous les chemins d'une arène. Clic gauche = éditer, clic droit = supprimer. Ajout de chemin via prompt chat.
- **`ArenaPathEditGUI`** : GUI 54 slots pour gérer un chemin (ajouter/retirer des waypoints via la baguette, visualiser les waypoints en particules FLAME + REDSTONE, ouvrir le GUI d'assignation des spawners, effacer tous les waypoints).
- **`ArenaPathSpawnersGUI`** : GUI 54 slots affichant tous les spawners de l'arène avec code couleur : vert = assigné à ce chemin, orange = assigné à un autre chemin, gris = libre. Clic pour basculer l'assignation.
- **Visualisation des chemins** : bouton "Visualiser" dans `ArenaPathEditGUI` fait apparaître des particules FLAME à chaque waypoint et des particules REDSTONE entre eux pendant 5 secondes.
- **`ArenaSetupGUI`** : ajout du bouton "Chemins" (slot 30) donnant accès à `ArenaPathsGUI`.
- **`ArenaManager`** : `loadFromFile()` et `saveArena()` persistent les chemins et les assignations spawner→chemin dans le fichier de configuration de chaque arène.
- **Clés de langue** : ajout de la section complète `admin.paths:` (34 clés) et des titres de GUI `gui.arena-paths.title`, `gui.arena-path-edit.title`, `gui.arena-path-spawners.title` dans `fr.yml`, `en.yml` et `de.yml`.

### Fixed

- **Race condition réseau MySQL** : `HubBungeeSender.sendToArena()` et `sendToArenaSpectate()` envoyaient la commande `Connect` immédiatement après `writeHubPending()` en asynchrone. Si MySQL était lent, le joueur arrivait sur le serveur VDP avant que l'entrée soit committée, bloquant silencieusement le join. Corrigé via `writeHubPendingThenRun(UUID, String, String, Runnable)` qui exécute le `Connect` dans le callback post-écriture sur le thread principal.
- **Erreur de compilation `addObserver`** : `PlayerListener` appelait une méthode inexistante. Corrigé en `onSpectatorJoinArena(Player, Arena)`.
- **Join hub incomplet** : les branches de join réseau (Priority 1 MySQL et Priority 2 BungeeCord Forward) n'appelaient pas `prepareForLobby()`, `prepareForArenaEntry()`, `setReturnLocation()`, `setCurrentArenaId()` ni `checkAndStart()`. Les items de lobby et le décompte de démarrage n'étaient jamais déclenchés.
- **Lore server-id tronqué** : les deux lignes d'avertissement du server-id dans `NetworkConfigGUI` étaient passées comme une chaîne unique avec `\n` littéraux. Corrigé en ajoutant deux clés distinctes (`admin.network.server-id-warning-1/2`) passées séparément à `ItemBuilder.lore()`.
- **GUIs de chemins non routés** : les trois nouveaux GUIs n'étaient pas enregistrés dans le dispatch `onInventoryClick` ni dans `isCustomGuiTitle()`, rendant tous les clics inertes. Les trois entrées sont maintenant ajoutées.

### Changed

- **`NetworkConfigGUI`** : l'item Server ID affiche maintenant deux lignes d'avertissement explicites indiquant que la valeur doit correspondre exactement au nom déclaré dans `velocity.toml` ou `bungeecord.yml`.
- **Chemins waypoint déplacés dans `ArenaGameplayLocationsGUI`** : le bouton Chemins n'est plus dans le GUI principal de setup mais dans le sous-GUI Gameplay (à côté de Tours), avec une note de lore indiquant qu'il est optionnel. Le status item du GUI principal le liste dans la section "Optionnel".
- **`ArenaOptionsGUI`** agrandi à 36 slots pour accueillir les deux items leaderboard côte à côte.
- **`ArenaPositionsGUI`** agrandi à 36 slots pour accueillir le bouton Mode de limite.
- **`ArenaGameplayLocationsGUI`** agrandi à 36 slots pour accueillir le bouton Chemins.

### Added (suite)

- **Mode de limite d'arène (`ArenaBoundaryMode`)** : nouvelle option dans `ArenaPositionsGUI` (slot 16, à côté du coffre shop) permettant de choisir le comportement quand un joueur tente de sortir des bornes pos1/pos2 pendant une partie.
  - `PUSH_BACK` (défaut) — annule le mouvement et envoie un message d'alerte, adapté aux cartes classiques avec murs.
  - `TP_CENTER` — téléporte le joueur au centre du village et envoie un message, adapté aux cartes avec vide.
  - Persisté dans le fichier YAML de l'arène sous la clé `boundary-mode`.
  - Clés de langue `admin.setup.boundary-mode`, `boundary-push-back/tp-center` et `arena.boundary-tp` ajoutées dans fr/en/de.

- **Leaderboard par arène (floating text)** : en plus du leaderboard global existant, chaque arène peut maintenant avoir un second hologramme affichant le classement propre à cette arène.
  - Nouvelle table MySQL/H2 `vd_arena_stats (uuid, arena_id, kills, waves, games)` créée automatiquement au démarrage.
  - Tracking par arène : kills, vagues complétées et parties jouées sont désormais enregistrés par arène en plus des compteurs globaux (`recordArenaKill`, `recordArenaWave`, `recordArenaGame`).
  - Nouvelle méthode `getTopPlayersByArena(limit, orderBy, arenaId)` sur `Database`, implémentée dans `MySQLDatabase` et `SQLiteDatabase`.
  - `LeaderboardDisplayManager` gère deux clés de display par arène : `arenaId_global` (top joueurs global) et `arenaId_arena` (top joueurs de cette arène). Les titres d'hologramme diffèrent.
  - `ArenaOptionsGUI` expose deux items séparés : **Classement Global** (armor stand, slot 15) et **Classement Arène** (player head, slot 16). Clic = placer au-dessus de sa position, Shift+Clic = retirer.
  - Nouveau champ `arenaLeaderboardLocation` dans `Arena`, persisté sous `arena-leaderboard-location` dans le YAML.
  - Clés de langue `gui.leaderboard.hologram-title-arena`, `admin.setup.leaderboard-global-lore`, `admin.setup.leaderboard-arena-item/lore` et `leaderboard-cleared` ajoutées dans fr/en/de.

- **Système de récompenses de fin de partie (`ArenaRewardsGUI`)** : nouveau sous-GUI accessible depuis `ArenaSetupGUI` (slot 29) pour configurer les récompenses distribuées à chaque joueur en fin de partie.
  - **Récompenses Vault** : activer/désactiver le dépôt d'économie Vault, configurer le montant. Shift+Clic pour saisir le montant. Indisponible si Vault n'est pas présent.
  - **Commandes console** : ajouter jusqu'à 9 commandes exécutées en console pour chaque joueur, avec le placeholder `{player}` remplacé par le nom du joueur. Clic pour supprimer une commande individuelle, Shift+Clic pour tout effacer.
  - Récompenses appliquées dans `GameManager.endGame()` pour tous les joueurs présents, victoire ou défaite.
  - Persisté dans le YAML de l'arène : `reward-vault-enabled`, `reward-vault-amount`, `reward-commands`.
  - Bouton `ArenaSetupGUI` affiche DIAMOND si des récompenses sont configurées, EMERALD sinon.
  - Clés de langue `admin.rewards.*` et `gui.arena-rewards.title` ajoutées dans fr/en/de.

- **Limite de vague** : nouvelle option dans `ArenaOptionsGUI` (slot 20) permettant de définir une vague maximale au-delà de laquelle la partie se termine en victoire.
  - Clic = prompt chat pour saisir un entier > 0 ; Shift+Clic = désactiver (vagues infinies).
  - Quand la limite est atteinte, le message `arena.wave-limit-victory` est diffusé à la place du message de victoire standard.
  - Affiché avec CLOCK si actif, COMPASS si désactivé.
  - Listé dans la section optionnelle de l'item d'état du `ArenaSetupGUI`.
  - Persisté dans le YAML de l'arène sous la clé `wave-limit`.
  - Clés de langue `admin.options.wave-limit-*`, `admin.setup.status-wave-limit` et `arena.wave-limit-victory` ajoutées dans fr/en/de.

## [1.1.1] - 2026-03-30

### Added

- **Support réseau multi-serveurs** : le plugin exporte maintenant l'état de toutes ses arènes dans la table MySQL partagée `vd_network_arenas` (server_id, state, player_count, etc.) pour que le plugin `VillageDefenseHub` puisse découvrir et afficher les arènes disponibles sur le réseau.
- **Canal Hub entrant** `villagedefense:hub` (BungeeCord `Forward`) : le Hub peut envoyer un message `JOIN_ARENA` avant de transférer un joueur ; celui-ci est mis en file d'attente et rejoint l'arène automatiquement à sa connexion.
- **Persistence des tokens de rejoin** : les tokens de rejoin sont maintenant écrits dans la table MySQL `vd_player_rejoin` quand la sync réseau est active, pour que le Hub puisse notifier un joueur revenu sur le Hub qu'il peut reprendre sa partie en cours.
- **Nouvelle section `network:` dans `config.yml`** avec les clés `enabled`, `server-id` et `sync-interval`.
- **`NetworkConfigGUI`** : nouveau sous-GUI dans Paramètres pour configurer la sync réseau directement en jeu (toggle, server-id, intervalle, sauvegarde + redémarrage instantané).
- **`SettingsGUI`** : nouveau GUI "Paramètres" regroupant Base de données, Langue, Proxy (BungeeCord) et Sync Réseau en un seul endroit.
- **Refonte du GUI de setup arène** : l'ancien GUI monolithique est découpé en 3 sous-GUIs dédiés accessibles depuis la vue principale.
  - `ArenaPositionsGUI` — lobby spawn, game spawn, centre village, pos1/pos2, coffre shop.
  - `ArenaGameplayLocationsGUI` — spawns mobs, spawns villageois, marchand shop, marchand enchanteur, tours, pièges.
  - `ArenaOptionsGUI` — prix du shop, prix des enchantements, configuration des tours, schématiques, panneau, leaderboard.
- **Système de succès dynamique** : les succès ne sont plus des valeurs d'enum Java figées. Ils sont maintenant définis dans `achievements.yml` (fichier généré au démarrage avec 9 succès par défaut) et entièrement configurables sans recompilation.
  - Succès par défaut inclus : `FIRST_BLOOD`, `ZOMBIE_SLAYER`, `ZOMBIE_LEGEND`, `SURVIVOR_I/II/III`, `VETERAN`, `WEALTHY`, `MILLIONNAIRE`.
- **`AchievementAdminGUI`** : nouveau GUI d'administration (54 slots) listant tous les succès. Permet d'en créer, modifier et supprimer directement en jeu.
- **`AchievementEditGUI`** : sous-GUI (27 slots) pour modifier chaque champ d'un succès (nom, description, icône, critère, cible) via des prompts de saisie en chat.
- Accès aux succès depuis le GUI admin principal (slot 48, étoile du Nether).

### Changed

- **Rangée basse du GUI admin simplifiée** : 6 boutons réduits à 3 — Schématiques (col 2), Niveaux des Kits (col 5), Paramètres (col 8). Database, Langue, BungeeCord et Sync Réseau sont désormais regroupés dans `SettingsGUI`.
- L'item BungeeCord dans `SettingsGUI` est renommé **Proxy** pour plus de clarté.
- Schématiques : lore mis à jour pour préciser que les schématiques sont partagés entre toutes les arènes.
- `FEATURES.txt` et `Wiki.md` réécrits pour documenter la surface complète du plugin.
- Les succès débloqués d'un joueur sont maintenant stockés sous forme d'IDs texte (CSV) en base de données au lieu de valeurs d'enum, ce qui rend le système extensible sans migration.

### Fixed

- Les atouts de session n'étaient pas toujours purgés quand un joueur quittait une partie puis entrait dans une autre, ce qui pouvait laisser des compagnons et états d'atouts résiduels entre deux games.
- La clé de message `admin.shop-chest-border-blocked` était mal référencée dans `PlayerListener` (chemin incorrect `admin.shop-chest-border-blocked` au lieu de `admin.setup.shop-chest-border-blocked`), ce qui affichait un message vide lors du blocage des slots bordure du coffre shop.
- Les nouveaux GUIs (`ArenaPositionsGUI`, `ArenaGameplayLocationsGUI`, `ArenaOptionsGUI`, `AchievementAdminGUI`, `AchievementEditGUI`) n'étaient pas enregistrés dans `isCustomGuiTitle()` du `PlayerListener`, ce qui permettait des drags non désirés dans ces inventaires.

## [1.1.0] - 2026-03-27

### Added

- Workflow de changelog formel mis en place pour preparer les futures notes de mise a jour.
- La capacite du `Medic` cree maintenant une zone de soin ciblee au clic avec cercle de particules vert persistant pendant 15 secondes.
- Un nouvel acces dans le GUI admin permet maintenant de configurer le level requis de chaque kit.
- Un nouveau sous-GUI d'administration permet maintenant de configurer `BungeeCord` sans editer `config.yml` a la main.
- Une API publique Bukkit est maintenant exposee pour les plugins de battle pass, quetes et missions via `VillageDefenseApi` et `VillageDefenseProgressEvent`.
- Le setup d'arene propose maintenant un vrai mode de disponibilite `Active / Test / Desactivee`.
- La permission `villagedefense.test` permet de reserver les arenes en mode test aux joueurs autorises.
- Un systeme de leaderboard flottant global peut maintenant etre place en option dans chaque arene via le setup GUI.
- Un nouveau `FEATURES.txt` documente maintenant la surface publique a communiquer: API Bukkit, snapshots, event de progression et support PlaceholderAPI.
- Une vraie expansion PlaceholderAPI `%villagedefense_*%` est maintenant exposee avec des placeholders joueurs, arene courante, arene ciblee par ID et compteurs globaux.

### Changed

- `Healing Pulse` du `Medic` ne soigne plus instantanement autour du joueur : elle place desormais un champ de soin visible a l'endroit clique.
- Les descriptions de kits dans la GUI utilisent maintenant un lore multi-lignes plus lisible avec une ligne par capacite et du formatage de chat.
- Les niveaux requis des kits ne sont plus figes dans l'enum et sont maintenant lus depuis la configuration du plugin.
- Le menu principal d'administration expose maintenant un acces direct a la configuration proxy `BungeeCord`.
- Les integrations externes n'ont plus besoin de lire les classes internes du plugin : snapshots, metadonnees de succes et progression runtime passent maintenant par une surface API stable.
- Les panneaux et la liste `/vd list` refletent maintenant le statut d'accessibilite des arenes, avec masquage des arenes desactivees et exposition des arenes de test aux seuls testeurs.
- Les arenes peuvent maintenant afficher un hologramme de classement global alimente par la base de donnees, avec rotation automatique entre kills, vagues et parties.
- `FEATURES.txt` et `PUBLIC_API.md` documentent maintenant explicitement la syntaxe PlaceholderAPI publique a communiquer aux integrateurs et packagers.

### Fixed

- Correction du `TowerConfigGUI` : les tours de la rangee basse (`Ice`, `Lightning`, `Cannon`) utilisent maintenant un clic robuste base sur le `rawSlot` du GUI, sans collision avec l'inventaire joueur.
- Blocage du GUI vanilla de commerce pour les villageois proteges du plugin, avec garde-fou supplementaire sur les inventaires `MERCHANT`.
- Les powerups au sol sont maintenant nettoyes apres `45 secondes` au lieu de rester plusieurs minutes.
- Le power-up `Purge` elimine maintenant tous les mobs actifs de l'arene sauf le boss en cours, au lieu de ne toucher que les zombies.
- Les vagues suivent maintenant le nombre total de mobs prevus, deja spawnes et deja resolus, ce qui empeche `Purge` ou une mort precoce de cloturer une vague tant que d'autres mobs restent a spawn.
- Les notifications de niveau, kits, atouts et succes debloques utilisent maintenant `Messages.send(...)` au lieu de sorties directes, pour rester coherentes avec le systeme de messages du plugin.
- Le mode spectateur n'utilise plus le `GameMode.SPECTATOR` vanilla : l'item de sortie fonctionne de nouveau, les observateurs sont invisibles avec le fly, ne peuvent plus interagir avec l'arene et restent confines dans ses limites.
- Le passage en mode observateur apres une mort de joueur suit maintenant le vrai cycle de respawn Bukkit : inventaire de kit vide, lit rouge de sortie, invisibilite, fly et masquage aux autres joueurs sont de nouveau appliques correctement pendant le timer de respawn.

## [1.0.1] - 2026-03-27

### Added

- Nouveau systeme de kits par roles avec `Guardian`, `Ranger`, `Berserker`, `Medic` et `Engineer`.
- Capacites actives et passives dediees pour chaque kit.
- Gestionnaire central `KitAbilityManager` pour les effets de combat et les capacites.
- Banner de demarrage detaille avec statut des hooks.
- Couche de compatibilite multi-version `1.17 -> 1.21.x`.
- Integrations fonctionnelles pour `Vault`, `PlaceholderAPI`, `ProtocolLib` et `BungeeCord`.
- Synchronisation runtime des descriptions de commandes et permissions depuis les fichiers de langue.
- `DEVELOPMENT_LOG.md` comme memoire persistante du projet.

### Changed

- Migration du namespace principal vers `fr.obsidianlabs.plugins.villagedefense`.
- Migration du roster de kits legacy vers le nouveau roster avec conversion automatique des anciennes donnees sauvegardees.
- Refonte des loadouts et de l'equilibrage des kits.
- Externalisation systematique des textes visibles vers `lang/fr.yml`, `lang/en.yml` et `lang/de.yml`.
- Adaptation du plugin pour rester compile et exploitable sur l'API commune Spigot/Paper.
- Baseline de compilation redescendue a Java 17 / Paper 1.17.1 pour viser la compatibilite multi-version.
- Les hologrammes de slots/tours/pieges utilisent maintenant des armor stands compatibles 1.17.
- Le backend local conserve la clef config `SQLITE` mais utilise H2 en pratique.
- Les bonus Engineer sur la "durabilite" ont ete adaptes au gameplay actuel sous forme de structures renforcees, overclock et refresh des pieges/tours.

### Fixed

- Suppression de l'ancien arbre source duplique `fr.maxbouard.villagedefense`.
- Correction de la persistance des `coins` joueur.
- Correction de la persistance des `achievements` en MySQL.
- Correction de la migration MySQL en evitant `ADD COLUMN IF NOT EXISTS` sur les serveurs qui refusent cette syntaxe.
- Alignement de la validation des arenes entre backend, GUI et runtime.
- Suppression des derniers textes hardcodes encore actifs dans les parcours runtime principaux.
- Correction de l'application des kits pour conserver la vraie vie max du kit.
- Remplacement de l'ancienne capacite Mage hardcodee par un systeme generique de capacites de kit.
