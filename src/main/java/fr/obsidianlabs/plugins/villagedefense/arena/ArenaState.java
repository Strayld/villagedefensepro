package fr.obsidianlabs.plugins.villagedefense.arena;

public enum ArenaState {
    /** L'arène est en configuration (admin setup) */
    SETUP,
    /** En attente de joueurs */
    WAITING,
    /** Compte à rebours avant le démarrage */
    STARTING,
    /** Partie en cours */
    RUNNING,
    /** Entre deux vagues */
    BETWEEN_WAVES,
    /** Partie terminée (victoire ou défaite) */
    ENDING,
    /** Réinitialisation de la map en cours */
    RESETTING
}
