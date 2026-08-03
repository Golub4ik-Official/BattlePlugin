package battle.model;

import battle.BattleTeam;

/**
 * Одно событие статистики битвы (убийство, захват, потеря и т.д.).
 */
public class StatEvent {

    public enum Type {
        KILL, DEATH, TEAMKILL, POINT_START, POINT_CAPTURED, POINT_LOST, POINT_HOLD
    }

    public final Type type;
    public final int timeSeconds;
    public final String killer;
    public final String victim;
    public final String weapon;
    public final BattleTeam team;
    public final String point;
    public final int scoreDelta;

    public StatEvent(Type type, int timeSeconds, String killer, String victim, String weapon,
                     BattleTeam team, String point, int scoreDelta) {
        this.type = type;
        this.timeSeconds = timeSeconds;
        this.killer = killer;
        this.victim = victim;
        this.weapon = weapon;
        this.team = team;
        this.point = point;
        this.scoreDelta = scoreDelta;
    }

    public static StatEvent kill(String killer, String victim, String weapon, BattleTeam team, int time, int delta) {
        return new StatEvent(Type.KILL, time, killer, victim, weapon, team, null, delta);
    }

    public static StatEvent death(String victim, BattleTeam team, int time, int delta) {
        return new StatEvent(Type.DEATH, time, null, victim, null, team, null, delta);
    }

    public static StatEvent teamkill(String killer, String victim, String weapon, BattleTeam team, int time, int delta) {
        return new StatEvent(Type.TEAMKILL, time, killer, victim, weapon, team, null, delta);
    }

    public static StatEvent pointStart(String point, BattleTeam team, int time) {
        return new StatEvent(Type.POINT_START, time, null, null, null, team, point, 0);
    }

    public static StatEvent pointCaptured(String point, BattleTeam team, int time) {
        return new StatEvent(Type.POINT_CAPTURED, time, null, null, null, team, point, 0);
    }

    public static StatEvent pointLost(String point, BattleTeam team, int time) {
        return new StatEvent(Type.POINT_LOST, time, null, null, null, team, point, 0);
    }

    public static StatEvent pointHold(String point, BattleTeam team, int time, int delta) {
        return new StatEvent(Type.POINT_HOLD, time, null, null, null, team, point, delta);
    }
}
