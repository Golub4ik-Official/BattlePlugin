package battle.model;

import battle.BattleTeam;
import org.bukkit.Location;

/**
 * Точка захвата.
 *
 * <p>Машина состояний:
 * <ul>
 *   <li>НЕЙТРАЛЬНАЯ — никого рядом / прогресс 0, владельца нет;</li>
 *   <li>ЗАХВАТЫВАЕТСЯ — доминирующая команда строит (или отбивает) прогресс;</li>
 *   <li>ЗАХВАЧЕНА — прогресс достиг максимума, есть владелец.</li>
 * </ul>
 * При равенстве числа игроков разных команд прогресс замирает.
 */
public class CapturePoint {

    public enum State {
        NEUTRAL, CAPTURING, CAPTURED
    }

    private final String name;
    private final Location location;
    private BattleTeam owner;
    private BattleTeam capturingTeam;
    private int progress;

    public CapturePoint(String name, Location location) {
        this.name = name;
        this.location = location.clone();
    }

    public String name() {
        return name;
    }

    public Location location() {
        return location;
    }

    public BattleTeam owner() {
        return owner;
    }

    /** Команда, которая в данный момент доминирует на точке (может быть null). */
    public BattleTeam capturingTeam() {
        return capturingTeam;
    }

    public int progress() {
        return progress;
    }

    public State state(int captureTime) {
        if (owner != null && progress >= captureTime) {
            return State.CAPTURED;
        }
        if (owner == null && progress == 0) {
            return State.NEUTRAL;
        }
        return State.CAPTURING;
    }

    /** Сброс состояния к нейтральному (при старте битвы). */
    public void reset() {
        owner = null;
        capturingTeam = null;
        progress = 0;
    }

    /** Результат одного шага обновления (для статистики и анонсов). */
    public static class Result {
        public BattleTeam startedBy;
        public BattleTeam capturedBy;
        public BattleTeam lostBy;
    }

    /**
     * Обновляет состояние точки за один шаг (1 секунда).
     *
     * @param dominant    команда со строгим большинством внутри радиуса, или {@code null}
     * @param captureTime время захвата в секундах
     */
    public Result update(BattleTeam dominant, int captureTime) {
        Result res = new Result();
        BattleTeam previousOwner = owner;

        if (dominant == null) {
            // Ничья или никого нет — прогресс замирает.
            return res;
        }
        capturingTeam = dominant;

        if (owner == null) {
            // Нейтральная/спорная точка — команда строит прогресс.
            if (progress == 0) {
                res.startedBy = dominant;
            }
            progress++;
            if (progress >= captureTime) {
                progress = captureTime;
                owner = dominant;
                res.capturedBy = dominant;
            }
        } else if (owner == dominant) {
            // Точка принадлежит доминирующей команде — остаётся захваченной.
            progress = captureTime;
        } else {
            // Точка захвачена другой командой — отбиваем.
            if (progress >= captureTime) {
                res.startedBy = dominant;
            }
            progress--;
            if (progress <= 0) {
                progress = 0;
                owner = dominant;
                res.lostBy = previousOwner;
                res.capturedBy = dominant;
                res.startedBy = dominant;
            }
        }
        return res;
    }
}
