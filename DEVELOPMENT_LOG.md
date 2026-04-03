# DEVELOPMENT_LOG

This file is the persistent development memory for the plugin.
It must be updated after every code change.

## Release Notes Workflow

- `CHANGELOG.md` is the release-notes source for published updates
- The `Unreleased` section must be checked and surfaced before starting the next development task
- After each completed task, both `CHANGELOG.md` and `DEVELOPMENT_LOG.md` must be updated

## Rules Source Of Truth

- External rules file: `/Users/maximebouard/Library/Mobile Documents/com~apple~CloudDocs/Pro/Obsidian Labs/Plugins Server/obsidian_labs_plugin_rules.md`
- Current active plugin source: `fr.obsidianlabs.plugins.villagedefense`

## Project Identity

- Plugin: `VillageDefensePro`
- Main class: `fr.obsidianlabs.plugins.villagedefense.VillageDefensePlugin`
- Build tool: Maven
- Target API: Paper `1.17.1-R0.1-SNAPSHOT`
- Java: 17
- Default language: French
- Current local database backend: H2 via config type `SQLITE`
- Multi-version runtime target: `1.17 -> 1.21.x`

## Architecture Overview

- `achievement`: achievement definitions and conditions
- `arena`: arena model, setup, persistence, map snapshot/restore
- `command`: `/vd` and `/vdadmin`
- `config`: message loading and language overlay
- `database`: H2/MySQL persistence, leaderboard queries, network arena sync and rejoin token persistence
- `game`: game lifecycle, scoreboard, respawn, spectators, rejoin, villagers, hub join queue
- `gui`: all inventory GUIs separated by responsibility
- `kit`: kit definitions, concrete loadouts and runtime ability manager
- `leaderboard`: global floating leaderboard displays backed by database stats
- `listener`: gameplay, inventory, sign and player event listeners
- `network`: arena state export to shared MySQL table (`NetworkArenaEntry`, `NetworkSyncManager`)
- `perk`: purchasable session perks
- `player`: player state, session state, async loading/saving
- `powerup`: random drops and arena-wide temporary effects
- `shop`: fallback shop plus chest-based shop reader
- `tower`: towers, specializations, schematics, tower management
- `trap`: trap slots, trap runtime, trap GUI
- `util`: small helpers
- `wave`: wave generation and mob spawning

## Features Implemented

- Multi-arena PvE defense gameplay
- Arena setup GUI and setup wand
- Wave system with normal, special and boss waves
- Village villagers, lives, guards and respawn handling
- Spectator mode and rejoin window
- Tower defense system with upgrades and specializations
- Trap system with cooldown displays
- Shop GUI, chest-driven shop layout and custom enchant GUI
- Optional floating global leaderboard anchor per arena, rendered as multi-line hologram
- Player level, XP, kits, perks, achievements and leaderboard
- New role-based kit system:
  - `Guardian`
  - `Ranger`
  - `Berserker`
  - `Medic`
  - `Engineer`
- Runtime kit passives and actives:
  - Guardian shield wall, taunt and passive damage reduction
  - Ranger charged shot, piercing arrow and headshot bonus
  - Berserker rage, lifesteal and low-health damage bonus
  - Medic targeted healing field with green particle circle, nearby regeneration aura and faster team respawns
  - Engineer tower/trap/upgrade discount, reinforced structures, overclock and repair pulse
- Local H2 backend and MySQL backend
- Schematic capture, preview and per-arena overrides
- Multi-server network sync layer for the VillageDefenseHub plugin:
  - `vd_network_arenas` MySQL table updated every N seconds with live arena state
  - `vd_player_rejoin` MySQL table persists rejoin tokens so the Hub can notify returning players
  - BungeeCord `villagedefense:hub` sub-channel accepts `JOIN_ARENA` messages from the Hub
  - Pending hub joins are queued in `GameManager` and consumed at player join
- `NetworkConfigGUI` and `SettingsGUI` for in-game configuration of all global plugin settings

## GUI Structure

- `VDAdminGUI`: global administration entrypoint (bottom row: Schematics col 2, Kit Levels col 5, Settings col 8)
- `SettingsGUI`: groups Database, Language, Proxy (BungeeCord) and Network Sync
- `BungeeConfigGUI`: proxy return-flow configuration for `BungeeCord` (accessible via SettingsGUI)
- `NetworkConfigGUI`: in-game configuration of `network.enabled`, `server-id`, `sync-interval` (accessible via SettingsGUI)
- `ArenaSetupGUI`: per-arena setup and validation
- `KitGUI`, `ShopGUI`, `PerksGUI`, `AchievementGUI`
- `TowerGUI`, `SpecializationGUI`, `TrapGUI`
- `SchematicCategoryGUI`, `SchematicGUI`, `IconPickerGUI`
- `DatabaseGUI`, `LanguageGUI`, `LeaderboardGUI`
- `ShopPricesGUI`, `EnchantGUI`, `EnchantPricesGUI`, `TowerConfigGUI`

## Commands And Permissions

- `/vd`: `join`, `rejoin`, `leave`, `kit`, `shop`, `stats`, `list`, `top`, `help`
- `/vdadmin`: `create`, `delete`, `setup`, `reload`, `forcestart`, `forcestop`, `tp`, `info`, `coins`, `setwave`, `setlevel`
- Permission tree root: `villagedefense.admin`
- Additional nodes: `arena`, `setup`, `schematic`, `lang`, `database`, `prices`, `reload`, `game`, `player`, `play`, `test`, `bypass`

## Message System

- All runtime-visible messages are loaded from `src/main/resources/lang/*.yml`
- Active language namespaces include:
  `general`, `arena`, `setup`, `waves`, `tower`, `shop`, `player`, `perks`, `kit`, `village`, `lobby`, `sign`, `scoreboard`, `spectator`, `admin`, `trap`, `game`, `powerup`, `spec`, `perk`, `gui`
- Message loader behavior:
  - loads bundled language file from jar
  - overlays disk customizations
  - ensures bundled keys remain available on startup
  - supports formatted multi-line lists for GUI lore via `Messages.lines(...)`
  - supports player-context formatting via `Messages.get/format/lines(CommandSender, ...)`
  - synchronizes command and permission descriptions from language files at runtime

## Dependencies And Hooks

- Provided: `paper-api`
- Shaded: `HikariCP`, `H2`, `mysql-connector-j`, `protobuf-java`, `slf4j-api`
- Soft depends declared:
  - `OldCombatMechanics`
  - `Vault`
  - `PlaceholderAPI`
  - `ProtocolLib`
- Startup banner now reports status for:
  - `Vault`
  - `PlaceholderAPI`
  - `ProtocolLib`
  - `BungeeCord`
  - `OldCombatMechanics`
- Functional integrations now implemented:
  - `Vault`: permission checks through Vault when available, plus optional coin mirroring to economy providers
  - `PlaceholderAPI`: placeholder resolution on sender-aware messages, scoreboards and runtime notifications
  - `ProtocolLib`: optional packet-based action bar transport
  - `BungeeCord`: optional plugin messaging return flow to a configured proxy server
- Admin GUI coverage now includes direct configuration for the `BungeeCord` hook:
  - enable/disable toggle
  - editable return-server target
  - save/apply flow that reloads hooks from the persisted config
- Paper-only APIs removed from critical paths (`EntityMoveEvent`, `NumberFormat.blank()`, `getPluginMeta()`) to keep runtime behavior compatible with Spigot/Paper APIs
- Cross-version compatibility layer added in `compat/VersionCompat.java`
- Floating slot/trap displays now use armor-stand holograms via `compat/FloatingText.java` instead of `TextDisplay`

## Technical Decisions

- Current compilation baseline is Paper 1.17.1 with Java 17
- Runtime compatibility is intentionally aimed at Paper/Spigot `1.17 -> 1.21.x`
- Cross-version enum/API differences are centralized in `compat/VersionCompat.java`
- Old saved kit names are migrated at load time:
  - `WARRIOR -> GUARDIAN`
  - `ARCHER -> RANGER`
  - `MAGE -> BERSERKER`
  - `MEDIC -> MEDIC`
  - `TRAPPER -> ENGINEER`
- Newer APIs with no 1.17 equivalent are replaced by stable fallbacks:
  - `TextDisplay` -> armor-stand holograms
  - modern enchantment/effect/potion/particle names -> runtime alias resolution
  - sign side API -> legacy sign line API
  - `BlockData.rotate(...)` -> reflection/manual rotation fallback
- Config value `SQLITE` is kept for backward compatibility, but the actual embedded backend is H2
- The previous duplicate source tree `fr.maxbouard.villagedefense` has been removed
- Legacy `messages.yml` has been removed because the active runtime language system uses `lang/*.yml`
- Engineer reinforcement is adapted to the current gameplay model:
  - structures are not damageable by enemies in the current plugin
  - reinforcement is therefore implemented as a persistent performance bonus on Engineer-built towers/traps
  - repair pulse refreshes structure readiness and trap cooldowns instead of restoring missing HP
- Medic active ability is now ground-targeted:
  - click a block or looked-at ground position
  - creates a 15-second green particle ring
  - heals players standing inside the field over time
- Kit GUI descriptions now use localized multi-line lore blocks:
  - one line per ability/effect
  - explicit role/style summary
  - legacy `desc` keys remain available as short summaries for other contexts
- Kit required levels are now config-driven:
  - defaults still come from `KitType`
  - runtime reads `kits.<kit>.level-required`
  - admin GUI can override and persist these values without editing files by hand
- Arenas now have an explicit availability mode:
  - `ACTIVE`: visible and joinable normally
  - `TEST`: visible and joinable only for players with `villagedefense.test` or elevated admin access
  - `DISABLED`: hidden from player flows and blocked from joining

## Known Issues

- `PotionData` is still used in a few kit/shop builders and triggers deprecation warnings on modern `1.21` builds
- PlaceholderAPI only applies when a player/sender context is available; sender-less formatting intentionally remains generic
- Local DB class name remains `SQLiteDatabase` although the backend is H2
- Multi-version support is compile-validated, but not yet smoke-tested on every runtime from `1.17` to `1.21.x`
- Spigot/Paper compatibility is now code-aligned on the shared API surface, but still not runtime-smoke-tested on a real Spigot server with all optional hooks loaded together
- Engineer durability in the design spec is represented by reinforced structure bonuses because towers/traps are not currently destructible by mobs

## Development Checklist

### Phase 1 - Structural Compliance

- Maintain `DEVELOPMENT_LOG.md` after every change
- Keep all visible messages externalized in language files
- Keep successful compilation after every completed task
- Review and preserve GUI-first UX for admin flows
- Add and maintain startup hook status banner
- Audit command declarations, permissions and tab completion

### Phase 2 - Namespace And Packaging Compliance

- Base package migrated to `fr.obsidianlabs.plugins.villagedefense`
- Directory structure, package declarations, imports and `plugin.yml` updated
- Shaded relocation paths updated to the new base package
- Rebuild and validation required after each next structural change

### Phase 3 - Hook And Platform Compliance

- Implement Vault permissions and optional economy sync
- Implement PlaceholderAPI sender-aware formatting
- Implement ProtocolLib runtime usage
- Implement BungeeCord proxy return flow
- Validate behavior on Spigot/Paper-compatible APIs

### Phase 4 - Data And Runtime Consistency

- Fix player coin persistence
- Fix MySQL achievements persistence
- Align arena setup validation with GUI and runtime checks
- Review docs against actual code behavior

### Phase 5 - Gameplay Hardening

- Add tests or validation routines for critical systems
- Review balance, perks, waves and tower config defaults
- Stabilize rejoin, spectator and end-game reset edge cases

## Latest Changes

### 2026-04-01 (suite — version 1.1.3)

#### Version 1.1.3 — Limite de tentatives par arène

**ArenaAttemptEntry (nouveau)**
- POJO simple avec `attemptsUsed` (int) et `windowStart` (long, epoch secondes).
- Méthode `increment()` pour incrémenter proprement le compteur.

**ArenaAttemptManager (nouveau)**
- Cache en mémoire `Map<UUID, Map<String, ArenaAttemptEntry>>` chargé à la connexion du joueur, libéré à la déconnexion.
- `loadPlayer(UUID)` : CompletableFuture async déclenché dans `PlayerListener.onJoin`.
- `unloadPlayer(UUID)` : appelé dans `PlayerListener.onQuit`.
- `isBlocked(UUID, Arena)` : vérifie synchronement si la limite est atteinte et la fenêtre active. Retourne false si limit = 0.
- `getRemainingAttempts(UUID, Arena)` : tentatives restantes dans la fenêtre courante.
- `getWindowRemainingSeconds(UUID, Arena)` : secondes avant reset de la fenêtre.
- `incrementAttempt(UUID, arenaId)` : si fenêtre expirée → nouvelle fenêtre (compteur = 1). Sinon incrémente. Persiste en DB async (fire-and-forget).

**Base de données — table vd_arena_attempts**
- Nouvelle table : `player_uuid, arena_id, attempts_used, window_start` (PK composite).
- `Database` interface : méthodes default `initAttemptTable()`, `loadAttemptEntries(UUID)`, `saveAttemptEntry(...)`.
- `MySQLDatabase` : `CREATE TABLE IF NOT EXISTS` + `ON DUPLICATE KEY UPDATE`.
- `SQLiteDatabase` : même structure H2 + pattern `INSERT IGNORE` + `UPDATE`.
- `initAttemptTable()` appelé dans `VillageDefensePlugin.onEnable()` après `initDatabase()`.

**Arena.java**
- Nouveaux champs : `attemptLimit` (int, défaut 0 = désactivé) et `attemptWindowHours` (int, défaut 12).
- Getters/setters + `hasAttemptLimit()`.
- `setAttemptLimit` : valeur minimale 0 (désactivé). `setAttemptWindowHours` : valeur minimale 1.

**ArenaManager.java**
- `loadFromFile()` : lecture de `attempt-limit` et `attempt-window-hours` depuis le YAML.
- `saveArena()` : persistance des mêmes clés.
- `joinArena()` : vérification synchrone via `plugin.getAttemptManager().isBlocked()` avant `arena.addPlayer()`. Envoie `arena.attempt-blocked` avec les paramètres `{0}=limit, {1}=windowHours, {2}=heuresRestantes, {3}=minutesRestantes`.

**ArenaOptionsGUI.java**
- Slot 21 ajouté : HOPPER si actif, BARREL si désactivé.
- Clic gauche → prompt chat pour définir la limite (entier > 0).
- Shift+clic gauche → désactiver la limite.
- Shift+clic droit → prompt chat pour changer la durée de la fenêtre en heures.
- Commentaire de layout mis à jour.

**VillageDefensePlugin.java**
- Champ `ArenaAttemptManager attemptManager` + getter `getAttemptManager()`.
- Instanciation après `playerManager` dans `onEnable()`.
- Appel `getDatabase().initAttemptTable()` au démarrage.

**GameManager.java**
- `startGame()` : après `recordArenaGame`, appel `plugin.getAttemptManager().incrementAttempt(p.getUniqueId(), arena.getId())` pour chaque joueur si la limite est activée.
- L'incrément se produit au **lancement de la partie**, pas à l'entrée en lobby — un joueur peut rejoindre le lobby et partir sans consommer de tentative.

**Langues fr/en/de**
- `arena.attempt-blocked` : message de blocage avec temps restant.
- Section `admin.options.attempt-limit-*` : item, current, disabled, click, shift-window, shift-disable, prompt, set, set-disabled, window-prompt, window-set, invalid.

### 2026-04-01

#### Version 1.1.2 — Récompenses, limite de vague, boundary mode, leaderboards par arène

**ArenaRewardsGUI (nouveau)**
- Nouveau sous-GUI 45 slots accessible depuis `ArenaSetupGUI` slot 29.
- Permet de configurer deux types de récompenses distribuées en fin de partie (victoire ou défaite) à chaque joueur présent.
- **Vault economy** : toggle activé/désactivé, Shift+Clic pour définir le montant par joueur via prompt chat. Indisponible si Vault n'est pas présent sur le serveur.
- **Commandes console** : jusqu'à 9 commandes, le placeholder `{player}` est remplacé par le nom du joueur au moment de l'exécution. Clic sur une commande listée pour la supprimer. Shift+Clic sur le bouton Effacer pour tout vider.
- Récompenses exécutées dans `GameManager.endGame()` sur tous les joueurs de l'arène avant la phase de reset.
- Persistance YAML : `reward-vault-enabled`, `reward-vault-amount`, `reward-commands`.
- Bouton dans `ArenaSetupGUI` : DIAMOND si des récompenses sont définies, EMERALD sinon.
- `VaultHook.deposit(Player, double)` ajouté via pattern `invokeBestMatch` (reflection).
- `HookManager.depositVaultMoney(Player, double)` exposé comme API interne.
- `ArenaRewardsGUI.TITLE` ajouté à `isCustomGuiTitle()` dans `PlayerListener`.
- Clés de langue : section `admin.rewards:` complète + `gui.arena-rewards.title` dans fr/en/de.

**Limite de vague**
- Champ `waveLimit` (int, 0 = désactivé) ajouté dans `Arena`.
- Bouton au slot 20 de `ArenaOptionsGUI` : CLOCK si actif, COMPASS si désactivé.
- Clic = prompt chat (entier > 0) ; Shift+Clic = désactiver.
- `GameManager.startBetweenWaves()` : si `waveLimit > 0 && currentWave >= waveLimit` → appel de `endGame(arena, true)` et retour immédiat avant les checks vague bonus et le timer inter-vague.
- `GameManager.endGame()` : si victoire par limite de vague → diffuse `arena.wave-limit-victory` à la place du message standard.
- Listé dans la section optionnelle de l'item d'état du `ArenaSetupGUI`.
- Persistance YAML : `wave-limit`.
- Clés de langue : `admin.options.wave-limit-*`, `admin.setup.status-wave-limit`, `arena.wave-limit-victory` dans fr/en/de.

**Mode de limite d'arène (ArenaBoundaryMode)**
- Enum `ArenaBoundaryMode` : `PUSH_BACK` (défaut) et `TP_CENTER`, avec méthode `next()`.
- Bouton au slot 16 de `ArenaPositionsGUI` (à côté du coffre shop) : BARRIER si PUSH_BACK, ENDER_EYE si TP_CENTER. Clic = cycle.
- `PlayerListener.onPlayerMove` : si TP_CENTER et `villageCenter != null`, téléporte le joueur au centre du village avec le message `arena.boundary-tp`.
- Persistance YAML : `boundary-mode`.
- Clés de langue : `admin.setup.boundary-mode/push-back/tp-center/push-back-lore/tp-center-lore/boundary-click`, `arena.boundary-tp` dans fr/en/de.

**Leaderboards par arène**
- Nouvelle table `vd_arena_stats (uuid, arena_id, kills, waves, games)` créée au démarrage dans MySQL et H2.
- `Database` interface : méthodes default `recordArenaKill`, `recordArenaWave`, `recordArenaGame`, `getTopPlayersByArena`.
- `MySQLDatabase` : `ON DUPLICATE KEY UPDATE` ; `SQLiteDatabase` : `INSERT IGNORE` + `UPDATE` (compatibilité H2).
- `ArenaListener` et `GameManager` alimentent les compteurs par arène.
- `LeaderboardDisplayManager` gère deux displays par arène : clé `arenaId_global` (query globale) et `arenaId_arena` (query par arène). Titres d'hologramme distincts.
- `ArenaOptionsGUI` slot 15 = Classement Global (ARMOR_STAND), slot 16 = Classement Arène (PLAYER_HEAD).
- Champ `arenaLeaderboardLocation` ajouté dans `Arena`, persisté sous `arena-leaderboard-location`.
- Clés de langue ajoutées dans fr/en/de.

**Chemins waypoints déplacés**
- Le bouton Chemins a été déplacé de `ArenaSetupGUI` vers `ArenaGameplayLocationsGUI` (slot 15).
- Le lore précise que les chemins sont optionnels.
- L'item d'état du `ArenaSetupGUI` liste les chemins dans la section optionnelle.

**Agrandissements de GUIs**
- `ArenaPositionsGUI` : 27 → 36 slots.
- `ArenaGameplayLocationsGUI` : 27 → 36 slots.
- `ArenaOptionsGUI` : 27 → 36 slots.

### 2026-03-30

#### Couche réseau Hub (VillageDefenseHub support)

- Added `network/NetworkArenaEntry.java` POJO representing one arena row in the shared network table.
- Added `network/NetworkSyncManager.java` that periodically exports all local arena states to the shared MySQL table `vd_network_arenas` (async, configurable interval). Only active when `network.enabled: true` AND the database backend is MySQL; silent no-op on H2.
- Added `initNetworkTable()`, `upsertNetworkArena(NetworkArenaEntry)` and `removeNetworkArenas(String)` as default no-op methods on the `Database` interface; implemented in `MySQLDatabase` (composite PK `server_id + arena_id`, `ON DUPLICATE KEY UPDATE`, cleanup on shutdown).
- Added `initRejoinTable()`, `saveRejoinToken(...)` and `deleteRejoinToken(UUID)` as default no-op methods on the `Database` interface; implemented in `MySQLDatabase` (table `vd_player_rejoin`, PK `player_uuid`). Persists rejoin tokens so the Hub plugin can notify returning players.
- Extended `BungeeCordHook.onPluginMessageReceived` to handle BungeeCord `Forward` messages on sub-channel `villagedefense:hub`. Parses `JOIN_ARENA | playerUUID | arenaId` and forwards to `GameManager.queueHubJoin`.
- Added `pendingHubJoinArenas` / `pendingHubJoinExpiries` maps and `queueHubJoin` / `consumeHubJoin` methods in `GameManager` (5-minute TTL, cleaned up in `shutdown()`).
- Extended `GameManager.onPlayerLeaveArena`: writes rejoin token to DB asynchronously when network sync is active.
- Extended `GameManager.clearRejoinToken`: deletes DB row asynchronously on local rejoin, expiry or explicit clear.
- Extended `GameManager.consumeHubJoin`: deletes DB rejoin row asynchronously when hub-requested join is consumed.
- Added private helper `GameManager.isNetworkSyncActive()`.
- Extended `PlayerListener.onJoin` (2-tick delay): consumes pending hub join and auto-joins arena; falls through to rejoin-token notification otherwise.
- Added `network:` section to `config.yml` with `enabled`, `server-id` and `sync-interval` keys (disabled by default).
- Added `NetworkSyncManager` field, `start()` / `stop()` lifecycle and getter in `VillageDefensePlugin`.
- `NetworkSyncManager.start()` calls both `initNetworkTable()` and `initRejoinTable()`.

#### GUI admin — NetworkConfigGUI

- Added `gui/NetworkConfigGUI.java`: 27-slot GUI to configure `network.enabled`, `network.server-id` and `network.sync-interval` in-game, with save + live restart of `NetworkSyncManager`.
- Registered `NetworkConfigGUI` in `VillageDefensePlugin` and routed its title in `PlayerListener.onInventoryClick`.

#### GUI admin — refonte rangée basse + SettingsGUI

- Replaced the 6-button bottom row of `VDAdminGUI` with 3 focused buttons (col 2 Schematics, col 5 Kit Levels, col 8 Settings).
- Added `gui/SettingsGUI.java`: 27-slot "Paramètres" GUI grouping Database, Language, Proxy (BungeeCord, renamed) and Network Sync with descriptive lore for each option.
- Registered `SettingsGUI` in `VillageDefensePlugin` and routed its title in `PlayerListener.onInventoryClick`.
- `BungeeConfigGUI` and `NetworkConfigGUI` are now only reachable through `SettingsGUI`; no direct slots in the main admin GUI.
- Added all new language keys (`admin.settings.*`, `admin.main.settings*`, `admin.main.schematics-lore-global`, `gui.settings.title`) in `lang/fr.yml`, `lang/en.yml` and `lang/de.yml`.

### 2026-03-27

- Removed duplicate source tree `fr.maxbouard.villagedefense`
- Cleaned old build artifacts and rebuilt the plugin
- Removed obsolete `messages.yml`
- Removed obsolete speculative document `village_defense_kits.md`
- Aligned visible kit descriptions with actual Warrior loadout
- Clarified that config type `SQLITE` maps to local H2 backend
- Added optional hook declarations in `plugin.yml`
- Added hook status lines to the startup banner
- Migrated the Java base package to `fr.obsidianlabs.plugins.villagedefense`
- Externalized remaining runtime-visible shop, achievement, progression, boss bar and admin GUI labels into `lang/*.yml`
- Added a centralized hook layer for Vault, PlaceholderAPI, ProtocolLib and BungeeCord
- Routed permission checks through Vault when available
- Added runtime localization sync for command and permission metadata
- Added PlaceholderAPI-aware message formatting for sender-bound outputs and scoreboards
- Added ProtocolLib-backed action bar sending with native fallback
- Added optional BungeeCord proxy return support through plugin messaging
- Migrated build target to Java 21 and Paper 1.21.1
- Removed Paper-only APIs from gameplay-critical paths to preserve Spigot/Paper compatibility
- Fixed player coin persistence in H2 and MySQL
- Fixed MySQL achievement persistence
- Aligned arena setup validation, GUI status and runtime readiness checks
- Removed remaining hardcoded fallback metadata from tower, perk, power-up, achievement, kit, trap and specialization enums used by the active systems
- Migrated build baseline back to Java 17 and Paper 1.17.1 for real `1.17 -> 1.21.x` compatibility
- Added `compat/VersionCompat.java` to centralize cross-version materials, enchantments, particles, potion effects, potion types and title timings
- Replaced `TextDisplay` slot/trap labels with `compat/FloatingText.java` armor-stand holograms compatible with 1.17
- Replaced 1.20+/1.21 enum usages across kits, shop, towers, waves, GUI and listeners with cross-version aliases
- Reworked sign updates, scoreboard objective creation and schematic rotation to stay compatible with the 1.17 API surface
- Replaced remaining Adventure/Paper UI calls with Spigot-safe legacy/Bukkit APIs for inventory titles, item meta, scoreboards, titles, sign lines, entity names and action bars
- Externalized the last runtime-visible fallbacks into `lang/*.yml`, including missing-message fallback, empty database password label and schematic rotation labels
- Rebuilt successfully after the Spigot-safe UI/API migration
- Fixed MySQL schema migration to add missing columns through metadata checks instead of `ALTER TABLE ... ADD COLUMN IF NOT EXISTS`, for compatibility with servers rejecting that syntax
- Created this `DEVELOPMENT_LOG.md`
- Created `CHANGELOG.md` for player-facing release notes and future patch-note tracking
- Established the workflow: surface `CHANGELOG.md` `Unreleased` before work, then update it after each delivered task
- Reworked `Medic` active ability so it creates a targeted 15-second green healing field at the clicked location instead of a single instant heal around the caster
- Added targeted-position resolution in `PlayerListener` for kit abilities using clicked blocks or ray-traced ground points
- Updated localized kit ability texts to describe the new Medic healing field behavior
- Reworked kit GUI descriptions into formatted multi-line lore entries with one dedicated line per ability in `lang/fr.yml`, `lang/en.yml` and `lang/de.yml`
- Added `gui/KitConfigGUI.java` and a dedicated admin entry to configure the required level of each kit from the main admin GUI
- Moved kit level checks and automatic unlocks to `KitManager`/`PlayerManager` runtime config reads instead of hardcoded enum values
- Added `gui/BungeeConfigGUI.java` and a dedicated admin entry in `VDAdminGUI` to configure `hooks.bungeecord.enabled` and `hooks.bungeecord.return-server`
- Hardened `TowerConfigGUI` click handling to use top-inventory `rawSlot` detection instead of ambiguous relative slots, fixing lower-row tower config interactions (`ICE`, `LIGHTNING`, `CANNON`)
- Blocked vanilla `MERCHANT` trading for protected plugin villagers:
  - village villagers marked with `VILLAGER_META`
  - service villagers marked with `SHOP_VILLAGER_META` / `ENCHANT_VILLAGER_META`
  - custom shop/enchant GUI flow remains allowed through the interaction handler
- Power-up ground lifetime is now explicitly capped at `45 seconds` in `PowerUpManager`
- Progression and unlock notifications are now routed through the message system entrypoint:
  - level-up
  - kit unlocked / selected / level-required
  - perk unlocked
  - achievement unlocked
- Replaced the old kit roster (`Warrior`, `Archer`, `Mage`, `Medic`, `Trapper`) with the new role roster (`Guardian`, `Ranger`, `Berserker`, `Medic`, `Engineer`)
- Added `kit/KitAbilityManager.java` to centralize active abilities, passives, taunt control, respawn reduction and structure buffs
- Added migration logic for old saved kit names in `KitType.fromSerialized(...)` and `VDPlayer.deserializeKits(...)`
- Reworked kit loadouts and ability items with localized lore stored in `lang/fr.yml`, `lang/en.yml` and `lang/de.yml`
- Updated `config.yml` kit entries and costs to the new roster
- Integrated Engineer discounts into tower placement, tower upgrades and trap purchases
- Added reinforced structure ownership flags to towers and trap slots
- Hooked Engineer overclock and repair pulse into tower attack intervals, tower damage and trap cooldown behavior
- Replaced the removed Mage blaze-rod tornado logic with the new generic kit ability item flow in `PlayerListener`
- Hooked kit combat logic into `ArenaListener` for Guardian mitigation, Ranger empowered arrows and Berserker damage/lifesteal
- Hooked Guardian taunt into both event-based mob targeting and the periodic retarget task in `GameManager`
- Adjusted respawn timing so living Medics reduce teammate respawn delay
- Fixed `PlayerManager.applyKit(...)` so kit-specific max health is preserved when the loadout is applied
- Replaced the vanilla spectator flow with a custom observer mode in `GameManager` so arena spectators and respawn-wait players use survival + fly + invisibility instead of `GameMode.SPECTATOR`
- Added observer restrictions in `PlayerListener` and `ArenaListener` to block entity interactions, block place/break, pickups, drops, hunger loss and leaving arena bounds while preserving the spectator leave item
- Hid observer-mode players from online viewers and from players joining mid-game, then restored visibility cleanly on leave or respawn
- Reworked the `Purge` power-up implementation in `PowerUpManager` so it clears every active non-boss mob in the arena instead of only `Zombie` entities
- Exposed the current arena boss UUID through `GameManager.isBossEntity(...)` and updated localized purge broadcast text to describe the boss exclusion correctly
- Added explicit wave progress tracking in `Arena` with total / spawned / resolved mob counters so wave completion no longer depends only on currently alive entities
- Updated `WaveManager`, `GameManager`, `PowerUpManager` and `ScoreboardManager` to rely on wave progress, preventing `Purge` or early full clears from ending a wave while additional mobs are still scheduled to spawn
- Added a public Bukkit-facing API layer in `api/` with `VillageDefenseApi`, immutable player/arena snapshots, achievement definitions and a single `VillageDefenseProgressEvent` stream for external mission / quest / battle-pass plugins
- Registered the public API through Bukkit `ServicesManager` and documented usage in `PUBLIC_API.md`
- Wired runtime progression hooks for arena join/leave, game start/end, mob kills, wave completion, XP gain, level-up, achievement unlock and power-up pickup
- Added `arena/ArenaAvailability.java` and migrated arena persistence from a simple `enabled` boolean to a compatibility-preserving `availability` mode with legacy fallback on old arena files
- Added an availability selector in `ArenaSetupGUI` so arena setup can cycle `Active -> Test -> Disabled` before the arena is exposed to players
- Routed `/vd join`, `/vd list`, tab completion and sign joins through the new access rules so test arenas are only exposed to authorized testers while disabled arenas stay hidden
- Added `leaderboard/LeaderboardDisplayManager.java` to render optional per-arena floating leaderboard holograms using `FloatingText` and live database rankings
- Added optional `leaderboard-location` arena persistence plus setup GUI placement/removal controls for the floating leaderboard anchor
- Added localized hologram text templates and arena setup messages for the floating leaderboard system in `lang/fr.yml`, `lang/en.yml` and `lang/de.yml`
- Rebuilt successfully after the full kit refactor
- Reworked the death -> respawn observer flow so temporary observers now enter custom spectator mode only after the Bukkit respawn event, with forced respawn fallback, cleared kit inventory, working leave-bed item, restored fly/invisibility and viewer hiding during the respawn timer
- Added root `FEATURES.txt` as a communication-oriented reference for the public API surface, snapshot fields, exposed progress event payload/types and the current PlaceholderAPI support boundary (consumption only, no native `%villagedefense_*%` expansion yet)
- Added a native PlaceholderAPI expansion `villagedefense` with lightweight global, player, current-arena and targeted-arena placeholders, wired through `HookManager` registration/unregistration and documented in both `FEATURES.txt` and `PUBLIC_API.md`
- Prepared release `1.1.0`: bumped the Maven project version, moved the current `CHANGELOG.md` unreleased notes into a real `1.1.0` release entry, and created `Nouveautes.md` as player/admin-facing release notes
- Performed a general rules audit before release packaging:
  - package base, hooks, GUI-first admin flow, language files, startup banner, command declarations, tab completion, development log and compilation workflow remain aligned with the Obsidian Labs rules
  - one strict-rule gap still remains: the codebase is not yet fully documented with per-function descriptive comments/Javadoc across every method
- Rewrote `FEATURES.txt` so it now documents the full product surface instead of only the public API and placeholders:
  - gameplay loop
  - arena setup and availability
  - kits, towers, specializations, traps, perks and power-ups
  - admin systems, database, hooks, commands and permissions
  - public API and native PlaceholderAPI expansion
- Rewrote `Wiki.md` as a full operator guide aligned with the current codebase:
  - installation and first boot
  - admin setup flow
  - exact arena setup order and wand modes
  - production checklist, hooks, API and troubleshooting
- Rebuilt successfully after the full documentation rewrite (`FEATURES.txt`, `Wiki.md`, `PUBLIC_API.md`) to confirm the 1.1.0 package still builds cleanly
- Reworked perk session cleanup so bought session perks now stay bound to their source arena and are purged even if the player left or disconnected before the match ended
- Added pre-join cleanup hooks in `/vd join`, `/vd rejoin` and sign joins so stale perk companions/session state cannot bleed into another arena while still preserving true same-match rejoins
