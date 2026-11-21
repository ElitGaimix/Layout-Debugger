package fr.eligaimix.ltdr.storage;

import fr.eligaimix.ltdr.playback.LayoutService;
import fr.eligaimix.ltdr.sequence.*;

import java.io.IOException;
import java.util.*;
import java.util.function.Consumer;

/** Shared command implementation; platform adapters supply permissions and the acting UUID. */
public final class AnimationCommands {
    private AnimationCommands() {}

    public static void admin(
            LayoutService service,
            String[] args,
            boolean view,
            boolean assign,
            UUID requester,
            Consumer<String> say)
            throws IOException {
        if (args.length == 3 && args[0].equals("assign")) {
            if (!assign) throw new IllegalArgumentException("Permission admin.assign requise.");
            UUID owner = uuid(args[2]);
            service.assignLegacy(args[1], owner);
            say.accept("Assigned: " + args[1] + " → " + owner);
            return;
        }
        if (!view) throw new IllegalArgumentException("Permission admin.view requise.");
        if (args.length >= 1 && args[0].equals("owners")) {
            if (args.length > 2) throw new IllegalArgumentException("/ld admin owners [page]");
            printPage(
                    service.definitions().values().stream()
                            .map(d -> d.owner() == null ? "legacy" : d.owner().toString())
                            .distinct()
                            .sorted()
                            .toList(),
                    args.length == 2 ? page(args[1]) : 1,
                    say);
            return;
        }

        if (args.length >= 2 && args[0].equals("list")) {
            int page = args.length == 3 ? page(args[2]) : 1;
            if (args.length > 3)
                throw new IllegalArgumentException("/ld admin list <UUID|legacy> [page]");
            UUID owner = args[1].equals("legacy") ? null : uuid(args[1]);
            var lines =
                    service.definitions().values().stream()
                            .filter(d -> Objects.equals(d.owner(), owner))
                            .map(d ->
                                    d.name()
                                            + " | "
                                            + d.pistons().size()
                                            + " pistons | opening="
                                            + d.actions().size()
                                            + " closing="
                                            + d.closingActions().size())
                            .sorted()
                            .toList();
            printPage(lines, page, say);
            return;
        }
        if (args.length >= 3 && args[0].equals("show")) {
            if (args.length > 4)
                throw new IllegalArgumentException("/ld admin show <UUID|legacy> <name> [page]");
            var d = resolve(service, args[1], args[2]);
            int page = args.length == 4 ? page(args[3]) : 1;
            var lines = new ArrayList<String>();
            lines.add(d.name() + " | owner=" + d.owner() + " | " + d.dimension());
            d.pistons()
                    .forEach(p ->
                            lines.add(
                                    p.id()
                                            + " @ "
                                            + p.position()
                                            + " "
                                            + p.facing()
                                            + " sticky="
                                            + p.sticky()));
            for (boolean closing : new boolean[] {false, true})
                for (var a : d.timeline(closing).actions())
                    lines.add(
                            (closing ? "closing" : "opening")
                                    + " t="
                                    + a.tick()
                                    + " #"
                                    + a.order()
                                    + " "
                                    + a.pistonId()
                                    + " "
                                    + (a.powered() ? "ON" : "OFF"));
            printPage(lines, page, say);
            return;
        }
        if (args.length == 3 && args[0].equals("export")) {
            if (requester == null)
                throw new IllegalArgumentException(
                        "Export admin réservé à un joueur (dossier UUID).");
            var result =
                    AnimationExchange.write(
                            service.directory(), requester, resolve(service, args[1], args[2]));
            say.accept("Export: " + result.path());
            return;
        }
        say.accept(
                "/ld admin list <UUID|legacy> [page] | show <UUID|legacy> <name> [page] | export"
                        + " <UUID|legacy> <name> | assign <legacyName> <creatorUUID>");
    }

    private static SequenceDefinition resolve(LayoutService s, String owner, String name) {
        SequenceDefinition.validateId(name);
        return owner.equals("legacy") ? s.definition(name) : s.owned(uuid(owner), name);
    }

    private static UUID uuid(String value) {
        UUID id = UUID.fromString(value);
        if (!id.toString().equalsIgnoreCase(value))
            throw new IllegalArgumentException("UUID complet requis.");
        return id;
    }

    private static int page(String value) {
        int page = Integer.parseInt(value);
        if (page < 1) throw new IllegalArgumentException("Page >= 1");
        return page;
    }

    private static void printPage(List<String> lines, int page, Consumer<String> say) {
        int pages = Math.max(1, (lines.size() + 19) / 20);
        if (page > pages) throw new IllegalArgumentException("Page maximale : " + pages);
        say.accept("Page " + page + "/" + pages);
        lines.stream().skip((page - 1L) * 20).limit(20).forEach(say);
    }

    public static void personal(
            LayoutService service,
            UUID player,
            String dimension,
            BlockPosition anchor,
            String[] args,
            Consumer<String> say)
            throws IOException {
        if (args.length == 2 && args[0].equals("export")) {
            say.accept(
                    "Export: "
                            + AnimationExchange.write(
                                            service.directory(),
                                            player,
                                            service.owned(player, args[1]))
                                    .path());
            return;
        }
        if (args.length == 3 && args[0].equals("import")) {
            var d =
                    AnimationExchange.read(
                            service.directory(), player, args[1], args[2], dimension, anchor);
            if (service.personal(player).containsKey(d.name()))
                throw new IllegalArgumentException("Ce nom existe déjà.");
            service.put(d);
            say.accept("Imported: " + d.name() + " (données uniquement ; aucun bloc placé)");
            return;
        }
        throw new IllegalArgumentException(
                "/ld export <name> | /ld import <file.ldanim.json> <newName> (ancre = bloc occupé"
                        + " par le joueur)");
    }
}
