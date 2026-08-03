package battle.listener;

import battle.manager.BattleManager;
import battle.manager.BossBarManager;
import battle.manager.ScoreboardManager;
import battle.manager.TeamManager;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;

/**
 * Слушатели событий: смерти (килы/тимкиллы), вход и выход игроков, заморозка.
 */
public class BattleListener implements Listener {

    private final BattleManager battleManager;
    private final ScoreboardManager scoreboardManager;
    private final BossBarManager bossBarManager;
    private final TeamManager teamManager;

    public BattleListener(BattleManager battleManager, ScoreboardManager scoreboardManager,
                          BossBarManager bossBarManager, TeamManager teamManager) {
        this.battleManager = battleManager;
        this.scoreboardManager = scoreboardManager;
        this.bossBarManager = bossBarManager;
        this.teamManager = teamManager;
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

    /** Учёт урона по игрокам битвы (для статистики нанесённого/полученного урона). */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onDamage(EntityDamageByEntityEvent event) {
        if (!(event.getEntity() instanceof Player victim)) {
            return;
        }
        Player attacker = null;
        if (event.getDamager() instanceof Player p) {
            attacker = p;
        } else if (event.getDamager() instanceof Projectile projectile
                && projectile.getShooter() instanceof Player p) {
            attacker = p;
        }
        double damage = event.getFinalDamage();
        if (damage <= 0) {
            return;
        }
        battleManager.onDamage(victim, attacker, damage);
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        battleManager.refreshDisplays();
        teamManager.refresh(event.getPlayer());
    }

    @EventHandler(ignoreCancelled = true)
    public void onMove(PlayerMoveEvent event) {
        if (!teamManager.isFrozen(event.getPlayer())) {
            return;
        }
        Location from = event.getFrom();
        Location to = event.getTo();
        if (from.getX() != to.getX() || from.getY() != to.getY()
                || from.getZ() != to.getZ()) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        scoreboardManager.remove(player);
        bossBarManager.remove(player);
        teamManager.clearFrozen(player);
    }
}
