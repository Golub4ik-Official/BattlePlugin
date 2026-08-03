package battle.api;

import battle.BattleTeam;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Публичный API плагина BattlePlugin для интеграции с другими плагинами.
 *
 * <p>Получение экземпляра:
 * <pre>{@code
 * BattleApi api = BattleApiProvider.get();
 * if (api != null) {
 *     BattleTeam team = api.getTeam(player);
 * }
 * }</pre>
 *
 * <p>Все методы можно вызывать только с главного потока сервера (как и остальное
 * Bukkit API).
 */
public interface BattleApi {

    /** Идёт ли сейчас битва. */
    boolean isBattleActive();

    /**
     * Команда игрока, или {@code null}, если игрок никуда не назначен.
     * Учитывает назначения, сделанные через /battle team set.
     */
    BattleTeam getTeam(Player player);

    /** Команда игрока по UUID, или {@code null}. */
    BattleTeam getTeam(UUID playerId);

    /** Все текущие назначения игроков в команды (UUID → команда). */
    Map<UUID, BattleTeam> getAllAssignments();

    /** Команды, участвующие в текущей битве. Пустое множество, если битвы нет. */
    Set<BattleTeam> getBattleTeams();

    /** Участвует ли игрок в текущей битве (назначен в одну из команд битвы). */
    boolean isParticipant(Player player);

    /** Название текущей битвы, или {@code null}, если битвы нет. */
    String getBattleName();

    /** Осталось секунд до конца битвы, или {@code -1}, если битвы нет. */
    int getBattleTimeLeft();

    /** Отображаемое название команды (учитывает кастомный ярлык, если задан). */
    String getDisplayName(BattleTeam team);

    /** MiniMessage-тег цвета команды, например {@code <#ff5555>}. */
    String getTeamColorTag(BattleTeam team);

    /** Онлайн-игроки команды. */
    List<Player> getOnlineTeamMembers(BattleTeam team);

    /**
     * Отправляет сообщение (MiniMessage) всем онлайн-игрокам указанной команды.
     * Пример: {@code api.sendTeamMessage(team, "<gold>Голосование открыто!</gold>")}.
     */
    void sendTeamMessage(BattleTeam team, String miniMessage);
}
