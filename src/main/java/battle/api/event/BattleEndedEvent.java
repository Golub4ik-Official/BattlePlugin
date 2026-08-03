package battle.api.event;

import battle.BattleTeam;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

import java.util.Set;

/**
 * Вызывается при завершении битвы (по таймеру или вручную).
 */
public class BattleEndedEvent extends Event {

    private static final HandlerList HANDLERS = new HandlerList();

    private final String battleName;
    private final BattleTeam winner;
    private final Set<BattleTeam> teams;

    public BattleEndedEvent(String battleName, BattleTeam winner, Set<BattleTeam> teams) {
        this.battleName = battleName;
        this.winner = winner;
        this.teams = Set.copyOf(teams);
    }

    /** Название завершившейся битвы. */
    public String getBattleName() {
        return battleName;
    }

    /** Победитель битвы, или {@code null} при ничьей. */
    public BattleTeam getWinner() {
        return winner;
    }

    /** Команды, участвовавшие в битве. */
    public Set<BattleTeam> getTeams() {
        return teams;
    }

    @Override
    public HandlerList getHandlers() {
        return HANDLERS;
    }

    public static HandlerList getHandlerList() {
        return HANDLERS;
    }
}
