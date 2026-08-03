package battle.manager;

import battle.BattlePlugin;
import battle.BattleTeam;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Интеграция с аддоном pv-addon-groups (PlasmoVoice).
 *
 * <p>Во время битвы на каждую команду автоматически создаётся отдельный голосовой
 * канал: участники подключаются к нему при старте битвы, при входе на сервер
 * или при смене команды. По завершении битвы все каналы удаляются.</p>
 *
 * <h3>Почему тут используется рефлексия?</h3>
 * <p>pv-addon-groups — сторонний плагин (softdepend). Чтобы BattlePlugin
 * компилировался и работал даже без него, мы не импортируем классы аддона
 * напрямую, а обращаемся к ним через {@link Class#forName}, {@link Method#invoke}
 * и {@link Field#get}. Все вызовы обёрнуты в try/catch — при любом сбое
 * интеграция просто отключается, а плагин продолжает работать.</p>
 *
 * <h3>Как устроены голосовые каналы в pv-addon-groups</h3>
 * <p>Канал (группа) — это объект {@code Group} с UUID, названием, паролем
 * и списком игроков. Группы хранятся в {@code GroupsManager.groups} —
 * обычная {@code Map<UUID, Group>}. Подключение игрока — метод
 * {@code GroupsManager.join(voicePlayer, group)}, отключение — {@code leave}.</p>
 *
 * <p>Мы создаём группы прямо в памяти аддона (через конструктор {@code Group}
 * и запись в карту), как это делает встроенная команда {@code /groups create}.
 * Это позволяет управлять группами программно, не дёргая команды.</p>
 *
 * <h3>Закрытость каналов</h3>
 * <p>Каждая боевая группа создаётся со случайным паролем. При ручном вводе
 * {@code /groups join <id>} аддон требует пароль (и противник его не знает).
 * Наше авто-подключение через {@code GroupsManager.join()} пароль не проверяет —
 * поэтому участники команды заходят без проблем.</p>
 */
public final class VoiceGroupsHook {

    /*
     * Полные имена классов pv-addon-groups и PlasmoVoice.
     * Не можем импортировать их напрямую — используем строки для Class.forName().
     */
    private static final String GROUP_CLASS = "su.plo.voice.groups.group.Group";
    private static final String VOICE_PLAYER_CLASS = "su.plo.voice.api.server.player.VoicePlayer";
    private static final String GAME_PROFILE_CLASS = "su.plo.slib.api.entity.player.McGameProfile";

    private final BattlePlugin plugin;
    private final TeamManager teamManager;

    /** Включена ли интеграция в конфиге (battle.voice-groups.enabled). */
    private boolean enabled = true;

    /** Префикс названия канала из конфига (например, "Битва"). */
    private String channelPrefix = "Битва";

    /** Защищать ли каналы случайным паролем (из конфига). */
    private boolean passwordProtected = true;

    /**
     * {@code true} — аддон pv-addon-groups установлен, конфиг включён,
     * и все внутренние объекты (groupManager, voiceServer) успешно получены.
     */
    private boolean available = false;

    /*
     * Объекты аддона, полученные через рефлексию.
     * Хранятся как Object, потому что классы аддона нам недоступны при компиляции.
     *
     * groupManager — центральный менеджер групп (GroupsManager).
     *   Через него: join, leave, kick, ban, deleteGroup, getSourceLine.
     *
     * voiceServer — сервер PlasmoVoice (PlasmoBaseVoiceServer).
     *   Через него: getPlayerManager → getPlayerByInstance (получить VoicePlayer по Bukkit Player).
     */
    private Object groupManager;
    private Object voiceServer;

    /** Классы аддона, загруженные через Class.forName() для рефлексивных вызовов. */
    private Class<?> groupClass;
    private Class<?> voicePlayerClass;
    private Class<?> gameProfileClass;

    /**
     * Активные голосовые каналы текущей битвы.
     * Ключ — команда битвы, значение — объект {@code Group} из pv-addon-groups.
     */
    private final Map<BattleTeam, Object> teamGroups = new java.util.EnumMap<>(BattleTeam.class);

    /**
     * Создаёт хук интеграции с pv-addon-groups.
     *
     * @param plugin      главный класс BattlePlugin (для логов и конфига)
     * @param teamManager менеджер команд (для получения онлайн-участников)
     */
    public VoiceGroupsHook(BattlePlugin plugin, TeamManager teamManager) {
        this.plugin = plugin;
        this.teamManager = teamManager;
        readConfig();
        tryConnectToAddon();
    }

    // ─── Конфигурация ─────────────────────────────────────────────────────

    /** Читает настройки голосовых каналов из config.yml. */
    private void readConfig() {
        enabled = plugin.getConfig().getBoolean("battle.voice-groups.enabled", true);
        channelPrefix = plugin.getConfig().getString("battle.voice-groups.channel-prefix", "Битва");
        passwordProtected = plugin.getConfig().getBoolean("battle.voice-groups.password-protected", true);
    }

    /**
     * Перечитывает конфиг и заново подключается к аддону.
     * Вызывается при {@code /battle reload}.
     */
    public void reload() {
        readConfig();
        tryConnectToAddon();
    }

    /** Доступна ли интеграция (аддон установлен, конфиг включён, API поднялся). */
    public boolean isAvailable() {
        return available;
    }

    // ─── Подключение к аддону ────────────────────────────────────────────

    /**
     * Подключается к pv-addon-groups через рефлексию.
     *
     * <p>Путь к нужным объектам:</p>
     * <ol>
     *   <li>Плагин pv-addon-groups (BukkitEntryPoint) хранит аддон в приватном поле {@code pvAddonGroups}.</li>
     *   <li>Из аддона (GroupsAddon) берём публичное поле {@code groupManager} (GroupsManager)</li>
     *   <li>и метод {@code getVoiceServer()} → PlasmoBaseVoiceServer.</li>
     * </ol>
     *
     * <p>Если хоть один шаг падает — интеграция остаётся unavailable, плагин работает без голоса.</p>
     */
    private void tryConnectToAddon() {
        available = false;
        groupManager = null;
        voiceServer = null;

        if (!enabled) {
            return;
        }

        try {
            // Шаг 1: находим плагин pv-addon-groups
            org.bukkit.plugin.Plugin addonPlugin = Bukkit.getPluginManager().getPlugin("pv-addon-groups");
            if (addonPlugin == null) {
                return;
            }

            // Загружаем классы для рефлексии (они существуют, раз плагин установлен)
            groupClass = Class.forName(GROUP_CLASS);
            voicePlayerClass = Class.forName(VOICE_PLAYER_CLASS);
            gameProfileClass = Class.forName(GAME_PROFILE_CLASS);

            // Шаг 2: из BukkitEntryPoint достаём объект GroupsAddon (приватное поле pvAddonGroups)
            Field addonField = addonPlugin.getClass().getDeclaredField("pvAddonGroups");
            addonField.setAccessible(true);
            Object addon = addonField.get(addonPlugin);
            if (addon == null) {
                return;
            }

            // Шаг 3: из GroupsAddon берём groupManager и voiceServer
            Class<?> addonClass = addon.getClass();
            groupManager = addonClass.getField("groupManager").get(addon);
            Method getVoiceServer = addonClass.getMethod("getVoiceServer");
            voiceServer = getVoiceServer.invoke(addon);

            if (groupManager == null || voiceServer == null) {
                groupManager = null;
                voiceServer = null;
                return;
            }

            available = true;
            plugin.getLogger().info("Интеграция с pv-addon-groups активна: голосовые каналы команд.");

        } catch (Throwable t) {
            available = false;
            groupManager = null;
            voiceServer = null;
            plugin.getLogger().warning("Интеграция с pv-addon-groups недоступна: " + t);
        }
    }

    // ─── Публичный API — вызывается из BattleManager и BattleListener ─────

    /**
     * Создаёт голосовой канал для каждой команды битвы и подключает всех онлайн-участников.
     *
     * <p>Вызывается из {@link BattleManager#beginBattle} при старте битвы.</p>
     *
     * @param name  название битвы (для логов)
     * @param teams команды, участвующие в битве
     */
    public void startBattle(String name, Set<BattleTeam> teams) {
        if (!available) {
            return;
        }
        teamGroups.clear();
        try {
            for (BattleTeam team : teams) {
                Object group = createGroup(team);
                if (group == null) {
                    continue;
                }
                teamGroups.put(team, group);

                // Подключаем всех онлайн-игроков этой команды в только что созданный канал
                for (Player player : teamManager.onlineMembers(team)) {
                    joinPlayerToGroup(player, group);
                }
            }
        } catch (Throwable ignored) {
        }
    }

    /**
     * Подключает игрока к голосовому каналу его команды.
     *
     * <p>Вызывается из {@link battle.listener.BattleListener} при входе игрока
     * на сервер или при смене его команды во время битвы.</p>
     *
     * @param player игрок, которого нужно подключить
     * @param team   команда игрока
     */
    public void joinTeam(Player player, BattleTeam team) {
        if (!available) {
            return;
        }
        Object group = findOrCreateGroup(team);
        if (group != null) {
            joinPlayerToGroup(player, group);
        }
    }

    /**
     * Завершает все голосовые каналы битвы: отключает всех игроков и удаляет группы.
     *
     * <p>Вызывается из {@link BattleManager#endBattle} при завершении или остановке битвы.</p>
     */
    public void endBattle() {
        if (!available) {
            return;
        }
        try {
            for (Object group : teamGroups.values()) {
                // Сначала отключаем всех онлайн-игроков из этой группы
                removeOnlinePlayersFrom(group);

                // Затем удаляем саму группу из аддона
                Method deleteGroup = groupManager.getClass().getMethod("deleteGroup", groupClass);
                deleteGroup.invoke(groupManager, group);
            }
        } catch (Throwable ignored) {
        }
        teamGroups.clear();
    }

    // ─── Внутренние методы ────────────────────────────────────────────────

    /**
     * Возвращает существующий канал команды, или создаёт новый, если старый был удалён.
     * Это защита от ситуации, когда кто-то удалил группу через /groups delete.
     */
    private Object findOrCreateGroup(BattleTeam team) {
        Object existing = teamGroups.get(team);
        if (existing != null && groupStillExists(existing)) {
            return existing;
        }
        Object fresh = createGroup(team);
        if (fresh != null) {
            teamGroups.put(team, fresh);
        }
        return fresh;
    }

    /**
     * Проверяет, что группа всё ещё зарегистрирована в pv-addon-groups.
     * Сравниваем UUID нашей группы с ключами в карте groups аддона.
     */
    private boolean groupStillExists(Object group) {
        try {
            UUID id = (UUID) groupClass.getMethod("getId").invoke(group);
            Map<UUID, ?> allGroups = invokeMap(groupManager, "getGroups");
            return allGroups.containsKey(id);
        } catch (Throwable t) {
            return false;
        }
    }

    /**
     * Создаёт и регистрирует новый голосовой канал для команды.
     *
     * <p>Процесс:</p>
     * <ol>
     *   <li>Создаём ServerPlayerSet (контейнер игроков для аудио-линии) через playerSetManager.</li>
     *   <li>Создаём объект Group через конструктор: (playerSet, uuid, name, password, persistent, empty set, empty list, null owner).</li>
     *   <li>Добавляем группу в карту groupManager.groups — теперь она «официально» существует.</li>
     * </ol>
     *
     * @return объект Group, или {@code null} при ошибке
     */
    private Object createGroup(BattleTeam team) {
        try {
            Class<?> playerSetClass = Class.forName("su.plo.voice.api.server.audio.line.ServerPlayerSet");

            // Создаём ServerPlayerSet: groupManager → sourceLine → playerSetManager → createBroadcastSet()
            Object sourceLine = invoke(groupManager, "getSourceLine");
            Object playerSetManager = invoke(sourceLine, "getPlayerSetManager");
            Object playerSet = invoke(playerSetManager, "createBroadcastSet");

            // Конструктор Group: (playerSet, uuid, name, password, persistent, emptyPlayersSet, emptyBannedList, owner)
            Constructor<?> ctor = groupClass.getConstructor(
                    playerSetClass, UUID.class, String.class, String.class,
                    boolean.class, Set.class, List.class, gameProfileClass);

            String password = passwordProtected ? generateRandomPassword() : null;
            Object group = ctor.newInstance(
                    playerSet,
                    UUID.randomUUID(),
                    channelName(team),    // например, "Битва · Красные"
                    password,              // null — канал открытый; строка — защищён паролем
                    true,                  // persistent: true — канал не удалится сам, мы удалим его при endBattle()
                    new HashSet<>(),       // пустой список UUID участников
                    new ArrayList<>(),     // пустой список забаненных профилей
                    null                   // без владельца
            );

            // Регистрируем группу в аддоне — добавляем в его карту groups.
            // Теперь группа видна в /groups browse и вообще считается существующей.
            Map<UUID, Object> allGroups = invokeMap(groupManager, "getGroups");
            UUID groupId = (UUID) groupClass.getMethod("getId").invoke(group);
            allGroups.put(groupId, group);

            return group;
        } catch (Throwable t) {
            plugin.getLogger().warning("Не удалось создать голосовой канал для "
                    + team.displayName() + ": " + t);
            return null;
        }
    }

    /**
     * Формирует название голосового канала: "{префикс} · {название команды}".
     * Например: "Битва · Красные".
     */
    private String channelName(BattleTeam team) {
        String base = (channelPrefix == null || channelPrefix.isBlank())
                ? team.displayName()
                : channelPrefix + " · " + team.displayName();
        return base.replace('§', ' ');
    }

    /*
     * Генерация случайного пароля для защиты канала.
     *
     * Пароль нужен только для блокировки ручного входа через /groups join.
     * Наш метод joinPlayerToGroup() подключает напрямую через GroupsManager.join(),
     * который пароль НЕ проверяет — поэтому участники заходят без пароля,
     * а посторонние через /groups join получить доступ не могут.
     */
    private static final char[] PASSWORD_CHARS =
            "abcdefghijkmnopqrstuvwxyzABCDEFGHJKLMNPQRSTUVWXYZ23456789".toCharArray();
    private static final SecureRandom PASSWORD_RANDOM = new SecureRandom();

    /** Генерирует случайный пароль длиной 12 символов (без похожих символов типа 0/O, 1/l/I). */
    private String generateRandomPassword() {
        StringBuilder sb = new StringBuilder(12);
        for (int i = 0; i < 12; i++) {
            sb.append(PASSWORD_CHARS[PASSWORD_RANDOM.nextInt(PASSWORD_CHARS.length)]);
        }
        return sb.toString();
    }

    /**
     * Подключает Bukkit-игрока в голосовую группу через API pv-addon-groups.
     *
     * <p>Сначала преобразуем Bukkit Player → VoicePlayer (объект PlasmoVoice),
     * затем вызываем {@code GroupsManager.join(voicePlayer, group)}.</p>
     *
     * <p>Если у игрока нет PlasmoVoice (не подключил голосовой чат) —
     * toVoicePlayer() вернёт null, и ничего не произойдёт.</p>
     */
    private void joinPlayerToGroup(Player player, Object group) {
        try {
            Object voicePlayer = toVoicePlayer(player);
            if (voicePlayer == null) {
                return;
            }
            Method join = groupManager.getClass().getMethod("join", voicePlayerClass, groupClass);
            join.invoke(groupManager, voicePlayer, group);
        } catch (Throwable ignored) {
        }
    }

    /**
     * Отключает всех онлайн-игроков от голосовой группы.
     * Вызывается перед удалением группы при endBattle().
     */
    private void removeOnlinePlayersFrom(Object group) {
        try {
            Method getOnlinePlayers = groupClass.getMethod("getOnlinePlayers");
            @SuppressWarnings("unchecked")
            Collection<Object> online = (Collection<Object>) getOnlinePlayers.invoke(group);

            Method leave = groupManager.getClass().getMethod("leave", voicePlayerClass);
            for (Object voicePlayer : new ArrayList<>(online)) {
                leave.invoke(groupManager, voicePlayer);
            }
        } catch (Throwable ignored) {
        }
    }

    /**
     * Преобразует Bukkit Player в VoicePlayer (объект PlasmoVoice).
     *
     * <p>Цепочка вызовов через рефлексию:</p>
     * {@code voiceServer → getPlayerManager() → getPlayerByInstance(bukkitPlayer)}.
     *
     * @return VoicePlayer, или {@code null}, если игрок не использует голосовой чат
     */
    private Object toVoicePlayer(Player player) {
        try {
            Object playerManager = invoke(voiceServer, "getPlayerManager");
            Method getPlayerByInstance = playerManager.getClass()
                    .getMethod("getPlayerByInstance", Object.class);
            return getPlayerByInstance.invoke(playerManager, player);
        } catch (Throwable ignored) {
            return null;
        }
    }

    // ─── Вспомогательные методы рефлексии ────────────────────────────────

    /** Вызывает безаргументный метод без типов на объекте через рефлексию. */
    private static Object invoke(Object target, String methodName) throws Exception {
        return target.getClass().getMethod(methodName).invoke(target);
    }

    /** Вызывает безаргументный метод, возвращающий Map, через рефлексию. */
    @SuppressWarnings("unchecked")
    private static Map<UUID, Object> invokeMap(Object target, String methodName) throws Exception {
        return (Map<UUID, Object>) target.getClass().getMethod(methodName).invoke(target);
    }
}
