package fr.obsidianlabs.plugins.villagedefense.util;

/**
 * Constantes des nœuds de permission du plugin.
 *
 * Hiérarchie :
 *   villagedefense.admin  →  parent de tous les nœuds admin (accordé aux OP par défaut)
 *     ├── villagedefense.admin.arena      — créer / supprimer des arènes
 *     ├── villagedefense.admin.setup      — configurer une arène (GUI + baguette)
 *     ├── villagedefense.admin.schematic  — gérer les schematics de tours
 *     ├── villagedefense.admin.lang       — changer la langue du plugin
 *     ├── villagedefense.admin.database   — configurer la base de données
 *     ├── villagedefense.admin.prices     — éditer les prix du shop
 *     ├── villagedefense.admin.reload     — recharger la config + schematics
 *     ├── villagedefense.admin.game       — forcestart / forcestop / setwave / tp / info
 *     └── villagedefense.admin.player     — coins / setlevel
 *
 * Fonctionnalités joueur : aucune permission requise.
 */
public final class Perm {

    private Perm() {}

    // ── Admin parent ──────────────────────────────────────────────────────────
    public static final String ADMIN           = "villagedefense.admin";

    // ── Admin enfants ─────────────────────────────────────────────────────────
    public static final String ADMIN_ARENA     = "villagedefense.admin.arena";
    public static final String ADMIN_SETUP     = "villagedefense.admin.setup";
    public static final String ADMIN_SCHEMATIC = "villagedefense.admin.schematic";
    public static final String ADMIN_LANG      = "villagedefense.admin.lang";
    public static final String ADMIN_DATABASE  = "villagedefense.admin.database";
    public static final String ADMIN_PRICES    = "villagedefense.admin.prices";
    public static final String ADMIN_RELOAD    = "villagedefense.admin.reload";
    public static final String ADMIN_GAME      = "villagedefense.admin.game";
    public static final String ADMIN_PLAYER    = "villagedefense.admin.player";

    // ── Joueur ────────────────────────────────────────────────────────────────
    public static final String PLAY            = "villagedefense.play";
    public static final String TEST            = "villagedefense.test";
    public static final String BYPASS          = "villagedefense.bypass";
}
