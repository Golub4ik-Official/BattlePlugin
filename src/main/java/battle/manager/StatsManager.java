package battle.manager;

import battle.BattlePlugin;
import battle.BattleTeam;
import battle.model.BattleStats;
import battle.model.StatEvent;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Хранение истории битв в SQLite-базе данных (battles.db).
 */
public class StatsManager {

    private final BattlePlugin plugin;
    private final File file;
    private Connection connection;
    private final List<BattleStats> history = new ArrayList<>();
    private int nextId = 1;

    public StatsManager(BattlePlugin plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "battles.db");
    }

    public void load() {
        history.clear();
        try {
            Class.forName("org.sqlite.JDBC");
            plugin.getDataFolder().mkdirs();
            connection = DriverManager.getConnection("jdbc:sqlite:" + file.getAbsolutePath());
            createTables();
            loadAll();
            if (history.isEmpty()) {
                migrateFromYaml();
                loadAll();
            }
        } catch (Exception e) {
            plugin.getLogger().severe("Не удалось открыть базу данных статистики: " + e.getMessage());
        }
    }

    private void createTables() throws SQLException {
        try (Statement st = connection.createStatement()) {
            st.execute("PRAGMA foreign_keys = ON");
            st.execute("CREATE TABLE IF NOT EXISTS battles ("
                    + "id INTEGER PRIMARY KEY AUTOINCREMENT,"
                    + "name TEXT NOT NULL,"
                    + "started INTEGER NOT NULL,"
                    + "duration INTEGER NOT NULL,"
                    + "winner TEXT)");
            st.execute("CREATE TABLE IF NOT EXISTS battle_teams ("
                    + "battle_id INTEGER NOT NULL,"
                    + "team TEXT NOT NULL,"
                    + "score INTEGER NOT NULL DEFAULT 0,"
                    + "kills INTEGER NOT NULL DEFAULT 0,"
                    + "deaths INTEGER NOT NULL DEFAULT 0,"
                    + "teamkills INTEGER NOT NULL DEFAULT 0,"
                    + "points_captured INTEGER NOT NULL DEFAULT 0,"
                    + "hold_awards INTEGER NOT NULL DEFAULT 0,"
                    + "label TEXT,"
                    + "PRIMARY KEY (battle_id, team),"
                    + "FOREIGN KEY (battle_id) REFERENCES battles(id) ON DELETE CASCADE)");
            st.execute("CREATE TABLE IF NOT EXISTS battle_players ("
                    + "battle_id INTEGER NOT NULL,"
                    + "uuid TEXT NOT NULL,"
                    + "name TEXT,"
                    + "team TEXT,"
                    + "kills INTEGER NOT NULL DEFAULT 0,"
                    + "deaths INTEGER NOT NULL DEFAULT 0,"
                    + "teamkills INTEGER NOT NULL DEFAULT 0,"
                    + "best_streak INTEGER NOT NULL DEFAULT 0,"
                    + "worst_death_streak INTEGER NOT NULL DEFAULT 0,"
                    + "points_captured INTEGER NOT NULL DEFAULT 0,"
                    + "damage_dealt INTEGER NOT NULL DEFAULT 0,"
                    + "damage_taken INTEGER NOT NULL DEFAULT 0,"
                    + "PRIMARY KEY (battle_id, uuid),"
                    + "FOREIGN KEY (battle_id) REFERENCES battles(id) ON DELETE CASCADE)");
            st.execute("CREATE TABLE IF NOT EXISTS battle_events ("
                    + "battle_id INTEGER NOT NULL,"
                    + "seq INTEGER NOT NULL,"
                    + "type TEXT NOT NULL,"
                    + "time INTEGER NOT NULL DEFAULT 0,"
                    + "killer TEXT,"
                    + "victim TEXT,"
                    + "weapon TEXT,"
                    + "team TEXT,"
                    + "point TEXT,"
                    + "delta INTEGER NOT NULL DEFAULT 0,"
                    + "PRIMARY KEY (battle_id, seq),"
                    + "FOREIGN KEY (battle_id) REFERENCES battles(id) ON DELETE CASCADE)");
        }
        // Миграция существующей БД: добавляем новые колонки, если их ещё нет.
        ensureColumn("battle_players", "worst_death_streak", "INTEGER NOT NULL DEFAULT 0");
        ensureColumn("battle_players", "damage_dealt", "INTEGER NOT NULL DEFAULT 0");
        ensureColumn("battle_players", "damage_taken", "INTEGER NOT NULL DEFAULT 0");
    }

    private void ensureColumn(String table, String column, String type) throws SQLException {
        boolean found = false;
        try (Statement st = connection.createStatement();
             ResultSet rs = st.executeQuery("PRAGMA table_info(" + table + ")")) {
            while (rs.next()) {
                if (column.equals(rs.getString("name"))) {
                    found = true;
                    break;
                }
            }
        }
        if (!found) {
            try (Statement st = connection.createStatement()) {
                st.execute("ALTER TABLE " + table + " ADD COLUMN " + column + " " + type);
            }
            plugin.getLogger().info("БД: добавлена колонка " + table + "." + column);
        }
    }

    private void loadAll() throws SQLException {
        try (Statement st = connection.createStatement();
             ResultSet rs = st.executeQuery("SELECT id FROM battles ORDER BY id")) {
            while (rs.next()) {
                history.add(readBattle(rs.getInt("id")));
            }
        }
        history.sort(Comparator.comparingInt(s -> s.id));
        int max = history.isEmpty() ? 0 : history.get(history.size() - 1).id;
        nextId = max + 1;
    }

    /** Импорт старых данных из battles.yml в базу, если база пустая. */
    private void migrateFromYaml() {
        File yaml = new File(plugin.getDataFolder(), "battles.yml");
        if (!yaml.exists()) {
            return;
        }
        YamlConfiguration config = YamlConfiguration.loadConfiguration(yaml);
        ConfigurationSection battles = config.getConfigurationSection("battles");
        if (battles == null || battles.getKeys(false).isEmpty()) {
            return;
        }
        plugin.getLogger().info("Импорт истории битв из battles.yml в базу данных...");
        int imported = 0;
        for (String key : battles.getKeys(false)) {
            BattleStats stats = BattleStats.load(battles.getConfigurationSection(key));
            insert(stats);
            imported++;
        }
        plugin.getLogger().info("Импортировано битв: " + imported);
    }

    public void add(BattleStats stats) {
        history.add(stats);
        if (stats.id >= nextId) {
            nextId = stats.id + 1;
        }
        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> insert(stats));
    }

    public int allocateId() {
        return nextId++;
    }

    public List<BattleStats> history() {
        return new ArrayList<>(history);
    }

    public BattleStats get(int id) {
        for (BattleStats s : history) {
            if (s.id == id) {
                return s;
            }
        }
        return null;
    }

    /** Запись сразу сохраняется в БД при добавлении; метод оставлен для совместимости. */
    public void save() {
        // nothing to do — данные персистятся в add()
    }

    /** Закрывает соединение с БД (при отключении плагина). */
    public void close() {
        if (connection != null) {
            try {
                connection.close();
            } catch (SQLException e) {
                plugin.getLogger().warning("Ошибка при закрытии БД: " + e.getMessage());
            }
            connection = null;
        }
    }

    // ------------------------------------------------------------------
    // Чтение
    // ------------------------------------------------------------------

    private BattleStats readBattle(int id) throws SQLException {
        try (PreparedStatement ps = connection.prepareStatement(
                "SELECT * FROM battles WHERE id = ?")) {
            ps.setInt(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    throw new SQLException("Битва #" + id + " не найдена в БД");
                }
                String winnerName = rs.getString("winner");
                BattleTeam winner = winnerName == null ? null : BattleTeam.valueOf(winnerName);
                return new BattleStats(id,
                        rs.getString("name"),
                        rs.getLong("started"),
                        rs.getInt("duration"),
                        winner,
                        readTeams(id),
                        readPlayers(id),
                        readEvents(id));
            }
        }
    }

    private Map<BattleTeam, BattleStats.TeamSummary> readTeams(int battleId) throws SQLException {
        Map<BattleTeam, BattleStats.TeamSummary> teams = new EnumMap<>(BattleTeam.class);
        try (PreparedStatement ps = connection.prepareStatement(
                "SELECT * FROM battle_teams WHERE battle_id = ? ORDER BY team")) {
            ps.setInt(1, battleId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    BattleTeam team = BattleTeam.valueOf(rs.getString("team"));
                    BattleStats.TeamSummary s = new BattleStats.TeamSummary();
                    s.score = rs.getInt("score");
                    s.kills = rs.getInt("kills");
                    s.deaths = rs.getInt("deaths");
                    s.teamkills = rs.getInt("teamkills");
                    s.pointsCaptured = rs.getInt("points_captured");
                    s.holdAwards = rs.getInt("hold_awards");
                    s.label = rs.getString("label");
                    teams.put(team, s);
                }
            }
        }
        return teams;
    }

    private List<BattleStats.PlayerSummary> readPlayers(int battleId) throws SQLException {
        List<BattleStats.PlayerSummary> players = new ArrayList<>();
        try (PreparedStatement ps = connection.prepareStatement(
                "SELECT * FROM battle_players WHERE battle_id = ? ORDER BY kills DESC")) {
            ps.setInt(1, battleId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    BattleStats.PlayerSummary pl = new BattleStats.PlayerSummary();
                    String uuidStr = rs.getString("uuid");
                    pl.uuid = uuidStr == null ? null : UUID.fromString(uuidStr);
                    pl.name = rs.getString("name");
                    String teamName = rs.getString("team");
                    pl.team = teamName == null ? null : BattleTeam.valueOf(teamName);
                    pl.kills = rs.getInt("kills");
                    pl.deaths = rs.getInt("deaths");
                    pl.teamkills = rs.getInt("teamkills");
                    pl.bestStreak = rs.getInt("best_streak");
                    pl.worstDeathStreak = rs.getInt("worst_death_streak");
                    pl.pointsCaptured = rs.getInt("points_captured");
                    pl.damageDealt = rs.getInt("damage_dealt");
                    pl.damageTaken = rs.getInt("damage_taken");
                    players.add(pl);
                }
            }
        }
        return players;
    }

    private List<StatEvent> readEvents(int battleId) throws SQLException {
        List<StatEvent> events = new ArrayList<>();
        try (PreparedStatement ps = connection.prepareStatement(
                "SELECT * FROM battle_events WHERE battle_id = ? ORDER BY seq")) {
            ps.setInt(1, battleId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    String teamName = rs.getString("team");
                    BattleTeam team = teamName == null ? null : BattleTeam.valueOf(teamName);
                    events.add(new StatEvent(
                            StatEvent.Type.valueOf(rs.getString("type")),
                            rs.getInt("time"),
                            rs.getString("killer"),
                            rs.getString("victim"),
                            rs.getString("weapon"),
                            team,
                            rs.getString("point"),
                            rs.getInt("delta")));
                }
            }
        }
        return events;
    }

    // ------------------------------------------------------------------
    // Запись
    // ------------------------------------------------------------------

    private void insert(BattleStats stats) {
        try {
            connection.setAutoCommit(false);
            try (PreparedStatement ps = connection.prepareStatement(
                    "INSERT INTO battles (id, name, started, duration, winner) VALUES (?, ?, ?, ?, ?)")) {
                ps.setInt(1, stats.id);
                ps.setString(2, stats.name);
                ps.setLong(3, stats.started);
                ps.setInt(4, stats.durationSeconds);
                ps.setString(5, stats.winner == null ? null : stats.winner.name());
                ps.executeUpdate();
            }
            insertTeams(stats.id, stats.teams);
            insertPlayers(stats.id, stats.players);
            insertEvents(stats.id, stats.events);
            connection.commit();
        } catch (SQLException e) {
            rollbackQuietly();
            plugin.getLogger().severe("Не удалось сохранить битву #" + stats.id + " в БД: " + e.getMessage());
        } finally {
            autoCommitQuietly();
        }
    }

    private void insertTeams(int battleId, Map<BattleTeam, BattleStats.TeamSummary> teams) throws SQLException {
        try (PreparedStatement ps = connection.prepareStatement(
                "INSERT INTO battle_teams (battle_id, team, score, kills, deaths, teamkills,"
                        + " points_captured, hold_awards, label) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)")) {
            for (Map.Entry<BattleTeam, BattleStats.TeamSummary> e : teams.entrySet()) {
                BattleStats.TeamSummary s = e.getValue();
                ps.setInt(1, battleId);
                ps.setString(2, e.getKey().name());
                ps.setInt(3, s.score);
                ps.setInt(4, s.kills);
                ps.setInt(5, s.deaths);
                ps.setInt(6, s.teamkills);
                ps.setInt(7, s.pointsCaptured);
                ps.setInt(8, s.holdAwards);
                ps.setString(9, s.label);
                ps.addBatch();
            }
            ps.executeBatch();
        }
    }

    private void insertPlayers(int battleId, List<BattleStats.PlayerSummary> players) throws SQLException {
        try (PreparedStatement ps = connection.prepareStatement(
                "INSERT INTO battle_players (battle_id, uuid, name, team, kills, deaths, teamkills,"
                        + " best_streak, worst_death_streak, points_captured, damage_dealt, damage_taken)"
                        + " VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)")) {
            for (BattleStats.PlayerSummary pl : players) {
                ps.setInt(1, battleId);
                ps.setString(2, pl.uuid == null ? null : pl.uuid.toString());
                ps.setString(3, pl.name);
                ps.setString(4, pl.team == null ? null : pl.team.name());
                ps.setInt(5, pl.kills);
                ps.setInt(6, pl.deaths);
                ps.setInt(7, pl.teamkills);
                ps.setInt(8, pl.bestStreak);
                ps.setInt(9, pl.worstDeathStreak);
                ps.setInt(10, pl.pointsCaptured);
                ps.setInt(11, pl.damageDealt);
                ps.setInt(12, pl.damageTaken);
                ps.addBatch();
            }
            ps.executeBatch();
        }
    }

    private void insertEvents(int battleId, List<StatEvent> events) throws SQLException {
        try (PreparedStatement ps = connection.prepareStatement(
                "INSERT INTO battle_events (battle_id, seq, type, time, killer, victim, weapon, team, point, delta)"
                        + " VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)")) {
            int seq = 0;
            for (StatEvent ev : events) {
                ps.setInt(1, battleId);
                ps.setInt(2, seq++);
                ps.setString(3, ev.type.name());
                ps.setInt(4, ev.timeSeconds);
                ps.setString(5, ev.killer);
                ps.setString(6, ev.victim);
                ps.setString(7, ev.weapon);
                ps.setString(8, ev.team == null ? null : ev.team.name());
                ps.setString(9, ev.point);
                ps.setInt(10, ev.scoreDelta);
                ps.addBatch();
            }
            ps.executeBatch();
        }
    }

    private void rollbackQuietly() {
        try {
            connection.rollback();
        } catch (SQLException ignored) {
            // ignore
        }
    }

    private void autoCommitQuietly() {
        try {
            connection.setAutoCommit(true);
        } catch (SQLException ignored) {
            // ignore
        }
    }
}
