package battle.manager;

import battle.BattleTeam;
import battle.BattlePlugin;
import battle.Messages;
import battle.api.event.BattleEndedEvent;
import battle.api.event.BattleStartedEvent;
import battle.model.Battle;
import battle.model.BattleStats;
import battle.model.CapturePoint;
import battle.model.PlayerStats;
import battle.model.StatEvent;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Display;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Сердце плагина: жизненный цикл битвы, тик, захват точек, очки, частицы.
 */
public class BattleManager {

    private static final LegacyComponentSerializer LEGACY = LegacyComponentSerializer.legacySection();

    private final BattlePlugin plugin;
    private final TeamManager teamManager;
    private final PointManager pointManager;
    private final ScoreboardManager scoreboardManager;
    private final BossBarManager bossBarManager;
    private final StatsManager statsManager;

    private Battle battle;
    private BukkitTask tickTask;

    private int defaultDurationMinutes;
    private int endCountdown;
    private int captureRadius;
    private int captureTime;
    private int holdScore;
    private int holdInterval;
    private int bossbarRadius;
    private int killScore;
    private int deathScore;
    private int teamkillScore;

    private final Map<CapturePoint, Integer> holdTicks = new HashMap<>();
    private final Map<CapturePoint, TextDisplay> pointDisplays = new HashMap<>();

    public BattleManager(BattlePlugin plugin, TeamManager teamManager, PointManager pointManager,
                         ScoreboardManager scoreboardManager, BossBarManager bossBarManager,
                         StatsManager statsManager) {
        this.plugin = plugin;
        this.teamManager = teamManager;
        this.pointManager = pointManager;
        this.scoreboardManager = scoreboardManager;
        this.bossBarManager = bossBarManager;
        this.statsManager = statsManager;
        readConfig();
    }

    public void readConfig() {
        var c = plugin.getConfig();
        defaultDurationMinutes = c.getInt("battle.default-duration-minutes", 30);
        endCountdown = c.getInt("battle.end-countdown-seconds", 10);
        captureRadius = c.getInt("battle.capture.radius", 10);
        captureTime = c.getInt("battle.capture.time-seconds", 30);
        holdScore = c.getInt("battle.capture.hold-score", 1);
        holdInterval = c.getInt("battle.capture.hold-interval-seconds", 30);
        bossbarRadius = c.getInt("battle.bossbar.radius", 30);
        killScore = c.getInt("battle.scoring.kill", 5);
        deathScore = c.getInt("battle.scoring.death", -2);
        teamkillScore = c.getInt("battle.scoring.teamkill", -5);
        teamManager.setMinPlaytimeHours(c.getInt("team.min-playtime-hours", 0));
    }

    public void reload() {
        plugin.reloadConfig();
        readConfig();
    }

    public Battle getActiveBattle() {
        return battle;
    }

    public int captureTime() {
        return captureTime;
    }

    /** Игрок назначен в команду, участвующую в текущей битве. */
    public boolean isParticipant(Player player) {
        if (battle == null) {
            return false;
        }
        BattleTeam team = teamManager.get(player);
        return team != null && battle.teams().contains(team);
    }

    /** Запускает новую битву. Возвращает {@code false}, если битва уже идёт. */
    public boolean start(CommandSender starter, String name, int minutes, Set<BattleTeam> teams) {
        if (battle != null) {
            starter.sendMessage(Messages.msg("<red>Битва <yellow>" + battle.name()
                    + "</yellow> уже идёт. Сначала остановите её."));
            return false;
        }
        if (teams.size() < 2) {
            starter.sendMessage(Messages.msg("<red>Нужно минимум 2 команды."));
            return false;
        }

        int seconds = Math.max(1, minutes) * 60;
        battle = new Battle(name, seconds, teams);
        pointManager.resetAll();
        holdTicks.clear();

        broadcast(Messages.raw("<gold>═══ Битва началась! ═══"));
        broadcast(Messages.raw("<gold>Название: <white>" + name));
        broadcast(Messages.raw("<gold>Длительность: <white>" + minutes + " <gold>мин."));
        broadcast(Messages.raw("<gold>Команды: <white>" + formatTeams(teams)));

        plugin.getServer().getPluginManager().callEvent(new BattleStartedEvent(name, seconds, teams));

        refreshDisplays();
        updatePointDisplays();
        tickTask = plugin.getServer().getScheduler().runTaskTimer(plugin, this::tick, 20L, 20L);
        return true;
    }

    public void stop(CommandSender stopper) {
        if (battle == null) {
            stopper.sendMessage(Messages.msg("<red>Битва не идёт."));
            return;
        }
        endBattle("stop");
    }

    /** Вызывается при отключении плагина: останавливает битву и сохраняет статистику. */
    public void shutdown() {
        if (tickTask != null) {
            tickTask.cancel();
            tickTask = null;
        }
        if (battle != null) {
            endBattle("stop");
        }
    }

    /** Обработка смерти игрока во время битвы (убийство / смерть / тимкилл). */
    public void onPlayerDeath(Player victim, Player killer) {
        if (battle == null || !isParticipant(victim)) {
            return;
        }
        BattleTeam victimTeam = teamManager.get(victim);
        BattleTeam killerTeam = killer == null ? null : teamManager.get(killer);
        boolean killerInBattle = killer != null && killerTeam != null && battle.teams().contains(killerTeam);
        String weapon = weaponName(killer);
        int time = battle.elapsedSeconds();

        if (killerInBattle && killerTeam == victimTeam) {
            battle.addScore(killerTeam, teamkillScore);
            battle.scoreOf(killerTeam).teamkills++;
            battle.statsOf(victim.getUniqueId(), victim.getName()).addDeath();
            battle.statsOf(killer.getUniqueId(), killer.getName()).addTeamkill();
            battle.events().add(StatEvent.teamkill(killer.getName(), victim.getName(), weapon, killerTeam, time, teamkillScore));
            broadcast(Messages.raw(killerTeam.colorize(killer.getName()) + " <red>убил(а) союзника</red> "
                    + victimTeam.colorize(victim.getName()) + " <gray>(" + weapon + ")</gray> <yellow>(" + teamkillScore + ")</yellow>"));
        } else if (killerInBattle) {
            battle.addScore(killerTeam, killScore);
            battle.scoreOf(killerTeam).kills++;
            battle.scoreOf(victimTeam).deaths++;
            battle.statsOf(killer.getUniqueId(), killer.getName()).addKill();
            battle.statsOf(victim.getUniqueId(), victim.getName()).addDeath();
            battle.events().add(StatEvent.kill(killer.getName(), victim.getName(), weapon, killerTeam, time, killScore));
            broadcast(Messages.raw(killerTeam.colorize(killer.getName()) + " <gray>убил(а)</gray> "
                    + victimTeam.colorize(victim.getName()) + " <gray>(" + weapon + ")</gray> <green>(+" + killScore + ")</green>"));
        } else {
            battle.addScore(victimTeam, deathScore);
            battle.scoreOf(victimTeam).deaths++;
            battle.statsOf(victim.getUniqueId(), victim.getName()).addDeath();
            battle.events().add(StatEvent.death(victim.getName(), victimTeam, time, deathScore));
            broadcast(Messages.raw(victimTeam.colorize(victim.getName()) + " <gray>погиб(ла)</gray> <yellow>(" + deathScore + ")</yellow>"));
        }
        refreshDisplays();
    }

    /** Обновляет дашборды и полосы у всех участников битвы. */
    public void refreshDisplays() {
        if (battle == null) {
            return;
        }
        Component title = Messages.raw("<yellow>Битва</yellow>");
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (!isParticipant(p)) {
                continue;
            }
            if (!scoreboardManager.hasBoard(p)) {
                scoreboardManager.create(p, title);
            }
            scoreboardManager.update(p, title, buildLines(p));
            bossBarManager.update(p, bossbarRadius, captureTime);
        }
    }

    private void tick() {
        if (battle == null) {
            return;
        }
        battle.tickDown();
        updatePoints();
        awardHoldScores();
        spawnParticles();
        updatePointDisplays();
        refreshDisplays();

        int remaining = battle.remainingSeconds();
        if (remaining <= endCountdown && remaining > 0) {
            broadcast(Messages.raw("<red>Битва завершится через <white>" + remaining + " <red>сек."));
        } else if (remaining <= 300 && remaining > 0 && remaining % 60 == 0) {
            broadcast(Messages.raw("<gold>До конца битвы: <white>" + (remaining / 60) + " <gold>мин."));
        }

        if (battle.isOver()) {
            endBattle("time");
        }
    }

    private void updatePoints() {
        for (CapturePoint point : pointManager.all()) {
            BattleTeam dominant = dominantTeam(point);
            CapturePoint.Result res = point.update(dominant, captureTime);
            int time = battle.elapsedSeconds();

            if (res.startedBy != null && res.capturedBy == null) {
                battle.events().add(StatEvent.pointStart(point.name(), res.startedBy, time));
                broadcast(Messages.raw(res.startedBy.colorize(res.startedBy.displayName())
                        + " <gray>начал(а) захват точки</gray> <yellow>" + point.name() + "</yellow>"));
            }
            if (res.capturedBy != null) {
                battle.scoreOf(res.capturedBy).pointsCaptured++;
                battle.events().add(StatEvent.pointCaptured(point.name(), res.capturedBy, time));
                broadcast(Messages.raw(res.capturedBy.colorize(res.capturedBy.displayName())
                        + " <green>захватил(а) точку</green> <yellow>" + point.name() + "</yellow>"));
                holdTicks.put(point, 0);
                creditCapture(point, res.capturedBy);
            }
            if (res.lostBy != null) {
                battle.events().add(StatEvent.pointLost(point.name(), res.lostBy, time));
                broadcast(Messages.raw(res.lostBy.colorize(res.lostBy.displayName())
                        + " <red>потерял(а) точку</red> <yellow>" + point.name() + "</yellow>"));
            }
        }
    }

    /** Очки за удержание захваченных точек с интервалом из конфига. */
    private void awardHoldScores() {
        for (CapturePoint point : pointManager.all()) {
            if (point.owner() == null) {
                holdTicks.remove(point);
                continue;
            }
            int ticks = holdTicks.merge(point, 1, Integer::sum);
            if (ticks >= holdInterval) {
                BattleTeam owner = point.owner();
                battle.addScore(owner, holdScore);
                battle.scoreOf(owner).holdAwards++;
                battle.events().add(StatEvent.pointHold(point.name(), owner, battle.elapsedSeconds(), holdScore));
                broadcast(Messages.raw(owner.colorize(owner.displayName())
                        + " <gray>удерживает точку</gray> <yellow>" + point.name() + "</yellow> <green>(+"
                        + holdScore + ")</green>"));
                holdTicks.put(point, 0);
            }
        }
    }

    /** Команда со строгим большинством игроков внутри радиуса, или {@code null} при ничьей. */
    private BattleTeam dominantTeam(CapturePoint point) {
        Map<BattleTeam, Integer> counts = new EnumMap<>(BattleTeam.class);
        Location loc = point.location();
        int radiusSq = captureRadius * captureRadius;
        for (Player p : Bukkit.getOnlinePlayers()) {
            BattleTeam team = teamManager.get(p);
            if (team == null || !battle.teams().contains(team)) {
                continue;
            }
            if (!p.getLocation().getWorld().equals(loc.getWorld())) {
                continue;
            }
            if (p.getLocation().distanceSquared(loc) <= radiusSq) {
                counts.merge(team, 1, Integer::sum);
            }
        }
        BattleTeam best = null;
        int bestCount = 0;
        boolean tie = false;
        for (Map.Entry<BattleTeam, Integer> e : counts.entrySet()) {
            int c = e.getValue();
            if (c > bestCount) {
                bestCount = c;
                best = e.getKey();
                tie = false;
            } else if (c == bestCount) {
                tie = true;
            }
        }
        return tie ? null : best;
    }

    /** Записывает захват точки игроку команды, ближайшему к точке. */
    private void creditCapture(CapturePoint point, BattleTeam team) {
        Player best = null;
        double bestDist = Double.MAX_VALUE;
        for (Player p : onlineInTeam(team)) {
            if (!p.getLocation().getWorld().equals(point.location().getWorld())) {
                continue;
            }
            double d = p.getLocation().distanceSquared(point.location());
            if (d < bestDist) {
                bestDist = d;
                best = p;
            }
        }
        if (best != null) {
            battle.statsOf(best.getUniqueId(), best.getName()).addCapture();
        }
    }

    private List<Player> onlineInTeam(BattleTeam team) {
        return Bukkit.getOnlinePlayers().stream()
                .filter(p -> teamManager.get(p) == team)
                .map(p -> (Player) p)
                .toList();
    }

    private void spawnParticles() {
        for (CapturePoint point : pointManager.all()) {
            BattleTeam colorTeam = switch (point.state(captureTime)) {
                case CAPTURED -> point.owner();
                case CAPTURING -> point.capturingTeam();
                default -> null;
            };
            Color color = colorTeam != null ? colorTeam.particleColor() : Color.fromRGB(0xAAAAAA);
            Particle.DustOptions dust = new Particle.DustOptions(color, 1.5f);
            Location loc = point.location();
            if (loc.getWorld() == null) {
                continue;
            }
            // Плотный вращающийся круг радиусом захвата
            double baseAngle = Math.toRadians((battle.elapsedSeconds() * 4) % 360);
            int steps = Math.max(72, captureRadius * 8);
            double step = 2 * Math.PI / steps;
            for (int i = 0; i < steps; i++) {
                double angle = baseAngle + step * i;
                double x = loc.getX() + captureRadius * Math.cos(angle);
                double z = loc.getZ() + captureRadius * Math.sin(angle);
                loc.getWorld().spawnParticle(Particle.DUST,
                        new Location(loc.getWorld(), x, loc.getY() + 1.0, z),
                        1, 0, 0, 0, 0, dust);
            }
        }
    }

    /** Спавнит/обновляет летающий текст с названием точек (виден сквозь блоки). */
    private void updatePointDisplays() {
        for (CapturePoint point : pointManager.all()) {
            TextDisplay display = pointDisplays.get(point);
            if (display == null || display.isDead() || !display.isValid()) {
                TextDisplay spawned = spawnPointDisplay(point);
                if (spawned == null) {
                    continue;
                }
                pointDisplays.put(point, spawned);
                display = spawned;
            }
            updatePointDisplay(display, point);
        }
        pointDisplays.entrySet().removeIf(e -> e.getValue().isDead() || !e.getValue().isValid());
    }

    private TextDisplay spawnPointDisplay(CapturePoint point) {
        Location loc = point.location();
        if (loc.getWorld() == null) {
            return null;
        }
        Location pos = loc.clone().add(0.5, 3.2, 0.5);
        return loc.getWorld().spawn(pos, TextDisplay.class, d -> {
            d.setBillboard(Display.Billboard.CENTER);
            d.setSeeThrough(true);
            d.setShadowed(true);
            d.setTextOpacity((byte) 255);
            d.setBackgroundColor(Color.fromARGB(0, 0, 0, 0));
            d.setViewRange(2.0f);
            d.setPersistent(false);
        });
    }

    private void updatePointDisplay(TextDisplay display, CapturePoint point) {
        CapturePoint.State state = point.state(captureTime);
        BattleTeam team = state == CapturePoint.State.CAPTURED ? point.owner() : point.capturingTeam();
        int pct = state == CapturePoint.State.NEUTRAL ? 0 : Math.round(100f * point.progress() / captureTime);
        String text = team != null
                ? team.colorize(point.name()) + " <gray>[" + pct + "%]</gray>"
                : "<white>" + point.name() + " <gray>[" + pct + "%]</gray>";
        display.text(Messages.raw(text));
        display.setGlowing(true);
        display.setGlowColorOverride(team != null ? team.particleColor() : Color.WHITE);
    }

    private void removePointDisplays() {
        for (TextDisplay display : pointDisplays.values()) {
            display.remove();
        }
        pointDisplays.clear();
    }

    private void endBattle(String reason) {
        if (battle == null) {
            return;
        }
        Battle ended = battle;
        if (tickTask != null) {
            tickTask.cancel();
            tickTask = null;
        }

        BattleTeam winner = determineWinner(ended);
        plugin.getServer().getPluginManager().callEvent(new BattleEndedEvent(ended.name(), winner, ended.teams()));

        Map<BattleTeam, BattleStats.TeamSummary> teams = new EnumMap<>(BattleTeam.class);
        for (BattleTeam t : ended.teams()) {
            Battle.TeamScore ts = ended.scoreOf(t);
            BattleStats.TeamSummary s = new BattleStats.TeamSummary();
            s.score = ts.score;
            s.kills = ts.kills;
            s.deaths = ts.deaths;
            s.teamkills = ts.teamkills;
            s.pointsCaptured = ts.pointsCaptured;
            s.holdAwards = ts.holdAwards;
            s.label = t.hasLabel() ? t.rawLabel() : null;
            teams.put(t, s);
        }

        List<BattleStats.PlayerSummary> players = new ArrayList<>();
        for (Map.Entry<UUID, PlayerStats> e : ended.playerStats().entrySet()) {
            PlayerStats ps = e.getValue();
            BattleStats.PlayerSummary summary = new BattleStats.PlayerSummary();
            summary.uuid = ps.uuid();
            summary.name = ps.name() == null ? "?" : ps.name();
            summary.team = teamManager.get(e.getKey());
            summary.kills = ps.kills();
            summary.deaths = ps.deaths();
            summary.teamkills = ps.teamkills();
            summary.bestStreak = ps.bestStreak();
            summary.pointsCaptured = ps.pointsCaptured();
            players.add(summary);
        }
        players.sort(Comparator.comparingInt((BattleStats.PlayerSummary s) -> s.kills).reversed());

        BattleStats stats = new BattleStats(statsManager.allocateId(), ended.name(), ended.startTime(),
                ended.elapsedSeconds(), winner, teams, players, List.copyOf(ended.events()));
        statsManager.add(stats);
        statsManager.save();

        broadcast(Messages.raw("<gold>═══ Битва завершена! ═══"));
        broadcast(Messages.raw("stop".equals(reason)
                ? "<red>Битва остановлена вручную."
                : "<red>Время битвы вышло."));
        broadcast(Messages.raw("<gold>Название: <white>" + ended.name()));
        if (winner != null) {
            broadcast(Messages.raw("<gold>Победитель: " + winner.colorize(winner.displayName())));
        } else {
            broadcast(Messages.raw("<gray>Ничья!"));
        }
        for (BattleTeam t : ended.teams()) {
            Battle.TeamScore ts = ended.scoreOf(t);
            broadcast(Messages.raw(t.colorize(t.displayName()) + ": <white>" + ts.score
                    + " <gray>(убийств: " + ts.kills + ", смертей: " + ts.deaths + ")</gray>"));
        }

        scoreboardManager.removeAll(Set.of());
        bossBarManager.clearAll();
        removePointDisplays();
        battle = null;
    }

    private BattleTeam determineWinner(Battle b) {
        BattleTeam top = null;
        int topScore = Integer.MIN_VALUE;
        boolean tie = false;
        for (BattleTeam t : b.teams()) {
            int s = b.scoreOf(t).score;
            if (s > topScore) {
                topScore = s;
                top = t;
                tie = false;
            } else if (s == topScore) {
                tie = true;
            }
        }
        return tie ? null : top;
    }

    private List<Component> buildLines(Player p) {
        BattleTeam team = teamManager.get(p);
        int totalPoints = pointManager.all().size();
        int teamPoints = 0;
        for (CapturePoint point : pointManager.all()) {
            if (point.owner() == team) {
                teamPoints++;
            }
        }
        PlayerStats stats = battle.statsOf(p.getUniqueId(), p.getName());

        List<Component> lines = new ArrayList<>();
        lines.add(Messages.raw("<gold>" + battle.name() + "</gold>"));
        lines.add(Messages.raw("<green>Время: <white>" + formatTime(battle.remainingSeconds())));
        lines.add(Messages.raw("<gray>────────"));
        lines.add(Messages.raw("<yellow>Точки: <white>" + totalPoints + " <gray>(<green>" + teamPoints + "<gray>)"));
        lines.add(Messages.raw("<gray>──────"));
        for (BattleTeam t : battle.teams()) {
            Battle.TeamScore ts = battle.scoreOf(t);
            int online = teamManager.onlineMembers(t).size();
            lines.add(Messages.raw(t.colorize(t.displayName()) + ": <white>" + ts.score + " <gray>(" + online + ")"));
        }
        lines.add(Messages.raw("<gray>────"));
        if (team != null) {
            lines.add(Messages.raw("<gold>Команда: " + team.colorize(team.displayName())));
        }
        lines.add(Messages.raw("<green>Убийств: <white>" + stats.kills() + "   <red>Смертей: <white>" + stats.deaths()));
        return lines;
    }

    private String weaponName(Player killer) {
        if (killer == null) {
            return "—";
        }
        ItemStack item = killer.getInventory().getItemInMainHand();
        if (item == null || item.getType().isAir()) {
            return "руками";
        }
        Component name = item.displayName();
        if (name == null) {
            return item.getType().name().toLowerCase();
        }
        String s = LEGACY.serialize(name);
        return s.isEmpty() ? item.getType().name().toLowerCase() : s;
    }

    private String formatTeams(Set<BattleTeam> teams) {
        StringBuilder sb = new StringBuilder();
        for (BattleTeam t : teams) {
            if (sb.length() > 0) {
                sb.append("<gray>, </gray>");
            }
            sb.append(t.colorize(t.displayName()));
        }
        return sb.toString();
    }

    private String formatTime(int seconds) {
        return String.format("%d:%02d", seconds / 60, seconds % 60);
    }

    private void broadcast(Component component) {
        plugin.getServer().broadcast(component);
    }
}
