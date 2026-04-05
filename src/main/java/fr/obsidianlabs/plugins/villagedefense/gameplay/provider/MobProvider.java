package fr.obsidianlabs.plugins.villagedefense.gameplay.provider;

import fr.obsidianlabs.plugins.villagedefense.gameplay.model.MobEntry;
import fr.obsidianlabs.plugins.villagedefense.gameplay.model.MobSource;
import org.bukkit.Location;

import java.util.List;

/**
 * Abstraction de la source des mobs.
 * Le moteur de vagues n'interagit jamais directement avec Bukkit/MythicMobs —
 * il délègue toujours à l'implémentation correspondante.
 */
public interface MobProvider {

    MobSource getSource();

    List<MobOption> getAvailableMobs();

    boolean canSpawn(MobEntry entry);

    SpawnResult spawn(Location location, MobEntry entry, WaveSpawnContext context);
}
