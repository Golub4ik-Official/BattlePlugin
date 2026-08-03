package battle.manager;

import battle.BattleTeam;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.Team;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Назначение игроков в команды. Данные только в памяти.
 */
public class TeamManager {

    private final Map<UUID, BattleTeam> assignments = new HashMap<>();
    private final Set<UUID> frozen = new HashSet<>();
    private final Map<UUID, Float> walkSpeeds = new HashMap<>();
    private final Map<UUID, Float> flySpeeds = new HashMap<>();

    public void set(Player player, BattleTeam team) {
        assignments.put(player.getUniqueId(), team);
        refreshColoredNames();
    }

    public void remove(Player player) {
        assignments.remove(player.getUniqueId());
        clearFrozen(player);
        refreshColoredNames();
    }

    public BattleTeam get(Player player) {
        return assignments.get(player.getUniqueId());
    }

    public BattleTeam get(UUID uuid) {
        return assignments.get(uuid);
    }

    public Map<UUID, BattleTeam> all() {
        return assignments;
    }

    /** Замораживает всех онлайн-игроков команды (запрет движения). */
    public void freeze(BattleTeam team) {
        for (Player p : onlineMembers(team)) {
            freeze(p);
        }
    }

    /** Замораживает одного игрока. */
    public void freeze(Player p) {
        UUID id = p.getUniqueId();
        if (!frozen.add(id)) {
            return;
        }
        walkSpeeds.put(id, p.getWalkSpeed());
        flySpeeds.put(id, p.getFlySpeed());
        p.setWalkSpeed(0.0f);
        p.setFlySpeed(0.0f);
    }

    /** Размораживает всех онлайн-игроков команды. */
    public void unfreeze(BattleTeam team) {
        for (Player p : onlineMembers(team)) {
            unfreeze(p);
        }
    }

    /** Размораживает одного игрока. */
    public void unfreeze(Player p) {
        UUID id = p.getUniqueId();
        if (!frozen.remove(id)) {
            return;
        }
        Float walk = walkSpeeds.remove(id);
        Float fly = flySpeeds.remove(id);
        p.setWalkSpeed(walk == null ? 0.2f : walk);
        p.setFlySpeed(fly == null ? 0.1f : fly);
    }

    /** Заморожен ли игрок. */
    public boolean isFrozen(Player player) {
        return frozen.contains(player.getUniqueId());
    }

    /** Заморожен ли игрок по UUID. */
    public boolean isFrozen(UUID uuid) {
        return frozen.contains(uuid);
    }

    /** Очищает заморозку у игрока (при выходе). */
    public void clearFrozen(Player player) {
        unfreeze(player);
    }

    /** Онлайн-игроки заданной команды. */
    public List<Player> onlineMembers(BattleTeam team) {
        return Bukkit.getOnlinePlayers().stream()
                .filter(p -> get(p) == team)
                .map(p -> (Player) p)
                .toList();
    }

    private static String teamKey(BattleTeam team) {
        return "battle-" + team.name().toLowerCase(java.util.Locale.ROOT);
    }

    /**
     * Перекрашивает ник игроков в цвет их команды на дашборде каждого онлайн-игрока.
     * Работает для всех зрителей независимо от их личного scoreboard.
     */
    public void refreshColoredNames() {
        for (Player viewer : Bukkit.getOnlinePlayers()) {
            Scoreboard sb = viewer.getScoreboard();
            if (sb == null) {
                continue;
            }
            for (BattleTeam team : BattleTeam.values()) {
                Team t = sb.getTeam(teamKey(team));
                if (t == null) {
                    t = sb.registerNewTeam(teamKey(team));
                }
                if (t.getColor() != team.colorName()) {
                    t.setColor(team.colorName());
                }
                boolean changed = false;
                for (String entry : t.getEntries()) {
                    if (hasEntry(entry, team)) {
                        continue;
                    }
                    t.removeEntry(entry);
                    changed = true;
                }
                for (Player member : onlineMembers(team)) {
                    String name = member.getName();
                    if (!t.hasEntry(name)) {
                        t.addEntry(name);
                        changed = true;
                    }
                }
            }
        }
    }

    private boolean hasEntry(String entry, BattleTeam team) {
        Player player = Bukkit.getPlayerExact(entry);
        return player != null && get(player) == team;
    }

    /** Убирает цветовые команды со всех scoreboard игроков (при отключении/уборке). */
    public void clearColoredNames() {
        for (Player viewer : Bukkit.getOnlinePlayers()) {
            Scoreboard sb = viewer.getScoreboard();
            if (sb == null) {
                continue;
            }
            for (BattleTeam team : BattleTeam.values()) {
                Team t = sb.getTeam(teamKey(team));
                if (t != null) {
                    t.unregister();
                }
            }
        }
    }
}
