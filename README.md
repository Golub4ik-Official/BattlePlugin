# BattlePlugin

Плагин для Paper **1.21.11**, который позволяет проводить на сервере битвы между командами с захватом точек, очками за убийства и статистикой.

## Возможности

- Команды (до 4): red, blue, green, yellow.
- Командные битвы по таймеру с названием.
- Захват и удержание точек на карте.
- Очки за убийства, смерти, тимкиллы и удержание точек (настраивается).
- BossBar со счётом и таймером, скорборды, цветные имена в командах.
- Статистика и история завершённых битв.
- Сохранение статистики между перезапусками сервера.

## Сборка

Требуется JDK 21:

```bash
./gradlew build
```

Артефакт: `build/libs/BattlePlugin.jar`.

## Установка

1. Скопируй `BattlePlugin.jar` в папку `plugins` сервера.
2. Перезапусти сервер.
3. Настрой точки захвата через команды.

## Команды

| Команда | Описание | Права |
| --- | --- | --- |
| `/battle help` | Справка | — |
| `/battle team set <игрок> <команда>` | Назначить игрока в команду | `battle.team.set` |
| `/battle team remove <игрок>` | Убрать игрока из команд | `battle.admin` |
| `/battle team list` | Список игроков по командам | `battle.status` |
| `/battle start <минуты> <команда1> <команда2> [команда3] [команда4] <название>` | Начать битву | `battle.admin` |
| `/battle stop` | Остановить битву | `battle.admin` |
| `/battle status` | Статус текущей битвы | `battle.status` |
| `/battle point add <название>` | Добавить точку на месте игрока | `battle.admin` |
| `/battle point remove <название>` | Удалить точку | `battle.admin` |
| `/battle point list` | Список точек | `battle.status` |
| `/battle stats [id]` | Статистика последней (или #id) битвы | `battle.status` |
| `/battle stats <id> team <команда>` | Статистика команды | `battle.status` |
| `/battle stats <id> me` | Личная статистика | `battle.status` |
| `/battle stats <id> player <ник>` | Статистика игрока | `battle.stats.others` |
| `/battle history` | История завершённых битв | `battle.status` |
| `/battle reload` | Перезагрузить конфиг | `battle.admin` |

## Разработка

Репозиторий использует GitHub Actions: при пуше тега `v*` плагин автоматически собирается и создаётся Release с `BattlePlugin.jar`. Правила для нейросети-ассистента описаны в [AGENTS.md](AGENTS.md).
