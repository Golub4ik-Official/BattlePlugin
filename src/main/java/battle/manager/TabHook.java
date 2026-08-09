package battle.manager;

import battle.BattlePlugin;
import battle.BattleTeam;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

/**
 * Интеграция с TAB API v6 для окраски ников в табе (tablist) и над головой (nametag).
 * TAB является softdepend: если плагин не установлен, все методы превращаются в no-op,
 * и BattlePlugin работает через обычные scoreboard-темы.
 *
 * <p>Все обращения к классам TAB API спрятаны в телах методов и защищены
 * try/catch, чтобы главный класс плагина не зависел от TAB на этапе загрузки.</p>
 */
public final class TabHook {

    private static BattlePlugin plugin;
    private static TeamManager teamManager;
    private static boolean available = false;
    private static Object tabLoadHandler;

    private TabHook() {
    }

    /** Доступна ли интеграция с TAB (плагин установлен и API загрузился). */
    public static boolean isAvailable() {
        return available;
    }

    /**
     * Проверяет наличие TAB и регистрирует слушатель загрузки игрока:
     * после реконнекта TAB сбрасывает свои значения, поэтому при входе
     * игрока, уже состоящего в команде, окраска применяется заново.
     */
    public static void init(BattlePlugin plugin, TeamManager teamManager) {
        TabHook.plugin = plugin;
        TabHook.teamManager = teamManager;
        available = false;
        try {
            if (Bukkit.getPluginManager().getPlugin("TAB") == null) {
                return;
            }
            me.neznamy.tab.api.TabAPI api = me.neznamy.tab.api.TabAPI.getInstance();
            if (api == null) {
                return;
            }
            tabLoadHandler = new me.neznamy.tab.api.event.EventHandler<me.neznamy.tab.api.event.player.PlayerLoadEvent>() {
                @Override
                public void handle(me.neznamy.tab.api.event.player.PlayerLoadEvent event) {
                    me.neznamy.tab.api.TabPlayer tabPlayer = event.getPlayer();
                    Object handle = tabPlayer.getPlayer();
                    if (!(handle instanceof Player player)) {
                        return;
                    }
                    Bukkit.getScheduler().runTask(TabHook.plugin, () -> {
                        BattleTeam team = TabHook.teamManager.get(player);
                        if (team != null) {
                            TabHook.teamManager.refresh(player);
                        }
                    });
                }
            };
            api.getEventBus().register(
                    me.neznamy.tab.api.event.player.PlayerLoadEvent.class,
                    (me.neznamy.tab.api.event.EventHandler) tabLoadHandler);
            available = true;
            plugin.getLogger().info("Интеграция с TAB активна: цвет ников в табе и над головой.");
        } catch (Throwable t) {
            available = false;
            plugin.getLogger().warning("Интеграция с TAB недоступна: " + t);
        }
    }

    /**
     * Окрашивает ник игрока в цвет его команды в табе и над головой.
     * Префикс равен цвету команды: TAB определяет цвет ника по последнему
     * цветовому коду префикса и применяет его и к имени в табе, и к nametag.
     */
    public static void apply(Player player, BattleTeam team) {
        if (!available) {
            return;
        }
        try {
            me.neznamy.tab.api.TabAPI api = me.neznamy.tab.api.TabAPI.getInstance();
            me.neznamy.tab.api.TabPlayer tabPlayer = api.getPlayer(player.getUniqueId());
            if (tabPlayer == null) {
                return;
            }
            String color = team.miniTag();
            api.getTabListFormatManager().setPrefix(tabPlayer, color);
            api.getNameTagManager().setPrefix(tabPlayer, color);
        } catch (Throwable ignored) {
        }
    }

    /** Сбрасывает кастомные значения TAB (возвращает префиксы из конфига). */
    public static void reset(Player player) {
        if (!available) {
            return;
        }
        try {
            me.neznamy.tab.api.TabAPI api = me.neznamy.tab.api.TabAPI.getInstance();
            me.neznamy.tab.api.TabPlayer tabPlayer = api.getPlayer(player.getUniqueId());
            if (tabPlayer == null) {
                return;
            }
            api.getTabListFormatManager().setPrefix(tabPlayer, null);
            api.getNameTagManager().setPrefix(tabPlayer, null);
        } catch (Throwable ignored) {
        }
    }

    /** Отключает интеграцию и снимает слушатель (при выключении плагина). */
    public static void disable() {
        if (available && tabLoadHandler != null) {
            try {
                me.neznamy.tab.api.TabAPI.getInstance().getEventBus()
                        .unregister((me.neznamy.tab.api.event.EventHandler) tabLoadHandler);
            } catch (Throwable ignored) {
            }
        }
        available = false;
        plugin = null;
        teamManager = null;
        tabLoadHandler = null;
    }
}
