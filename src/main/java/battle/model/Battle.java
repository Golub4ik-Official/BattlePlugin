package battle.model;

import battle.BattleTeam;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Активная битва: название, таймер, очки команд, личная статистика и лента событий.
 */
public class Battle {

    /** Очки команды и счётчики (для статистики). */
    public static class TeamScore {
        public int score;
        public int kills;
        public int deaths;
        public int teamkills;
        public int pointsCaptured;
        public int holdAwards;
    }

    private final String name;
    private final long startTime;
    private final int totalSeconds;
    private int remainingSeconds;
    private final Set<BattleTeam> teams;
    private final EnumMap<BattleTeam, TeamScore> teamScores = new EnumMap<>(BattleTeam.class);
    private final Map<UUID, PlayerStats> playerStats = new HashMap<>();
    private final List<StatEvent> events = new ArrayList<>();

    public Battle(String name, int durationSeconds, Set<BattleTeam> teams) {
        this.name = name;
        this.startTime = System.currentTimeMillis();
        this.totalSeconds = durationSeconds;
        this.remainingSeconds = durationSeconds;
        this.teams = Set.copyOf(teams);
        for (BattleTeam team : teams) {
            teamScores.put(team, new TeamScore());
        }
    }

    public String name() {
        return name;
    }

    public long startTime() {
        return startTime;
    }

    public int totalSeconds() {
        return totalSeconds;
    }

    public int remainingSeconds() {
        return remainingSeconds;
    }

    public void tickDown() {
        remainingSeconds--;
    }

    public int elapsedSeconds() {
        return totalSeconds - remainingSeconds;
    }

    public boolean isOver() {
        return remainingSeconds <= 0;
    }

    public Set<BattleTeam> teams() {
        return teams;
    }

    public EnumMap<BattleTeam, TeamScore> teamScores() {
        return teamScores;
    }

    public TeamScore scoreOf(BattleTeam team) {
        return teamScores.computeIfAbsent(team, t -> new TeamScore());
    }

    public Map<UUID, PlayerStats> playerStats() {
        return playerStats;
    }

    public PlayerStats statsOf(UUID uuid, String name) {
        return playerStats.computeIfAbsent(uuid, u -> new PlayerStats(u, name));
    }

    public List<StatEvent> events() {
        return events;
    }

    public void addScore(BattleTeam team, int delta) {
        scoreOf(team).score += delta;
    }
}
