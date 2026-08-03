package battle.manager;

import battle.model.CapturePoint;
import org.bukkit.Location;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Список точек захвата. Данные только в памяти.
 */
public class PointManager {

    private final List<CapturePoint> points = new ArrayList<>();

    public CapturePoint add(String name, Location location) {
        CapturePoint point = new CapturePoint(name, location);
        points.add(point);
        return point;
    }

    public boolean remove(String name) {
        return points.removeIf(p -> p.name().equalsIgnoreCase(name));
    }

    public CapturePoint get(String name) {
        for (CapturePoint p : points) {
            if (p.name().equalsIgnoreCase(name)) {
                return p;
            }
        }
        return null;
    }

    public boolean nameExists(String name) {
        return get(name) != null;
    }

    public List<CapturePoint> all() {
        return Collections.unmodifiableList(points);
    }

    /** Сброс всех точек к нейтральному состоянию (при старте битвы). */
    public void resetAll() {
        for (CapturePoint p : points) {
            p.reset();
        }
    }
}
