package battle.model;

import battle.BattleTeam;
import org.bukkit.configuration.ConfigurationSection;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Историческая запись завершённой битвы (сохраняется в battles.yml).
 */
public class BattleStats {

    public static class TeamSummary {
        public int score;
        public int kills;
        public int deaths;
        public int teamkills;
        public int pointsCaptured;
        public int holdAwards;
    }

    /** Индивидуальная статистика игрока за битву. */
    public static class PlayerSummary {
        public UUID uuid;
        public String name;
        public BattleTeam team;
        public int kills;
        public int deaths;
        public int teamkills;
        public int bestStreak;
        public int pointsCaptured;

        public String kd() {
            double kd = deaths == 0 ? kills : (double) kills / deaths;
            return String.format("%.2f", kd);
        }
    }

    public final int id;
    public final String name;
    public final long started;
    public final int durationSeconds;
    public final BattleTeam winner; // null — ничья
    public final Map<BattleTeam, TeamSummary> teams;
    public final List<PlayerSummary> players;
    public final List<StatEvent> events;

    public BattleStats(int id, String name, long started, int durationSeconds, BattleTeam winner,
                       Map<BattleTeam, TeamSummary> teams, List<PlayerSummary> players, List<StatEvent> events) {
        this.id = id;
        this.name = name;
        this.started = started;
        this.durationSeconds = durationSeconds;
        this.winner = winner;
        this.teams = teams;
        this.players = players;
        this.events = events;
    }

    /** Сохраняет запись в секцию конфигурации. */
    public void save(ConfigurationSection section) {
        section.set("id", id);
        section.set("name", name);
        section.set("started", started);
        section.set("duration", durationSeconds);
        section.set("winner", winner == null ? "draw" : winner.name());

        ConfigurationSection teamsSection = section.createSection("teams");
        for (Map.Entry<BattleTeam, TeamSummary> e : teams.entrySet()) {
            ConfigurationSection t = teamsSection.createSection(e.getKey().name());
            TeamSummary s = e.getValue();
            t.set("score", s.score);
            t.set("kills", s.kills);
            t.set("deaths", s.deaths);
            t.set("teamkills", s.teamkills);
            t.set("pointsCaptured", s.pointsCaptured);
            t.set("holdAwards", s.holdAwards);
        }

        ConfigurationSection playersSection = section.createSection("players");
        int p = 0;
        for (PlayerSummary ps : players) {
            ConfigurationSection psSection = playersSection.createSection("p" + (p++));
            psSection.set("uuid", ps.uuid.toString());
            psSection.set("name", ps.name);
            psSection.set("team", ps.team == null ? null : ps.team.name());
            psSection.set("kills", ps.kills);
            psSection.set("deaths", ps.deaths);
            psSection.set("teamkills", ps.teamkills);
            psSection.set("bestStreak", ps.bestStreak);
            psSection.set("pointsCaptured", ps.pointsCaptured);
        }

        ConfigurationSection eventsSection = section.createSection("events");
        int i = 0;
        for (StatEvent ev : events) {
            ConfigurationSection es = eventsSection.createSection("e" + (i++));
            es.set("type", ev.type.name());
            es.set("time", ev.timeSeconds);
            es.set("killer", ev.killer);
            es.set("victim", ev.victim);
            es.set("weapon", ev.weapon);
            es.set("team", ev.team == null ? null : ev.team.name());
            es.set("point", ev.point);
            es.set("delta", ev.scoreDelta);
        }
    }

    /** Читает запись из секции конфигурации. */
    public static BattleStats load(ConfigurationSection section) {
        int id = section.getInt("id");
        String name = section.getString("name", "?");
        long started = section.getLong("started");
        int duration = section.getInt("duration");

        String winnerName = section.getString("winner", "draw");
        BattleTeam winner = "draw".equalsIgnoreCase(winnerName) ? null : BattleTeam.valueOf(winnerName);

        Map<BattleTeam, TeamSummary> teams = new EnumMap<>(BattleTeam.class);
        ConfigurationSection teamsSection = section.getConfigurationSection("teams");
        if (teamsSection != null) {
            for (String key : teamsSection.getKeys(false)) {
                BattleTeam team = BattleTeam.valueOf(key);
                ConfigurationSection t = teamsSection.getConfigurationSection(key);
                TeamSummary s = new TeamSummary();
                s.score = t.getInt("score");
                s.kills = t.getInt("kills");
                s.deaths = t.getInt("deaths");
                s.teamkills = t.getInt("teamkills");
                s.pointsCaptured = t.getInt("pointsCaptured");
                s.holdAwards = t.getInt("holdAwards");
                teams.put(team, s);
            }
        }

        List<PlayerSummary> players = new ArrayList<>();
        ConfigurationSection playersSection = section.getConfigurationSection("players");
        if (playersSection != null) {
            for (String key : playersSection.getKeys(false)) {
                ConfigurationSection ps = playersSection.getConfigurationSection(key);
                PlayerSummary pl = new PlayerSummary();
                String uuidStr = ps.getString("uuid");
                pl.uuid = uuidStr == null ? null : UUID.fromString(uuidStr);
                pl.name = ps.getString("name");
                pl.team = ps.getString("team") == null ? null : BattleTeam.valueOf(ps.getString("team"));
                pl.kills = ps.getInt("kills");
                pl.deaths = ps.getInt("deaths");
                pl.teamkills = ps.getInt("teamkills");
                pl.bestStreak = ps.getInt("bestStreak");
                pl.pointsCaptured = ps.getInt("pointsCaptured");
                players.add(pl);
            }
        }

        List<StatEvent> events = new ArrayList<>();
        ConfigurationSection eventsSection = section.getConfigurationSection("events");
        if (eventsSection != null) {
            for (String key : eventsSection.getKeys(false)) {
                ConfigurationSection es = eventsSection.getConfigurationSection(key);
                String typeName = es.getString("type", "KILL");
                BattleTeam team = es.getString("team") == null ? null : BattleTeam.valueOf(es.getString("team"));
                events.add(new StatEvent(
                        StatEvent.Type.valueOf(typeName),
                        es.getInt("time"),
                        es.getString("killer"),
                        es.getString("victim"),
                        es.getString("weapon"),
                        team,
                        es.getString("point"),
                        es.getInt("delta")
                ));
            }
        }

        return new BattleStats(id, name, started, duration, winner, teams, players, events);
    }
}
