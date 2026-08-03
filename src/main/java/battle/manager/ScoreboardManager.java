package battle.manager;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.scoreboard.Criteria;
import org.bukkit.scoreboard.DisplaySlot;
import org.bukkit.scoreboard.Objective;
import org.bukkit.scoreboard.Score;
import org.bukkit.scoreboard.Scoreboard;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Sidebar-дашборд участников битвы (классический Scoreboard API).
 */
public class ScoreboardManager {

    private static final LegacyComponentSerializer LEGACY = LegacyComponentSerializer.legacySection();
    private static final String OBJECTIVE = "battle";

    private final Map<UUID, Scoreboard> boards = new HashMap<>();
    private final Map<UUID, Map<String, Integer>> entries = new HashMap<>();

    /** Создаёт дашборд игроку. */
    public void create(Player player, Component title) {
        Scoreboard board = Bukkit.getScoreboardManager().getNewScoreboard();
        Objective obj = board.registerNewObjective(OBJECTIVE, Criteria.DUMMY, title);
        obj.setDisplaySlot(DisplaySlot.SIDEBAR);
        obj.numberFormat(io.papermc.paper.scoreboard.numbers.NumberFormat.blank());
        player.setScoreboard(board);
        boards.put(player.getUniqueId(), board);
        entries.put(player.getUniqueId(), new HashMap<>());
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

        Map<String, Integer> current = entries.get(player.getUniqueId());
        Map<String, Integer> next = new HashMap<>();

        int i = lines.size();
        for (Component line : lines) {
            String entry = LEGACY.serialize(line);
            if (entry.isEmpty()) {
                entry = "§r";
            }
            next.put(entry, i--);
        }

        for (Map.Entry<String, Integer> e : current.entrySet()) {
            if (!next.containsKey(e.getKey())) {
                board.resetScores(e.getKey());
            }
        }
        for (Map.Entry<String, Integer> e : next.entrySet()) {
            Integer previous = current.get(e.getKey());
            if (previous == null || !previous.equals(e.getValue())) {
                Score score = obj.getScore(e.getKey());
                score.setScore(e.getValue());
            }
        }

        entries.put(player.getUniqueId(), next);
    }

    /** Есть ли дашборд у игрока. */
    public boolean hasBoard(Player player) {
        return boards.containsKey(player.getUniqueId());
    }

    /** Убирает дашборд у игрока. */
    public void remove(Player player) {
        boards.remove(player.getUniqueId());
        entries.remove(player.getUniqueId());
        player.setScoreboard(Bukkit.getScoreboardManager().getMainScoreboard());
    }

    /** Убирает дашборды у всех игроков (при завершении битвы). */
    public void removeAll(Set<UUID> participants) {
        for (UUID uuid : participants) {
            boards.remove(uuid);
            entries.remove(uuid);
        }
        for (Player player : Bukkit.getOnlinePlayers()) {
            Scoreboard board = player.getScoreboard();
            if (board != null && board != Bukkit.getScoreboardManager().getMainScoreboard()
                    && board.getObjective(OBJECTIVE) != null) {
                player.setScoreboard(Bukkit.getScoreboardManager().getMainScoreboard());
            }
        }
    }
}
