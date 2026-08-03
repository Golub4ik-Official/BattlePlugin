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
import java.util.Date;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

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
                                        .executes(this::teamGiveInv))))
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
            sender.sendMessage(Messages.raw("<yellow>/battle history</yellow> <gray>— история завершённых битв</gray>"));
        }
        if (admin) {
            sender.sendMessage(Messages.raw("<yellow>/battle start <минуты> <команда1> <команда2> [команда3] [команда4] <название></yellow> <gray>— начать битву</gray>"));
            sender.sendMessage(Messages.raw("<yellow>/battle stop</yellow> <gray>— остановить битву</gray>"));
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
        sender.sendMessage(Messages.raw("<gold>═══ Команда " + team.colorize(team.displayName())
                + "</gold> <gray>(битва #<white>" + stats.id + "</white>: " + stats.name + ")</gray>"));
        sender.sendMessage(Messages.raw("<gray>- Очки: <white>"
                + s.score + "    <gray>Убийств: <white>" + s.kills
                + "    <gray>Смертей: <white>" + s.deaths
                + "    <gray>Тимкиллов: <white>" + s.teamkills));
        sender.sendMessage(Messages.raw("<gray>Точек захвачено: <white>" + s.pointsCaptured
                + "    <gray>Удержаний: <white>" + s.holdAwards));
        sender.sendMessage(Messages.raw("<gold>Игроки команды:"));
        for (BattleStats.PlayerSummary ps : stats.players) {
            if (ps.team != team) {
                continue;
            }
            sender.sendMessage(Messages.raw("  " + team.colorize(ps.name)
                    + "<gray>: <white>" + ps.kills + " К / " + ps.deaths + " С / " + ps.teamkills + " ТК"
                    + " <gray>[</gray><green>K/D " + ps.kd() + "</green><gray>]</gray>"));
        }
    }

    private void printPlayerStats(CommandSender sender, BattleStats stats, BattleStats.PlayerSummary ps) {
        BattleTeam team = ps.team;
        String colored = team != null ? team.colorize(ps.name) : "<white>" + ps.name + "</white>";
        sender.sendMessage(Messages.raw("<gold>═══ " + colored + " <gold>— битва #<white>" + stats.id
                + "</white>: <white>" + stats.name + "</white> ═══"));
        if (team != null) {
            sender.sendMessage(Messages.raw("<gray>Команда: " + team.colorize(team.displayName())));
        }
        sender.sendMessage(Messages.raw("<red>Убийств: <white>" + ps.kills
                + "    <red>Смертей: <white>" + ps.deaths
                + "    <red>Тимкиллов: <white>" + ps.teamkills));
        sender.sendMessage(Messages.raw("<green>K/D: <white>" + ps.kd()
                + "    <yellow>Лучшая серия: <white>" + ps.bestStreak
                + "    <yellow>Точек захвачено: <white>" + ps.pointsCaptured));
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
            String winner = s.winner == null ? "<gray>ничья" : s.winner.colorize(s.winner.displayName());
            sender.sendMessage(Messages.raw("<gold>#" + s.id + "</gold> <white>" + s.name + "</white> — " + winner));
        }
        return 1;
    }

    private int reload(CommandContext<CommandSourceStack> ctx) {
        battleManager.reload();
        ctx.getSource().getSender().sendMessage(Messages.msg("<green>Конфиг перезагружен."));
        return 1;
    }

    private void printStats(CommandSender sender, BattleStats stats) {
        SimpleDateFormat fmt = new SimpleDateFormat("dd.MM.yyyy HH:mm");
        sender.sendMessage(Messages.raw("<gold>═══ Битва #<white>" + stats.id + "</white>: <white>"
                + stats.name + "</white> ═══"));
        sender.sendMessage(Messages.raw("<gray>Начата: <white>" + fmt.format(new Date(stats.started))
                + "</white>   Длительность: <white>" + stats.durationSeconds + "</white> сек."));
        if (stats.winner != null) {
            sender.sendMessage(Messages.raw("<gold>Победитель: " + stats.winner.colorize(stats.winner.displayName())));
        } else {
            sender.sendMessage(Messages.raw("<gray>Ничья"));
        }
        for (Map.Entry<BattleTeam, BattleStats.TeamSummary> e : stats.teams.entrySet()) {
            BattleStats.TeamSummary s = e.getValue();
            sender.sendMessage(Messages.raw(e.getKey().colorize(e.getKey().displayName()) + ": <white>" + s.score
                    + " <gray>(убийств: " + s.kills + ", смертей: " + s.deaths + ", тимкиллов: " + s.teamkills
                    + ", точек: " + s.pointsCaptured + ", удержаний: " + s.holdAwards + ")</gray>"));
        }
        if (!stats.players.isEmpty()) {
            sender.sendMessage(Messages.raw("<gold>Топ игроков по убийствам:"));
            for (BattleStats.PlayerSummary ps : stats.players) {
                String colored = ps.team != null ? ps.team.colorize(ps.name) : "<white>" + ps.name + "</white>";
                sender.sendMessage(Messages.raw("  " + colored + "<gray>: <white>" + ps.kills
                        + " К / " + ps.deaths + " С</gray> <green>[K/D " + ps.kd() + "]</green>"));
            }
        }
        sender.sendMessage(Messages.raw("<gray>Совет: <white>/battle stats <id> team <команда></white> — команда, "
                + "<white>/battle stats <id> me</white> — лично, "
                + "<white>/battle stats <id> player <ник></white> — другой игрок.</gray>"));
        if (!stats.events.isEmpty()) {
            sender.sendMessage(Messages.raw("<gold>События:"));
            for (StatEvent ev : stats.events) {
                sender.sendMessage(Messages.raw("  <gray>[" + formatTime(ev.timeSeconds) + "]</gray> " + describe(ev)));
            }
        }
    }

    private String describe(StatEvent ev) {
        String killer = ev.killer == null ? null
                : (ev.team != null ? ev.team.colorize(ev.killer) : "<white>" + ev.killer + "</white>");
        return switch (ev.type) {
            case KILL -> killer + " <gray>убил(а)</gray> <white>" + ev.victim + "</white> <gray>("
                    + ev.weapon + ")</gray> <green>(+" + ev.scoreDelta + ")</green>";
            case TEAMKILL -> killer + " <red>убил(а) союзника</red> <white>" + ev.victim + "</white> <gray>("
                    + ev.weapon + ")</gray> <yellow>(" + ev.scoreDelta + ")</yellow>";
            case DEATH -> "<white>" + ev.victim + "</white> <gray>погиб(ла)</gray> <yellow>(" + ev.scoreDelta + ")</yellow>";
            case POINT_START -> (ev.team != null ? ev.team.colorize(ev.team.displayName()) : "")
                    + " <gray>начал(а) захват</gray> <yellow>" + ev.point + "</yellow>";
            case POINT_CAPTURED -> (ev.team != null ? ev.team.colorize(ev.team.displayName()) : "")
                    + " <green>захватил(а)</green> <yellow>" + ev.point + "</yellow>";
            case POINT_LOST -> (ev.team != null ? ev.team.colorize(ev.team.displayName()) : "")
                    + " <red>потерял(а)</red> <yellow>" + ev.point + "</yellow>";
            case POINT_HOLD -> (ev.team != null ? ev.team.colorize(ev.team.displayName()) : "")
                    + " <gray>удержание точки</gray> <yellow>" + ev.point + "</yellow> <green>(+" + ev.scoreDelta + ")</green>";
            default -> ev.type.name();
        };
    }

    private String formatTime(int seconds) {
        return String.format("%d:%02d", seconds / 60, seconds % 60);
    }
}
