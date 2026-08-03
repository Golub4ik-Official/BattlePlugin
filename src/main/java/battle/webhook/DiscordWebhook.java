package battle.webhook;

import battle.BattleTeam;
import battle.model.BattleStats;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;

/**
 * Отправка итогов завершённой битвы в Discord-канал через вебхук.
 * URL вебхука задаётся в config.yml (discord.webhook-url).
 */
public class DiscordWebhook {

    private static final int MAX_FIELD_LENGTH = 1024;
    private static final int MAX_TOP_PLAYERS = 5;

    private final String url;
    private final HttpClient client;

    public DiscordWebhook(String url) {
        this.url = url == null ? "" : url.trim();
        this.client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .build();
    }

    public boolean enabled() {
        return !url.isEmpty();
    }

    /** Отправляет итоги битвы асинхронно (не блокирует главный поток). */
    public void sendAsync(BattleStats stats) {
        if (!enabled()) {
            return;
        }
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("Content-Type", "application/json")
                .timeout(Duration.ofSeconds(10))
                .POST(HttpRequest.BodyPublishers.ofString(buildPayload(stats)))
                .build();
        client.sendAsync(request, HttpResponse.BodyHandlers.discarding())
                .exceptionally(ex -> {
                    // Ошибка отправки не должна падать в консоль с трейсом каждый раз громко;
                    // логируется на уровне конфига ниже только при включённом debug-выводе.
                    return null;
                });
    }

    /** Собирает JSON-полезную нагрузку для вебхука (package-private для тестов). */
    String buildPayload(BattleStats stats) {
        String title = "Битва #" + stats.id + ": " + strip(stats.name);
        int color = winnerColor(stats);
        StringBuilder fields = new StringBuilder();
        fields.append('{').append("\"name\":\"Победитель\",\"value\":\"")
                .append(json(clamp(winnerText(stats)))).append("\",\"inline\":false").append('}').append(',');
        fields.append('{').append("\"name\":\"Команды\",\"value\":\"")
                .append(json(clamp(teamsText(stats)))).append("\",\"inline\":false").append('}').append(',');
        fields.append('{').append("\"name\":\"Топ игроков\",\"value\":\"")
                .append(json(clamp(playersText(stats)))).append("\",\"inline\":false").append('}');
        return "{\"embeds\":[{\"title\":\"" + json(title)
                + "\",\"color\":" + color
                + ",\"fields\":[" + fields + "]}]}";
    }

    private int winnerColor(BattleStats stats) {
        return stats.winner == null ? 0x888888 : stats.winner.particleColor().asRGB();
    }

    private String winnerText(BattleStats stats) {
        return stats.winner == null ? "Ничья" : teamDisplay(stats, stats.winner);
    }

    private String teamsText(BattleStats stats) {
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<BattleTeam, BattleStats.TeamSummary> e : stats.teams.entrySet()) {
            BattleTeam team = e.getKey();
            BattleStats.TeamSummary s = e.getValue();
            if (sb.length() > 0) {
                sb.append('\n');
            }
            sb.append("**").append(teamDisplay(stats, team)).append("**: ").append(s.score)
                    .append(" очков | убийств: ").append(s.kills)
                    .append(", смертей: ").append(s.deaths)
                    .append(", тимкиллов: ").append(s.teamkills)
                    .append(", точек: ").append(s.pointsCaptured);
        }
        return sb.toString();
    }

    private String playersText(BattleStats stats) {
        StringBuilder sb = new StringBuilder();
        int shown = 0;
        for (BattleStats.PlayerSummary ps : stats.players) {
            if (shown >= MAX_TOP_PLAYERS) {
                break;
            }
            String name = ps.name == null ? "?" : ps.name;
            if (ps.kills <= 0 && shown > 0) {
                continue;
            }
            if (sb.length() > 0) {
                sb.append('\n');
            }
            sb.append("`").append(name).append("` — ").append(ps.kills)
                    .append(" К / ").append(ps.deaths)
                    .append(" С (K/D ").append(ps.kd()).append(')');
            shown++;
        }
        return sb.length() == 0 ? "Нет участников" : sb.toString();
    }

    /** Название команды с учётом ярлыка, сохранённого в записи битвы. */
    private String teamDisplay(BattleStats stats, BattleTeam team) {
        BattleStats.TeamSummary s = stats.teams.get(team);
        String label = s == null ? null : s.label;
        return strip((label != null && !label.isBlank()) ? label : team.displayName());
    }

    private String clamp(String s) {
        if (s == null) {
            return "";
        }
        return s.length() <= MAX_FIELD_LENGTH ? s : s.substring(0, MAX_FIELD_LENGTH - 3) + "...";
    }

    /** Убирает MiniMessage-теги (например {@code <#ff5555>}) из текста. */
    private String strip(String s) {
        if (s == null) {
            return "";
        }
        return s.replaceAll("<[^>]*>", "").trim();
    }

    /** Экранирует строку для встраивания в JSON. */
    private String json(String s) {
        StringBuilder sb = new StringBuilder();
        for (char c : s.toCharArray()) {
            switch (c) {
                case '"' -> sb.append("\\\"");
                case '\\' -> sb.append("\\\\");
                case '\n' -> sb.append("\\n");
                case '\r' -> sb.append("\\r");
                case '\t' -> sb.append("\\t");
                default -> {
                    if (c < 0x20) {
                        sb.append(String.format("\\u%04x", (int) c));
                    } else {
                        sb.append(c);
                    }
                }
            }
        }
        return sb.toString();
    }
}
