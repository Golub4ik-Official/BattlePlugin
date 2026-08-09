package battle.manager;

import battle.BattleTeam;
import battle.BattlePlugin;
import battle.Messages;
import battle.model.CapturePoint;
import net.kyori.adventure.bossbar.BossBar;
import org.bukkit.Location;
import org.bukkit.entity.Player;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * BossBar-полоса возле точек захвата: показывается ближайшая точка в радиусе.
 */
public class BossBarManager {

    private final BattlePlugin plugin;
    private final PointManager pointManager;
    private final Map<UUID, BossBar> bars = new HashMap<>();

    public BossBarManager(BattlePlugin plugin, PointManager pointManager) {
        this.plugin = plugin;
        this.pointManager = pointManager;
    }

    /**
     * Обновляет полосу игрока. Ближайшая точка в радиусе — показывается, иначе скрыта.
     */
    public void update(Player player, int radius, int captureTime) {
        CapturePoint nearest = null;
        double best = Double.MAX_VALUE;
        Location playerLoc = player.getLocation();

        for (CapturePoint point : pointManager.all()) {
            Location loc = point.location();
            if (!loc.getWorld().equals(playerLoc.getWorld())) {
                continue;
            }
            double d = playerLoc.distanceSquared(loc);
            double max = (double) radius * radius;
            if (d <= max && d < best) {
                best = d;
                nearest = point;
            }
        }

        if (nearest == null) {
            BossBar bar = bars.remove(player.getUniqueId());
            if (bar != null) {
                player.hideBossBar(bar);
            }
            return;
        }

        BossBar bar = bars.get(player.getUniqueId());
        if (bar == null) {
            bar = BossBar.bossBar(Messages.raw("<white>" + nearest.name()), 0f, BossBar.Color.WHITE, BossBar.Overlay.PROGRESS);
            player.showBossBar(bar);
            bars.put(player.getUniqueId(), bar);
        }

        bar.name(Messages.raw("<white>" + nearest.name()));

        CapturePoint.State state = nearest.state(captureTime);
        BossBar.Color color;
        double progress;
        switch (state) {
            case CAPTURED -> {
                BattleTeam owner = nearest.owner();
                color = owner != null ? owner.barColor() : BossBar.Color.WHITE;
                progress = 1.0;
            }
            case CAPTURING -> {
                BattleTeam capturing = nearest.capturingTeam();
                color = capturing != null ? capturing.barColor() : BossBar.Color.WHITE;
                progress = (double) nearest.progress() / captureTime;
                progress = Math.max(0.0, Math.min(1.0, progress));
            }
            default -> {
                color = BossBar.Color.WHITE;
                progress = 0.0;
            }
        }
        bar.color(color);
        bar.progress((float) progress);
    }

    /** Убирает полосу у игрока. */
    public void remove(Player player) {
        BossBar bar = bars.remove(player.getUniqueId());
        if (bar != null) {
            player.hideBossBar(bar);
        }
    }

    /** Убирает все полосы (при завершении битвы). */
    public void clearAll() {
        for (Map.Entry<UUID, BossBar> entry : bars.entrySet()) {
            Player player = org.bukkit.Bukkit.getPlayer(entry.getKey());
            if (player != null) {
                player.hideBossBar(entry.getValue());
            }
        }
        bars.clear();
    }
}
