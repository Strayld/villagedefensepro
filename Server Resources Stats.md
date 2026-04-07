# Server Resources Stats — VillageDefensePro

> Estimation basée sur l'analyse statique du code source (version post-optimisation).
> Référence : 1 arène active, 5 joueurs, vague en cours (~30 mobs).

---

## CPU — Charge thread principal (budget = 50 ms/tick)

### Par état d'arène

#### WAITING / STARTING (lobby)

| Système | Fréquence | Coût estimé |
|---|---|---|
| ScoreboardManager (lobby) | 2×/s × 5 joueurs | ~100 µs/s |
| SignListener update | 2×/s | ~50 µs/s |
| PlayerMove bounds check | ~15 events/s | négligeable (O(1) cache) |
| **Total** | | **~0.2–0.4 ms/s** |

#### RUNNING — vague active (~30 mobs)

| Système | Fréquence | Coût estimé |
|---|---|---|
| **TowerManager** tick | 20×/s | 5 tours × 30 mobs = 150 `getEntity()` + calculs range/dégâts → **3–5 ms/s** |
| EntityTarget events (IA mob) | ~10–15/s | `findNearestVillager` × 10 UUID lookups → ~1–2 ms/s |
| GameManager retarget task | 0.5×/s | ~30 mobs `setTarget()` → ~0.3 ms/s |
| WaypointGuideManager tick | 4×/s | 2–3 téléports ArmorStand + iter mobs → ~0.3 ms/s |
| ScoreboardManager (jeu) | 2×/s × 5 joueurs | ~200 µs/s |
| KitAbilityManager passive | 1×/s | ~100 µs/s |
| EntityDamageByEntity | ~10 hits/s | metadata checks + kit adjust → ~100 µs/s |
| **Total** | | **~6–9 ms/s → 12–18% d'un cœur** |

#### BETWEEN_WAVES (inter-vague)

Pas de mobs actifs — TowerManager idle, aucun `EntityTarget`.

**~0.5–1 ms/s**

#### RESETTING (restauration schematic)

`RegionSnapshot.restore()` traite **2 000 blocs/tick** en batch.

- Arène 50×30×50 = 75 000 blocs → ~38 ticks ≈ 2 secondes de restauration
- Pendant ces 2 secondes : **+2–5 ms/tick** au-dessus de la baseline
- Retour à WAITING immédiatement après

---

## RAM — Objets en mémoire JVM

### Objets plugin (heap, présents dès le chargement)

| Composante | Taille |
|---|---|
| `Arena` — champs, locations, collections config | ~10–15 KB |
| `ArenaGameplaySettings` — pools mobs, templates, overrides | ~10–15 KB |
| `WaypointGuideManager` — maps + PathGuide par chemin | ~4 KB |
| `ScoreboardManager` — 5 Scoreboard Bukkit (Teams, Scores) | ~15–20 KB |
| 5 × `VDPlayer` — stats, kits, achievements | ~4 KB |
| `KitAbilityManager` — state maps (taunt, shields, cooldowns) | ~1–2 KB |
| `GameManager` — rejoin tokens, respawn tasks, observers | ~2 KB |
| `PowerUpManager` — effets actifs, items droppés | ~1 KB |
| État runtime Arena — activeMobs, villagerUUIDs, waveProgress | ~5 KB |
| **Sous-total objets plugin** | **~55–70 KB** |

### Entités Bukkit/Paper (heap, côté monde)

| Type | Quantité | Taille Paper/entité | Total |
|---|---|---|---|
| Mobs ennemis actifs | 30–50 | ~6–10 KB | ~210–350 KB |
| Villageois (village + shop + enchant) | 10–15 | ~10–15 KB | ~100–200 KB |
| ArmorStands (waypoints + leaderboard) | 5–8 | ~3 KB | ~15–25 KB |
| Projectiles en vol (flèches tours, TNT) | 0–20 | ~2 KB | ~0–40 KB |
| **Sous-total entités monde** | | | **~325–615 KB** |

### Pic mémoire pendant RESETTING

`RegionSnapshot` stocke l'intégralité des blocs de l'arène pendant la restauration :

- 75 000 blocs → `Map<BlockVector, BlockData>` ≈ **~80 bytes/entrée**
- **Pic : ~6 MB** pendant ~2 secondes, libéré dès la fin de la restauration

### Synthèse RAM

```
État normal (vague active)  :  ~380–685 KB
Pic pendant reset           :  ~6–7 MB  (2–3 secondes, puis libéré par le GC)
```

---

## Disque — Stockage et I/O

### Fichiers de configuration (par arène)

| Fichier | Contenu | Taille |
|---|---|---|
| `arenas/{id}.yml` | Locations, spawns, slots, chemins, prix | 5–20 KB |
| `arenas/{id}_gameplay.yml` | Pools mobs, templates, wave overrides, difficulté | 10–50 KB |
| `arenas/{id}_schematic_*.yml` | Blocs capturés par type/niveau de tour + pièges | 100–300 KB |
| **Total fichiers par arène** | | **~115–370 KB** |

### Base de données (H2 ou MySQL)

| Table | Contenu | Taille par partie |
|---|---|---|
| `vd_players` | 5 upserts à la fin de partie | ~1.1 KB |
| `vd_arena_stats` | ~30–100 `recordArenaKill` (async) | ~2–8 KB |
| `vd_attempts` | 5 insertions au join | ~400 bytes |
| Leaderboard query | SELECT 10 rows toutes les 10s | ~1–5 KB |

**Taille H2 après 100 parties** : ~500 KB – 2 MB (fichier partagé entre toutes les arènes)

### Fréquence des I/O disque

| Opération | Déclencheur | Fréquence |
|---|---|---|
| Lecture `arenas/*.yml` | Démarrage / `/vda reload` | Une seule fois |
| Écriture `arenas/*.yml` | Sauvegarde GUI admin | Rare (manuel) |
| Lecture schematic | Début de chaque partie | 1×/partie |
| `loadPlayer` + `loadAttemptEntries` | Connexion joueur | 5× en début de partie |
| `savePlayer` | Déconnexion / fin de partie | 5× en fin de partie |
| `recordArenaKill` (async depuis v1.1) | Chaque mort de mob | 30–100× par partie |
| Leaderboard query | Toutes les 10s | Continu, async |
| NetworkSync upsert (MySQL only) | Toutes les 30s | Continu, async |

---

## Synthèse globale

```
╔══════════════════╦══════════════════════════════════════╗
║ CPU thread main  ║ ~0.3 ms/tick en jeu (~6% d'un cœur) ║
║ RAM steady state ║ ~400–700 KB                          ║
║ RAM pic (reset)  ║ ~6–7 MB pendant ~2 secondes          ║
║ Disque (config)  ║ ~115–370 KB par arène                ║
║ I/O DB/partie    ║ ~3–10 KB écriture, 1–5 KB lecture    ║
╚══════════════════╩══════════════════════════════════════╝
```

### Points d'attention

| Priorité | Système | Risque |
|---|---|---|
| 🔴 CPU | `TowerManager` — O(tours × mobs) par tick | Dégradation linéaire avec le nombre de mobs |
| 🟠 RAM | `RegionSnapshot` — pic de ~6 MB pendant le reset | GC pause possible si heap JVM trop petit |
| 🟡 I/O | Leaderboard query toutes les 10s | Requête légère mais continu ; à désactiver si 0 joueur en ligne |

### Scalabilité multi-arènes

Tous les systèmes sont **indépendants par arène** — aucun état global partagé hormis la base de données.  
Le coût se multiplie **linéairement** avec le nombre d'arènes simultanées.

| Arènes actives | CPU estimé | RAM estimée |
|---|---|---|
| 1 | ~6–9 ms/s | ~400–700 KB |
| 5 | ~30–45 ms/s | ~2–3.5 MB |
| 10 | ~60–90 ms/s | ~4–7 MB |

> Au-delà de 5–6 arènes simultanées, le `TowerManager` devient le facteur limitant principal.  
> Optimisation recommandée : batch les lookups `getEntity()` en une seule itération par tick plutôt qu'une par tour.
