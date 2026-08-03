package battle;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;

import java.util.regex.Pattern;

/**
 * Утилита для сообщений в формате MiniMessage.
 *
 * <p>Внимание: MiniMessage 4.18+ бросает исключение при legacy-кодах цвета
 * (символ {@code §}) внутри строки. Так как в строки попадают данные извне
 * (названия кастомных предметов, имена, названия битв), перед парсингом
 * legacy-коды вырезаются, чтобы ни одно сообщение не упало.
 */
public final class Messages {

    public static final MiniMessage MM = MiniMessage.miniMessage();
    public static final String PREFIX = "<gray>[<gold>Битва</gold>]</gray> ";

    /** Legacy-коды цвета: {@code §c}, {@code §x§R§R§G§G§B§B} и т.п. */
    private static final Pattern LEGACY_CODES =
            Pattern.compile("\u00a7x(?:[\u00a7][0-9a-fA-F]){6}|\u00a7[0-9a-fk-orxA-FK-ORX]");

    private Messages() {
    }

    /** Сообщение с префиксом плагина. */
    public static Component msg(String miniMessage) {
        return MM.deserialize(PREFIX + clean(miniMessage));
    }

    /** Сообщение без префикса. */
    public static Component raw(String miniMessage) {
        return MM.deserialize(clean(miniMessage));
    }

    /** Вырезает legacy-коды цвета, чтобы MiniMessage не упал на них. */
    private static String clean(String s) {
        if (s == null || s.indexOf('\u00a7') < 0) {
            return s;
        }
        return LEGACY_CODES.matcher(s).replaceAll("");
    }
}
