package battle.listener;

import battle.manager.BattleManager;
import battle.manager.BossBarManager;
import battle.manager.ScoreboardManager;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

/**
 * Слушатели событий: смерти (килы/тимкиллы), вход и выход игроков.
 */
public class BattleListener implements Listener {

    private final BattleManager battleManager;
    private final ScoreboardManager scoreboardManager;
    private final BossBarManager bossBarManager;

    public BattleListener(BattleManager battleManager, ScoreboardManager scoreboardManager,
                          BossBarManager bossBarManager) {
        this.battleManager = battleManager;
        this.scoreboardManager = scoreboardManager;
        this.bossBarManager = bossBarManager;
    }

    @EventHandler(ignoreCancelled = true)
    public void onDeath(PlayerDeathEvent event) {
        Player victim = event.getPlayer();
        Player killer = null;
        if (event.getDamageSource().getCausingEntity() instanceof Player p) {
            killer = p;
        }
        battleManager.onPlayerDeath(victim, killer);
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        battleManager.refreshDisplays();
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        scoreboardManager.remove(player);
        bossBarManager.remove(player);
    }
}
