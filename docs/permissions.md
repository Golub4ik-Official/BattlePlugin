# Права (пермишенны)

Права задаются в `plugin.yml`.

## Список прав

| Право | По умолчанию | Назначение |
| --- | --- | --- |
| `battle.admin` | `op` | Полный доступ к управлению битвами. Родительское право: включает `battle.team.set` |
| `battle.team.set` | `false` | Доступ к `/battle team set`. Родительское право: включает `pv.addon.groups.*` |
| `battle.status` | `true` | Просмотр статуса, списков и статистики битв |
| `battle.stats.others` | `false` | Просмотр статистики другого игрока (`/battle stats <id> player <ник>`) |

## Иерархия

```
battle.admin
└── battle.team.set          (включено через children)
    └── pv.addon.groups.*    (включено через children)
```

Право `battle.admin` автоматически даёт `battle.team.set`.

Право `pv.addon.groups.*` — максимальные права аддона pv-addon-groups (мягкая
зависимость): доступ к `/groups`, кик/бан/трансфер/удаление из любой группы
(`/groups kick`, `/groups ban`, `/groups transfer`, `/groups delete` и т.п.).
Выдаётся всем, у кого есть доступ к назначению игроков в команды, чтобы они
могли управлять голосовыми каналами битвы. Аддон не установлен — право не влияет.

## Какие команды что требуют

| Право | Команды |
| --- | --- |
| `battle.admin` | `/battle team remove`, `/battle start`, `/battle stop`, `/battle point add`, `/battle point remove`, `/battle reload` |
| `battle.team.set` | `/battle team set` |
| `battle.status` | `/battle team list`, `/battle status`, `/battle point list`, `/battle stats` (все варианты), `/battle history` |
| `battle.stats.others` | `/battle stats <id> player <ник>` |

## Замечания

- `battle.admin` по умолчанию выдано операторам (`default: op`).
- `battle.status` по умолчанию доступно всем игрокам (`default: true`) — базовые
  команды просмотра открыты.
- Права настраиваются обычными средствами (LuckPerms, permissions.yml и т.п.).
