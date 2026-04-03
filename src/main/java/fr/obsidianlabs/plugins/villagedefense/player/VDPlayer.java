package fr.obsidianlabs.plugins.villagedefense.player;

import fr.obsidianlabs.plugins.villagedefense.VillageDefensePlugin;
import fr.obsidianlabs.plugins.villagedefense.kit.KitType;

import org.bukkit.Location;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

public class VDPlayer {

    private final UUID uuid;
    private String name;

    // Données persistantes
    private long coins;
    private int level;
    private long totalXp;
    private int totalKills;
    private int totalWavesCompleted;
    private int totalGamesPlayed;
    private long totalCoinsEarned;
    private final Set<KitType> unlockedKits;
    private final Set<String> completedAchievements;

    // Données de session (non persistantes)
    private int sessionKills;
    private long sessionCoins;
    private KitType selectedKit;
    private String currentArenaId;
    private Location returnLocation;

    public VDPlayer(UUID uuid, String name) {
        this.uuid = uuid;
        this.name = name;
        this.coins = 0;
        this.level = 1;
        this.totalXp = 0;
        this.totalKills = 0;
        this.totalWavesCompleted = 0;
        this.totalGamesPlayed = 0;
        this.totalCoinsEarned = 0;
        this.unlockedKits = new HashSet<>();
        this.unlockedKits.add(KitType.getDefault());
        this.selectedKit = KitType.getDefault();
        this.completedAchievements = new HashSet<>();
    }

    // --- Coins ---

    public long getCoins() { return coins; }
    public void setCoins(long coins) {
        this.coins = Math.max(0, coins);
        syncVaultBalance();
    }
    public void addCoins(long amount) {
        this.coins += amount;
        this.totalCoinsEarned += amount;
        this.sessionCoins += amount;
        syncVaultBalance();
    }
    public boolean removeCoins(long amount) {
        if (coins < amount) return false;
        coins -= amount;
        syncVaultBalance();
        return true;
    }

    // --- Level / XP ---

    public int getLevel() { return level; }
    public void setLevel(int level) { this.level = level; }
    public long getTotalXp() { return totalXp; }
    public void addXp(long xp) {
        this.totalXp += xp;
        // Formule quadratique : level = 1 + floor(sqrt(totalXp / 100))
        // Niveaux : 1→0 XP, 2→100, 3→400, 4→900, 5→1600, 6→2500, 10→8100
        this.level = 1 + (int) Math.sqrt(totalXp / 100.0);
    }

    // --- Stats ---

    public int getTotalKills() { return totalKills; }
    public void addKill() { this.totalKills++; this.sessionKills++; }
    public int getTotalWavesCompleted() { return totalWavesCompleted; }
    public void addWaveCompleted() { this.totalWavesCompleted++; }
    public int getTotalGamesPlayed() { return totalGamesPlayed; }
    public void addGame() { this.totalGamesPlayed++; }
    public long getTotalCoinsEarned() { return totalCoinsEarned; }

    // --- Achievements ---

    public Set<String> getCompletedAchievements() { return Collections.unmodifiableSet(completedAchievements); }
    public boolean hasAchievementId(String id) { return completedAchievements.contains(id.toUpperCase()); }
    public void unlockAchievementId(String id) { completedAchievements.add(id.toUpperCase()); }

    // --- Kits ---

    public Set<KitType> getUnlockedKits() { return unlockedKits; }
    public boolean hasKit(KitType kit) { return unlockedKits.contains(kit); }
    public void unlockKit(KitType kit) { unlockedKits.add(kit); }

    public KitType getSelectedKit() { return selectedKit; }
    public void setSelectedKit(KitType kit) { this.selectedKit = kit == null ? KitType.getDefault() : kit; }

    // --- Session ---

    public int getSessionKills() { return sessionKills; }
    public long getSessionCoins() { return sessionCoins; }
    public void resetSession() { this.sessionKills = 0; this.sessionCoins = 0; }

    public String getCurrentArenaId() { return currentArenaId; }
    public void setCurrentArenaId(String id) { this.currentArenaId = id; }
    public boolean isInArena() { return currentArenaId != null; }

    public Location getReturnLocation() { return returnLocation; }
    public void setReturnLocation(Location loc) { this.returnLocation = loc; }

    // --- Identité ---

    public UUID getUuid() { return uuid; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    // --- Sérialisation des kits pour BDD ---

    public String serializeKits() {
        StringBuilder sb = new StringBuilder();
        for (KitType kit : unlockedKits) {
            if (sb.length() > 0) sb.append(",");
            sb.append(kit.name());
        }
        return sb.toString();
    }

    public void deserializeKits(String s) {
        unlockedKits.clear();
        unlockedKits.add(KitType.getDefault());
        if (s == null || s.isBlank()) return;
        for (String part : s.split(",")) {
            unlockedKits.add(KitType.fromSerialized(part));
        }
    }

    /**
     * Fixe le niveau ET l'XP total correspondant (usage : commande admin).
     * XP minimale pour le niveau L = (L-1)² × 100.
     */
    public void setLevelWithXp(int level) {
        this.level   = Math.max(1, level);
        this.totalXp = (long) Math.pow(this.level - 1, 2) * 100;
    }

    // --- Sérialisation des succès pour BDD ---

    public String serializeAchievements() {
        return String.join(",", completedAchievements);
    }

    public void deserializeAchievements(String s) {
        completedAchievements.clear();
        if (s == null || s.isBlank()) return;
        for (String part : s.split(",")) {
            String trimmed = part.trim();
            if (!trimmed.isEmpty()) {
                completedAchievements.add(trimmed.toUpperCase());
            }
        }
    }

    // Setters pour le chargement depuis BDD
    public void setTotalKills(int v) { totalKills = v; }
    public void setTotalWavesCompleted(int v) { totalWavesCompleted = v; }
    public void setTotalGamesPlayed(int v) { totalGamesPlayed = v; }
    public void setTotalCoinsEarned(long v) { totalCoinsEarned = v; }
    public void setTotalXp(long v) { totalXp = v; }

    private void syncVaultBalance() {
        VillageDefensePlugin plugin = VillageDefensePlugin.getInstance();
        if (plugin != null && plugin.getHookManager() != null) {
            plugin.getHookManager().syncEconomyBalance(uuid, name, coins);
        }
    }
}
