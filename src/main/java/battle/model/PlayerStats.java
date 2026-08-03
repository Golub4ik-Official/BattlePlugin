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
    private int pointsCaptured;
    private int pointsContested;

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
    }

    public void addDeath() {
        deaths++;
        currentStreak = 0;
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

    /** Отношение убийств к смертям (0 смертей = полный K/D = kills). */
    public String kd(boolean prefix) {
        double kd = deaths == 0 ? kills : (double) kills / deaths;
        String s = String.format("%.2f", kd);
        return prefix ? "K/D: " + s : s;
    }
}