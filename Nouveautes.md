# Nouveautes v1.1.0

Date de release: 2026-03-27
Plugin: VillageDefensePro

## Nouvelles fonctionnalites

- Nouveau Kits avec des effets uniques
- Le GUI admin permet maintenant de configurer le niveau requis de chaque kit.
- Un sous-GUI dedie permet maintenant de configurer `BungeeCord` directement depuis l'administration.
- Une API publique Bukkit est maintenant exposee pour les plugins de battle pass, quetes, missions et integrations externes. (Wiki a venir)
- Le setup d'arene propose maintenant un vrai mode de disponibilite `Active / Test / Desactivee`.
- La permission `villagedefense.test` permet de reserver les arenes de test aux joueurs autorises.
- Chaque arene peut maintenant afficher un leaderboard flottant global en hologramme.
- Une vraie expansion PlaceholderAPI `%villagedefense_*%` est maintenant disponible.
- Le plugin est compatible avec Spigot de la 1.17.1 jusqu'au dernière version

## Ameliorations

- Les descriptions de kits ont ete retravaillees avec un lore multi-lignes plus lisible, une ligne par capacite et du formatage de chat.
- Les niveaux requis des kits sont maintenant lus depuis la configuration au lieu d'etre figes dans le code.
- Le menu principal d'administration donne un acces direct aux reglages proxy `BungeeCord`.
- Les integrations externes n'ont plus besoin de lire les classes internes du plugin : snapshots, succes et progression runtime passent maintenant par une surface publique stable.
- Les panneaux et `/vd list` refletent maintenant correctement le statut des arenes, avec support du mode test.

## Corrections

- Correction du `TowerConfigGUI` pour les tours de la rangee basse (`Ice`, `Lightning`, `Cannon`).
- Blocage du GUI vanilla de trade pour les villageois proteges du plugin.
- Les power-ups au sol expirent maintenant apres `45 secondes`.
- Le power-up `Purge` affecte maintenant tous les mobs actifs sauf le boss.
- Les vagues suivent maintenant le nombre total de mobs prevus, deja spawnes et deja resolus, ce qui empeche `Purge` de cloturer une vague trop tot.
- Les messages de niveau, kits, atouts et succes debloques passent maintenant par le systeme de messages du plugin.
- Le mode spectateur custom est corrige : fly, invisibilite, item de sortie et blocage d'interactions refonctionnent correctement.
- Le passage en observateur apres une mort suit maintenant le vrai cycle de respawn Bukkit.

## API Publique

- Service Bukkit: `VillageDefenseApi`
- Event public: `VillageDefenseProgressEvent`
- Snapshots exposes: joueur, arene, definitions de succes
- Actions exposees: donner des coins, donner de l'XP, debloquer un succes

## PlaceholderAPI

Identifiant d'expansion:

- `villagedefense`

Exemples de placeholders disponibles:

- `%villagedefense_player_coins%`
- `%villagedefense_player_level%`
- `%villagedefense_player_selected_kit%`
- `%villagedefense_arena_wave%`
- `%villagedefense_arena_remaining_mobs%`
- `%villagedefense_arenas_running%`
- `%villagedefense_arena_village_01_state%`
