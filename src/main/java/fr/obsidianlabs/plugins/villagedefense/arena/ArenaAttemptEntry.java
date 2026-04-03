package fr.obsidianlabs.plugins.villagedefense.arena;

/**
 * Entrée en mémoire pour le suivi des tentatives d'un joueur sur une arène.
 * <ul>
 *   <li>{@code attemptsUsed} — nombre de parties lancées dans la fenêtre courante</li>
 *   <li>{@code windowStart} — timestamp Unix (secondes) du début de la fenêtre</li>
 * </ul>
 */
public class ArenaAttemptEntry {

    private int  attemptsUsed;
    private long windowStart;   // epoch seconds

    public ArenaAttemptEntry(int attemptsUsed, long windowStart) {
        this.attemptsUsed = attemptsUsed;
        this.windowStart  = windowStart;
    }

    public int  getAttemptsUsed()              { return attemptsUsed; }
    public long getWindowStart()               { return windowStart; }

    public void setAttemptsUsed(int n)         { this.attemptsUsed = n; }
    public void setWindowStart(long ts)        { this.windowStart  = ts; }

    /** Incrémente le compteur de tentatives dans la fenêtre courante. */
    public void increment() { this.attemptsUsed++; }
}
