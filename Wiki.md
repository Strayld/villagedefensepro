# VillageDefensePro - Wiki Officiel

Guide complet pour installer, configurer et exploiter tout le plugin.

## 1. Resume Rapide

`VillageDefensePro` est un plugin PvE multi-arenes ou les joueurs doivent proteger un village contre des vagues de monstres.

Le plugin inclut:

- systeme de vagues, bonus waves et boss waves
- tours, specialisations et pieges
- kits a roles
- shop, atouts et enchantements
- progression persistante joueur
- GUI admin complet
- base locale H2 ou MySQL
- API publique Bukkit
- expansion PlaceholderAPI native

Compatibilite runtime ciblee:

- Spigot / Paper `1.17.1 -> 1.21.x`
- Java `21`

## 2. Installation

1. Placez `VillageDefensePro-1.1.3.jar` dans le dossier `plugins/` de votre serveur.
2. Demarrez le serveur une premiere fois.
3. Le plugin va generer sa structure de travail.
4. Arretez ou rechargez ensuite le serveur si vous voulez ajuster la configuration avant usage.

Fichiers et dossiers importants:

- `plugins/VillageDefensePro/config.yml`
- `plugins/VillageDefensePro/lang/fr.yml`
- `plugins/VillageDefensePro/lang/en.yml`
- `plugins/VillageDefensePro/lang/de.yml`
- `plugins/VillageDefensePro/arenas/`
- `plugins/VillageDefensePro/schematics/`

Hooks optionnels supportes:

- `OldCombatMechanics`
- `Vault`
- `PlaceholderAPI`
- `ProtocolLib`
- `BungeeCord` via plugin messaging

Important:

- le plugin fonctionne sans `PlaceholderAPI`
- le plugin fonctionne sans `Vault`
- le plugin fonctionne sans `ProtocolLib`
- le plugin fonctionne sans proxy `BungeeCord`

## 3. Premiere Configuration

Le point d'entree admin est soit:

- `/vdadmin`
- ou son alias `/vda`

Le GUI admin principal permet de gerer:

- les arenes
- la langue
- la base de donnees
- les schematics
- les niveaux requis des kits
- la configuration `BungeeCord`

### 3.1 Langue

Depuis le GUI admin:

- ouvrez `Langue`
- choisissez `fr`, `en` ou `de`

### 3.2 Base de donnees

Depuis le GUI admin:

- ouvrez `Base de Donnees`
- choisissez le backend
- sauvegardez

Backends disponibles:

- `SQLITE` dans la config, qui utilise en pratique H2 en local
- `MYSQL`

Le backend local est le plus simple pour commencer.

### 3.3 BungeeCord

Depuis le GUI admin:

- ouvrez `BungeeCord`
- activez ou desactivez le hook
- renseignez le `return-server` si vous voulez renvoyer les joueurs vers un serveur proxy precis
- sauvegardez et appliquez

### 3.4 Niveaux requis des kits

Depuis le GUI admin:

- ouvrez `Niveaux des Kits`
- cliquez sur un kit pour changer son niveau requis
- utilisez `Shift+Clic` pour revenir a la valeur par defaut

## 4. Commandes Joueur

Commande principale:

- `/vd`

Sous-commandes:

- `/vd join [arene]`
- `/vd rejoin`
- `/vd leave`
- `/vd kit`
- `/vd shop`
- `/vd stats`
- `/vd list`
- `/vd top`
- `/vd help`

Aliases:

- `/villagedefense`
- `/vdef`

Usage pratique:

- `join`: rejoint une arene accessible
- `rejoin`: revient dans la partie apres deco si le delai le permet
- `leave`: quitte l'arene ou le mode spectateur
- `kit`: ouvre le menu des kits
- `shop`: ouvre le shop de partie
- `stats`: affiche les stats persistantes
- `list`: affiche les arenes visibles selon leur disponibilite
- `top`: ouvre le classement global

## 5. Commandes Admin

Commande principale:

- `/vdadmin`

Sous-commandes:

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

Comportement utile:

- `/vdadmin` sans argument ouvre le GUI admin si l'expediteur est un joueur
- `/vdadmin setup <id>` ouvre directement le setup GUI de l'arene et donne la baguette de config

## 6. Permissions

Permissions principales:

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

Permission cle pour les arenes en test:

- `villagedefense.test`

## 7. Demarrage Rapide D'Une Premiere Arene

Flux recommande:

1. Creez l'arene:

```text
/vdadmin create village_01
```

2. Ouvrez le setup:

```text
/vdadmin setup village_01
```

3. Configurez les positions et elements requis.
4. Sauvegardez.
5. Placez l'arene en `TEST`.
6. Testez avec un compte ayant `villagedefense.test`.
7. Passez l'arene en `ACTIVE` quand tout est pret.

## 8. Setup Complet D'Une Arene

Le setup d'arene se fait dans le `ArenaSetupGUI` et avec la baguette de configuration.

### 8.1 Champs a configurer en priorite

Pour exploiter pleinement le plugin, configurez au minimum:

- nom affiche
- joueurs minimum
- joueurs maximum
- lobby spawn
- game spawn
- centre du village
- pos1
- pos2
- spawns de mobs
- spawns de villageois
- emplacements de tours
- coffre shop
- villageois marchand
- villageois enchanteur
- disponibilite de l'arene

Champs optionnels mais fortement utiles:

- panneau de join
- emplacements de pieges
- schematics locaux
- leaderboard flottant global
- leaderboard flottant par arene
- icone admin
- prix d'enchantement personnalises
- configuration des tours par arene
- mode de limite d'arene (PUSH_BACK / TP_CENTER)
- chemins de waypoints
- limite de vague
- limite de tentatives par arene
- recompenses de fin de partie

### 8.2 Ce que fait chaque bouton du setup GUI

`ArenaSetupGUI` permet de regler:

- `Nom`: nom affiche de l'arene
- `Min` / `Max`: capacite de joueurs
- `Spawn lobby`: point d'attente avant partie
- `Spawn de partie`: spawn utilise en jeu et au respawn
- `Center Village`: coeur du village protege
- `Pos1` / `Pos2`: limites de l'arene
- `Panneau`: panneau de join / spectate
- `Spawns Mob`: points de spawn des monstres
- `Spawns villageois`: points de spawn des habitants
- `Tours`: emplacements de tours
- `Pieges`: emplacements de pieges
- `Schematics`: capture et edition des structures
- `Coffre Shop`: coffre double qui sert de source au shop
- `Marchand`: emplacement du villageois shop
- `Enchanteur`: emplacement du villageois enchantement
- `Prix ench.`: prix de base des enchantements pour cette arene
- `Config des tours`: activation, cout et niveau requis par tour
- `Classement flottant Global`: point d'ancrage optionnel (top joueurs toutes arenes)
- `Classement flottant Arene`: point d'ancrage optionnel (top joueurs de cette arene)
- `Mode de limite`: PUSH_BACK (repousse les joueurs) ou TP_CENTER (teleporte au centre du village)
- `Limite de vague`: arrete la partie en victoire a la vague N (optionnel)
- `Limite de tentatives`: N tentatives par fenetre de X heures par joueur (optionnel)
- `Recompenses`: commandes console et depot Vault en fin de partie (optionnel)
- `Chemins`: chemins de waypoints pour guider les mobs (optionnel)
- `Disponibilite`: `ACTIVE`, `TEST`, `DISABLED`
- `Sauvegarder`
- `Supprimer`
- `TP centre`
- `Icone`

### 8.3 Modes de la baguette

La baguette de setup fonctionne selon un mode.

Modes disponibles:

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

Actions exactes:

`DEFAULT`

- `Shift + Clic gauche`: ajouter un spawn de mob
- `Shift + Clic droit`: ajouter un emplacement de tour

`BOUNDARY`

- `Clic gauche`: definir `Pos1`
- `Clic droit`: definir `Pos2`

`CHEST`

- `Clic droit sur un coffre`: enregistrer le coffre shop

`SCHEMATIC`

- `Clic gauche`: definir le point 1
- `Clic droit`: definir le point 2
- `Clic droit a nouveau`: capturer le schematic
- `Shift + Clic gauche`: annuler l'apercu

`REMOVE`

- `Shift + Clic gauche`: supprimer un spawn de mob
- `Shift + Clic droit`: supprimer un emplacement de tour

`TRAP`

- `Shift + Clic droit`: ajouter un emplacement de piege
- `Shift + Clic gauche`: supprimer un emplacement de piege

`VILLAGER_SPAWN`

- `Shift + Clic droit`: ajouter un spawn de villageois
- `Shift + Clic gauche`: supprimer un spawn de villageois

`SIGN`

- `Clic droit sur un panneau`: enregistrer le panneau de l'arene

`SHOP_VILLAGER`

- `Shift + Clic droit`: definir la position du villageois marchand

`ENCHANT_VILLAGER`

- `Shift + Clic droit`: definir la position du villageois enchanteur

### 8.4 Ordre recommande pour un setup propre

Ordre conseille:

1. Definir `Nom`, `Min`, `Max`
2. Definir `Pos1` et `Pos2`
3. Definir `Lobby spawn`
4. Definir `Game spawn`
5. Definir le `Center Village`
6. Poser les `Spawns Mob`
7. Poser les `Spawns villageois`
8. Poser les `Slots de tours`
9. Poser les `Slots de pieges` si utilises
10. Definir le `Coffre Shop`
11. Definir le `Marchand`
12. Definir l'`Enchanteur`
13. Configurer les `Prix ench.`
14. Configurer les `Tours`
15. Placer le `Classement flottant` si besoin
16. Definir le `Panneau`
17. Choisir `TEST`
18. Sauvegarder

### 8.5 Validation avant publication

Avant de passer une arene en production, verifiez:

- la zone est bien delimitee
- les mobs spawnent dans la bonne zone
- les villageois apparaissent au bon endroit
- les tours sont posables
- le marchand ouvre bien le shop custom
- l'enchanteur ouvre bien le GUI d'enchantement custom
- le trade vanilla des villageois proteges ne s'ouvre pas
- le scoreboard s'affiche correctement
- le mode spectateur fonctionne apres mort
- la partie se reset correctement

## 9. Disponibilite D'Arene

Chaque arene possede un mode de disponibilite:

`ACTIVE`

- visible aux joueurs
- rejoignable normalement

`TEST`

- reservee aux testeurs
- exige `villagedefense.test` ou un acces admin superieur

`DISABLED`

- non affichee dans les flux joueur
- non rejoignable

Usage recommande:

- pendant le setup: `DISABLED`
- pendant les tests internes: `TEST`
- a la mise en ligne: `ACTIVE`

## 10. Mode De Limite D'Arene

Chaque arene peut etre configuree avec un mode de comportement quand un joueur tente de sortir des bornes de l'arene (pos1/pos2) pendant une partie.

Accessible depuis `ArenaPositionsGUI` (a cote du coffre shop) :

`PUSH_BACK` (defaut)

- le mouvement est annule
- le joueur recoit un message d'alerte
- adapte aux cartes classiques avec murs

`TP_CENTER`

- le joueur est teleporte au centre du village
- le joueur recoit un message de retour
- adapte aux cartes avec vide ou zones ouvertes

Clic sur le bouton pour alterner entre les deux modes.

## 10.1 Limite De Vague

Une arene peut avoir une limite de vague optionnelle : quand les joueurs atteignent la vague N, la partie se termine automatiquement en victoire.

Configuration depuis `ArenaOptionsGUI` :

- Clic = saisir un nombre de vagues
- Shift+Clic = desactiver (vagues infinies)

Quand la limite est atteinte, un message de victoire specifique est diffuse ("Felicitations, vous avez defendu le village avec bravoure !").

Sans limite de vague, la partie continue jusqu'a la defaite.

## 10.2 Recompenses De Fin De Partie

Les recompenses sont configurables depuis `ArenaSetupGUI` → bouton Recompenses.

Deux types disponibles :

**Depot Vault**

- activer/desactiver via clic
- definir le montant avec Shift+Clic et saisie du montant
- distribue automatiquement a chaque joueur en fin de partie
- necessite que Vault soit installe sur le serveur

**Commandes console**

- ajouter des commandes via le bouton Ajouter
- utiliser `{player}` pour inclure le nom du joueur dans la commande
- maximum 9 commandes
- cliquer sur une commande listee pour la supprimer
- Shift+Clic sur le bouton Effacer pour vider toutes les commandes

Les recompenses s'appliquent a tous les joueurs presents en fin de partie, victoire ou defaite.

## 10.3 Limite De Tentatives

Une arene peut limiter le nombre de tentatives par joueur sur une fenetre de temps glissante.

Configuration depuis `ArenaOptionsGUI` (slot 21) :

- Clic gauche = definir la limite (nombre entier > 0)
- Shift+Clic gauche = desactiver la limite (tentatives illimitees)
- Shift+Clic droit = changer la duree de la fenetre en heures

Par defaut : desactive (0). La valeur par defaut de la fenetre est 12 heures.

Comportement :

- les donnees sont chargees en memoire a la connexion du joueur et liberees a la deconnexion
- un joueur peut rejoindre le lobby et partir sans consommer de tentative
- l'incrément se produit uniquement au lancement effectif de la partie
- quand la fenetre expire, un nouveau cycle commence automatiquement
- si un joueur tente de rejoindre alors qu'il a epuise ses tentatives, il recoit un message indiquant le nombre de tentatives autorisees, la duree de la fenetre, et le temps restant avant reset

Les donnees sont persistees dans la table `vd_arena_attempts` (creee automatiquement).

Cette option est independante par arene : une limite sur `arena_01` n'affecte pas `arena_02`.

## 11. Systeme De Partie (cycle complet)

Une partie suit ce cycle:

- lobby d'attente
- countdown
- lancement
- vagues
- inter-vagues
- boss / bonus selon le rythme de jeu
- victoire ou defeat
- reset de l'arene

Le village:

- des habitants doivent etre proteges
- les mobs cherchent a les atteindre
- la perte des habitants mene au game over

Elements runtime notables:

- scoreboard dedie
- annonces de countdown
- boss bar pour les boss
- respawn avec timer
- mode spectateur custom pendant l'attente ou apres elimination

## 12. Spectateurs, Mort Et Rejoin

Quand un joueur meurt:

- il respawn proprement
- il passe en mode spectateur custom
- il devient invisible
- il peut fly
- il ne peut pas casser, poser ou interagir avec l'arene
- il recoit un item dedie pour quitter ce mode / quitter la partie

Quand un joueur rejoint une partie deja en cours et spectatable:

- il rejoint en spectateur

Quand un joueur se deconnecte:

- un jeton de rejoin est conserve temporairement
- `/vd rejoin` permet de revenir dans l'arene si le delai n'est pas expire

## 13. Kits

Kits disponibles:

- `Guardian`
- `Ranger`
- `Berserker`
- `Medic`
- `Engineer`

### 12.1 Guardian

Role:

- tank de premiere ligne

Capacites:

- `Mur de Bouclier`
- `Provocation`
- reduction passive des degats recus

### 12.2 Ranger

Role:

- DPS a distance

Capacites:

- `Tir Charge`
- `Fleche Perforante`
- bonus passif de precision / headshot

### 12.3 Berserker

Role:

- DPS melee agressif

Capacites:

- `Rage`
- `Vol de Vie`
- bonus de degats a basse vie

### 12.4 Medic

Role:

- support de survie

Capacites:

- `Impulsion de Soin`
- clique une position au sol
- cree un cercle de particules vert
- la zone reste 15 secondes
- soigne les allies dans la zone
- aura de regeneration de proximite
- acceleration du respawn des allies

### 12.5 Engineer

Role:

- support defensif

Capacites:

- reduction des couts de structures
- `Suroverclock`
- `Impulsion de Reparation`
- renforcement des structures proches

## 14. Tours

Types de tours:

- `Archer`
- `Trebuchet`
- `Mage`
- `Ice`
- `Lightning`
- `Cannon`

Chaque tour:

- se pose sur un slot dedie
- a un proprietaire
- a 3 niveaux
- peut etre vendue
- peut etre remplacee
- peut etre specialisee au niveau max

### 13.1 Specialisations

Archer:

- `Multi Shot`
- `Incendiary Arrow`

Trebuchet:

- `Incendiary TNT`
- `Cluster`

Mage:

- `Fire`
- `Poison`

Ice:

- `Blizzard`
- `Frost Nova`

Lightning:

- `Overload`
- `Stun`

Cannon:

- `Explosive Shot`
- `Grapeshot`

### 13.2 Configuration des tours par arene

Le `TowerConfigGUI` permet pour chaque arene:

- d'activer ou desactiver chaque tour
- de changer son cout
- de changer son niveau requis
- de revenir aux valeurs par defaut

## 15. Pieges

Types de pieges:

- `Slow Trap`
- `Damage Mine`
- `Net Trap`

Chaque piege:

- se pose sur un slot dedie
- dispose d'un affichage d'etat
- peut etre retire
- suit son propre cooldown

## 16. Shop, Atouts Et Enchantements

### 15.1 Shop

Le shop peut fonctionner de deux manieres:

- via le coffre de l'arene
- via le fallback shop interne

Le shop sert a acheter:

- nourriture
- soins
- potions
- equipement
- TNT
- toiles
- acces aux atouts

### 15.2 Atouts

Atouts actuellement integres:

- `Vitalite`
- `Force`
- `Companion Wolf`
- `Companion Golem`
- `Wave Skip`
- `Buy Villager`
- `Village Guard`

### 15.3 Enchantements

Le villageois enchanteur ouvre un GUI personnalise.

Options d'enchantement:

- `Sharpness`
- `Fire Aspect`
- `Unbreaking`
- `Power`
- `Flame`
- `Infinity`
- `Protection`
- `Feather Falling`
- `Mending`

Le `EnchantPricesGUI` permet de changer les prix de base de chaque option pour une arene.

## 17. Power-Ups

Power-ups exposes:

- `Resistance`
- `Speed`
- `Strength`
- `Regeneration`
- `Zombie Clear`
- `Instant Kill`
- `Double Damage`

Regles utiles:

- les power-ups apparaissent au sol
- ils restent 45 secondes maximum
- `Purge` elimine tous les mobs actifs sauf le boss
- les messages de ramassage sont integres au systeme de langue

## 18. Progression, XP, Niveaux Et Succes

Le plugin gere:

- XP totale
- niveau
- pieces
- kills
- vagues completes
- parties jouees
- pieces totales gagnees
- succes

Succes integres:

- `first-blood`
- `zombie-slayer`
- `zombie-legend`
- `survivor-i`
- `survivor-ii`
- `survivor-iii`
- `veteran`
- `wealthy`
- `millionaire`

Les messages de level up, kit debloque, atout debloque et succes debloque passent par le systeme de messages.

## 19. Leaderboards

Leaderboards disponibles:

- top kills
- top waves
- top games

Modes d'affichage:

- GUI `/vd top`
- hologramme flottant optionnel par arene

Le classement flottant global:

- se configure dans ArenaOptionsGUI (slot 15 — armor stand)
- affiche le top joueurs toutes arenes confondues
- alterne automatiquement entre kills, vagues et parties

Le classement flottant par arene:

- se configure dans ArenaOptionsGUI (slot 16 — tete de joueur)
- affiche le top joueurs de cette arene uniquement
- alterne automatiquement entre kills, vagues et parties
- necessite la table vd_arena_stats (creee automatiquement au demarrage)

## 20. Schematics

Le plugin supporte les schematics pour:

- les tours
- les marqueurs de slot de tour
- les points d'entree monstres

Les schematics peuvent etre:

- globaux
- locaux a une arene

Categories disponibles:

- `ARCHER`
- `TREBUCHET`
- `MAGE`
- `ICE`
- `LIGHTNING`
- `CANNON`
- `SPAWN_MONSTER`
- `TOWER_SLOT`

Usage:

- ouvrez `Schematics` depuis le GUI admin ou le setup GUI
- choisissez la categorie
- lancez la capture
- utilisez la baguette en mode `SCHEMATIC`

## 21. Langues Et Messages

Les messages visibles sont externalises dans:

- `lang/fr.yml`
- `lang/en.yml`
- `lang/de.yml`

Le plugin utilise ce systeme pour:

- chat
- lore
- GUI
- scoreboard
- signs
- progression
- admin

Le changement de langue se fait depuis le `LanguageGUI`.

## 22. Base De Donnees

Le plugin supporte:

- H2 local via la valeur de config `SQLITE`
- MySQL

Le `DatabaseGUI` permet:

- de choisir le backend
- de definir host, port, base, user, mot de passe
- de sauvegarder et recharger

Conseil:

- utilisez H2 pour un serveur simple
- utilisez MySQL pour un reseau ou une persistence centralisee

## 23. PlaceholderAPI

Si `PlaceholderAPI` est installe:

- l'expansion native `%villagedefense_*%` est enregistree

Si `PlaceholderAPI` n'est pas installe:

- le plugin continue de fonctionner normalement

Exemples:

- `%villagedefense_player_coins%`
- `%villagedefense_player_level%`
- `%villagedefense_arena_wave%`
- `%villagedefense_arena_village_01_state%`
- `%villagedefense_arenas_running%`

La liste complete est documentee dans `FEATURES.txt`.

## 24. API Publique

Le plugin expose une API Bukkit publique pour les plugins externes.

Point d'entree:

- `fr.obsidianlabs.plugins.villagedefense.api.VillageDefenseApi`

Recuperation:

```java
VillageDefenseApi api = Bukkit.getServicesManager().load(VillageDefenseApi.class);
```

Capacites principales:

- lire les snapshots joueurs
- lire les snapshots d'arenes
- lire les definitions de succes
- savoir si un joueur est en arene ou en spectateur
- attribuer des pieces
- attribuer de l'XP
- debloquer un succes

Evenement public:

- `VillageDefenseProgressEvent`

Usages cibles:

- battle pass
- quetes
- missions
- succes externes

## 25. Workflow Recommande Pour Mettre Une Arene En Ligne

Workflow complet conseille:

1. Installez le plugin.
2. Configurez la langue.
3. Choisissez la base de donnees.
4. Creez l'arene.
5. Faites tout le setup.
6. Placez l'arene en `TEST`.
7. Donnez `villagedefense.test` aux testeurs.
8. Faites plusieurs parties de validation.
9. Verifiez le respawn, spectateur, shop, tours, pieges, vague boss, fin de partie.
10. Passez l'arene en `ACTIVE`.
11. Placez un panneau et, si voulu, un classement flottant.

## 26. Verification Avant Mise En Production

Checklist finale:

- la base de donnees fonctionne
- les joueurs chargent et sauvegardent correctement
- les arenes visibles sont bien celles attendues
- les arenes `TEST` sont reservees aux bons joueurs
- le marchand et l'enchanteur ouvrent les bons GUI
- le trade vanilla des villageois proteges ne s'ouvre pas
- les tours Ice / Lightning / Cannon sont bien configurables
- les power-ups disparaissent bien apres 45 secondes
- `Purge` ne termine pas une vague trop tot
- le spectateur fonctionne apres mort
- `/vd rejoin` fonctionne
- les placeholders, si utilises, sont resolus correctement

## 27. Fichiers Utiles

Documentation:

- `FEATURES.txt`
- `PUBLIC_API.md`
- `CHANGELOG.md`
- `DEVELOPMENT_LOG.md`

Configuration:

- `config.yml`
- `lang/*.yml`
- `arenas/*.yml`

## 28. Support D'Exploitation

Si une arene n'apparait pas pour les joueurs, verifiez:

- son mode de disponibilite
- ses permissions
- son statut de setup

Si les placeholders ne fonctionnent pas, verifiez:

- que `PlaceholderAPI` est installe
- que l'expansion `%villagedefense_*%` est bien chargee au demarrage

Si une arene ne sauvegarde pas, verifiez:

- la base choisie
- les acces MySQL si vous etes en distant
- les logs de demarrage du plugin
