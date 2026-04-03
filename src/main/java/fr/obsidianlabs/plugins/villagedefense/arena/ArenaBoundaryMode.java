package fr.obsidianlabs.plugins.villagedefense.arena;

public enum ArenaBoundaryMode {

    /** Annule le mouvement et repousse le joueur — carte classique avec murs. */
    PUSH_BACK,

    /** Téléporte le joueur au centre du village — carte spéciale avec vide. */
    TP_CENTER;

    public ArenaBoundaryMode next() {
        return this == PUSH_BACK ? TP_CENTER : PUSH_BACK;
    }
}
