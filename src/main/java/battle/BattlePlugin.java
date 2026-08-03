package battle;

import battle.api.BattleApi;
import battle.api.BattleApiImpl;
import battle.command.BattleCommand;
import battle.listener.BattleListener;
import battle.manager.BossBarManager;
import battle.manager.BattleManager;
import battle.manager.PointManager;
import battle.manager.ScoreboardManager;
import battle.manager.StatsManager;
import battle.manager.TeamManager;
import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Главный класс плагина BattlePlugin.
 */
public class BattlePlugin extends JavaPlugin {

    private TeamManager teamManager;
    private PointManager pointManager;
    private ScoreboardManager scoreboardManager;
    private BossBarManager bossBarManager;
    private StatsManager statsManager;
    private BattleManager battleManager;

    @Override
    public void onEnable() {
        saveDefaultConfig();

        teamManager = new TeamManager();
        pointManager = new PointManager();
        scoreboardManager = new ScoreboardManager();
        bossBarManager = new BossBarManager(this, pointManager);
        statsManager = new StatsManager(this);
        statsManager.load();
        battleManager = new BattleManager(this, teamManager, pointManager,
                scoreboardManager, bossBarManager, statsManager);

        getServer().getServicesManager().register(BattleApi.class,
                new BattleApiImpl(this), this, org.bukkit.plugin.ServicePriority.Normal);

        getServer().getPluginManager().registerEvents(
                new BattleListener(battleManager, scoreboardManager, bossBarManager, teamManager), this);

        getServer().getScheduler().runTaskTimer(this,
                () -> teamManager.refreshColoredNames(), 20L, 20L);

        getLifecycleManager().registerEventHandler(LifecycleEvents.COMMANDS, event ->
                event.registrar().register(new BattleCommand(
                        battleManager, teamManager, pointManager, statsManager).root()));

        getLogger().info("BattlePlugin включён.");
    }

    @Override
    public void onDisable() {
        if (battleManager != null) {
            battleManager.shutdown();
        }
        if (statsManager != null) {
            statsManager.save();
        }
        if (teamManager != null) {
            teamManager.clearColoredNames();
        }
        getLogger().info("BattlePlugin выключен.");
    }

    public BattleManager battleManager() {
        return battleManager;
    }

    public TeamManager teamManager() {
        return teamManager;
    }

    public PointManager pointManager() {
        return pointManager;
    }

    public ScoreboardManager scoreboardManager() {
        return scoreboardManager;
    }

    public BossBarManager bossBarManager() {
        return bossBarManager;
    }

    public StatsManager statsManager() {
        return statsManager;
    }
}
