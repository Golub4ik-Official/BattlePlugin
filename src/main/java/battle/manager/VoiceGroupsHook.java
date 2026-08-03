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
 * Интеграция с pv-addon-groups (PlasmoVoice): автосоздание голосового канала
 * для каждой команды битвы и подключение участников.
 *
 * <p>Аддон является softdepend: если pv-addon-groups не установлен или битвы
 * не используют голосовые каналы — все методы превращаются в no-op.</p>
 *
 * <p>Все обращения к классам pv-addon-groups и PlasmoVoice API выполняются
 * через рефлексию и защищены try/catch, чтобы главный класс плагина не
 * зависел от аддона на этапе загрузки.</p>
 */
public final class VoiceGroupsHook {

    private static final String GROUPS_CLASS = "su.plo.voice.groups.group.Group";
    private static final String VOICE_PLAYER_CLASS = "su.plo.voice.api.server.player.VoicePlayer";
    private static final String GAME_PROFILE_CLASS = "su.plo.slib.api.entity.player.McGameProfile";

    private final BattlePlugin plugin;
    private final TeamManager teamManager;

    private boolean available = false;
    private boolean enabled = true;
    private String channelPrefix = "Битва";
    private boolean passwordProtected = true;

    private Object groupManager;
    private Object voiceServer;
    private Class<?> groupClass;
    private Class<?> voicePlayerClass;
    private Class<?> gameProfileClass;

    /** Каналы активной битвы: команда -> группа (объект {@code Group} аддона). */
    private final Map<BattleTeam, Object> teamGroups = new java.util.EnumMap<>(BattleTeam.class);
    private String battleName = "";

    public VoiceGroupsHook(BattlePlugin plugin, TeamManager teamManager) {
        this.plugin = plugin;
        this.teamManager = teamManager;
        readConfig();
        init();
    }

    private void readConfig() {
        enabled = plugin.getConfig().getBoolean("battle.voice-groups.enabled", true);
        channelPrefix = plugin.getConfig().getString("battle.voice-groups.channel-prefix", "Битва");
        passwordProtected = plugin.getConfig().getBoolean("battle.voice-groups.password-protected", true);
    }

    /** Повторно читает конфиг и перепроверяет доступность аддона (команда /battle reload). */
    public void reload() {
        readConfig();
        init();
    }

    /** Доступна ли интеграция (аддон установлен, конфиг включён, API поднялся). */
    public boolean isAvailable() {
        return available;
    }

    private void init() {
        available = false;
        groupManager = null;
        voiceServer = null;
        if (!enabled) {
            return;
        }
        try {
            org.bukkit.plugin.Plugin addonPlugin = Bukkit.getPluginManager().getPlugin("pv-addon-groups");
            if (addonPlugin == null) {
                return;
            }
            groupClass = Class.forName(GROUPS_CLASS);
            voicePlayerClass = Class.forName(VOICE_PLAYER_CLASS);
            gameProfileClass = Class.forName(GAME_PROFILE_CLASS);

            // BukkitEntryPoint хранит аддон в приватном поле pvAddonGroups.
            Field field = addonPlugin.getClass().getDeclaredField("pvAddonGroups");
            field.setAccessible(true);
            Object addon = field.get(addonPlugin);
            if (addon == null) {
                return;
            }
            Class<?> addonClass = addon.getClass();
            Field gmField = addonClass.getField("groupManager");
            groupManager = gmField.get(addon);
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

    /** Создаёт канал для каждой команды битвы и подключает всех онлайн-участников. */
    public void startBattle(String name, Set<BattleTeam> teams) {
        if (!available) {
            return;
        }
        battleName = name == null ? "" : name;
        teamGroups.clear();
        try {
            for (BattleTeam team : teams) {
                Object group = createGroup(team);
                if (group == null) {
                    continue;
                }
                teamGroups.put(team, group);
                for (Player player : teamManager.onlineMembers(team)) {
                    join(player, group);
                }
            }
        } catch (Throwable ignored) {
        }
    }

    /** Подключает игрока к голосовому каналу его команды (если битва идёт и канал создан). */
    public void joinTeam(Player player, BattleTeam team) {
        if (!available) {
            return;
        }
        Object group = groupFor(team);
        if (group != null) {
            join(player, group);
        }
    }

    /** Завершает голосовые каналы битвы: удаляет всех из каналов и удаляет группы. */
    public void endBattle() {
        if (!available) {
            return;
        }
        try {
            for (Object group : teamGroups.values()) {
                Method getOnlinePlayers = groupClass.getMethod("getOnlinePlayers");
                @SuppressWarnings("unchecked")
                Collection<Object> online = (Collection<Object>) getOnlinePlayers.invoke(group);
                Method leave = groupManager.getClass().getMethod("leave", voicePlayerClass);
                for (Object voicePlayer : new ArrayList<>(online)) {
                    leave.invoke(groupManager, voicePlayer);
                }
                Method deleteGroup = groupManager.getClass().getMethod("deleteGroup", groupClass);
                deleteGroup.invoke(groupManager, group);
            }
        } catch (Throwable ignored) {
        }
        teamGroups.clear();
        battleName = "";
    }

    /** Существующий канал команды или новый, если канала нет (например, удалили через аддон). */
    private Object groupFor(BattleTeam team) {
        Object group = teamGroups.get(team);
        if (group != null && groupExists(group)) {
            return group;
        }
        Object fresh = createGroup(team);
        if (fresh != null) {
            teamGroups.put(team, fresh);
        }
        return fresh;
    }

    /** Канал ещё зарегистрирован в аддоне (не удалён). */
    private boolean groupExists(Object group) {
        try {
            UUID id = (UUID) groupClass.getMethod("getId").invoke(group);
            Method getGroups = groupManager.getClass().getMethod("getGroups");
            @SuppressWarnings("unchecked")
            Map<UUID, ?> groups = (Map<UUID, ?>) getGroups.invoke(groupManager);
            return groups.containsKey(id);
        } catch (Throwable t) {
            return false;
        }
    }

    /** Создаёт и регистрирует голосовой канал для команды (как CreateCommand аддона). */
    private Object createGroup(BattleTeam team) {
        try {
            Class<?> playerSetClass = Class.forName("su.plo.voice.api.server.audio.line.ServerPlayerSet");

            // groupManager.sourceLine.playerSetManager.createBroadcastSet()
            Method getSourceLine = groupManager.getClass().getMethod("getSourceLine");
            Object sourceLine = getSourceLine.invoke(groupManager);
            Method getPlayerSetManager = sourceLine.getClass().getMethod("getPlayerSetManager");
            Object playerSetManager = getPlayerSetManager.invoke(sourceLine);
            Method createBroadcastSet = playerSetManager.getClass().getMethod("createBroadcastSet");
            Object playerSet = createBroadcastSet.invoke(playerSetManager);

            Constructor<?> ctor = groupClass.getConstructor(playerSetClass, UUID.class, String.class, String.class,
                    boolean.class, Set.class, List.class, gameProfileClass);
            Object group = ctor.newInstance(playerSet, UUID.randomUUID(),
                    channelName(team), passwordProtected ? randomPassword() : null, true,
                    new HashSet<UUID>(), new ArrayList<>(), null);

            // Регистрируем группу в аддоне, чтобы она была видна в /groups browse.
            Method getGroups = groupManager.getClass().getMethod("getGroups");
            @SuppressWarnings("unchecked")
            Map<UUID, Object> groups = (Map<UUID, Object>) getGroups.invoke(groupManager);
            UUID id = (UUID) groupClass.getMethod("getId").invoke(group);
            groups.put(id, group);
            return group;
        } catch (Throwable t) {
            plugin.getLogger().warning("Не удалось создать голосовой канал для "
                    + team.displayName() + ": " + t);
            return null;
        }
    }

    private String channelName(BattleTeam team) {
        String base = channelPrefix == null || channelPrefix.isBlank()
                ? team.displayName()
                : channelPrefix + " · " + team.displayName();
        return base.replace('§', ' ');
    }

    private static final char[] PASSWORD_CHARS = "abcdefghijkmnopqrstuvwxyzABCDEFGHJKLMNPQRSTUVWXYZ23456789".toCharArray();
    private static final SecureRandom PASSWORD_RANDOM = new SecureRandom();

    /** Случайный пароль канала — ручной /groups join без пароля не пройдёт (авто-подключение игнорирует пароль). */
    private String randomPassword() {
        StringBuilder sb = new StringBuilder(12);
        for (int i = 0; i < 12; i++) {
            sb.append(PASSWORD_CHARS[PASSWORD_RANDOM.nextInt(PASSWORD_CHARS.length)]);
        }
        return sb.toString();
    }

    /** Подключает Bukkit-игрока к группе (без эффекта, если у игрока нет голосового чата). */
    private void join(Player player, Object group) {
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

    /** VoicePlayer из PlasmoVoice для Bukkit-игрока, или {@code null}, если игрок не в голосовом чате. */
    private Object toVoicePlayer(Player player) {
        try {
            Method getPlayerManager = voiceServer.getClass().getMethod("getPlayerManager");
            Object playerManager = getPlayerManager.invoke(voiceServer);
            Method getPlayerByInstance = playerManager.getClass().getMethod("getPlayerByInstance", Object.class);
            return getPlayerByInstance.invoke(playerManager, player);
        } catch (Throwable ignored) {
            return null;
        }
    }
}
