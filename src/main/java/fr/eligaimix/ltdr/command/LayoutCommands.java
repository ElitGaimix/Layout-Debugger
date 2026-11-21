package fr.eligaimix.ltdr.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import com.mojang.brigadier.suggestion.Suggestions;

import fr.eligaimix.ltdr.LayoutDebugger;
import fr.eligaimix.ltdr.network.EditorRequest;
import fr.eligaimix.ltdr.piston.RedstonePistonController;
import fr.eligaimix.ltdr.playback.LayoutService;
import fr.eligaimix.ltdr.sequence.BlockPosition;
import fr.eligaimix.ltdr.sequence.PistonTarget;
import fr.eligaimix.ltdr.sequence.SequenceAction;
import fr.eligaimix.ltdr.sequence.SequenceDefinition;

import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.coordinates.BlockPosArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.piston.PistonBaseBlock;
import net.minecraft.world.level.block.state.BlockState;

import java.io.IOException;

/** Coordinates select piston bases explicitly; all mutations run on the logical server. */
public final class LayoutCommands {
    private LayoutCommands() {}

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        var root =
                Commands.literal("ld")
                        .requires(source ->
                                permitted(source, "use")
                                        || permitted(source, "admin/view")
                                        || permitted(source, "admin/assign"))
                        .executes(context ->
                                execute(
                                        context,
                                        () ->
                                                say(
                                                        context,
                                                        "/ld create|rename|delete|list|show ; /ld"
                                                                + " piston add|remove ; /ld action"
                                                                + " add|remove ; /ld"
                                                                + " run|stop|status")));

        root.then(
                Commands.literal("create")
                        .then(
                                Commands.argument("sequence", StringArgumentType.word())
                                        .executes(context ->
                                                execute(
                                                        context,
                                                        () -> {
                                                            service(context)
                                                                    .create(
                                                                            context.getSource()
                                                                                    .getPlayerOrException()
                                                                                    .getUUID(),
                                                                            sequenceName(context),
                                                                            context.getSource()
                                                                                    .getLevel()
                                                                                    .dimension()
                                                                                    .identifier()
                                                                                    .toString());
                                                            say(
                                                                    context,
                                                                    "Séquence créée : "
                                                                            + sequenceName(
                                                                                    context));
                                                        }))));
        root.then(
                Commands.literal("rename")
                        .then(
                                sequenceArgument()
                                        .then(
                                                Commands.argument(
                                                                "newName",
                                                                StringArgumentType.word())
                                                        .executes(context ->
                                                                execute(
                                                                        context,
                                                                        () -> {
                                                                            String name =
                                                                                    StringArgumentType
                                                                                            .getString(
                                                                                                    context,
                                                                                                    "newName");
                                                                            service(context)
                                                                                    .rename(
                                                                                            LayoutService
                                                                                                    .key(
                                                                                                            context.getSource()
                                                                                                                    .getPlayerOrException()
                                                                                                                    .getUUID(),
                                                                                                            sequenceName(
                                                                                                                    context)),
                                                                                            name);
                                                                            say(
                                                                                    context,
                                                                                    "Animation"
                                                                                        + " renommée"
                                                                                        + " : "
                                                                                            + name);
                                                                        })))));
        root.then(
                Commands.literal("delete")
                        .then(
                                sequenceArgument()
                                        .executes(context ->
                                                execute(
                                                        context,
                                                        () -> {
                                                            service(context)
                                                                    .delete(
                                                                            LayoutService.key(
                                                                                    context.getSource()
                                                                                            .getPlayerOrException()
                                                                                            .getUUID(),
                                                                                    sequenceName(
                                                                                            context)));
                                                            say(
                                                                    context,
                                                                    "Séquence supprimée : "
                                                                            + sequenceName(
                                                                                    context));
                                                        }))));
        root.then(
                Commands.literal("list")
                        .executes(context ->
                                execute(
                                        context,
                                        () -> {
                                            var definitions =
                                                    service(context)
                                                            .personal(
                                                                    context.getSource()
                                                                            .getPlayerOrException()
                                                                            .getUUID());
                                            if (definitions.isEmpty())
                                                say(context, "Aucune séquence. /ld create <nom>");
                                            definitions
                                                    .values()
                                                    .forEach(sequence ->
                                                            say(
                                                                    context,
                                                                    sequence.name()
                                                                            + " : "
                                                                            + sequence.dimension()
                                                                            + ", "
                                                                            + sequence.pistons()
                                                                                    .size()
                                                                            + " pistons, "
                                                                            + sequence.actions()
                                                                                    .size()
                                                                            + " actions"));
                                        })));
        root.then(
                Commands.literal("show")
                        .then(
                                sequenceArgument()
                                        .executes(
                                                context -> execute(context, () -> show(context)))));
        root.then(
                Commands.literal("edit")
                        .executes(context ->
                                execute(
                                        context,
                                        () ->
                                                service(context)
                                                        .editor()
                                                        .request(
                                                                context.getSource()
                                                                        .getPlayerOrException(),
                                                                new EditorRequest(
                                                                        "open", "", 0, 0)))));

        var selectionPosition =
                Commands.argument("pos", BlockPosArgument.blockPos())
                        .executes(context -> execute(context, () -> addPiston(context)));
        var selectionId =
                Commands.argument("piston", StringArgumentType.word()).then(selectionPosition);
        var removePistonId =
                pistonArgument()
                        .executes(context ->
                                execute(
                                        context,
                                        () -> {
                                            service(context)
                                                    .put(
                                                            definition(context)
                                                                    .withoutPiston(
                                                                            pistonName(context)));
                                            say(context, "Piston retiré : " + pistonName(context));
                                        }));
        root.then(
                Commands.literal("piston")
                        .then(Commands.literal("add").then(sequenceArgument().then(selectionId)))
                        .then(
                                Commands.literal("remove")
                                        .then(sequenceArgument().then(removePistonId))));

        var actionTarget =
                pistonArgument()
                        .then(
                                Commands.literal("on")
                                        .executes(context ->
                                                execute(context, () -> addAction(context, true))))
                        .then(
                                Commands.literal("off")
                                        .executes(context ->
                                                execute(context, () -> addAction(context, false))));
        var actionOrder =
                Commands.argument("order", IntegerArgumentType.integer(0)).then(actionTarget);
        var actionTick =
                Commands.argument("tick", IntegerArgumentType.integer(0)).then(actionOrder);
        var removeActionOrder =
                Commands.argument("order", IntegerArgumentType.integer(0))
                        .executes(context ->
                                execute(
                                        context,
                                        () -> {
                                            service(context)
                                                    .put(
                                                            definition(context)
                                                                    .withoutAction(
                                                                            integer(
                                                                                    context,
                                                                                    "tick"),
                                                                            integer(
                                                                                    context,
                                                                                    "order")));
                                            say(context, "Action supprimée.");
                                        }));
        var removeActionTick =
                Commands.argument("tick", IntegerArgumentType.integer(0)).then(removeActionOrder);
        root.then(
                Commands.literal("action")
                        .then(Commands.literal("add").then(sequenceArgument().then(actionTick)))
                        .then(
                                Commands.literal("remove")
                                        .then(sequenceArgument().then(removeActionTick))));

        root.then(
                Commands.literal("run")
                        .then(
                                sequenceArgument()
                                        .executes(context ->
                                                execute(
                                                        context,
                                                        () -> {
                                                            service(context)
                                                                    .start(
                                                                            sequenceName(context),
                                                                            context.getSource()
                                                                                    .getPlayerOrException(),
                                                                            context.getSource()
                                                                                    .getLevel());
                                                            say(
                                                                    context,
                                                                    "Lecture armée. Tick 0 au"
                                                                        + " prochain tick de jeu de"
                                                                        + " la dimension ; /ld"
                                                                        + " status pour les"
                                                                        + " consignes.");
                                                        }))));
        root.then(
                Commands.literal("stop")
                        .executes(context ->
                                execute(
                                        context,
                                        () -> {
                                            if (service(context).editor().isActive()) {
                                                service(context)
                                                        .editor()
                                                        .request(
                                                                context.getSource()
                                                                        .getPlayerOrException(),
                                                                new EditorRequest("end", "", 0, 0));
                                                return;
                                            }
                                            service(context)
                                                    .requirePlaybackOwner(
                                                            context.getSource()
                                                                    .getPlayerOrException()
                                                                    .getUUID());
                                            service(context)
                                                    .stop()
                                                    .forEach(warning -> say(context, warning));
                                            say(
                                                    context,
                                                    "Lecture arrêtée ; retrait des alimentations"
                                                        + " possédées demandé. Aucun rembobinage.");
                                        })));
        root.then(
                Commands.literal("status")
                        .executes(context ->
                                execute(
                                        context,
                                        () -> {
                                            service(context)
                                                    .requirePlaybackOwner(
                                                            context.getSource()
                                                                    .getPlayerOrException()
                                                                    .getUUID());
                                            service(context)
                                                    .status()
                                                    .forEach(line -> say(context, line));
                                        })));
        for (String operation : java.util.List.of("export", "import"))
            root.then(
                    Commands.literal(operation)
                            .then(
                                    Commands.argument(
                                                    "arguments", StringArgumentType.greedyString())
                                            .executes(context ->
                                                    execute(
                                                            context,
                                                            () -> {
                                                                var player =
                                                                        context.getSource()
                                                                                .getPlayerOrException();
                                                                var pos = player.blockPosition();
                                                                fr.eligaimix.ltdr.storage
                                                                        .AnimationCommands.personal(
                                                                        service(context),
                                                                        player.getUUID(),
                                                                        player.level()
                                                                                .dimension()
                                                                                .identifier()
                                                                                .toString(),
                                                                        new BlockPosition(
                                                                                pos.getX(),
                                                                                pos.getY(),
                                                                                pos.getZ()),
                                                                        (operation
                                                                                        + " "
                                                                                        + StringArgumentType
                                                                                                .getString(
                                                                                                        context,
                                                                                                        "arguments"))
                                                                                .split(" +"),
                                                                        line -> say(context, line));
                                                            }))));
        root.then(
                Commands.literal("admin")
                        .requires(source ->
                                permitted(source, "admin/view")
                                        || permitted(source, "admin/assign"))
                        .then(
                                Commands.argument("arguments", StringArgumentType.greedyString())
                                        .executes(context -> {
                                            try {
                                                fr.eligaimix.ltdr.storage.AnimationCommands.admin(
                                                        service(context),
                                                        StringArgumentType.getString(
                                                                        context, "arguments")
                                                                .split(" +"),
                                                        permitted(
                                                                context.getSource(), "admin/view"),
                                                        permitted(
                                                                context.getSource(),
                                                                "admin/assign"),
                                                        context.getSource().getEntity()
                                                                        instanceof
                                                                        net.minecraft.server.level
                                                                                        .ServerPlayer
                                                                                p
                                                                ? p.getUUID()
                                                                : null,
                                                        line -> say(context, line));
                                                return 1;
                                            } catch (IOException
                                                    | IllegalArgumentException
                                                    | IllegalStateException e) {
                                                throw new SimpleCommandExceptionType(
                                                                Component.literal(e.getMessage()))
                                                        .create();
                                            }
                                        })));
        root.then(
                Commands.literal("whoami")
                        .executes(context ->
                                execute(
                                        context,
                                        () ->
                                                say(
                                                        context,
                                                        context.getSource()
                                                                .getPlayerOrException()
                                                                .getUUID()
                                                                .toString()))));
        dispatcher.register(root);
    }

    private static boolean permitted(CommandSourceStack source, String node) {
        return ((net.fabricmc.fabric.api.permission.v1.PermissionContextOwner) (Object) source)
                .checkPermission(
                        LayoutDebugger.id(node),
                        node.equals("use")
                                ? net.minecraft.server.permissions.PermissionLevel.GAMEMASTERS
                                : net.minecraft.server.permissions.PermissionLevel.ADMINS);
    }

    private static void addPiston(CommandContext<CommandSourceStack> context)
            throws CommandSyntaxException, IOException {
        SequenceDefinition definition = definition(context);
        var level = context.getSource().getLevel();
        if (!definition.dimension().equals(level.dimension().identifier().toString())) {
            throw new IllegalArgumentException(
                    "Sélectionner les pistons dans la dimension " + definition.dimension());
        }
        BlockPos pos = BlockPosArgument.getLoadedBlockPos(context, "pos");
        BlockState state = level.getBlockState(pos);
        if (!(state.getBlock() instanceof PistonBaseBlock))
            throw new IllegalArgumentException("Sélectionner la base d'un piston vanilla.");
        PistonTarget target =
                new PistonTarget(
                        pistonName(context),
                        new BlockPosition(pos.getX(), pos.getY(), pos.getZ()),
                        state.getValue(PistonBaseBlock.FACING).getName(),
                        state.is(Blocks.STICKY_PISTON));
        SequenceDefinition updated = definition.withPiston(target);
        // Same preflight as playback: report occupied/overlapping source slots immediately.
        RedstonePistonController.validateTargets(level, updated.pistons());
        service(context).put(updated);
        say(context, "Piston " + target.id() + " enregistré ; alimentation virtuelle additive.");
    }

    private static void addAction(CommandContext<CommandSourceStack> context, boolean powered)
            throws IOException, CommandSyntaxException {
        service(context)
                .put(
                        definition(context)
                                .withAction(
                                        new SequenceAction(
                                                integer(context, "tick"),
                                                integer(context, "order"),
                                                pistonName(context),
                                                powered)));
        say(
                context,
                "Action ajoutée : t="
                        + integer(context, "tick")
                        + " ordre="
                        + integer(context, "order")
                        + " "
                        + pistonName(context)
                        + " "
                        + (powered ? "on" : "off"));
    }

    private static void show(CommandContext<CommandSourceStack> context)
            throws CommandSyntaxException {
        SequenceDefinition sequence = definition(context);
        say(context, sequence.name() + " — " + sequence.dimension());
        sequence.pistons()
                .forEach(target ->
                        say(
                                context,
                                target.id()
                                        + " @ "
                                        + target.position()
                                        + " "
                                        + target.facing()
                                        + (target.sticky() ? " collant" : " normal")));
        sequence.actions()
                .forEach(action ->
                        say(
                                context,
                                "t="
                                        + action.tick()
                                        + " ordre="
                                        + action.order()
                                        + " "
                                        + action.pistonId()
                                        + " "
                                        + (action.powered() ? "on" : "off")));
    }

    private static RequiredArgumentBuilder<CommandSourceStack, String> sequenceArgument() {
        return Commands.argument("sequence", StringArgumentType.word())
                .suggests((context, builder) -> {
                    try {
                        return SharedSuggestionProvider.suggest(
                                service(context)
                                        .personal(
                                                context.getSource()
                                                        .getPlayerOrException()
                                                        .getUUID())
                                        .keySet(),
                                builder);
                    } catch (IllegalArgumentException
                            | IllegalStateException
                            | CommandSyntaxException exception) {
                        return Suggestions.empty();
                    }
                });
    }

    private static RequiredArgumentBuilder<CommandSourceStack, String> pistonArgument() {
        return Commands.argument("piston", StringArgumentType.word())
                .suggests((context, builder) -> {
                    try {
                        return SharedSuggestionProvider.suggest(
                                definition(context).pistons().stream().map(PistonTarget::id),
                                builder);
                    } catch (IllegalArgumentException
                            | IllegalStateException
                            | CommandSyntaxException exception) {
                        return Suggestions.empty();
                    }
                });
    }

    private static String sequenceName(CommandContext<CommandSourceStack> context) {
        return StringArgumentType.getString(context, "sequence");
    }

    private static String pistonName(CommandContext<CommandSourceStack> context) {
        return StringArgumentType.getString(context, "piston");
    }

    private static int integer(CommandContext<CommandSourceStack> context, String name) {
        return IntegerArgumentType.getInteger(context, name);
    }

    private static LayoutService service(CommandContext<CommandSourceStack> context) {
        return LayoutDebugger.service(context.getSource().getServer());
    }

    private static SequenceDefinition definition(CommandContext<CommandSourceStack> context)
            throws CommandSyntaxException {
        return service(context)
                .owned(context.getSource().getPlayerOrException().getUUID(), sequenceName(context));
    }

    private static void say(CommandContext<CommandSourceStack> context, String message) {
        context.getSource()
                .sendSuccess(
                        () ->
                                Component.literal("[Layout Debugger] ")
                                        .withStyle(ChatFormatting.AQUA)
                                        .append(
                                                Component.literal(message)
                                                        .withStyle(ChatFormatting.GRAY)),
                        false);
    }

    private static int execute(CommandContext<CommandSourceStack> context, Operation operation)
            throws CommandSyntaxException {
        if (!permitted(context.getSource(), "use")
                || !context.getSource().getPlayerOrException().isCreative()) {
            throw new SimpleCommandExceptionType(
                            Component.literal("Layout Debugger nécessite le mode créatif."))
                    .create();
        }
        try {
            operation.run();
            return 1;
        } catch (IllegalArgumentException | IllegalStateException | IOException exception) {
            if (exception instanceof IOException)
                LayoutDebugger.LOGGER.error("Sauvegarde des séquences impossible", exception);
            throw new SimpleCommandExceptionType(Component.literal(exception.getMessage()))
                    .create();
        }
    }

    @FunctionalInterface
    private interface Operation {
        void run() throws CommandSyntaxException, IOException;
    }
}
