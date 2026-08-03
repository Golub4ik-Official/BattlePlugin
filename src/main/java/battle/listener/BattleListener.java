package battle.listener;

import battle.BattleTeam;
import battle.api.event.TeamChangeEvent;
import battle.manager.BattleManager;
import battle.manager.BossBarManager;
import battle.manager.ScoreboardManager;
import battle.manager.TeamManager;
import battle.manager.VoiceGroupsHook;
import org.bukkit.Bukkit;
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
    private final VoiceGroupsHook voiceGroupsHook;

    public BattleListener(BattleManager battleManager, ScoreboardManager scoreboardManager,
                          BossBarManager bossBarManager, TeamManager teamManager,
                          VoiceGroupsHook voiceGroupsHook) {
        this.battleManager = battleManager;
        this.scoreboardManager = scoreboardManager;
        this.bossBarManager = bossBarManager;
        this.teamManager = teamManager;
        this.voiceGroupsHook = voiceGroupsHook;
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
        Player player = event.getPlayer();
        battleManager.refreshDisplays();
        teamManager.refresh(player);
        if (battleManager.getActiveBattle() != null) {
            BattleTeam team = teamManager.get(player);
            if (team != null && battleManager.getActiveBattle().teams().contains(team)) {
                voiceGroupsHook.joinTeam(player, team);
            }
        }
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

    /** При смене команды во время битвы игрок переводится в голосовой канал новой команды. */
    @EventHandler
    public void onTeamChange(TeamChangeEvent event) {
        if (battleManager.getActiveBattle() == null) {
            return;
        }
        BattleTeam team = event.getTeam();
        if (team == null) {
            return;
        }
        Player player = Bukkit.getPlayer(event.getPlayerId());
        if (player != null && battleManager.getActiveBattle().teams().contains(team)) {
            voiceGroupsHook.joinTeam(player, team);
        }
    }
}
