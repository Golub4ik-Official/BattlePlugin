package battle.manager;

import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.scoreboard.Criteria;
import org.bukkit.scoreboard.DisplaySlot;
import org.bukkit.scoreboard.Objective;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.Team;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Sidebar-дашборд участников битвы (классический Scoreboard API).
 */
public class ScoreboardManager {

    private static final String OBJECTIVE = "battle";

    private final Map<UUID, Scoreboard> boards = new HashMap<>();

    /** Создаёт дашборд игроку. */
    public void create(Player player, Component title) {
        Scoreboard board = Bukkit.getScoreboardManager().getNewScoreboard();
        Objective obj = board.registerNewObjective(OBJECTIVE, Criteria.DUMMY, title);
        obj.setDisplaySlot(DisplaySlot.SIDEBAR);
        obj.numberFormat(io.papermc.paper.scoreboard.numbers.NumberFormat.blank());
        player.setScoreboard(board);
        boards.put(player.getUniqueId(), board);
        
        // Регистрируем 15 команд для строк
        for (int i = 0; i < 15; i++) {
            Team team = board.registerNewTeam("line_" + i);
            String entry = "§" + Integer.toHexString(i) + "§r";
            team.addEntry(entry);
            obj.getScore(entry).setScore(15 - i);
        }
    }

    /** Обновляет строки дашборда. Первая строка — сверху. */
    public void update(Player player, Component title, List<Component> lines) {
        Scoreboard board = boards.get(player.getUniqueId());
        if (board == null) {
            return;
        }
        Objective obj = board.getObjective(OBJECTIVE);
        if (obj == null) {
            return;
        }
        obj.displayName(title);

        for (int i = 0; i < 15; i++) {
            Team team = board.getTeam("line_" + i);
            if (team == null) continue;
            
            String entry = "§" + Integer.toHexString(i) + "§r";
            if (i < lines.size()) {
                team.prefix(lines.get(i));
                obj.getScore(entry).setScore(15 - i);
            } else {
                board.resetScores(entry);
            }
        }
    }

    /** Есть ли дашборд у игрока. */
    public boolean hasBoard(Player player) {
        return boards.containsKey(player.getUniqueId());
    }

    /** Убирает дашборд у игрока. */
    public void remove(Player player) {
        boards.remove(player.getUniqueId());
        player.setScoreboard(Bukkit.getScoreboardManager().getMainScoreboard());
    }

    /** Убирает дашборды у всех игроков (при завершении битвы). */
    public void removeAll() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            Scoreboard board = player.getScoreboard();
            if (board != null && board != Bukkit.getScoreboardManager().getMainScoreboard()
                    && board.getObjective(OBJECTIVE) != null) {
                player.setScoreboard(Bukkit.getScoreboardManager().getMainScoreboard());
            }
        }
        boards.clear();
    }
}
