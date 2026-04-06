# VillageDefensePro - Fonctionnalites

Version documentee : `1.3`  
Plugin : `VillageDefensePro`  
Classe principale : `fr.obsidianlabs.plugins.villagedefense.VillageDefensePlugin`  
Compatibilite ciblee : `Paper / Spigot 1.17 -> 1.21.x`  
Java requis : `17`  
Langues bundlees : `fr`, `en`, `de`

---

## 1. Vue d'ensemble

VillageDefensePro est un plugin PvE cooperatif multi-arenes centre sur la defense d'un village contre des vagues de monstres.

Le coeur du gameplay repose sur :

- des arenes configurees independamment
- une boucle de jeu en vagues
- des kits a roles
- un systeme de tours et de pieges
- une economie en partie
- une progression joueur persistante
- une administration complete via GUI
- une API publique pour integrations externes

---

## 2. Boucle de jeu

Une partie complete comprend :

- un lobby d'attente
- un compte a rebours de depart
- des vagues normales, speciales et boss
- des inter-vagues pour acheter et se preparer
- des bonus et power-ups
- une victoire, une defaite, ou une fin sur limite de vague
- un reset complet de l'arene

Systemes runtime inclus :

- scoreboard lobby + partie
- boss bar pour les vagues boss
- annonces de vagues et de phases
- respawn avec timer
- mode spectateur custom
- rejoin apres deconnexion
- nettoyage et reset de la carte en fin de partie

Suivi avance des vagues :

- suivi des mobs prevus
- suivi des mobs deja spawnes
- suivi des mobs resolus
- prevention des fins de vague prematurees
- compatibilite avec `Purge` sans casser la logique de vague

---

## 3. Etats d'arene

Chaque arene possede :

- un etat runtime : `waiting`, `starting`, `running`, `between_waves`, `ending`, `resetting`
- un mode d'accessibilite : `ACTIVE`, `TEST`, `DISABLED`

Details des modes :

- `ACTIVE` : visible et rejoignable normalement
- `TEST` : reservee aux joueurs ayant `villagedefense.test`
- `DISABLED` : masquee et non rejoignable

Le plugin gere aussi :

- multi-arenes
- liste d'arenes cote joueur
- panneaux de join / spectate
- icone d'arene cote admin

---

## 4. Systeme joueur

Progression persistante :

- pieces
- XP totale
- niveau
- kills totaux
- vagues completees
- parties jouees
- pieces totales gagnees
- succes debloques
- kits debloques

Progression de session :

- kills de partie
- pieces de partie
- kit selectionne
- arene actuelle
- statut spectateur

Flux joueur :

- rejoindre une arene
- rejoindre en spectateur si la partie est deja lancee
- quitter proprement une partie
- rejoin apres deco
- consulter ses stats
- ouvrir le classement
- ouvrir le shop
- ouvrir le menu de kit

Mode spectateur custom :

- pas de flux principal base sur `GameMode.SPECTATOR`
- joueur invisible
- fly actif
- interactions bloquees
- item dedie pour quitter

---

## 5. Kits

Roster actuel :

- `Guardian`
- `Ranger`
- `Berserker`
- `Medic`
- `Engineer`

Chaque kit dispose :

- d'un nom localise
- d'un lore propre
- d'un niveau requis configurable
- de capacites actives et passives

Roles principaux :

- `Guardian` : tank de premiere ligne
- `Ranger` : DPS a distance
- `Berserker` : melee agressif
- `Medic` : support et soin
- `Engineer` : support defensif et structures

Fonctionnalites liees aux kits :

- menu de selection
- debloquage par niveau
- migration automatique des anciens IDs sauvegardes
- configuration admin des niveaux requis via GUI

---

## 6. Tours

Systeme tower defense complet :

- emplacements de tours par arene
- achat de tours
- proprietaire de tour
- niveaux `1 -> 3`
- vente avec remboursement partiel
- remplacement d'une tour par une autre
- activation / desactivation par arene
- cout configurable par arene
- niveau requis configurable par arene
- affichage flottant de l'etat du slot / de la tour
- specialisation au niveau maximum

Types de tours :

- `Archer`
- `Trebuchet`
- `Mage`
- `Ice`
- `Lightning`
- `Cannon`

Specialisations disponibles :

- `Archer` : `Multi Shot`, `Incendiary Arrow`
- `Trebuchet` : `Incendiary TNT`, `Cluster`
- `Mage` : `Fire`, `Poison`
- `Ice` : `Blizzard`, `Frost Nova`
- `Lightning` : `Overload`, `Stun`
- `Cannon` : `Explosive Shot`, `Grapeshot`

Schematics de tours :

- capture globale ou locale par arene
- categories separees
- apercu et recapture in-game
- override par arene

---

## 7. Pieges

Systeme de pieges :

- emplacements de pieges par arene
- achat et pose via GUI
- cooldown visuel
- retrait du piege
- support des hologrammes / labels

Types de pieges :

- `Slow Trap`
- `Damage Mine`
- `Net Trap`

---

## 8. Shop, achats et enchantements

Shop :

- GUI de shop en partie
- fallback interne
- support d'un shop pilote par coffre
- prix configurables
- acces aux perks depuis le shop

Contenu par defaut :

- nourriture
- pomme doree
- potions utilitaires
- ameliorations d'armes
- ameliorations d'armure
- TNT
- bundles de toiles d'araignee

Mode coffre :

- configure par arene
- utilise un coffre double
- bordures reservees
- interieur achetable

Enchantements :

- GUI personnalise
- prix de base configurables par arene
- formule de cout progressive
- villageois enchanteur dedie

Options exposees :

- `Sharpness`
- `Fire Aspect`
- `Unbreaking`
- `Power`
- `Flame`
- `Infinity`
- `Protection`
- `Feather Falling`
- `Mending`

---

## 9. Perks et power-ups

Perks de partie actuellement exposes :

- `Vitalite`
- `Force`
- `Companion Wolf`
- `Companion Golem`
- `Wave Skip`
- `Buy Villager`
- `Village Guard`

Capacites couvertes :

- bonus de stats
- invocation de compagnons
- ajout d'habitants supplementaires
- saut de vague
- invocation de gardiens

Power-ups actuellement exposes :

- `Resistance`
- `Speed`
- `Strength`
- `Regeneration`
- `Zombie Clear / Purge`
- `Instant Kill`
- `Double Damage`

Comportements notables :

- annonce chat lors du ramassage
- effet global ou temporaire selon le type
- `Purge` elimine les mobs actifs hors boss en cours
- nettoyage automatique des drops de power-up apres `45 secondes`

---

## 10. Systeme de gameplay avance

Le plugin embarque un systeme de vagues avance configure par arene, via le menu de setup sous le nom :

- `Customisation Vague`

Le `GameplayMainGUI` expose directement :

- `Difficulte`
- `Scaling des vagues`
- `Categories de vagues`
- `Composition`
- `Overrides de vague`

Fonctionnalites du systeme avance :

- activation par defaut
- difficulte par profils
- scaling lineaire / exponentiel
- categories `NORMAL`, `ELITE`, `BOSS`, `SPECIAL`
- composition de vagues par pools de mobs
- overrides sur des vagues precises
- templates par categorie
- sauvegarde dediee par arene

Le systeme supporte :

- mobs vanilla
- integration MythicMobs
- fournisseurs de mobs extensibles
- construction de vague via services dedies

---

## 11. Waypoints et chemins

Systeme de waypoints complet :

- plusieurs chemins par arene
- waypoints ordonnes
- assignation des spawners a un chemin
- visualisation par particules
- guidage des mobs via entite guide invisible

GUI associes :

- `ArenaPathsGUI`
- `ArenaPathEditGUI`
- `ArenaPathSpawnersGUI`

Comportements notables :

- plusieurs chemins nommes par arene
- chaque spawner peut etre assigne a un chemin
- les mobs suivent le chemin puis se redirigent vers le village

---

## 12. Achievements et classements

Achievements :

- systeme dynamique configure via fichier
- creation, edition et suppression via GUI admin
- icone, nom, description, condition et cible configurables

Succes par defaut inclus :

- `first-blood`
- `zombie-slayer`
- `zombie-legend`
- `survivor-i`
- `survivor-ii`
- `survivor-iii`
- `veteran`
- `wealthy`
- `millionaire`

Classements :

- GUI top global
- categories `kills`, `waves`, `games`
- affichage flottant global optionnel
- affichage flottant par arene optionnel
- rotation automatique des categories sur hologrammes

Important :

- le classement global est persiste
- les stats specifiques par arene sont reservees au backend MySQL
- en backend local H2, ces stats d'arene sont ignorees proprement

---

## 13. Setup d'arene

Le setup comprend :

- creation / suppression
- mode setup in-game
- baguette de configuration
- GUI de setup principal
- sous-GUI specialises
- validation de prets / incomplets

Elements configurables par arene :

- ID et nom affiche
- min players
- max players
- lobby spawn
- game spawn
- centre du village
- pos1 / pos2
- panneau de join
- spawns mobs
- spawns villageois
- slots de tours
- slots de pieges
- coffre shop
- villageois marchand
- villageois enchanteur
- prix d'enchantement
- configuration des tours
- schematics locaux
- leaderboards flottants
- disponibilite `ACTIVE / TEST / DISABLED`
- icone admin
- mode de limite d'arene `PUSH_BACK / TP_CENTER`
- chemins de waypoints
- limite de vague
- limite de tentatives
- recompenses de fin de partie
- customisation avancee des vagues

Validation :

- distinction requis / optionnel dans le GUI
- statut visible in-game
- blocage des arenes non pretes

Modes principaux de la baguette :

- `DEFAULT`
- `BOUNDARY`
- `CHEST`
- `SCHEMATIC`
- `REMOVE`
- `TRAP`
- `VILLAGER_SPAWN`
- `SIGN`
- `SHOP_VILLAGER`
- `ENCHANT_VILLAGER`
- `WAYPOINT`

---

## 14. Outils admin et GUIs

GUI admin principal :

- liste et creation d'arenes
- acces setup
- gestion des schematics
- configuration database
- changement de langue
- configuration des niveaux requis des kits
- configuration proxy
- configuration sync reseau
- gestion des achievements

Sous-GUI majeurs :

- `ArenaSetupGUI`
- `ArenaPositionsGUI`
- `ArenaGameplayLocationsGUI`
- `ArenaOptionsGUI`
- `ArenaRewardsGUI`
- `ArenaPathsGUI`
- `ArenaPathEditGUI`
- `ArenaPathSpawnersGUI`
- `GameplayMainGUI`
- `DifficultyGUI`
- `WaveScalingGUI`
- `WaveCategoriesGUI`
- `MobPoolsGUI`
- `WaveOverridesGUI`
- `AchievementAdminGUI`
- `AchievementEditGUI`
- `DatabaseGUI`
- `LanguageGUI`
- `KitConfigGUI`
- `BungeeConfigGUI`
- `NetworkConfigGUI`
- `SettingsGUI`
- `SchematicCategoryGUI`
- `SchematicGUI`
- `IconPickerGUI`
- `EnchantPricesGUI`
- `TowerConfigGUI`
- `ShopPricesGUI`

Configuration admin possible sans edition manuelle :

- langue
- backend local H2 / MySQL
- proxy BungeeCord
- sync reseau
- niveaux requis des kits
- prix du shop
- prix d'enchantement par arene
- cout / activation / niveau requis des tours
- mode de limite d'arene
- limite de vague
- limite de tentatives
- recompenses de fin de partie
- leaderboards flottants

---

## 15. Base de donnees et persistance

Backends supportes :

- `SQLITE` dans la config, qui utilise `H2` en local
- `MySQL`

Donnees persistantes principales :

- profil joueur
- niveau / XP
- pieces
- statistiques globales
- succes
- debloquages de kits
- tokens de rejoin
- tentatives par arene
- sync reseau MySQL

Tables principales :

- `vd_players`
- `vd_player_rejoin`
- `vd_hub_pending`
- `vd_network_arenas` `MySQL uniquement`
- `vd_arena_attempts`
- `vd_arena_stats` `MySQL pour le tracking par arene`

Infrastructure incluse :

- pooling `HikariCP`
- creation automatique des tables
- migrations de colonnes manquantes
- requetes leaderboard

---

## 16. Reseau, proxy et integrations

Soft depends declares :

- `OldCombatMechanics`
- `Vault`
- `PlaceholderAPI`
- `ProtocolLib`
- `MythicMobs`

Support runtime :

- `Vault` : economie optionnelle et recompenses Vault
- `PlaceholderAPI` : expansion `%villagedefense_*%`
- `ProtocolLib` : compatibilite affichages
- `OldCombatMechanics` : compatibilite combat legacy
- `MythicMobs` : mobs custom dans le systeme de vagues avance
- `BungeeCord` : retour proxy optionnel

Reseau multi-serveurs :

- synchronisation des arenes via `vd_network_arenas`
- pending joins depuis hub
- tokens de rejoin persistes en mode reseau
- configuration via `NetworkConfigGUI`

---

## 17. API publique

Service expose :

- `fr.obsidianlabs.plugins.villagedefense.api.VillageDefenseApi`

Recuperation :

- `Bukkit.getServicesManager().load(VillageDefenseApi.class)`

Capacites principales :

- snapshots joueur
- snapshots arene
- liste des arenes
- definitions de succes
- verification de presence en arene
- verification du statut spectateur
- attribution de coins
- attribution d'XP
- debloquage de succes

Event public expose :

- `VillageDefenseProgressEvent`

Types de progression exposes :

- `ARENA_JOIN`
- `ARENA_SPECTATE_JOIN`
- `ARENA_LEAVE`
- `GAME_START`
- `MOB_KILL`
- `WAVE_COMPLETE`
- `GAME_COMPLETE`
- `GAME_WIN`
- `GAME_LOSS`
- `XP_EARNED`
- `LEVEL_UP`
- `ACHIEVEMENT_UNLOCKED`
- `POWERUP_PICKUP`

---

## 18. PlaceholderAPI

Identifiant de l'expansion :

- `villagedefense`

Couverture :

- placeholders globaux
- placeholders joueur
- placeholders de l'arene courante
- placeholders d'une arene ciblee par ID

Exemples :

- `%villagedefense_plugin_version%`
- `%villagedefense_player_coins%`
- `%villagedefense_player_level%`
- `%villagedefense_arena_wave%`
- `%villagedefense_arenas_running%`

---

## 19. Commandes

Commande joueur principale :

- `/vd`

Sous-commandes joueur :

- `/vd join [arene]`
- `/vd rejoin`
- `/vd leave`
- `/vd kit`
- `/vd shop`
- `/vd stats`
- `/vd list`
- `/vd top`
- `/vd help`

Aliases joueur :

- `/villagedefense`
- `/vdef`

Commande admin principale :

- `/vdadmin`

Sous-commandes admin :

- `/vdadmin create <id>`
- `/vdadmin delete <id>`
- `/vdadmin setup <id>`
- `/vdadmin reload`
- `/vdadmin forcestart <id>`
- `/vdadmin forcestop <id>`
- `/vdadmin tp <id>`
- `/vdadmin info <id>`
- `/vdadmin coins <joueur> <montant>`
- `/vdadmin setwave <arene> <vague>`
- `/vdadmin setlevel <joueur> <niveau>`

Alias admin :

- `/vda`

---

## 20. Permissions

Permissions principales :

- `villagedefense.admin`
- `villagedefense.admin.arena`
- `villagedefense.admin.setup`
- `villagedefense.admin.schematic`
- `villagedefense.admin.lang`
- `villagedefense.admin.database`
- `villagedefense.admin.prices`
- `villagedefense.admin.reload`
- `villagedefense.admin.game`
- `villagedefense.admin.player`
- `villagedefense.play`
- `villagedefense.test`
- `villagedefense.bypass`

---

## 21. Documentation associee

Pour aller plus loin :

- voir `README.md` pour l'installation rapide
- voir `Wiki.md` pour la documentation complete
- voir `CHANGELOG.md` pour l'historique des versions
