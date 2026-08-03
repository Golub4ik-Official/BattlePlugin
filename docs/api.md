# API для интеграции

Плагин предоставляет публичный API, который могут использовать другие плагины
(например, плагин голосований, чтобы показывать голосование только игрокам
одной команды).

## Подключение

1. Положи JAR BattlePlugin на сервер (как `depend`/`softdepend` в своём `plugin.yml`).
2. Подключи исходники API при сборке (пакеты `battle.api` и `battle.api.event`).
   Проще всего склонировать репозиторий и подключить `src/main/java` к своему
   проекту, либо скопировать нужные классы из пакета `battle.api`.

## Получение экземпляра API

```java
import battle.api.BattleApi;
import battle.api.BattleApiProvider;

BattleApi api = BattleApiProvider.get();
if (api == null) {
    // BattlePlugin не установлен — делай fallback
    return;
}
```

## Возможности

### Команды игроков

```java
BattleTeam team = api.getTeam(player);        // команда игрока или null
BattleTeam byUuid = api.getTeam(playerId);    // по UUID
Map<UUID, BattleTeam> all = api.getAllAssignments();
List<Player> members = api.getOnlineTeamMembers(BattleTeam.RED);
```

`BattleTeam` — перечисление `RED`, `BLUE`, `GREEN`, `YELLOW` (класс
`battle.BattleTeam`). Названия команд: `displayName()` (учитывает кастомный ярлык),
цвет: `miniTag()` (например `<#ff5555>`) или `textColor()`/`particleColor()`.

### Текущая битва

```java
boolean active = api.isBattleActive();
String name = api.getBattleName();            // название битвы или null
int left = api.getBattleTimeLeft();           // секунд до конца, или -1
Set<BattleTeam> teams = api.getBattleTeams(); // команды битвы (пусто, если нет битвы)
boolean participant = api.isParticipant(player);
```

### Отправка сообщений одной команде

```java
// Показать голосование только команде red (MiniMessage поддерживается)
api.sendTeamMessage(BattleTeam.RED, "<gold>Голосование открыто!</gold>");
```

### События

Плагин вызывает события, на которые можно подписаться через обычный
`@EventHandler`:

- **`battle.api.event.BattleStartedEvent`** — старт битвы (`getBattleName()`,
  `getDurationSeconds()`, `getTeams()`).
- **`battle.api.event.BattleEndedEvent`** — завершение битвы (`getWinner()`,
  `null` при ничьей, `getTeams()`).
- **`battle.api.event.TeamChangeEvent`** — игрок назначен в команду или снят
  (`getPlayerId()`, `getTeam()` — `null`, если снят).

```java
public class MyListener implements Listener {
    @EventHandler
    public void onBattleStart(BattleStartedEvent e) {
        // ...
    }
}
```

## Важно

- Все методы API вызываются только на **главном потоке** сервера.
- `getTeam(player)` возвращает назначение из памяти плагина — оно сбрасывается
  при перезапуске сервера.
- API стабильно только для публичных классов: `battle.api.*` и
  `battle.BattleTeam`. Остальные внутренние классы (`battle.manager.*`,
  `battle.model.*`) могут меняться.
