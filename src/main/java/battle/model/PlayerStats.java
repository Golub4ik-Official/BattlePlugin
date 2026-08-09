package battle.model;

import java.util.UUID;

/**
 * Личная статистика игрока за текущую битву.
 */
public class PlayerStats {

    private final UUID uuid;
    private final String name;
    private int kills;
    private int deaths;
    private int teamkills;
    private int currentStreak;
    private int bestStreak;
    private int currentDeathStreak;
    private int worstDeathStreak;
    private int pointsCaptured;
    private int pointsContested;
    private int damageDealt;
    private int damageTaken;

    public PlayerStats(UUID uuid, String name) {
        this.uuid = uuid;
        this.name = name;
    }

    public void addKill() {
        kills++;
        currentStreak++;
        if (currentStreak > bestStreak) {
            bestStreak = currentStreak;
        }
        currentDeathStreak = 0;
    }

    public void addDeath() {
        deaths++;
        currentStreak = 0;
        currentDeathStreak++;
        if (currentDeathStreak > worstDeathStreak) {
            worstDeathStreak = currentDeathStreak;
        }
    }

    public void addTeamkill() {
        teamkills++;
    }

    public void addCapture() {
        pointsCaptured++;
    }

    public void addContest() {
        pointsContested++;
    }

    public void addDamageDealt(double amount) {
        damageDealt += (int) Math.round(amount);
    }

    public void addDamageTaken(double amount) {
        damageTaken += (int) Math.round(amount);
    }

    public UUID uuid() {
        return uuid;
    }

    public String name() {
        return name;
    }

    public int kills() {
        return kills;
    }

    public int deaths() {
        return deaths;
    }

    public int teamkills() {
        return teamkills;
    }

    public int bestStreak() {
        return bestStreak;
    }

    public int pointsCaptured() {
        return pointsCaptured;
    }

    public int pointsContested() {
        return pointsContested;
    }

    public int damageDealt() {
        return damageDealt;
    }

    public int damageTaken() {
        return damageTaken;
    }

    public int worstDeathStreak() {
        return worstDeathStreak;
    }

    /** Отношение убийств к смертям (0 смертей = K/D = kills). */
    public String kd() {
        double kd = deaths == 0 ? kills : (double) kills / deaths;
        return String.format("%.2f", kd);
    }
}