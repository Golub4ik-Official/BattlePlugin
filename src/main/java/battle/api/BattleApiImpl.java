package battle.api;

import battle.BattlePlugin;
import battle.BattleTeam;
import battle.Messages;
import battle.manager.BattleManager;
import battle.manager.TeamManager;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Реализация API поверх менеджеров плагина. Не предназначен для прямого
 * использования — получайте экземпляр через {@link BattleApiProvider}.
 */
public class BattleApiImpl implements BattleApi {

    private final BattlePlugin plugin;

    public BattleApiImpl(BattlePlugin plugin) {
        this.plugin = plugin;
    }

    private TeamManager teamManager() {
        return plugin.teamManager();
    }

    private BattleManager battleManager() {
        return plugin.battleManager();
    }

    @Override
    public boolean isBattleActive() {
        return battleManager().getActiveBattle() != null;
    }

    @Override
    public BattleTeam getTeam(Player player) {
        return teamManager().get(player);
    }

    @Override
    public BattleTeam getTeam(UUID playerId) {
        return teamManager().get(playerId);
    }

    @Override
    public Map<UUID, BattleTeam> getAllAssignments() {
        return Collections.unmodifiableMap(teamManager().all());
    }

    @Override
    public Set<BattleTeam> getBattleTeams() {
        var battle = battleManager().getActiveBattle();
        return battle == null ? Set.of() : battle.teams();
    }

    @Override
    public boolean isParticipant(Player player) {
        return battleManager().isParticipant(player);
    }

    @Override
    public String getBattleName() {
        var battle = battleManager().getActiveBattle();
        return battle == null ? null : battle.name();
    }

    @Override
    public int getBattleTimeLeft() {
        var battle = battleManager().getActiveBattle();
        return battle == null ? -1 : battle.remainingSeconds();
    }

    @Override
    public String getDisplayName(BattleTeam team) {
        return team.displayName();
    }

    @Override
    public String getTeamColorTag(BattleTeam team) {
        return team.miniTag();
    }

    @Override
    public List<Player> getOnlineTeamMembers(BattleTeam team) {
        return new ArrayList<>(teamManager().onlineMembers(team));
    }

    @Override
    public void sendTeamMessage(BattleTeam team, String miniMessage) {
        for (Player p : teamManager().onlineMembers(team)) {
            p.sendMessage(Messages.msg(miniMessage));
        }
    }
}
