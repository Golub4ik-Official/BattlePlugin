package battle;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;

/**
 * Утилита для сообщений в формате MiniMessage.
 */
public final class Messages {

    public static final MiniMessage MM = MiniMessage.miniMessage();
    public static final String PREFIX = "<gray>[<gold>Битва</gold>]</gray> ";

    private Messages() {
    }

    /** Сообщение с префиксом плагина. */
    public static Component msg(String miniMessage) {
        return MM.deserialize(PREFIX + miniMessage);
    }

    /** Сообщение без префикса. */
    public static Component raw(String miniMessage) {
        return MM.deserialize(miniMessage);
    }
}
