package battle.api.event;

import battle.BattleTeam;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

import java.util.UUID;

/**
 * Вызывается при назначении игрока в команду или снятии с команды.
 */
public class TeamChangeEvent extends Event {

    private static final HandlerList HANDLERS = new HandlerList();

    private final UUID playerId;
    private final BattleTeam team; // null — игрок снят с команды

    public TeamChangeEvent(UUID playerId, BattleTeam team) {
        this.playerId = playerId;
        this.team = team;
    }

    /** UUID игрока. */
    public UUID getPlayerId() {
        return playerId;
    }

    /** Новая команда игрока, или {@code null}, если игрок снят с команды. */
    public BattleTeam getTeam() {
        return team;
    }

    @Override
    public HandlerList getHandlers() {
        return HANDLERS;
    }

    public static HandlerList getHandlerList() {
        return HANDLERS;
    }
}
