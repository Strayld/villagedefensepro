# Village Defense — Spécification ultra détaillée du système Gameplay (V1)

## 1. But du document

Ce document doit permettre à une IA d'implémenter le système avec **le moins de réflexion possible**.

L'IA doit surtout :

- comparer cette spécification avec l'architecture déjà existante du plugin ;
- réutiliser ce qui existe déjà quand c'est pertinent ;
- ajouter les nouvelles briques manquantes ;
- respecter la structure actuelle du plugin si elle est propre ;
- éviter les régressions.

Ce document décrit :

- la vision générale ;
- l'architecture recommandée ;
- les objets de données ;
- les services ;
- le flow runtime ;
- la structure des GUI ;
- les règles métier ;
- les validations ;
- la persistance ;
- le plan d'intégration ;
- le pseudo-code principal.

---

# 2. Décisions déjà validées

## 2.1 Nom du menu principal
Dans `ArenaSetupGUI`, le nouvel item s'appelle :

**Gameplay**

Ce nom est validé.

---

## 2.2 Gestion des pools
Les `MobPool` sont :

- techniquement réutilisables ;
- mais peuvent avoir une catégorie par défaut pour simplifier l'édition dans les GUI.

---

## 2.3 Gestion des vagues
Les vagues ne doivent **pas** être configurées une par une.

Le système doit fonctionner avec :

- des **catégories de vagues** ;
- des **templates de catégories** ;
- des **pools de mobs** ;
- un **scaling automatique** ;
- des **overrides uniquement pour les cas spéciaux**.

---

## 2.4 Portée de la V1
Pour la **V1**, la difficulté choisie influence :

- les stats des mobs ;
- les récompenses.

Pour la V1, la difficulté **n'influence pas encore directement la composition avancée des vagues**.

Cela veut dire :

- la composition dépend surtout du scaling + catégories + pools ;
- la difficulté sélectionnée par le joueur agit comme couche de multiplicateurs.

---

# 3. Objectif fonctionnel complet

Le système Gameplay doit permettre à un admin de :

- ouvrir `ArenaSetupGUI` ;
- cliquer sur `Gameplay` ;
- configurer la difficulté d'une arène ;
- définir quelles difficultés sont disponibles ;
- définir la difficulté par défaut ;
- configurer la montée en puissance des vagues ;
- définir les catégories de vagues (`NORMAL`, `ELITE`, `BOSS`, `SPECIAL`) ;
- associer des pools de mobs à ces catégories ;
- configurer les mobs autorisés dans les pools ;
- choisir les paramètres de spawn de chaque mob ;
- préparer l'intégration MythicMobs ;
- créer des exceptions sur certaines vagues via des overrides ;
- sauvegarder le tout par arène ;
- faire fonctionner automatiquement le moteur de génération des vagues sans configuration manuelle vague par vague.

---

# 4. Règle fondamentale de design

## 4.1 Ce qu'il ne faut PAS faire

Le système ne doit pas être pensé comme :

- wave1 = config
- wave2 = config
- wave3 = config
- wave4 = config

Cela rendrait le système :

- trop lourd à maintenir ;
- trop difficile à éditer in-game ;
- trop compliqué à étendre ;
- fragile avec les nouveaux mobs ;
- très mauvais pour MythicMobs.

---

## 4.2 Ce qu'il faut faire à la place

Le système doit être pensé comme :

1. une arène possède des **settings gameplay** ;
2. ces settings définissent :
   - les difficultés ;
   - le scaling ;
   - les catégories ;
   - les pools ;
   - les overrides ;
3. chaque vague est générée dynamiquement à partir de ces settings ;
4. seules certaines vagues précises ont éventuellement un override.

---

# 5. Vision globale de l'architecture

Le système doit être séparé en 5 couches.

---

## 5.1 Couche 1 — Configuration in-game

Responsabilité :

- fournir des GUI propres ;
- éditer tous les paramètres ;
- modifier les settings d'une arène ;
- lancer la sauvegarde.

Contenu :

- `ArenaSetupGUI`
- `GameplayMainGUI`
- `DifficultyGUI`
- `WaveScalingGUI`
- `WaveCategoriesGUI`
- `WaveCategoryEditorGUI`
- `MobPoolsGUI`
- `MobPoolEditorGUI`
- `MobSelectionListGUI`
- `WaveOverridesGUI`
- plus tard `BossSettingsGUI`
- plus tard `MythicMobsGUI`

---

## 5.2 Couche 2 — Modèle de données

Responsabilité :

- représenter les réglages persistants ;
- être simple à sérialiser ;
- être simple à manipuler en mémoire ;
- ne pas dépendre des APIs runtime externes.

---

## 5.3 Couche 3 — Services métier

Responsabilité :

- calculer ;
- valider ;
- composer ;
- générer ;
- appliquer les règles.

---

## 5.4 Couche 4 — Providers / intégrations externes

Responsabilité :

- abstraire la source des mobs ;
- permettre la compatibilité vanilla / custom / MythicMobs ;
- éviter de polluer le moteur avec des dépendances externes.

---

## 5.5 Couche 5 — Persistance

Responsabilité :

- charger les settings ;
- sauvegarder les settings ;
- migrer l'ancien format si nécessaire ;
- mapper les objets Java vers les configs.

---

# 6. Objet racine principal

Le système doit être centré autour d'un objet racine unique par arène.

## 6.1 Classe recommandée

```java
class ArenaGameplaySettings {
    DifficultySettings difficultySettings;
    WaveScalingSettings waveScalingSettings;
    Map<WaveCategory, WaveCategoryTemplate> categoryTemplates;
    Map<String, MobPool> mobPools;
    Map<Integer, WaveOverride> waveOverrides;
    GameplayGeneralSettings generalSettings;
}
```

---

## 6.2 Rôle de cet objet

Cet objet représente **tout le système Gameplay d'une arène**.

Il doit être :

- chargé au démarrage ou à l'ouverture de la config ;
- modifiable en mémoire ;
- sauvegardable ;
- utilisable par le moteur de vagues pendant la partie.

---

# 7. Modèles de données détaillés

---

## 7.1 `GameplayGeneralSettings`

Permet de stocker des réglages généraux hors difficulté pure.

```java
class GameplayGeneralSettings {
    boolean enabled;
    boolean useAdvancedWaveSystem;
    boolean allowWaveOverrides;
    boolean allowMythicMobs;
}
```

### Rôle
- activer/désactiver le système gameplay avancé ;
- garder des flags généraux de comportement ;
- prévoir l'extensibilité.

---

## 7.2 `DifficultySettings`

```java
class DifficultySettings {
    List<GameDifficulty> enabledDifficulties;
    GameDifficulty defaultDifficulty;
    Map<GameDifficulty, DifficultyProfile> profiles;
}
```

### Rôle
Définit les difficultés disponibles pour l'arène.

### Contraintes
- `enabledDifficulties` ne doit pas être vide ;
- `defaultDifficulty` doit être contenue dans `enabledDifficulties` ;
- `profiles` doit contenir un profil pour chaque difficulté activée.

---

## 7.3 `GameDifficulty`

```java
enum GameDifficulty {
    EASY,
    NORMAL,
    HARD,
    CUSTOM
}
```

### Remarque
`CUSTOM` est optionnel mais recommandé si le plugin permet de créer une difficulté très spécifique.

---

## 7.4 `DifficultyProfile`

```java
class DifficultyProfile {
    double healthMultiplier;
    double damageMultiplier;
    double speedMultiplier;
    double rewardMultiplier;
}
```

### Rôle
Appliquer une couche de multiplicateurs selon la difficulté choisie pendant la partie.

### Exemples
#### EASY
- healthMultiplier = 0.85
- damageMultiplier = 0.85
- speedMultiplier = 0.95
- rewardMultiplier = 0.9

#### NORMAL
- healthMultiplier = 1.0
- damageMultiplier = 1.0
- speedMultiplier = 1.0
- rewardMultiplier = 1.0

#### HARD
- healthMultiplier = 1.2
- damageMultiplier = 1.15
- speedMultiplier = 1.05
- rewardMultiplier = 1.25

---

## 7.5 `WaveScalingSettings`

```java
class WaveScalingSettings {
    double baseBudget;
    double linearGrowth;
    double exponentialGrowth;

    double baseHealthMultiplier;
    double baseDamageMultiplier;
    double baseSpeedMultiplier;

    double healthPerWave;
    double damagePerWave;
    double speedPerWave;

    int bossInterval;
    int eliteInterval;
    int specialInterval;

    int earlyGameMaxMobTypes;
    int midGameMaxMobTypes;
    int lateGameMaxMobTypes;

    int midGameStartWave;
    int lateGameStartWave;
}
```

### Rôle
Définir comment la difficulté des vagues augmente automatiquement.

### Interprétation
- `baseBudget` = budget de difficulté de départ ;
- `linearGrowth` = croissance régulière ;
- `exponentialGrowth` = accélération progressive ;
- `baseHealthMultiplier`, `baseDamageMultiplier`, `baseSpeedMultiplier` = base des stats scaling avant application de la difficulté choisie ;
- `healthPerWave`, `damagePerWave`, `speedPerWave` = progression par vague ;
- `bossInterval`, `eliteInterval`, `specialInterval` = fréquence des catégories ;
- `earlyGameMaxMobTypes`, `midGameMaxMobTypes`, `lateGameMaxMobTypes` = contrôle de la variété ;
- `midGameStartWave`, `lateGameStartWave` = paliers de jeu.

---

## 7.6 `WaveCategory`

```java
enum WaveCategory {
    NORMAL,
    ELITE,
    BOSS,
    SPECIAL
}
```

### Rôle
Décrire les grands types de vagues.

### Sens recommandé
- `NORMAL` = vague classique ;
- `ELITE` = vague plus forte qu'une normale ;
- `BOSS` = grosse vague spéciale ;
- `SPECIAL` = vague événement, mini-boss, gimmick, composition particulière.

---

## 7.7 `CompositionMode`

```java
enum CompositionMode {
    FIXED,
    RANDOM,
    WEIGHTED
}
```

### Rôle
Décrire comment une vague est composée à partir du pool.

### Recommandations V1
- `NORMAL` → `WEIGHTED`
- `ELITE` → `WEIGHTED`
- `BOSS` → `FIXED` ou `WEIGHTED`
- `SPECIAL` → libre

---

## 7.8 `WaveCategoryTemplate`

```java
class WaveCategoryTemplate {
    WaveCategory category;
    String mobPoolId;
    CompositionMode compositionMode;

    double categoryBudgetMultiplier;
    double healthMultiplier;
    double damageMultiplier;
    double speedMultiplier;

    int maxMobTypes;
    int defaultMobCount;
    boolean enabled;
}
```

### Rôle
Définir le comportement par défaut d'une catégorie de vague.

### Signification des champs
- `mobPoolId` : pool utilisé par défaut ;
- `compositionMode` : comment choisir les mobs ;
- `categoryBudgetMultiplier` : multiplicateur de budget spécifique à la catégorie ;
- `healthMultiplier`, `damageMultiplier`, `speedMultiplier` : bonus de catégorie ;
- `maxMobTypes` : diversité max ;
- `defaultMobCount` : valeur indicative pour certaines compositions ;
- `enabled` : permet de désactiver une catégorie.

---

## 7.9 `MobSource`

```java
enum MobSource {
    VANILLA,
    INTERNAL,
    MYTHICMOBS
}
```

### Rôle
Identifier la source du mob.

### Important
Le moteur de vagues ne doit pas dépendre directement de l'origine du mob.  
Il doit manipuler `MobEntry` + `MobSource`, puis déléguer au bon provider.

---

## 7.10 `MobPool`

```java
class MobPool {
    String id;
    String displayName;
    WaveCategory defaultCategory;
    List<MobEntry> entries;
    boolean enabled;
}
```

### Rôle
Représente une liste réutilisable de mobs autorisés.

### Remarque
`defaultCategory` sert uniquement à améliorer l'édition in-game.
Un pool peut techniquement être réutilisé ailleurs.

---

## 7.11 `MobEntry`

```java
class MobEntry {
    String id;
    MobSource source;
    String displayName;

    int weight;
    double threat;

    int minAmount;
    int maxAmount;

    int minWave;
    int maxWave;

    boolean enabled;
}
```

### Rôle
Représente un mob configurable dans un pool.

### Sens des champs
- `id` : identifiant logique du mob ;
- `source` : provenance ;
- `displayName` : nom affiché en GUI ;
- `weight` : poids dans le tirage pondéré ;
- `threat` : coût de difficulté du mob ;
- `minAmount` / `maxAmount` : quantité min/max quand ce mob est sélectionné ;
- `minWave` / `maxWave` : vagues dans lesquelles le mob est autorisé ;
- `enabled` : permet de désactiver l'entrée sans la supprimer.

### Exemples
#### Zombie vanilla
- id = `ZOMBIE`
- source = `VANILLA`

#### Mythic Skeleton Boss
- id = `SkeletonKing`
- source = `MYTHICMOBS`

---

## 7.12 `WaveOverride`

```java
class WaveOverride {
    int waveIndex;
    boolean enabled;

    WaveCategory forcedCategory;
    String forcedPoolId;

    CompositionMode forcedCompositionMode;

    Double healthMultiplierOverride;
    Double damageMultiplierOverride;
    Double speedMultiplierOverride;
}
```

### Rôle
Permet de modifier une vague précise sans casser tout le système automatique.

### Usage
Réservé aux cas particuliers.

### Règle
Le système doit fonctionner parfaitement même avec zéro override.

---

## 7.13 `WaveDifficultyProfile`

```java
class WaveDifficultyProfile {
    int waveIndex;
    WaveCategory category;

    double totalBudget;

    double healthMultiplier;
    double damageMultiplier;
    double speedMultiplier;
    double rewardMultiplier;

    int maxMobTypes;
}
```

### Rôle
Objet calculé à runtime représentant la difficulté finale d'une vague.

### Important
Cet objet n'est pas forcément persisté.  
Il peut être purement calculé au moment du lancement de la vague.

---

## 7.14 `WaveMobSelection`

```java
class WaveMobSelection {
    MobEntry mobEntry;
    int amount;
}
```

### Rôle
Représente une sélection finale d'un type de mob dans une vague.

---

## 7.15 `WaveComposition`

```java
class WaveComposition {
    int waveIndex;
    WaveCategory category;
    List<WaveMobSelection> selections;
}
```

### Rôle
Représente la composition finale d'une vague prête à être spawnée.

---

# 8. Services métier à implémenter

---

## 8.1 `ArenaGameplayService`

### Responsabilités
- charger les settings d'une arène ;
- sauvegarder les settings ;
- fournir les settings au moteur de jeu ;
- exposer les méthodes de modification utilisées par les GUI ;
- créer une config par défaut quand il n'y en a pas.

### Méthodes recommandées

```java
ArenaGameplaySettings getSettings(String arenaId);
void saveSettings(String arenaId);
void resetSettings(String arenaId);
boolean hasSettings(String arenaId);
```

---

## 8.2 `WaveCategoryResolver`

### Responsabilité
Déterminer la catégorie d'une vague en fonction de son index et du scaling.

### Signature recommandée

```java
WaveCategory resolveCategory(int waveIndex, ArenaGameplaySettings settings);
```

### Règle par défaut recommandée
1. si `specialInterval > 0` et `waveIndex % specialInterval == 0` → `SPECIAL`
2. sinon si `bossInterval > 0` et `waveIndex % bossInterval == 0` → `BOSS`
3. sinon si `eliteInterval > 0` et `waveIndex % eliteInterval == 0` → `ELITE`
4. sinon → `NORMAL`

### Remarque
L'ordre doit être volontaire et explicite.

---

## 8.3 `WaveDifficultyCalculator`

### Responsabilité
Calculer le profil final de difficulté.

### Signature recommandée

```java
WaveDifficultyProfile calculate(
    int waveIndex,
    GameDifficulty selectedDifficulty,
    WaveCategory category,
    ArenaGameplaySettings settings
);
```

### Étapes du calcul
1. lire `WaveScalingSettings` ;
2. lire `DifficultyProfile` correspondant à la difficulté sélectionnée ;
3. calculer le budget de base ;
4. appliquer le multiplicateur de catégorie ;
5. calculer les multiplicateurs de stats par vague ;
6. appliquer les multiplicateurs de difficulté ;
7. déterminer le `maxMobTypes` selon le stage (early / mid / late game).

### Formules recommandées

#### Budget
```java
budget = baseBudget + (waveIndex * linearGrowth) + (waveIndex * waveIndex * exponentialGrowth)
```

#### Health
```java
healthMultiplier = (baseHealthMultiplier + (waveIndex * healthPerWave))
                   * categoryHealthMultiplier
                   * difficultyHealthMultiplier
```

#### Damage
```java
damageMultiplier = (baseDamageMultiplier + (waveIndex * damagePerWave))
                   * categoryDamageMultiplier
                   * difficultyDamageMultiplier
```

#### Speed
```java
speedMultiplier = (baseSpeedMultiplier + (waveIndex * speedPerWave))
                  * categorySpeedMultiplier
                  * difficultySpeedMultiplier
```

#### Reward
```java
rewardMultiplier = difficultyRewardMultiplier
```

---

## 8.4 `WaveComposer`

### Responsabilité
Construire la composition finale d'une vague.

### Signature recommandée

```java
WaveComposition compose(
    int waveIndex,
    WaveDifficultyProfile profile,
    WaveCategoryTemplate template,
    MobPool pool,
    WaveOverride override
);
```

### Étapes de composition
1. partir du template ;
2. appliquer l'override si présent ;
3. filtrer les `MobEntry` :
   - `enabled == true`
   - `minWave <= waveIndex`
   - `maxWave <= 0 || waveIndex <= maxWave`
4. vérifier qu'il reste des mobs valides ;
5. limiter le nombre de types selon `profile.maxMobTypes` ;
6. composer selon `CompositionMode` ;
7. retourner la composition finale.

### Modes

#### FIXED
L'implémentation peut utiliser :
- un pool avec quantités déterministes ;
- ou plus tard une structure dédiée.

Pour la V1, si `FIXED` n'est pas encore supporté complètement, il est acceptable de le simplifier.

#### WEIGHTED
- tirage selon `weight` ;
- ajout de groupes de mobs tant que le budget n'est pas trop dépassé.

#### RANDOM
- tirage uniforme dans la liste filtrée.

---

## 8.5 `MobRegistryService`

### Responsabilités
- fournir la liste globale des mobs disponibles ;
- agréger les sources ;
- alimenter la `MobSelectionListGUI`.

### Signature recommandée

```java
List<MobOption> getAllAvailableMobs();
List<MobOption> getAvailableMobs(MobSource source);
```

---

## 8.6 `MobSpawnService`

### Responsabilité
Spawner réellement les mobs sélectionnés.

### Signature recommandée

```java
void spawnWaveComposition(
    Arena arena,
    WaveComposition composition,
    WaveDifficultyProfile profile
);
```

### Étapes
1. parcourir chaque `WaveMobSelection` ;
2. trouver le `MobProvider` adapté ;
3. spawn le bon nombre d'entités ;
4. appliquer les multiplicateurs ;
5. enregistrer les métadonnées utiles.

---

## 8.7 `GameplayValidationService`

### Responsabilité
Valider les settings et éviter les configs cassées.

### Méthodes recommandées

```java
List<String> validate(ArenaGameplaySettings settings);
List<String> validatePool(MobPool pool);
List<String> validateTemplate(WaveCategoryTemplate template);
List<String> validateOverride(WaveOverride override);
```

---

# 9. Providers / abstraction des mobs

---

## 9.1 Principe

Le système ne doit jamais manipuler directement des types de mobs runtime partout dans le code.

Il faut une abstraction unique.

---

## 9.2 `MobProvider`

```java
interface MobProvider {
    MobSource getSource();
    List<MobOption> getAvailableMobs();
    boolean canSpawn(MobEntry entry);
    SpawnResult spawn(Location location, MobEntry entry, WaveSpawnContext context);
}
```

---

## 9.3 `MobOption`

```java
class MobOption {
    String id;
    MobSource source;
    String displayName;
    String description;
    Material icon;
}
```

### Rôle
Objet GUI / sélection.  
Il ne représente pas la config persistée, mais un mob selectable par l'admin.

---

## 9.4 `WaveSpawnContext`

```java
class WaveSpawnContext {
    String arenaId;
    int waveIndex;
    WaveCategory category;
    double healthMultiplier;
    double damageMultiplier;
    double speedMultiplier;
}
```

### Rôle
Transmettre les infos nécessaires au provider au moment du spawn.

---

## 9.5 `SpawnResult`

```java
class SpawnResult {
    boolean success;
    UUID entityId;
    String errorMessage;
}
```

---

## 9.6 Implémentations recommandées

### `VanillaMobProvider`
Responsable des mobs vanilla.
Utilise `EntityType`.

### `InternalMobProvider`
Pour des mobs custom internes si le plugin en a.

### `MythicMobProvider`
Préparé pour plus tard.
Ne doit être chargé que si MythicMobs est présent.

---

## 9.7 Règle très importante
Le moteur de vagues ne doit pas connaître :

- les détails de l'API MythicMobs ;
- la logique de spawn vanilla ;
- la logique des mobs internes.

Il appelle seulement le provider correspondant à `MobSource`.

---

# 10. Flow runtime exact d'une vague

Quand une partie veut lancer une vague, suivre cet ordre strict :

1. récupérer `ArenaGameplaySettings` pour l'arène ;
2. récupérer la difficulté choisie pour la partie ;
3. résoudre la catégorie de la vague ;
4. récupérer un éventuel `WaveOverride` ;
5. calculer le `WaveDifficultyProfile` ;
6. récupérer le `WaveCategoryTemplate` ;
7. si override de catégorie ou de pool, appliquer ces remplacements ;
8. récupérer le `MobPool` final ;
9. composer la vague via `WaveComposer` ;
10. spawn la composition via `MobSpawnService` ;
11. enregistrer les mobs spawnés ;
12. utiliser ces données pour suivre la vague.

---

# 11. Structure des GUI

---

## 11.1 `ArenaSetupGUI`

Ajouter un item :
- **Gameplay**

### Action
Ouvre `GameplayMainGUI`.

---

## 11.2 `GameplayMainGUI`

### Boutons recommandés
- `Difficulty`
- `Wave Scaling`
- `Wave Categories`
- `Mob Pools`
- `Wave Overrides`
- `Preview`
- `Back`

### Rôle
Point d'entrée global de tout le système.

---

## 11.3 `DifficultyGUI`

### Fonctions
- activer/désactiver les difficultés ;
- choisir la difficulté par défaut ;
- ouvrir un éditeur pour chaque `DifficultyProfile`.

### Pour chaque difficulté
Afficher :
- Health Multiplier
- Damage Multiplier
- Speed Multiplier
- Reward Multiplier

### Interactions recommandées
- clic gauche = augmenter
- clic droit = diminuer
- shift + clic = variation plus grande
- touche de chat / anvil input si le système le permet

---

## 11.4 `WaveScalingGUI`

### Fonctions
Modifier :
- baseBudget
- linearGrowth
- exponentialGrowth
- baseHealthMultiplier
- baseDamageMultiplier
- baseSpeedMultiplier
- healthPerWave
- damagePerWave
- speedPerWave
- bossInterval
- eliteInterval
- specialInterval
- earlyGameMaxMobTypes
- midGameMaxMobTypes
- lateGameMaxMobTypes
- midGameStartWave
- lateGameStartWave

### Bonus utile
Un bouton `Preview wave X`.

---

## 11.5 `WaveCategoriesGUI`

### Affiche
- NORMAL
- ELITE
- BOSS
- SPECIAL

### Action
Clique sur une catégorie → ouvre `WaveCategoryEditorGUI`.

---

## 11.6 `WaveCategoryEditorGUI`

### Fonctions
Modifier :
- pool assigné
- mode de composition
- categoryBudgetMultiplier
- healthMultiplier
- damageMultiplier
- speedMultiplier
- maxMobTypes
- defaultMobCount
- enabled

---

## 11.7 `MobPoolsGUI`

### Fonctions
- lister les pools ;
- créer un pool ;
- renommer un pool ;
- supprimer un pool ;
- ouvrir un pool.

### Affichage conseillé
Pour chaque pool :
- nom
- catégorie par défaut
- nombre d'entrées
- état activé/désactivé

---

## 11.8 `MobPoolEditorGUI`

### Fonctions
- voir les mobs du pool ;
- ajouter un mob ;
- retirer un mob ;
- éditer une entrée.

### Affichage d'une entrée
- nom
- source
- threat
- weight
- min/max amount
- min/max wave
- enabled/disabled

---

## 11.9 `MobEntryEditorGUI`

### Fonctions
Modifier :
- weight
- threat
- minAmount
- maxAmount
- minWave
- maxWave
- enabled

### Remarque
`id`, `source`, `displayName` sont en général choisis lors de l'ajout initial depuis le sélecteur global.

---

## 11.10 `MobSelectionListGUI`

### Rôle
Afficher tous les mobs disponibles pour ajout dans un pool.

### Doit permettre
- pagination ;
- recherche ;
- filtre par source :
  - Vanilla
  - Internal
  - MythicMobs
- ajout direct dans le pool courant.

### Important
Cette GUI doit récupérer ses données via `MobRegistryService`.

---

## 11.11 `WaveOverridesGUI`

### Fonctions
- lister les overrides ;
- créer un override ;
- supprimer un override ;
- éditer :
  - `waveIndex`
  - `forcedCategory`
  - `forcedPoolId`
  - `forcedCompositionMode`
  - multiplicateurs override

### Remarque
Ce menu doit être clairement présenté comme **avancé / optionnel**.

---

## 11.12 `PreviewGUI` ou système de preview

### Très recommandé
Permet d'afficher pour une vague donnée :
- catégorie résolue ;
- budget calculé ;
- multipliers finaux ;
- pool utilisé ;
- mobs potentiellement sélectionnables ;
- composition générée à blanc si souhaité.

### Intérêt
Extrêmement utile pour debug et équilibrage.

---

# 12. Règles métier de génération

---

## 12.1 Détermination de la catégorie

Règle par défaut :
- `SPECIAL` si divisible par `specialInterval`
- sinon `BOSS` si divisible par `bossInterval`
- sinon `ELITE` si divisible par `eliteInterval`
- sinon `NORMAL`

### Important
La priorité doit être stable et documentée.

---

## 12.2 Calcul de budget

### Formule recommandée
```java
double budget = baseBudget
              + (waveIndex * linearGrowth)
              + (waveIndex * waveIndex * exponentialGrowth);
```

Puis :
```java
budget *= categoryTemplate.getCategoryBudgetMultiplier();
```

---

## 12.3 Calcul de stats

### Base scaling
```java
double baseHealth = scaling.baseHealthMultiplier + (waveIndex * scaling.healthPerWave);
double baseDamage = scaling.baseDamageMultiplier + (waveIndex * scaling.damagePerWave);
double baseSpeed  = scaling.baseSpeedMultiplier  + (waveIndex * scaling.speedPerWave);
```

### Catégorie
```java
baseHealth *= categoryTemplate.getHealthMultiplier();
baseDamage *= categoryTemplate.getDamageMultiplier();
baseSpeed  *= categoryTemplate.getSpeedMultiplier();
```

### Difficulté sélectionnée
```java
finalHealth = baseHealth * difficultyProfile.getHealthMultiplier();
finalDamage = baseDamage * difficultyProfile.getDamageMultiplier();
finalSpeed  = baseSpeed  * difficultyProfile.getSpeedMultiplier();
finalReward = difficultyProfile.getRewardMultiplier();
```

---

## 12.4 Max mob types

### Exemple de logique
```java
if (waveIndex >= lateGameStartWave) {
    maxMobTypes = lateGameMaxMobTypes;
} else if (waveIndex >= midGameStartWave) {
    maxMobTypes = midGameMaxMobTypes;
} else {
    maxMobTypes = earlyGameMaxMobTypes;
}
```

Ensuite :
```java
maxMobTypes = Math.min(maxMobTypes, categoryTemplate.getMaxMobTypes());
```

---

## 12.5 Filtrage des mobs valides

Un `MobEntry` est valide pour une vague si :

- `enabled == true`
- `minWave <= waveIndex`
- `maxWave <= 0 || waveIndex <= maxWave`

### Important
Si aucun mob valide n'est trouvé :
- il faut logguer l'erreur ;
- empêcher le crash ;
- utiliser éventuellement un fallback si prévu ;
- ou annuler proprement la génération.

---

## 12.6 Composition WEIGHTED

### Logique recommandée
1. filtrer les mobs valides ;
2. sélectionner au maximum `maxMobTypes` types différents ;
3. tirer selon `weight` ;
4. pour chaque mob sélectionné, choisir une quantité dans `[minAmount, maxAmount]` ;
5. additionner `amount * threat` ;
6. arrêter quand le budget est suffisamment rempli.

### Remarque
Il est acceptable de légèrement dépasser le budget si cela simplifie la logique, tant que c'est contrôlé.

---

## 12.7 Composition RANDOM

Même logique, mais sans prendre en compte les poids.

---

## 12.8 Composition FIXED

Pour la V1, deux approches possibles :

### Option A — support simplifié
`FIXED` utilise les entrées du pool dans l'ordre jusqu'à remplir la vague.

### Option B — support ultérieur
Créer plus tard une structure dédiée `FixedWaveCompositionTemplate`.

### Recommandation V1
Si l'implémentation doit rester simple, garder `FIXED` minimal au début.

---

# 13. Validations nécessaires

L'IA doit implémenter un système de validation sérieux.

---

## 13.1 Validation globale
Vérifier :
- présence d'au moins une difficulté activée ;
- présence d'un defaultDifficulty valide ;
- présence d'un template pour chaque catégorie requise ;
- présence des pools référencés ;
- cohérence des overrides.

---

## 13.2 Validation des `DifficultyProfile`
- multipliers > 0

---

## 13.3 Validation de `WaveScalingSettings`
- baseBudget > 0
- linearGrowth >= 0
- exponentialGrowth >= 0
- bossInterval >= 0
- eliteInterval >= 0
- specialInterval >= 0
- midGameStartWave >= 1
- lateGameStartWave >= midGameStartWave

---

## 13.4 Validation de `WaveCategoryTemplate`
- `mobPoolId` non nul si catégorie active
- `categoryBudgetMultiplier > 0`
- `healthMultiplier > 0`
- `damageMultiplier > 0`
- `speedMultiplier > 0`
- `maxMobTypes >= 1`

---

## 13.5 Validation de `MobPool`
- `id` non vide
- `displayName` non vide
- `entries` non null
- si pool actif, au moins une entrée valide recommandée

---

## 13.6 Validation de `MobEntry`
- `id` non vide
- `source` non nul
- `weight > 0`
- `threat > 0`
- `minAmount >= 1`
- `maxAmount >= minAmount`
- `minWave >= 1`
- `maxWave == 0 || maxWave >= minWave`

---

## 13.7 Validation de `WaveOverride`
- `waveIndex >= 1`
- `forcedPoolId` valide si renseigné
- les multiplicateurs override > 0 si renseignés

---

# 14. Persistance

---

## 14.1 Principe
Tout est édité in-game, mais doit être sauvegardé proprement en config.

Les objets runtime ou API externes ne doivent jamais être stockés directement.

---

## 14.2 Structure logique recommandée

Exemple de hiérarchie :

```yaml
arenas:
  arena_1:
    gameplay:
      general:
      difficulties:
      scaling:
      categories:
      pools:
      overrides:
```

---

## 14.3 Mapper / repository à créer

### `GameplayConfigMapper`
Responsable de :
- convertir config -> objets ;
- convertir objets -> config.

### `GameplayConfigRepository`
Responsable de :
- lire/écrire le fichier ;
- gérer la persistance disque.

### `GameplayMigrationService`
Responsable de :
- convertir l'ancien système zombie-only vers le nouveau format si nécessaire.

---

# 15. Compatibilité avec l'ancien système

L'IA doit comparer le système existant avec cette architecture et réutiliser autant que possible.

---

## 15.1 Ce qui peut être conservé
- système actuel de spawn de vague ;
- système actuel de tracking des mobs vivants ;
- système actuel de rewards ;
- système actuel de sauvegarde d'arène ;
- GUI de base si elles sont propres ;
- anciens multiplicateurs de difficulté si déjà présents.

---

## 15.2 Ce qui doit être refactoré
- toute logique trop hardcodée sur les zombies ;
- toute logique `if wave == X` dispersée ;
- toute logique de composition codée en dur ;
- toute dépendance directe future à MythicMobs dans les classes centrales.

---

## 15.3 Stratégie de migration recommandée
Créer par défaut :
- un pool `normal_default` avec zombies vanilla ;
- un template `NORMAL` utilisant ce pool ;
- des templates `ELITE`, `BOSS`, `SPECIAL` minimalistes ;
- un scaling proche du comportement actuel.

Ainsi, l'ancien gameplay reste similaire après migration.

---

# 16. Tracking des mobs spawnés

Le système doit prévoir un tracking propre.

---

## 16.1 Pourquoi
Pour :
- savoir quand une vague est terminée ;
- identifier à quelle vague appartient une entité ;
- appliquer les récompenses ;
- debug ;
- stats futures.

---

## 16.2 Métadonnées recommandées
Chaque mob spawné devrait être associé à :
- arenaId
- waveIndex
- waveCategory
- mobEntryId
- mobSource

Selon l'architecture existante, cela peut être fait avec :
- metadata Bukkit ;
- PersistentDataContainer ;
- registre mémoire interne.

---

# 17. Pseudo-code principal

---

## 17.1 Génération d'une vague

```java
public WaveComposition buildWave(Arena arena, int waveIndex, GameDifficulty selectedDifficulty) {
    ArenaGameplaySettings settings = arenaGameplayService.getSettings(arena.getId());

    WaveOverride override = settings.getWaveOverrides().get(waveIndex);

    WaveCategory category = waveCategoryResolver.resolveCategory(waveIndex, settings);
    if (override != null && override.isEnabled() && override.getForcedCategory() != null) {
        category = override.getForcedCategory();
    }

    WaveDifficultyProfile profile = waveDifficultyCalculator.calculate(
        waveIndex,
        selectedDifficulty,
        category,
        settings
    );

    WaveCategoryTemplate template = settings.getCategoryTemplates().get(category);
    if (template == null || !template.isEnabled()) {
        throw new IllegalStateException("Missing or disabled template for category " + category);
    }

    String poolId = template.getMobPoolId();
    if (override != null && override.isEnabled() && override.getForcedPoolId() != null) {
        poolId = override.getForcedPoolId();
    }

    MobPool pool = settings.getMobPools().get(poolId);
    if (pool == null || !pool.isEnabled()) {
        throw new IllegalStateException("Missing or disabled mob pool " + poolId);
    }

    return waveComposer.compose(waveIndex, profile, template, pool, override);
}
```

---

## 17.2 Spawn d'une vague

```java
public void spawnWave(Arena arena, int waveIndex, GameDifficulty selectedDifficulty) {
    WaveComposition composition = buildWave(arena, waveIndex, selectedDifficulty);

    ArenaGameplaySettings settings = arenaGameplayService.getSettings(arena.getId());
    WaveDifficultyProfile profile = waveDifficultyCalculator.calculate(
        waveIndex,
        selectedDifficulty,
        composition.getCategory(),
        settings
    );

    mobSpawnService.spawnWaveComposition(arena, composition, profile);
}
```

---

# 18. Structure de packages recommandée

L'IA peut adapter selon le plugin existant, mais doit tendre vers quelque chose de propre de ce style :

```text
fr.xxx.villagedefense.gameplay
 ├── model
 │    ├── ArenaGameplaySettings
 │    ├── GameplayGeneralSettings
 │    ├── DifficultySettings
 │    ├── DifficultyProfile
 │    ├── GameDifficulty
 │    ├── WaveScalingSettings
 │    ├── WaveCategory
 │    ├── CompositionMode
 │    ├── WaveCategoryTemplate
 │    ├── WaveDifficultyProfile
 │    ├── WaveOverride
 │    ├── WaveComposition
 │    ├── WaveMobSelection
 │    ├── MobPool
 │    ├── MobEntry
 │    └── MobSource
 │
 ├── service
 │    ├── ArenaGameplayService
 │    ├── WaveCategoryResolver
 │    ├── WaveDifficultyCalculator
 │    ├── WaveComposer
 │    ├── MobRegistryService
 │    ├── MobSpawnService
 │    └── GameplayValidationService
 │
 ├── provider
 │    ├── MobProvider
 │    ├── MobOption
 │    ├── SpawnResult
 │    ├── WaveSpawnContext
 │    ├── VanillaMobProvider
 │    ├── InternalMobProvider
 │    └── MythicMobProvider
 │
 ├── gui
 │    ├── GameplayMainGUI
 │    ├── DifficultyGUI
 │    ├── DifficultyProfileEditorGUI
 │    ├── WaveScalingGUI
 │    ├── WaveCategoriesGUI
 │    ├── WaveCategoryEditorGUI
 │    ├── MobPoolsGUI
 │    ├── MobPoolEditorGUI
 │    ├── MobEntryEditorGUI
 │    ├── MobSelectionListGUI
 │    ├── WaveOverridesGUI
 │    └── GameplayPreviewGUI
 │
 ├── persistence
 │    ├── GameplayConfigMapper
 │    ├── GameplayConfigRepository
 │    └── GameplayMigrationService
 │
 └── hook
      └── MythicMobsHook
```

---

# 19. Plan d'implémentation détaillé

L'IA doit implémenter dans cet ordre pour limiter les erreurs.

---

## Phase 1 — Base de données et settings
Implémenter :
- `ArenaGameplaySettings`
- `GameplayGeneralSettings`
- `DifficultySettings`
- `DifficultyProfile`
- `WaveScalingSettings`
- `WaveCategory`
- `CompositionMode`
- `WaveCategoryTemplate`
- `MobPool`
- `MobEntry`
- `WaveOverride`

Puis :
- mapper config
- repository
- service de chargement/sauvegarde

---

## Phase 2 — GUI principale
Implémenter :
- ajout de l'item `Gameplay` dans `ArenaSetupGUI`
- `GameplayMainGUI`
- `DifficultyGUI`
- `WaveScalingGUI`
- `WaveCategoriesGUI`
- `WaveCategoryEditorGUI`

---

## Phase 3 — Pools de mobs
Implémenter :
- `MobPoolsGUI`
- `MobPoolEditorGUI`
- `MobEntryEditorGUI`
- `MobSelectionListGUI`
- `MobRegistryService`
- `VanillaMobProvider`

---

## Phase 4 — Moteur de vague
Implémenter :
- `WaveCategoryResolver`
- `WaveDifficultyCalculator`
- `WaveDifficultyProfile`
- `WaveMobSelection`
- `WaveComposition`
- `WaveComposer`
- intégration au lancement des vagues

---

## Phase 5 — Spawn unifié
Implémenter :
- `MobSpawnService`
- `WaveSpawnContext`
- `SpawnResult`
- tracking des mobs spawnés

---

## Phase 6 — Overrides
Implémenter :
- `WaveOverridesGUI`
- support runtime des overrides

---

## Phase 7 — Validation + preview
Implémenter :
- `GameplayValidationService`
- écran ou fonction de preview
- logs propres
- gestion d'erreurs

---

## Phase 8 — MythicMobs
Préparer :
- `MythicMobsHook`
- `MythicMobProvider`
- chargement conditionnel si plugin présent
- ajout de la source dans `MobSelectionListGUI`

---

# 20. Règles de qualité d'implémentation

L'IA doit respecter ces règles :

1. **Ne pas casser le système existant**
2. **Réutiliser les classes existantes si elles sont cohérentes**
3. **Ne pas hardcoder les zombies**
4. **Ne pas mélanger GUI, logique métier et persistance**
5. **Ne pas rendre MythicMobs obligatoire**
6. **Toujours valider les configs**
7. **Toujours prévoir des valeurs par défaut**
8. **Toujours garder le système fonctionnel même sans overrides**
9. **Toujours garder les pools éditables in-game**
10. **Toujours centraliser le spawn via `MobSpawnService`**

---

# 21. Résultat attendu final

À la fin de l'implémentation, le plugin doit permettre :

- de configurer un système Gameplay complet depuis `ArenaSetupGUI` ;
- de gérer la difficulté par arène ;
- de scaler automatiquement les vagues ;
- de définir les catégories de vagues ;
- de définir les pools de mobs ;
- de choisir quels mobs sont autorisés ;
- de préparer les MythicMobs ;
- de créer des overrides ciblés ;
- de générer les vagues automatiquement sans configuration manuelle vague par vague.

---

# 22. Instruction finale pour l'IA qui code

Implémente ce système en **comparant avec l'existant** et en **réutilisant au maximum ce qui existe déjà** dans le plugin, mais fais en sorte que l'architecture finale respecte cette spécification :

- menu `Gameplay` dans `ArenaSetupGUI`
- settings par arène
- difficulté V1 = stats + rewards
- génération des vagues basée sur catégories + templates + pools + scaling
- overrides seulement pour exceptions
- abstraction `MobProvider`
- `VanillaMobProvider` en premier
- compatibilité MythicMobs préparée proprement
- GUI claires, rangées, maintenables
- persistance propre
- validations complètes
- flow runtime stable

Ne construis pas un système vague par vague.  
Construis un moteur modulaire, extensible, configurable in-game, et compatible avec l'architecture du plugin existant.

---

Fin du document.
