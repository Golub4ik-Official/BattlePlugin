package battle.api.event;

import battle.BattleTeam;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

import java.util.Set;

/**
 * Вызывается при старте новой битвы.
 */
public class BattleStartedEvent extends Event {

    private static final HandlerList HANDLERS = new HandlerList();

    private final String battleName;
    private final int durationSeconds;
    private final Set<BattleTeam> teams;

    public BattleStartedEvent(String battleName, int durationSeconds, Set<BattleTeam> teams) {
        this.battleName = battleName;
        this.durationSeconds = durationSeconds;
        this.teams = Set.copyOf(teams);
    }

    /** Название начавшейся битвы. */
    public String getBattleName() {
        return battleName;
    }

    /** Длительность битвы в секундах. */
    public int getDurationSeconds() {
        return durationSeconds;
    }

    /** Команды, участвующие в битве. */
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
