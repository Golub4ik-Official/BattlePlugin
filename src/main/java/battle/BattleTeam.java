package battle;

import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.ChatColor;
import org.bukkit.Color;
import org.bukkit.boss.BarColor;

import java.util.Locale;

/**
 * Команды противодействия битвы.
 */
public enum BattleTeam {
    RED("Красные", NamedTextColor.RED, BarColor.RED, Color.fromRGB(0xFF5555)),
    BLUE("Синие", NamedTextColor.BLUE, BarColor.BLUE, Color.fromRGB(0x5555FF)),
    GREEN("Зелёные", NamedTextColor.GREEN, BarColor.GREEN, Color.fromRGB(0x55FF55)),
    YELLOW("Жёлтые", NamedTextColor.YELLOW, BarColor.YELLOW, Color.fromRGB(0xFFFF55));

    private final String displayName;
    private final NamedTextColor textColor;
    private final BarColor barColor;
    private final Color particleColor;
    private final ChatColor chatColor;
    private String label;

    BattleTeam(String displayName, NamedTextColor textColor, BarColor barColor, Color particleColor) {
        this.displayName = displayName;
        this.textColor = textColor;
        this.barColor = barColor;
        this.particleColor = particleColor;
        this.chatColor = switch (name()) {
            case "RED" -> ChatColor.RED;
            case "BLUE" -> ChatColor.BLUE;
            case "GREEN" -> ChatColor.GREEN;
            case "YELLOW" -> ChatColor.YELLOW;
            default -> ChatColor.WHITE;
        };
    }

    public String displayName() {
        return label != null && !label.isBlank() ? label : displayName;
    }

    /** Устанавливает кастомный ярлык (показывается вместо стандартного названия). */
    public void setLabel(String label) {
        this.label = label;
    }

    /** Сбрасывает ярлык к стандартному названию команды. */
    public void resetLabel() {
        this.label = null;
    }

    /** Есть ли установленный ярлык. */
    public boolean hasLabel() {
        return label != null && !label.isBlank();
    }

    /** Сырой ярлык команды или {@code null}, если ярлык не установлен. */
    public String rawLabel() {
        return label;
    }

    public NamedTextColor textColor() {
        return textColor;
    }

    public BarColor barColor() {
        return barColor;
    }

    public Color particleColor() {
        return particleColor;
    }

    public ChatColor colorName() {
        return chatColor;
    }

    /** MiniMessage-тег цвета команды, например {@code <#ff5555>}. */
    public String miniTag() {
        return String.format("<#%06x>", particleColor.asRGB());
    }

    /** Текст, окрашенный в цвет команды (MiniMessage-теги). */
    public String colorize(String text) {
        int rgb = particleColor.asRGB();
        return String.format("<#%06x>%s</#%06x>", rgb, text, rgb);
    }

    /**
     * Парсит название команды: английское (red) или русское (красный/красные).
     *
     * @return команда или {@code null}, если название неизвестно
     */
    public static BattleTeam fromString(String s) {
        if (s == null) {
            return null;
        }
        String t = s.trim().toLowerCase(Locale.ROOT);
        return switch (t) {
            case "red", "красный", "красная", "красные" -> RED;
            case "blue", "синий", "синяя", "синие" -> BLUE;
            case "green", "зелёный", "зеленый", "зелёная", "зеленая", "зелёные", "зеленые" -> GREEN;
            case "yellow", "жёлтый", "желтый", "жёлтая", "желтая", "жёлтые", "желтые" -> YELLOW;
            default -> null;
        };
    }
}
