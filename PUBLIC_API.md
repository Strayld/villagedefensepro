# Public API

`VillageDefensePro` expose une API publique Bukkit pour les plugins de quetes, battle pass et missions.

## Recuperer le service

```java
import fr.obsidianlabs.plugins.villagedefense.api.VillageDefenseApi;
import org.bukkit.Bukkit;

VillageDefenseApi api = Bukkit.getServicesManager().load(VillageDefenseApi.class);
if (api == null) {
    return;
}
```

## Lire les donnees

```java
var player = api.getPlayerSnapshot(playerUuid);
var arena = api.getArenaSnapshot("village_01");
var achievements = api.getAchievementDefinitions();
```

## Donner des recompenses

```java
api.grantCoins(playerUuid, 250);
api.grantXp(playerUuid, 500);
api.unlockAchievement(playerUuid, "first-blood");
```

## Ecouter la progression

```java
import fr.obsidianlabs.plugins.villagedefense.api.event.VillageDefenseProgressEvent;
import fr.obsidianlabs.plugins.villagedefense.api.VillageDefenseProgressType;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;

public final class QuestListener implements Listener {

    @EventHandler
    public void onVillageDefenseProgress(VillageDefenseProgressEvent event) {
        if (event.getProgressType() == VillageDefenseProgressType.MOB_KILL
                && event.getDetail().equals("zombie")) {
            // incrementer une mission externe
        }
    }
}
```

## Evenements exposes

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

## Notes

- L'API s'obtient via `Bukkit.getServicesManager().load(VillageDefenseApi.class)`.
- Les recompenses `grantCoins`, `grantXp` et `unlockAchievement` ciblent les joueurs charges/en ligne.
- Les identifiants exposes dans `detail` et dans les snapshots sont en `kebab-case`.

## PlaceholderAPI

`VillageDefensePro` expose aussi une expansion PlaceholderAPI native avec l'identifiant `villagedefense`.

Exemples:

```text
%villagedefense_player_coins%
%villagedefense_player_level%
%villagedefense_arena_wave%
%villagedefense_arena_village_01_state%
```

La liste complete des placeholders publics est documentee dans `FEATURES.txt`.
