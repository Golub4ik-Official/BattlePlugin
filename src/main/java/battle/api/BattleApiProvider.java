package battle.api;

import org.bukkit.Bukkit;
import org.bukkit.plugin.ServicesManager;

/**
 * Статический доступ к API BattlePlugin для других плагинов.
 *
 * <pre>{@code
 * BattleApi api = BattleApiProvider.get();
 * if (api != null) {
 *     // ...
 * }
 * }</pre>
 */
public final class BattleApiProvider {

    private static final Class<BattleApi> API_CLASS = BattleApi.class;

    private BattleApiProvider() {
    }

    /**
     * Возвращает экземпляр API, или {@code null}, если BattlePlugin не установлен
     * или ещё не загружен.
     */
    public static BattleApi get() {
        ServicesManager services = Bukkit.getServicesManager();
        if (services == null) {
            return null;
        }
        return services.load(API_CLASS);
    }
}
