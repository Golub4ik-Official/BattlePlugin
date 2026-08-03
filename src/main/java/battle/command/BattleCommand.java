package battle.command;

import battle.BattleTeam;
import battle.Messages;
import battle.manager.BattleManager;
import battle.manager.PointManager;
import battle.manager.StatsManager;
import battle.manager.TeamManager;
import battle.model.Battle;
import battle.model.BattleStats;
import battle.model.CapturePoint;
import battle.model.StatEvent;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import com.mojang.brigadier.tree.LiteralCommandNode;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import io.papermc.paper.command.brigadier.argument.ArgumentTypes;
import io.papermc.paper.command.brigadier.argument.resolvers.selector.PlayerSelectorArgumentResolver;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Date;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

/**
 * Brigadier-дерево команды /battle.
 */
public class BattleCommand {

    private final BattleManager battleManager;
    private final TeamManager teamManager;
    private final PointManager pointManager;
    private final StatsManager statsManager;

    public BattleCommand(BattleManager battleManager, TeamManager teamManager,
                         PointManager pointManager, StatsManager statsManager) {
        this.battleManager = battleManager;
        this.teamManager = teamManager;
        this.pointManager = pointManager;
        this.statsManager = statsManager;
    }

    public LiteralCommandNode<CommandSourceStack> root() {
        return Commands.literal("battle")
                .then(Commands.literal("help")
                        .executes(this::help))
                .then(Commands.literal("team")
                        .then(Commands.literal("set")
                                .requires(s -> s.getSender().hasPermission("battle.team.set"))
                                .then(Commands.argument("player", ArgumentTypes.player())
                                        .then(Commands.argument("team", StringArgumentType.word())
                                                .suggests((ctx, builder) -> {
                                                    for (BattleTeam t : BattleTeam.values()) {
                                                        builder.suggest(t.name().toLowerCase());
                                                    }
                                                    return builder.buildFuture();
                                                })
                                                .executes(this::teamSet))))
                        .then(Commands.literal("remove")
                                .requires(s -> s.getSender().hasPermission("battle.admin"))
                                .then(Commands.argument("player", ArgumentTypes.player())
                                        .executes(this::teamRemove)))
                        .then(Commands.literal("list")
                                .requires(s -> s.getSender().hasPermission("battle.status"))
                                .executes(this::teamList))
                        .then(Commands.literal("tp")
                                .requires(s -> s.getSender().hasPermission("battle.admin"))
                                .then(Commands.argument("team", StringArgumentType.word())
                                        .suggests(this::suggestTeams)
                                        .executes(this::teamTeleportHere)
                                        .then(Commands.argument("target", ArgumentTypes.player())
                                                .executes(this::teamTeleportTo))))
                        .then(Commands.literal("freeze")
                                .requires(s -> s.getSender().hasPermission("battle.admin"))
                                .then(Commands.argument("team", StringArgumentType.word())
                                        .suggests(this::suggestTeams)
                                        .executes(this::teamFreeze)))
                        .then(Commands.literal("unfreeze")
                                .requires(s -> s.getSender().hasPermission("battle.admin"))
                                .then(Commands.argument("team", StringArgumentType.word())
                                        .suggests(this::suggestTeams)
                                        .executes(this::teamUnfreeze)))
                        .then(Commands.literal("giveinv")
                                .requires(s -> s.getSender().hasPermission("battle.admin"))
                                .then(Commands.argument("team", StringArgumentType.word())
                                        .suggests(this::suggestTeams)
                                        .executes(this::teamGiveInv)))
                        .then(Commands.literal("label")
                                .requires(s -> s.getSender().hasPermission("battle.admin"))
                                .then(Commands.argument("team", StringArgumentType.word())
                                        .suggests(this::suggestTeams)
                                        .then(Commands.argument("name", StringArgumentType.greedyString())
                                                .executes(this::teamLabel))))
                        .then(Commands.literal("unlabel")
                                .requires(s -> s.getSender().hasPermission("battle.admin"))
                                .then(Commands.argument("team", StringArgumentType.word())
                                        .suggests(this::suggestTeams)
                                        .executes(this::teamUnlabel))))
                .then(Commands.literal("start")
                        .requires(s -> s.getSender().hasPermission("battle.admin"))
                        .then(Commands.argument("minutes", IntegerArgumentType.integer(1, 1440))
                                .then(buildTeamChain())))
                .then(Commands.literal("stop")
                        .requires(s -> s.getSender().hasPermission("battle.admin"))
                        .executes(this::stop))
                .then(Commands.literal("status")
                        .requires(s -> s.getSender().hasPermission("battle.status"))
                        .executes(this::status))
                .then(Commands.literal("point")
                        .then(Commands.literal("add")
                                .requires(s -> s.getSender().hasPermission("battle.admin"))
                                .then(Commands.argument("name", StringArgumentType.greedyString())
                                        .executes(this::pointAdd)))
                        .then(Commands.literal("remove")
                                .requires(s -> s.getSender().hasPermission("battle.admin"))
                                .then(Commands.argument("name", StringArgumentType.greedyString())
                                        .executes(this::pointRemove)))
                        .then(Commands.literal("list")
                                .requires(s -> s.getSender().hasPermission("battle.status"))
                                .executes(this::pointList)))
                .then(Commands.literal("stats")
                        .requires(s -> s.getSender().hasPermission("battle.status"))
                        .executes(ctx -> stats(ctx, -1))
                        .then(Commands.argument("id", IntegerArgumentType.integer(1))
                                .executes(ctx -> stats(ctx, ctx.getArgument("id", Integer.class)))
                                .then(Commands.literal("me")
                                        .requires(s -> s.getSender() instanceof Player)
                                        .executes(this::statsMe))
                                .then(Commands.literal("team")
                                        .then(Commands.argument("team", StringArgumentType.word())
                                                .suggests((ctx, builder) -> {
                                                    for (BattleTeam t : BattleTeam.values()) {
                                                        builder.suggest(t.name().toLowerCase());
                                                    }
                                                    return builder.buildFuture();
                                                })
                                                .executes(this::statsTeam)))
                                .then(Commands.literal("player")
                                        .requires(s -> s.getSender().hasPermission("battle.stats.others"))
                                        .then(Commands.argument("name", StringArgumentType.word())
                                                .executes(this::statsPlayer)))))
                .then(Commands.literal("top")
                        .requires(s -> s.getSender().hasPermission("battle.status"))
                        .executes(ctx -> top(ctx, "kills"))
                        .then(Commands.argument("metric", StringArgumentType.word())
                                .suggests((ctx, builder) -> {
                                    for (String m : List.of("kills", "kd", "damage", "captures")) {
                                        builder.suggest(m);
                                    }
                                    return builder.buildFuture();
                                })
                                .executes(ctx -> top(ctx, ctx.getArgument("metric", String.class)))))
                .then(Commands.literal("history")
                        .requires(s -> s.getSender().hasPermission("battle.status"))
                        .executes(this::history))
                .then(Commands.literal("reload")
                        .requires(s -> s.getSender().hasPermission("battle.admin"))
                        .executes(this::reload))
                .build();
    }

    private int help(CommandContext<CommandSourceStack> ctx) {
        CommandSender sender = ctx.getSource().getSender();
        boolean admin = sender.hasPermission("battle.admin");
        boolean teamSet = sender.hasPermission("battle.team.set");
        boolean status = sender.hasPermission("battle.status");

        sender.sendMessage(Messages.raw("<gold>═══════════ Битва — справка ═══════════"));
        sender.sendMessage(Messages.raw("<yellow>/battle help</yellow> <gray>— эта справка</gray>"));
        if (teamSet) {
            sender.sendMessage(Messages.raw("<yellow>/battle team set <игрок> <red|blue|green|yellow></yellow> <gray>— назначить игрока в команду</gray>"));
        }
        if (admin) {
            sender.sendMessage(Messages.raw("<yellow>/battle team remove <игрок></yellow> <gray>— убрать игрока из команд</gray>"));
        }
        if (status) {
            sender.sendMessage(Messages.raw("<yellow>/battle team list</yellow> <gray>— список игроков по командам</gray>"));
            sender.sendMessage(Messages.raw("<yellow>/battle status</yellow> <gray>— статус текущей битвы</gray>"));
            sender.sendMessage(Messages.raw("<yellow>/battle point list</yellow> <gray>— список точек захвата</gray>"));
            sender.sendMessage(Messages.raw("<yellow>/battle stats [id]</yellow> <gray>— статистика последней (или #id) битвы</gray>"));
            sender.sendMessage(Messages.raw("<yellow>/battle stats <id> team <команда></yellow> <gray>— статистика команды</gray>"));
            sender.sendMessage(Messages.raw("<yellow>/battle stats <id> me</yellow> <gray>— ваша личная статистика</gray>"));
            sender.sendMessage(Messages.raw("<yellow>/battle stats <id> player <ник></yellow> <gray>— статистика игрока (перм. battle.stats.others)</gray>"));
            sender.sendMessage(Messages.raw("<yellow>/battle top [kills|kd|damage|captures]</yellow> <gray>— топ игроков за всё время</gray>"));
            sender.sendMessage(Messages.raw("<yellow>/battle history</yellow> <gray>— история завершённых битв</gray>"));
        }
        if (admin) {
            sender.sendMessage(Messages.raw("<yellow>/battle start <минуты> <команда1> <команда2> [команда3] [команда4] <название></yellow> <gray>— начать битву</gray>"));
            sender.sendMessage(Messages.raw("<yellow>/battle stop</yellow> <gray>— остановить битву / отменить отсчёт</gray>"));
            sender.sendMessage(Messages.raw("<yellow>/battle point add <название></yellow> <gray>— добавить точку на месте игрока</gray>"));
            sender.sendMessage(Messages.raw("<yellow>/battle point remove <название></yellow> <gray>— удалить точку</gray>"));
            sender.sendMessage(Messages.raw("<yellow>/battle reload</yellow> <gray>— перезагрузить конфиг</gray>"));
        }
        sender.sendMessage(Messages.raw("<gray>Пример: <white>/battle start 30 red blue Битва за Киев</white></gray>"));
        return 1;
    }

    private int teamSet(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        CommandSender sender = ctx.getSource().getSender();
        List<Player> targets = ctx.getArgument("player", PlayerSelectorArgumentResolver.class).resolve(ctx.getSource());
        if (targets.isEmpty()) {
            sender.sendMessage(Messages.msg("<red>Игрок не найден."));
            return 1;
        }
        Player target = targets.get(0);
        String teamName = ctx.getArgument("team", String.class);
        BattleTeam team = BattleTeam.fromString(teamName);
        if (team == null) {
            sender.sendMessage(Messages.msg("<red>Неизвестная команда: <white>" + teamName
                    + "</white>. Доступные: red, blue, green, yellow."));
            return 1;
        }
        if (!teamManager.meetsPlaytime(target)) {
            sender.sendMessage(Messages.msg("<red>Игрок <white>" + target.getName()
                    + "</white> провёл на сервере только <white>"
                    + String.format("%.1f", teamManager.playtimeHours(target))
                    + "</white> ч. из <white>" + teamManager.minPlaytimeHours()
                    + "</white> ч. — назначение в команду запрещено."));
            return 1;
        }
        teamManager.set(target, team);
        sender.sendMessage(Messages.msg(team.colorize(target.getName())
                + " <gray>назначен(а) в команду</gray> " + team.colorize(team.displayName())));
        return 1;
    }

    private int teamRemove(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        CommandSender sender = ctx.getSource().getSender();
        List<Player> targets = ctx.getArgument("player", PlayerSelectorArgumentResolver.class).resolve(ctx.getSource());
        if (targets.isEmpty()) {
            sender.sendMessage(Messages.msg("<red>Игрок не найден."));
            return 1;
        }
        Player target = targets.get(0);
        teamManager.remove(target);
        sender.sendMessage(Messages.msg("<gray>Игрок <white>" + target.getName() + "</white> убран из команд."));
        return 1;
    }

    private int teamList(CommandContext<CommandSourceStack> ctx) {
        CommandSender sender = ctx.getSource().getSender();
        Map<UUID, BattleTeam> all = teamManager.all();
        if (all.isEmpty()) {
            sender.sendMessage(Messages.msg("<gray>Никто не назначен в команды."));
            return 1;
        }
        for (Map.Entry<UUID, BattleTeam> e : all.entrySet()) {
            String name = Bukkit.getOfflinePlayer(e.getKey()).getName();
            sender.sendMessage(Messages.raw("<gray>- </gray>"
                    + e.getValue().colorize(name == null ? e.getKey().toString() : name)
                    + " <gray>→</gray> " + e.getValue().colorize(e.getValue().displayName())));
        }
        return 1;
    }

    private BattleTeam resolveTeam(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        CommandSender sender = ctx.getSource().getSender();
        String teamName = ctx.getArgument("team", String.class);
        BattleTeam team = BattleTeam.fromString(teamName);
        if (team == null) {
            sender.sendMessage(Messages.msg("<red>Неизвестная команда: <white>" + teamName
                    + "</white>. Доступные: red, blue, green, yellow."));
        }
        return team;
    }

    private int teamTeleportHere(CommandContext<CommandSourceStack> ctx) {
        CommandSender sender = ctx.getSource().getSender();
        if (!(sender instanceof Player admin)) {
            sender.sendMessage(Messages.msg("<red>Команда доступна только игроку."));
            return 1;
        }
        return teleportTeam(sender, admin, ctx);
    }

    private int teamTeleportTo(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        CommandSender sender = ctx.getSource().getSender();
        List<Player> targets = ctx.getArgument("target", PlayerSelectorArgumentResolver.class).resolve(ctx.getSource());
        if (targets.isEmpty()) {
            sender.sendMessage(Messages.msg("<red>Игрок не найден."));
            return 1;
        }
        return teleportTeam(sender, targets.get(0), ctx);
    }

    private int teleportTeam(CommandSender sender, Player target, CommandContext<CommandSourceStack> ctx) {
        try {
            BattleTeam team = resolveTeam(ctx);
            if (team == null) {
                return 1;
            }
            List<Player> members = teamManager.onlineMembers(team);
            if (members.isEmpty()) {
                sender.sendMessage(Messages.msg("<gray>В команде " + team.colorize(team.displayName())
                        + " <gray>нет онлайн-игроков."));
                return 1;
            }
            Location loc = target.getLocation();
            int count = 0;
            for (Player member : members) {
                if (member.equals(target)) {
                    continue;
                }
                member.teleport(loc);
                count++;
            }
            sender.sendMessage(Messages.msg("<green>Команда</green> " + team.colorize(team.displayName())
                    + " <green>телепортирована к</green> <white>" + target.getName() + "</white> <gray>("
                    + count + " игр.).</gray>"));
            return 1;
        } catch (CommandSyntaxException e) {
            return 1;
        }
    }

    private int teamFreeze(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        CommandSender sender = ctx.getSource().getSender();
        BattleTeam team = resolveTeam(ctx);
        if (team == null) {
            return 1;
        }
        List<Player> members = teamManager.onlineMembers(team);
        if (members.isEmpty()) {
            sender.sendMessage(Messages.msg("<gray>В команде " + team.colorize(team.displayName())
                    + " <gray>нет онлайн-игроков."));
            return 1;
        }
        teamManager.freeze(team);
        sender.sendMessage(Messages.msg("<yellow>Команда</yellow> " + team.colorize(team.displayName())
                + " <yellow>заморожена</yellow> <gray>(" + members.size() + " игр.).</gray>"));
        return 1;
    }

    private int teamUnfreeze(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        CommandSender sender = ctx.getSource().getSender();
        BattleTeam team = resolveTeam(ctx);
        if (team == null) {
            return 1;
        }
        List<Player> members = teamManager.onlineMembers(team);
        if (members.isEmpty()) {
            sender.sendMessage(Messages.msg("<gray>В команде " + team.colorize(team.displayName())
                    + " <gray>нет онлайн-игроков."));
            return 1;
        }
        teamManager.unfreeze(team);
        sender.sendMessage(Messages.msg("<green>Команда</green> " + team.colorize(team.displayName())
                + " <green>разморожена</green> <gray>(" + members.size() + " игр.).</gray>"));
        return 1;
    }

    private int teamGiveInv(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        CommandSender sender = ctx.getSource().getSender();
        if (!(sender instanceof Player admin)) {
            sender.sendMessage(Messages.msg("<red>Команда доступна только игроку."));
            return 1;
        }
        BattleTeam team = resolveTeam(ctx);
        if (team == null) {
            return 1;
        }
        List<Player> members = teamManager.onlineMembers(team);
        if (members.isEmpty()) {
            sender.sendMessage(Messages.msg("<gray>В команде " + team.colorize(team.displayName())
                    + " <gray>нет онлайн-игроков."));
            return 1;
        }
        ItemStack[] contents = deepCopy(admin.getInventory().getContents());
        for (Player member : members) {
            if (member.equals(admin)) {
                continue;
            }
            member.getInventory().clear();
            member.getInventory().setContents(deepCopy(contents));
            member.updateInventory();
        }
        sender.sendMessage(Messages.msg("<green>Инвентарь скопирован команде</green> " + team.colorize(team.displayName())
                + " <green>(" + members.size() + " игр.).</green>"));
        return 1;
    }

    private ItemStack[] deepCopy(ItemStack[] source) {
        ItemStack[] copy = new ItemStack[source.length];
        for (int i = 0; i < source.length; i++) {
            copy[i] = source[i] == null ? null : source[i].clone();
        }
        return copy;
    }

    private int teamLabel(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        CommandSender sender = ctx.getSource().getSender();
        BattleTeam team = resolveTeam(ctx);
        if (team == null) {
            return 1;
        }
        String name = ctx.getArgument("name", String.class).trim();
        if (name.isEmpty()) {
            sender.sendMessage(Messages.msg("<red>Ярлык не может быть пустым."));
            return 1;
        }
        team.setLabel(name);
        sender.sendMessage(Messages.msg("<green>Команда</green> " + team.colorize(team.displayName())
                + " <green>теперь называется</green> <yellow>\"" + name + "\"</yellow> <gray>(цвет сохранён).</gray>"));
        return 1;
    }

    private int teamUnlabel(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        CommandSender sender = ctx.getSource().getSender();
        BattleTeam team = resolveTeam(ctx);
        if (team == null) {
            return 1;
        }
        if (!team.hasLabel()) {
            sender.sendMessage(Messages.msg("<gray>У команды " + team.colorize(team.displayName())
                    + " <gray>нет ярлыка."));
            return 1;
        }
        team.resetLabel();
        sender.sendMessage(Messages.msg("<green>Ярлык команды</green> " + team.colorize(team.displayName())
                + " <green>сброшен.</green>"));
        return 1;
    }

    @SuppressWarnings("unchecked")
    private int start(CommandContext<CommandSourceStack> ctx) {
        CommandSender sender = ctx.getSource().getSender();
        int minutes = ctx.getArgument("minutes", Integer.class);
        Set<BattleTeam> teams = new LinkedHashSet<>();
        for (String key : List.of("team1", "team2", "team3", "team4")) {
            boolean provided = ctx.getNodes().stream().anyMatch(n -> n.getNode().getName().equals(key));
            if (provided) {
                BattleTeam team = BattleTeam.fromString(ctx.getArgument(key, String.class));
                if (team != null) {
                    teams.add(team);
                }
            }
        }
        String name = ctx.getArgument("name", String.class);
        battleManager.start(sender, name, minutes, teams);
        return 1;
    }

    private RequiredArgumentBuilder<CommandSourceStack, String> buildTeamChain() {
        RequiredArgumentBuilder<CommandSourceStack, String> name =
                Commands.argument("name", StringArgumentType.greedyString()).executes(this::start);
        RequiredArgumentBuilder<CommandSourceStack, String> team4 =
                Commands.argument("team4", StringArgumentType.word()).suggests(this::suggestTeams).then(name);
        RequiredArgumentBuilder<CommandSourceStack, String> team3 =
                Commands.argument("team3", StringArgumentType.word()).suggests(this::suggestTeams).then(team4).then(name);
        RequiredArgumentBuilder<CommandSourceStack, String> team2 =
                Commands.argument("team2", StringArgumentType.word()).suggests(this::suggestTeams).then(team3).then(name);
        return Commands.argument("team1", StringArgumentType.word())
                .suggests(this::suggestTeams).then(team2).then(name);
    }

    private CompletableFuture<Suggestions> suggestTeams(CommandContext<CommandSourceStack> ctx, SuggestionsBuilder builder) {
        for (BattleTeam t : BattleTeam.values()) {
            builder.suggest(t.name().toLowerCase());
        }
        return builder.buildFuture();
    }

    private int stop(CommandContext<CommandSourceStack> ctx) {
        battleManager.stop(ctx.getSource().getSender());
        return 1;
    }

    private int status(CommandContext<CommandSourceStack> ctx) {
        CommandSender sender = ctx.getSource().getSender();
        int countdown = battleManager.countdownRemaining();
        if (countdown > 0) {
            sender.sendMessage(Messages.raw("<gold>Битва начнётся через <white>" + countdown + " <gold>сек."));
            return 1;
        }
        Battle battle = battleManager.getActiveBattle();
        if (battle == null) {
            sender.sendMessage(Messages.msg("<gray>Битва не идёт."));
            return 1;
        }
        sender.sendMessage(Messages.raw("<gold>═══ Битва: <white>" + battle.name() + "</white> ═══"));
        sender.sendMessage(Messages.raw("<green>Осталось: <white>" + formatTime(battle.remainingSeconds())));
        for (BattleTeam t : battle.teams()) {
            Battle.TeamScore ts = battle.scoreOf(t);
            sender.sendMessage(Messages.raw(t.colorize(t.displayName()) + ": <white>" + ts.score));
        }
        sender.sendMessage(Messages.raw("<gold>Точки:"));
        if (pointManager.all().isEmpty()) {
            sender.sendMessage(Messages.raw("<gray>  Точки не созданы."));
        } else {
            for (CapturePoint p : pointManager.all()) {
                String owner = p.owner() == null ? "<gray>нейтральна" : p.owner().colorize(p.owner().displayName());
                sender.sendMessage(Messages.raw("<gold>- </gold><yellow>" + p.name() + "</yellow> <gray>(</gray>"
                        + owner + "<gray>)</gray>"));
            }
        }
        return 1;
    }

    private int pointAdd(CommandContext<CommandSourceStack> ctx) {
        CommandSender sender = ctx.getSource().getSender();
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Messages.msg("<red>Точки можно создавать только находясь в мире."));
            return 1;
        }
        String name = ctx.getArgument("name", String.class);
        if (pointManager.nameExists(name)) {
            sender.sendMessage(Messages.msg("<red>Точка <yellow>" + name + "</yellow> уже существует."));
            return 1;
        }
        CapturePoint point = pointManager.add(name, player.getLocation());
        Location loc = point.location();
        sender.sendMessage(Messages.msg("<green>Точка</green> <yellow>" + name + "</yellow> <green>добавлена: </green>"
                + "<white>" + loc.getWorld().getName() + " " + loc.getBlockX() + " "
                + loc.getBlockY() + " " + loc.getBlockZ()));
        return 1;
    }

    private int pointRemove(CommandContext<CommandSourceStack> ctx) {
        CommandSender sender = ctx.getSource().getSender();
        String name = ctx.getArgument("name", String.class);
        if (pointManager.remove(name)) {
            sender.sendMessage(Messages.msg("<green>Точка</green> <yellow>" + name + "</yellow> <green>удалена.</green>"));
        } else {
            sender.sendMessage(Messages.msg("<red>Точка <yellow>" + name + "</yellow> не найдена."));
        }
        return 1;
    }

    private int pointList(CommandContext<CommandSourceStack> ctx) {
        CommandSender sender = ctx.getSource().getSender();
        if (pointManager.all().isEmpty()) {
            sender.sendMessage(Messages.msg("<gray>Точки не созданы."));
            return 1;
        }
        sender.sendMessage(Messages.raw("<gold>Список точек:"));
        for (CapturePoint p : pointManager.all()) {
            String owner = p.owner() == null ? "<gray>нейтральна" : p.owner().colorize(p.owner().displayName());
            sender.sendMessage(Messages.raw("<gold>- </gold><yellow>" + p.name() + "</yellow> <gray>(</gray>"
                    + owner + "<gray>)</gray>"));
        }
        return 1;
    }

    private int stats(CommandContext<CommandSourceStack> ctx, int id) {
        CommandSender sender = ctx.getSource().getSender();
        BattleStats stats;
        if (id == -1) {
            List<BattleStats> history = statsManager.history();
            if (history.isEmpty()) {
                sender.sendMessage(Messages.msg("<gray>История битв пуста."));
                return 1;
            }
            stats = history.get(history.size() - 1);
        } else {
            stats = statsManager.get(id);
            if (stats == null) {
                sender.sendMessage(Messages.msg("<red>Битва #<white>" + id + "</white> не найдена."));
                return 1;
            }
        }
        printStats(sender, stats);
        return 1;
    }

    private int statsMe(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        CommandSender sender = ctx.getSource().getSender();
        BattleStats stats = resolveStats(sender, ctx.getArgument("id", Integer.class));
        if (stats == null) {
            return 1;
        }
        Player self = (Player) sender;
        BattleStats.PlayerSummary ps = findPlayer(stats, self.getUniqueId());
        if (ps == null) {
            sender.sendMessage(Messages.msg("<gray>У вас нет записей в битве #<white>" + stats.id + "</white>."));
            return 1;
        }
        printPlayerStats(sender, stats, ps);
        return 1;
    }

    private int statsTeam(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        CommandSender sender = ctx.getSource().getSender();
        BattleStats stats = resolveStats(sender, ctx.getArgument("id", Integer.class));
        if (stats == null) {
            return 1;
        }
        BattleTeam team = BattleTeam.fromString(ctx.getArgument("team", String.class));
        if (team == null) {
            sender.sendMessage(Messages.msg("<red>Неизвестная команда. Доступные: red, blue, green, yellow."));
            return 1;
        }
        printTeamStats(sender, stats, team);
        return 1;
    }

    private int statsPlayer(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        CommandSender sender = ctx.getSource().getSender();
        BattleStats stats = resolveStats(sender, ctx.getArgument("id", Integer.class));
        if (stats == null) {
            return 1;
        }
        String name = ctx.getArgument("name", String.class).toLowerCase();
        BattleStats.PlayerSummary ps = stats.players.stream()
                .filter(p -> p.name != null && p.name.toLowerCase().equals(name))
                .findFirst().orElse(null);
        if (ps == null) {
            sender.sendMessage(Messages.msg("<red>Игрок <white>" + ctx.getArgument("name", String.class)
                    + "</white> не найден в битве #<white>" + stats.id + "</white>."));
            return 1;
        }
        printPlayerStats(sender, stats, ps);
        return 1;
    }

    private BattleStats resolveStats(CommandSender sender, int id) {
        BattleStats stats = statsManager.get(id);
        if (stats == null) {
            sender.sendMessage(Messages.msg("<red>Битва #<white>" + id + "</white> не найдена."));
            return null;
        }
        return stats;
    }

    private BattleStats.PlayerSummary findPlayer(BattleStats stats, UUID uuid) {
        for (BattleStats.PlayerSummary ps : stats.players) {
            if (uuid.equals(ps.uuid)) {
                return ps;
            }
        }
        return null;
    }

    private void printTeamStats(CommandSender sender, BattleStats stats, BattleTeam team) {
        BattleStats.TeamSummary s = stats.teams.get(team);
        if (s == null) {
            sender.sendMessage(Messages.msg("<red>Команда " + team.colorize(team.displayName())
                    + " <red>не участвовала в битве #<white>" + stats.id + "</white>."));
            return;
        }
        sender.sendMessage(Messages.raw("<gold>═══ Команда " + team.colorize(teamDisplay(stats, team))
                + "</gold> <gray>(битва #<white>" + stats.id + "</white>: " + stats.name + ")</gray>"));
        sender.sendMessage(Messages.raw("<gray>- Очки: <white>"
                + s.score + "    <gray>Убийств: <white>" + s.kills
                + "    <gray>Смертей: <white>" + s.deaths
                + "    <gray>Тимкиллов: <white>" + s.teamkills));
        sender.sendMessage(Messages.raw("<gray>Точек захвачено: <white>" + s.pointsCaptured
                + "    <gray>Удержаний: <white>" + s.holdAwards));
        int teamDamageDealt = stats.players.stream()
                .filter(ps -> ps.team == team)
                .mapToInt(ps -> ps.damageDealt)
                .sum();
        int teamDamageTaken = stats.players.stream()
                .filter(ps -> ps.team == team)
                .mapToInt(ps -> ps.damageTaken)
                .sum();
        sender.sendMessage(Messages.raw("<gray>Урон: нанесён <white>" + teamDamageDealt
                + "</white> / получен <white>" + teamDamageTaken));
        sender.sendMessage(Messages.raw("<gold>Игроки команды:"));
        for (BattleStats.PlayerSummary ps : stats.players) {
            if (ps.team != team) {
                continue;
            }
            sender.sendMessage(Messages.raw("  " + team.colorize(ps.name)
                    + "<gray>: <white>" + ps.kills + " К / " + ps.deaths + " С / " + ps.teamkills + " ТК"
                    + " <gray>[</gray><green>K/D " + ps.kd() + "</green><gray>]</gray>"
                    + " <gray>урон " + ps.damageDealt + " / серия смертей " + ps.worstDeathStreak + "</gray>"));
        }
    }

    private void printPlayerStats(CommandSender sender, BattleStats stats, BattleStats.PlayerSummary ps) {
        BattleTeam team = ps.team;
        String colored = team != null ? team.colorize(ps.name) : "<white>" + ps.name + "</white>";
        sender.sendMessage(Messages.raw("<gold>═══ " + colored + " <gold>— битва #<white>" + stats.id
                + "</white>: <white>" + stats.name + "</white> ═══"));
        if (team != null) {
            sender.sendMessage(Messages.raw("<gray>Команда: " + team.colorize(teamDisplay(stats, team))));
        }
        sender.sendMessage(Messages.raw("<red>Убийств: <white>" + ps.kills
                + "    <red>Смертей: <white>" + ps.deaths
                + "    <red>Тимкиллов: <white>" + ps.teamkills));
        sender.sendMessage(Messages.raw("<green>K/D: <white>" + ps.kd()
                + "    <yellow>Лучшая серия: <white>" + ps.bestStreak
                + "    <red>Худшая серия смертей: <white>" + ps.worstDeathStreak
                + "    <yellow>Точек захвачено: <white>" + ps.pointsCaptured));
        sender.sendMessage(Messages.raw("<gray>Урон: нанёс <white>" + ps.damageDealt
                + "</white> / получил <white>" + ps.damageTaken));

        Map<String, Integer> victims = sortedByCount(killsPerVictim(stats, ps.name));
        Map<String, Integer> tkVictims = sortedByCount(teamkillsPerVictim(stats, ps.name));
        Map<String, Integer> killers = sortedByCount(deathsPerKiller(stats, ps.name));
        Map<String, Integer> killWeapons = sortedByCount(weaponsOf(stats, ps.name, true));
        Map<String, Integer> deathWeapons = sortedByCount(weaponsOf(stats, ps.name, false));
        int environmentDeaths = deathsWithoutKiller(stats, ps.name);
        if (!victims.isEmpty()) {
            sender.sendMessage(Messages.raw("<green>Убил(а):</green> " + formatOpponents(victims)));
        }
        if (!tkVictims.isEmpty()) {
            sender.sendMessage(Messages.raw("<red>Тимкиллы (убил(а) союзников):</red> " + formatOpponents(tkVictims)));
        }
        if (!killWeapons.isEmpty()) {
            sender.sendMessage(Messages.raw("<green>Оружие (килы):</green> " + formatOpponents(killWeapons)));
        }
        if (!killers.isEmpty()) {
            sender.sendMessage(Messages.raw("<red>Убит(а) кем:</red> " + formatOpponents(killers)));
        }
        if (!deathWeapons.isEmpty()) {
            sender.sendMessage(Messages.raw("<red>Смерти от оружия:</red> " + formatOpponents(deathWeapons)));
        }
        if (environmentDeaths > 0) {
            sender.sendMessage(Messages.raw("<gray>Прочих смертей (не от игрока битвы): <white>" + environmentDeaths));
        }
    }

    /** Название команды с учётом ярлыка, сохранённого в записи битвы. */
    private String teamDisplay(BattleStats stats, BattleTeam team) {
        BattleStats.TeamSummary s = stats.teams.get(team);
        String label = s == null ? null : s.label;
        return (label != null && !label.isBlank()) ? label : team.displayName();
    }

    /** Кого игрок убил (по имени жертвы) и сколько раз. */
    private Map<String, Integer> killsPerVictim(BattleStats stats, String name) {
        Map<String, Integer> map = new LinkedHashMap<>();
        for (StatEvent ev : stats.events) {
            if (ev.type == StatEvent.Type.KILL && ev.killer != null && name.equalsIgnoreCase(ev.killer)) {
                map.merge(ev.victim, 1, Integer::sum);
            }
        }
        return map;
    }

    /** Каких союзников игрок убил (тимкиллы). */
    private Map<String, Integer> teamkillsPerVictim(BattleStats stats, String name) {
        Map<String, Integer> map = new LinkedHashMap<>();
        for (StatEvent ev : stats.events) {
            if (ev.type == StatEvent.Type.TEAMKILL && ev.killer != null && name.equalsIgnoreCase(ev.killer)) {
                map.merge(ev.victim, 1, Integer::sum);
            }
        }
        return map;
    }

    /** Кто убивал игрока (по имени киллера) и сколько раз. */
    private Map<String, Integer> deathsPerKiller(BattleStats stats, String name) {
        Map<String, Integer> map = new LinkedHashMap<>();
        for (StatEvent ev : stats.events) {
            if (ev.type == StatEvent.Type.KILL && ev.victim != null && name.equalsIgnoreCase(ev.victim)) {
                map.merge(ev.killer, 1, Integer::sum);
            }
        }
        return map;
    }

    /** Смерти игрока, где киллер — не участник битвы (события DEATH). */
    private int deathsWithoutKiller(BattleStats stats, String name) {
        int count = 0;
        for (StatEvent ev : stats.events) {
            if (ev.type == StatEvent.Type.DEATH && ev.victim != null && name.equalsIgnoreCase(ev.victim)) {
                count++;
            }
        }
        return count;
    }

    /** Оружие, которым игрок убивал (asKiller=true) или от которого умирал (asKiller=false). */
    private Map<String, Integer> weaponsOf(BattleStats stats, String name, boolean asKiller) {
        Map<String, Integer> map = new LinkedHashMap<>();
        for (StatEvent ev : stats.events) {
            if (ev.type != StatEvent.Type.KILL || ev.weapon == null) {
                continue;
            }
            boolean match = asKiller
                    ? (ev.killer != null && name.equalsIgnoreCase(ev.killer))
                    : (ev.victim != null && name.equalsIgnoreCase(ev.victim));
            if (match) {
                map.merge(ev.weapon, 1, Integer::sum);
            }
        }
        return map;
    }

    /** Сортирует карту «имя → счёт» по убыванию счёта. */
    private Map<String, Integer> sortedByCount(Map<String, Integer> map) {
        return map.entrySet().stream()
                .sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue, (a, b) -> a, LinkedHashMap::new));
    }

    private String formatOpponents(Map<String, Integer> map) {
        return map.entrySet().stream()
                .map(e -> "<white>" + e.getKey() + "</white>"
                        + (e.getValue() > 1 ? " <gray>(x" + e.getValue() + ")</gray>" : ""))
                .collect(Collectors.joining("<gray>, </gray>"));
    }

    private int history(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        CommandSender sender = ctx.getSource().getSender();
        List<BattleStats> history = statsManager.history();
        if (history.isEmpty()) {
            sender.sendMessage(Messages.msg("<gray>История битв пуста."));
            return 1;
        }
        sender.sendMessage(Messages.raw("<gold>История битв:"));
        for (BattleStats s : history) {
            String winner = s.winner == null ? "<gray>ничья" : s.winner.colorize(teamDisplay(s, s.winner));
            sender.sendMessage(Messages.raw("<gold>#" + s.id + "</gold> <white>" + s.name + "</white> — " + winner));
        }
        return 1;
    }

    private int reload(CommandContext<CommandSourceStack> ctx) {
        battleManager.reload();
        ctx.getSource().getSender().sendMessage(Messages.msg("<green>Конфиг перезагружен."));
        return 1;
    }

    /** Накопитель по игроку для сводной статистики за всё время. */
    private static class Totals {
        String name;
        int battles;
        int kills;
        int deaths;
        int pointsCaptured;
        int damageDealt;
    }

    /** Топ игроков за всё время по выбранной метрике. */
    private int top(CommandContext<CommandSourceStack> ctx, String metric) {
        CommandSender sender = ctx.getSource().getSender();
        List<BattleStats> history = statsManager.history();
        if (history.isEmpty()) {
            sender.sendMessage(Messages.msg("<gray>История битв пуста."));
            return 1;
        }

        Map<String, Totals> totals = new LinkedHashMap<>();
        for (BattleStats s : history) {
            for (BattleStats.PlayerSummary ps : s.players) {
                String key = ps.uuid != null ? ps.uuid.toString() : ps.name;
                if (key == null) {
                    continue;
                }
                Totals t = totals.computeIfAbsent(key, k -> new Totals());
                t.name = ps.name != null ? ps.name : t.name;
                t.battles++;
                t.kills += ps.kills;
                t.deaths += ps.deaths;
                t.pointsCaptured += ps.pointsCaptured;
                t.damageDealt += ps.damageDealt;
            }
        }

        String metricLabel;
        Comparator<Totals> cmp;
        switch (metric) {
            case "kd" -> {
                metricLabel = "K/D";
                cmp = Comparator.comparingDouble((Totals t) -> t.deaths == 0 ? t.kills : (double) t.kills / t.deaths).reversed();
            }
            case "damage" -> {
                metricLabel = "урон";
                cmp = Comparator.comparingInt((Totals t) -> t.damageDealt).reversed();
            }
            case "captures" -> {
                metricLabel = "захваты точек";
                cmp = Comparator.comparingInt((Totals t) -> t.pointsCaptured).reversed();
            }
            default -> {
                metricLabel = "убийства";
                cmp = Comparator.comparingInt((Totals t) -> t.kills).reversed();
            }
        }

        sender.sendMessage(Messages.raw("<gold>═══ Топ игроков за всё время: " + metricLabel + " ═══"));
        sender.sendMessage(Messages.raw("<gray>Всего битв в истории: <white>" + history.size()));
        int place = 1;
        for (Totals t : totals.values().stream().sorted(cmp).limit(10).toList()) {
            String value = switch (metric) {
                case "kd" -> String.format("%.2f", t.deaths == 0 ? t.kills : (double) t.kills / t.deaths);
                case "damage" -> String.valueOf(t.damageDealt);
                case "captures" -> String.valueOf(t.pointsCaptured);
                default -> String.valueOf(t.kills);
            };
            sender.sendMessage(Messages.raw("<gold>" + place++ + ".</gold> <white>" + t.name
                    + "</white> <gray>(" + t.battles + " битв)</gray>: <white>" + value + "</white>"));
        }
        return 1;
    }

    private void printStats(CommandSender sender, BattleStats stats) {
        SimpleDateFormat fmt = new SimpleDateFormat("dd.MM.yyyy HH:mm");
        sender.sendMessage(Messages.raw("<gold>═══ Битва #<white>" + stats.id + "</white>: <white>"
                + stats.name + "</white> ═══"));
        sender.sendMessage(Messages.raw("<gray>Начата: <white>" + fmt.format(new Date(stats.started))
                + "</white>   Длительность: <white>" + stats.durationSeconds + "</white> сек."));
        if (stats.winner != null) {
            sender.sendMessage(Messages.raw("<gold>Победитель: " + stats.winner.colorize(teamDisplay(stats, stats.winner))));
        } else {
            sender.sendMessage(Messages.raw("<gray>Ничья"));
        }
        for (Map.Entry<BattleTeam, BattleStats.TeamSummary> e : stats.teams.entrySet()) {
            BattleStats.TeamSummary s = e.getValue();
            sender.sendMessage(Messages.raw(e.getKey().colorize(teamDisplay(stats, e.getKey())) + ": <white>" + s.score
                    + " <gray>(убийств: " + s.kills + ", смертей: " + s.deaths + ", тимкиллов: " + s.teamkills
                    + ", точек: " + s.pointsCaptured + ", удержаний: " + s.holdAwards + ")</gray>"));
        }
        String firstBlood = firstBlood(stats);
        if (firstBlood != null) {
            sender.sendMessage(Messages.raw("<gold>First blood: </gold>" + firstBlood));
        }
        List<String> timeline = scoreTimeline(stats);
        if (!timeline.isEmpty()) {
            sender.sendMessage(Messages.raw("<gold>Динамика счёта:"));
            for (String line : timeline) {
                sender.sendMessage(Messages.raw("  " + line));
            }
        }
        String mvp = mvp(stats);
        if (mvp != null) {
            sender.sendMessage(Messages.raw("<gold>MVP: </gold>" + mvp));
        }
        if (!stats.players.isEmpty()) {
            sender.sendMessage(Messages.raw("<gold>Топ игроков по убийствам:"));
            int shown = 0;
            for (BattleStats.PlayerSummary ps : stats.players) {
                if (shown >= 10) {
                    break;
                }
                String colored = ps.team != null ? ps.team.colorize(ps.name) : "<white>" + ps.name + "</white>";
                sender.sendMessage(Messages.raw("  " + colored + "<gray>: <white>" + ps.kills
                        + " К / " + ps.deaths + " С</gray> <green>[K/D " + ps.kd() + "]</green>"
                        + " <gray>урон " + ps.damageDealt + "</gray>"));
                shown++;
            }
        }
        List<String> weapons = topWeapons(stats, 5);
        if (!weapons.isEmpty()) {
            sender.sendMessage(Messages.raw("<gold>Топ оружия:"));
            for (String w : weapons) {
                sender.sendMessage(Messages.raw("  - " + w));
            }
        }
        List<String> holds = holdTimes(stats);
        if (!holds.isEmpty()) {
            sender.sendMessage(Messages.raw("<gold>Владение точками:"));
            for (String h : holds) {
                sender.sendMessage(Messages.raw("  - " + h));
            }
        }
        printRoster(sender, stats);
        sender.sendMessage(Messages.raw("<gray>Совет: <white>/battle stats <id> team <команда></white> — команда, "
                + "<white>/battle stats <id> me</white> — лично, "
                + "<white>/battle stats <id> player <ник></white> — другой игрок.</gray>"));
        if (!stats.events.isEmpty()) {
            sender.sendMessage(Messages.raw("<gold>События:"));
            for (StatEvent ev : stats.events) {
                sender.sendMessage(Messages.raw("  <gray>[" + formatTime(ev.timeSeconds) + "]</gray> " + describe(stats, ev)));
            }
        }
    }

    /** Первое убийство битвы (First blood). */
    private String firstBlood(BattleStats stats) {
        StatEvent first = null;
        for (StatEvent ev : stats.events) {
            if (ev.type != StatEvent.Type.KILL && ev.type != StatEvent.Type.TEAMKILL) {
                continue;
            }
            if (first == null || ev.timeSeconds < first.timeSeconds) {
                first = ev;
            }
        }
        if (first == null) {
            return null;
        }
        String killer = first.killer == null ? "?"
                : (first.team != null ? first.team.colorize(first.killer) : "<white>" + first.killer + "</white>");
        String weapon = first.weapon == null ? "" : " <gray>(" + first.weapon + ")</gray>";
        return killer + " <gray>убил(а)</gray> <white>" + first.victim + "</white>" + weapon;
    }

    /** Динамика счёта: снимки каждые N секунд + финальный счёт. */
    private List<String> scoreTimeline(BattleStats stats) {
        if (stats.teams.isEmpty()) {
            return List.of();
        }
        Map<BattleTeam, Integer> scores = new EnumMap<>(BattleTeam.class);
        for (BattleTeam t : stats.teams.keySet()) {
            scores.put(t, 0);
        }
        List<StatEvent> sorted = stats.events.stream()
                .sorted(Comparator.comparingInt(e -> e.timeSeconds))
                .toList();
        int interval = Math.max(60, stats.durationSeconds / 10);
        List<String> lines = new ArrayList<>();
        int idx = 0;
        int mark = interval;
        while (mark <= stats.durationSeconds) {
            while (idx < sorted.size() && sorted.get(idx).timeSeconds < mark) {
                applyDelta(scores, sorted.get(idx));
                idx++;
            }
            lines.add("<gray>[" + formatTime(mark) + "]</gray> " + formatScores(stats, scores));
            mark += interval;
        }
        while (idx < sorted.size()) {
            applyDelta(scores, sorted.get(idx));
            idx++;
        }
        lines.add("<gray>[" + formatTime(stats.durationSeconds) + "]</gray> <white>Финал:</white> "
                + formatScores(stats, scores));
        return lines;
    }

    private void applyDelta(Map<BattleTeam, Integer> scores, StatEvent ev) {
        if (ev.scoreDelta == 0 || ev.team == null) {
            return;
        }
        scores.merge(ev.team, ev.scoreDelta, Integer::sum);
    }

    private String formatScores(BattleStats stats, Map<BattleTeam, Integer> scores) {
        return stats.teams.keySet().stream()
                .sorted(Comparator.comparingInt((BattleTeam t) -> scores.getOrDefault(t, 0)).reversed())
                .map(t -> t.colorize(teamDisplay(stats, t)) + ": <white>" + scores.getOrDefault(t, 0))
                .collect(Collectors.joining("   "));
    }

    /** Лучший игрок битвы (MVP) по комбинации очков. */
    private String mvp(BattleStats stats) {
        if (stats.players.isEmpty()) {
            return null;
        }
        BattleStats.PlayerSummary best = null;
        double bestScore = Double.NEGATIVE_INFINITY;
        for (BattleStats.PlayerSummary ps : stats.players) {
            double score = ps.kills * 2.0 + ps.pointsCaptured * 3.0 + ps.damageDealt / 100.0 - ps.teamkills * 2.0;
            if (score > bestScore) {
                bestScore = score;
                best = ps;
            }
        }
        if (best == null) {
            return null;
        }
        String colored = best.team != null ? best.team.colorize(best.name) : "<white>" + best.name + "</white>";
        return colored + " <gray>[</gray><white>" + best.kills + " К / " + best.deaths + " С</white>"
                + " <gray>урон " + best.damageDealt + ", точек " + best.pointsCaptured + "</gray><gray>]</gray>";
    }

    /** Топ оружия по всем убийствам битвы. */
    private List<String> topWeapons(BattleStats stats, int limit) {
        Map<String, Integer> counts = new LinkedHashMap<>();
        for (StatEvent ev : stats.events) {
            if ((ev.type != StatEvent.Type.KILL && ev.type != StatEvent.Type.TEAMKILL) || ev.weapon == null) {
                continue;
            }
            counts.merge(ev.weapon, 1, Integer::sum);
        }
        return counts.entrySet().stream()
                .sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
                .limit(limit)
                .map(e -> "<white>" + e.getKey() + "</white> <gray>— " + e.getValue() + " убийств(а)</gray>")
                .collect(Collectors.toList());
    }

    /** Сколько времени каждая команда удерживала каждую точку. */
    private List<String> holdTimes(BattleStats stats) {
        Map<String, Map<BattleTeam, Integer>> holds = new LinkedHashMap<>();
        Map<String, BattleTeam> owner = new LinkedHashMap<>();
        Map<String, Integer> since = new LinkedHashMap<>();

        for (StatEvent ev : stats.events.stream()
                .sorted(Comparator.comparingInt(e -> e.timeSeconds))
                .toList()) {
            if (ev.type != StatEvent.Type.POINT_CAPTURED || ev.team == null || ev.point == null) {
                continue;
            }
            BattleTeam prev = owner.put(ev.point, ev.team);
            int prevTime = since.getOrDefault(ev.point, 0);
            if (prev != null) {
                holds.computeIfAbsent(ev.point, k -> new EnumMap<>(BattleTeam.class))
                        .merge(prev, Math.max(0, ev.timeSeconds - prevTime), Integer::sum);
            }
            since.put(ev.point, ev.timeSeconds);
        }
        for (Map.Entry<String, BattleTeam> e : owner.entrySet()) {
            int held = Math.max(0, stats.durationSeconds - since.getOrDefault(e.getKey(), 0));
            if (held > 0) {
                holds.computeIfAbsent(e.getKey(), k -> new EnumMap<>(BattleTeam.class))
                        .merge(e.getValue(), held, Integer::sum);
            }
        }

        List<String> lines = new ArrayList<>();
        for (Map.Entry<String, Map<BattleTeam, Integer>> e : holds.entrySet()) {
            String details = e.getValue().entrySet().stream()
                    .sorted(Map.Entry.<BattleTeam, Integer>comparingByValue().reversed())
                    .map(en -> en.getKey().colorize(teamDisplay(stats, en.getKey()))
                            + " <white>" + formatTime(en.getValue()) + "</white>")
                    .collect(Collectors.joining("   "));
            if (!details.isBlank()) {
                lines.add("<yellow>" + e.getKey() + "</yellow>: " + details);
            }
        }
        return lines;
    }

    /** Полный состав участников битвы по командам с личной статистикой. */
    private void printRoster(CommandSender sender, BattleStats stats) {
        if (stats.players.isEmpty()) {
            return;
        }
        sender.sendMessage(Messages.raw("<gold>Состав участников: <gray>(" + stats.players.size() + ")</gray>"));
        Map<BattleTeam, List<BattleStats.PlayerSummary>> byTeam = new EnumMap<>(BattleTeam.class);
        List<BattleStats.PlayerSummary> noTeam = new ArrayList<>();
        for (BattleStats.PlayerSummary ps : stats.players) {
            if (ps.team != null) {
                byTeam.computeIfAbsent(ps.team, k -> new ArrayList<>()).add(ps);
            } else {
                noTeam.add(ps);
            }
        }
        for (BattleTeam team : stats.teams.keySet()) {
            List<BattleStats.PlayerSummary> members = new ArrayList<>(byTeam.getOrDefault(team, List.of()));
            members.sort(Comparator.comparingInt((BattleStats.PlayerSummary s) -> s.kills).reversed());
            sender.sendMessage(Messages.raw("  " + team.colorize(teamDisplay(stats, team))
                    + " <gray>(" + members.size() + "):</gray>"));
            for (BattleStats.PlayerSummary ps : members) {
                sender.sendMessage(Messages.raw("    " + team.colorize(ps.name) + "<gray>: <white>" + ps.kills
                        + " К / " + ps.deaths + " С</white> <green>[K/D " + ps.kd() + "]</green>"
                        + " <gray>урон " + ps.damageDealt + " / получен " + ps.damageTaken
                        + " / серия " + ps.bestStreak + "</gray>"));
            }
        }
        if (!noTeam.isEmpty()) {
            sender.sendMessage(Messages.raw("  <gray>Без команды:</gray>"));
            for (BattleStats.PlayerSummary ps : noTeam) {
                sender.sendMessage(Messages.raw("    <white>" + ps.name + "</white><gray>: <white>"
                        + ps.kills + " К / " + ps.deaths + " С</gray>"));
            }
        }
    }

    private String describe(BattleStats stats, StatEvent ev) {
        String killer = ev.killer == null ? null
                : (ev.team != null ? ev.team.colorize(ev.killer) : "<white>" + ev.killer + "</white>");
        return switch (ev.type) {
            case KILL -> killer + " <gray>убил(а)</gray> <white>" + ev.victim + "</white> <gray>("
                    + ev.weapon + ")</gray> <green>(+" + ev.scoreDelta + ")</green>";
            case TEAMKILL -> killer + " <red>убил(а) союзника</red> <white>" + ev.victim + "</white> <gray>("
                    + ev.weapon + ")</gray> <yellow>(" + ev.scoreDelta + ")</yellow>";
            case DEATH -> "<white>" + ev.victim + "</white> <gray>погиб(ла)</gray> <yellow>(" + ev.scoreDelta + ")</yellow>";
            case POINT_START -> (ev.team != null ? ev.team.colorize(teamDisplay(stats, ev.team)) : "")
                    + " <gray>начал(а) захват</gray> <yellow>" + ev.point + "</yellow>";
            case POINT_CAPTURED -> (ev.team != null ? ev.team.colorize(teamDisplay(stats, ev.team)) : "")
                    + " <green>захватил(а)</green> <yellow>" + ev.point + "</yellow>";
            case POINT_LOST -> (ev.team != null ? ev.team.colorize(teamDisplay(stats, ev.team)) : "")
                    + " <red>потерял(а)</red> <yellow>" + ev.point + "</yellow>";
            case POINT_HOLD -> (ev.team != null ? ev.team.colorize(teamDisplay(stats, ev.team)) : "")
                    + " <gray>удержание точки</gray> <yellow>" + ev.point + "</yellow> <green>(+" + ev.scoreDelta + ")</green>";
            default -> ev.type.name();
        };
    }

    private String formatTime(int seconds) {
        return String.format("%d:%02d", seconds / 60, seconds % 60);
    }
}
