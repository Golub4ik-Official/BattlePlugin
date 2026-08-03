package battle.manager;

import battle.BattleTeam;
import battle.BattlePlugin;
import battle.Messages;
import battle.model.CapturePoint;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Location;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.boss.BossBar;
import org.bukkit.entity.Player;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * BossBar-полоса возле точек захвата: показывается ближайшая точка в радиусе.
 */
public class BossBarManager {

    private static final LegacyComponentSerializer LEGACY = LegacyComponentSerializer.legacySection();

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
                bar.removePlayer(player);
            }
            return;
        }

        BossBar bar = bars.get(player.getUniqueId());
        if (bar == null) {
            bar = org.bukkit.Bukkit.createBossBar(nearest.name(), BarColor.WHITE, BarStyle.SOLID);
            bar.addPlayer(player);
            bars.put(player.getUniqueId(), bar);
        }

        bar.setTitle(LEGACY.serialize(Messages.raw("<white>" + nearest.name())));

        CapturePoint.State state = nearest.state(captureTime);
        BarColor color;
        double progress;
        switch (state) {
            case CAPTURED -> {
                BattleTeam owner = nearest.owner();
                color = owner != null ? owner.barColor() : BarColor.WHITE;
                progress = 1.0;
            }
            case CAPTURING -> {
                BattleTeam capturing = nearest.capturingTeam();
                color = capturing != null ? capturing.barColor() : BarColor.WHITE;
                progress = (double) nearest.progress() / captureTime;
                progress = Math.max(0.0, Math.min(1.0, progress));
            }
            default -> {
                color = BarColor.WHITE;
                progress = 0.0;
            }
        }
        bar.setColor(color);
        bar.setProgress(progress);
    }

    /** Убирает полосу у игрока. */
    public void remove(Player player) {
        BossBar bar = bars.remove(player.getUniqueId());
        if (bar != null) {
            bar.removePlayer(player);
        }
    }

    /** Убирает все полосы (при завершении битвы). */
    public void clearAll() {
        for (BossBar bar : bars.values()) {
            bar.removeAll();
        }
        bars.clear();
    }
}
