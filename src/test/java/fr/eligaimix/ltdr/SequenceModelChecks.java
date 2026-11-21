package fr.eligaimix.ltdr;

import fr.eligaimix.ltdr.network.BridgeProtocol;
import fr.eligaimix.ltdr.network.EditorRequest;
import fr.eligaimix.ltdr.piston.MovementBatch;
import fr.eligaimix.ltdr.sequence.BlockPosition;
import fr.eligaimix.ltdr.sequence.PistonTarget;
import fr.eligaimix.ltdr.sequence.SequenceAction;
import fr.eligaimix.ltdr.sequence.SequenceCursor;
import fr.eligaimix.ltdr.sequence.SequenceDefinition;
import fr.eligaimix.ltdr.sequence.TimelineProgram;
import fr.eligaimix.ltdr.storage.SequenceStore;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Dependency-free behavioral checks, run by the sequenceTest Gradle task. */
public final class SequenceModelChecks {
    private static int checks;

    public static void main(String[] args) throws Exception {
        check(
                fr.eligaimix.ltdr.i18n.TextCatalog.text("en_us", "ui.language")
                        .equals("Language: English"),
                "English catalog available");
        check(
                fr.eligaimix.ltdr.i18n.TextCatalog.text("fr_fr", "ui.language")
                        .equals("Langue : Français"),
                "French catalog available");
        check(
                fr.eligaimix.ltdr.i18n.TextCatalog.message(
                                "en_us", "Erreur : Séquence inconnue : Porte")
                        .equals("Error: Unknown sequence: Porte"),
                "Nested server messages localize without changing identifiers");
        check(
                fr.eligaimix.ltdr.i18n.TextCatalog.message("en_us", "piston_id_123")
                        .equals("piston_id_123"),
                "User identifiers remain intact");
        PistonTarget first = new PistonTarget("left", new BlockPosition(0, 64, 0), "east", true);
        PistonTarget second = new PistonTarget("right", new BlockPosition(0, 64, 5), "east", false);
        var mutablePistons = new ArrayList<>(List.of(first, second));
        var mutableActions =
                new ArrayList<>(
                        List.of(
                                new SequenceAction(2, 0, "left", false),
                                new SequenceAction(0, 7, "right", true),
                                new SequenceAction(0, 2, "left", true)));
        var definition =
                new SequenceDefinition(
                        "demo", "minecraft:overworld", mutablePistons, mutableActions);
        mutablePistons.clear();
        mutableActions.clear();
        check(
                definition.pistons().size() == 2 && definition.actions().size() == 3,
                "Snapshot detached from input lists");
        expect(UnsupportedOperationException.class, () -> definition.pistons().clear());
        expect(UnsupportedOperationException.class, () -> definition.actions().clear());
        check(
                definition.actions().getFirst().order() == 2,
                "Same-tick order independent of insertion order");
        check(definition.actions().getLast().tick() == 2, "Tick precedes order in playback sort");
        var cursor = new SequenceCursor(definition);
        check(cursor.nextTick() == 0 && !cursor.isFinished(), "Playback begins at zero");
        check(
                cursor.advance().equals(definition.actions().subList(0, 2)),
                "All actions for a tick emitted in order");
        check(
                cursor.nextTick() == 1 && cursor.advance().isEmpty() && !cursor.isFinished(),
                "Empty ticks remain real ticks");
        check(
                cursor.advance().equals(List.of(definition.actions().getLast()))
                        && cursor.isFinished(),
                "Last action completes cursor");
        check(
                cursor.nextTick() == 3 && cursor.advance().isEmpty(),
                "Completed cursor never replays actions");
        check(
                new SequenceCursor(
                                new SequenceDefinition(
                                        "empty", "minecraft:overworld", List.of(), List.of()))
                        .isFinished(),
                "Empty sequence completes immediately");
        expect(IllegalArgumentException.class, () -> new SequenceAction(-1, 0, "left", true));
        expect(IllegalArgumentException.class, () -> new SequenceAction(0, -1, "left", true));
        expect(
                IllegalArgumentException.class,
                () -> new PistonTarget("bad id", first.position(), "east", false));
        expect(
                IllegalArgumentException.class,
                () -> new PistonTarget("p", first.position(), "sideways", false));
        expect(
                IllegalArgumentException.class,
                () -> new SequenceDefinition("demo", "OVERWORLD", List.of(), List.of()));
        expect(IllegalArgumentException.class, () -> definition.withPiston(first));
        expect(
                IllegalArgumentException.class,
                () ->
                        definition.withPiston(
                                new PistonTarget("another", first.position(), "west", false)));
        expect(
                IllegalArgumentException.class,
                () -> definition.withAction(new SequenceAction(5, 0, "missing", true)));
        expect(
                IllegalArgumentException.class,
                () -> definition.withAction(new SequenceAction(0, 2, "right", false)));
        expect(IllegalArgumentException.class, () -> definition.withoutPiston("left"));
        expect(IllegalArgumentException.class, () -> definition.withoutAction(1, 0));
        check(
                definition
                        .withoutAction(0, 7)
                        .withoutPiston("right")
                        .pistons()
                        .equals(List.of(first)),
                "Removing unused targets preserves remaining program");
        check(definition.actions().size() == 3, "Edits return a new document");
        bridgeChecks();
        movementChecks();
        timelineChecks(first, second);

        var fixtures = Path.of("build", "test-fixtures");
        Files.createDirectories(fixtures);
        var directory = Files.createTempDirectory(fixtures, "layout-debugger-check-");
        var file = directory.resolve("sequences.json");
        var store = new SequenceStore(file);
        try {
            check(store.load().isEmpty(), "Missing file loads empty");
            store.save(Map.of(definition.name(), definition));
            check(store.load().equals(Map.of("demo", definition)), "Versioned document round trip");
            String valid = Files.readString(file);
            var dual =
                    definition
                            .withClosingActions(
                                    List.of(
                                            new SequenceAction(2, 4, "left", false),
                                            new SequenceAction(0, 8, "right", true),
                                            new SequenceAction(0, 1, "left", false)))
                            .withWarnings(false, List.of(new BlockPosition(4, 5, 6)));
            check(
                    dual.closingActions().getFirst().order() == 1,
                    "Closing actions have independent deterministic sorting");
            check(
                    dual.timeline(true).actions().equals(dual.closingActions())
                            && dual.timeline(false).actions().equals(definition.actions()),
                    "Timeline views share targets but select independent actions");
            check(
                    TimelineProgram.edit(dual, "left", 4, "off")
                            .closingActions()
                            .equals(dual.closingActions()),
                    "Opening edits preserve the closing program");
            expect(
                    IllegalArgumentException.class,
                    () ->
                            dual.withClosingActions(
                                    List.of(new SequenceAction(0, 0, "missing", true))));
            var closingOnly =
                    definition
                            .withActions(List.of())
                            .withClosingActions(List.of(new SequenceAction(0, 0, "left", false)));
            expect(IllegalArgumentException.class, () -> closingOnly.withoutPiston("left"));
            store.save(Map.of("demo", dual));
            check(
                    store.load().equals(Map.of("demo", dual)),
                    "Both programs, warning settings and ignored coordinates survive persistence");
            var renamed = dual.withName("renamed");
            check(
                    renamed.name().equals("renamed") && dual.name().equals("demo"),
                    "Rename returns a new immutable document");
            check(
                    renamed.pistons().equals(dual.pistons())
                            && renamed.actions().equals(dual.actions())
                            && renamed.closingActions().equals(dual.closingActions()),
                    "Rename preserves shared targets and both timelines");
            check(
                    renamed.ignoredWarnings().equals(dual.ignoredWarnings())
                            && renamed.warningsEnabled() == dual.warningsEnabled(),
                    "Rename preserves warning preferences");
            expect(IllegalArgumentException.class, () -> dual.withName("invalid name"));
            store.save(Map.of(renamed.name(), renamed));
            check(
                    store.load().equals(Map.of("renamed", renamed)),
                    "Renamed document round-trips under its new key");
            for (int oldVersion : List.of(1, 2)) {
                var old = com.google.gson.JsonParser.parseString(valid).getAsJsonObject();
                old.addProperty("schemaVersion", oldVersion);
                var entry = old.getAsJsonArray("sequences").get(0).getAsJsonObject();
                entry.remove("closingActions");
                entry.remove("warningsEnabled");
                entry.remove("ignoredWarnings");
                Files.writeString(file, old.toString());
                check(
                        store.load().equals(Map.of("demo", definition)),
                        "Older schema migrates to opening, empty closing and enabled warnings");
            }
            Files.writeString(file, valid);
            expect(IOException.class, () -> store.save(Map.of("wrong-key", definition)));
            check(Files.readString(file).equals(valid), "Rejected save preserves prior file");
            invalidDocument(
                    store, file, valid.replace("\"schemaVersion\": 4", "\"schemaVersion\": 999"));
            invalidDocument(
                    store,
                    file,
                    valid.replace("\"pistonId\": \"left\"", "\"pistonId\": \"missing\""));
            invalidDocument(store, file, valid.replace("\"tick\": 0", "\"tick\": -1"));
            invalidDocument(store, file, valid.replace("\"tick\": 0", "\"tick\": 0.5"));
            invalidDocument(store, file, valid.replace("\"tick\": 0", "\"tick\": 2147483648"));
            invalidDocument(store, file, valid.replace("\"order\": 7", "\"order\": 2"));
            var duplicateNames = com.google.gson.JsonParser.parseString(valid).getAsJsonObject();
            var sequences = duplicateNames.getAsJsonArray("sequences");
            sequences.add(sequences.get(0).deepCopy());
            invalidDocument(store, file, duplicateNames.toString());
            invalidDocument(
                    store, file, valid.replace("\"powered\": true", "\"powered\": \"true\""));
            invalidDocument(
                    store, file, valid.replace("\"sticky\": true", "\"invalidSticky\": true"));
            invalidDocument(store, file, valid + " {}");
            invalidDocument(store, file, "{ broken JSON");
            var legacy = com.google.gson.JsonParser.parseString(valid).getAsJsonObject();
            legacy.addProperty("schemaVersion", 1);
            legacy.getAsJsonArray("sequences")
                    .forEach(sequence ->
                            sequence.getAsJsonObject()
                                    .getAsJsonArray("actions")
                                    .forEach(action ->
                                            action.getAsJsonObject().remove("pulseEdge")));
            Files.writeString(file, legacy.toString());
            check(
                    store.load().equals(Map.of("demo", definition)),
                    "Version one documents migrate without changing game-tick timing");
            check(
                    store.load().get("demo").actions().stream()
                            .noneMatch(SequenceAction::pulseEdge),
                    "Legacy boolean actions do not become authored pulses");
            var pulse =
                    TimelineProgram.edit(
                            new SequenceDefinition(
                                    "pulse", "minecraft:overworld", List.of(first), List.of()),
                            "left",
                            1,
                            "pulse");
            store.save(Map.of("pulse", pulse));
            check(
                    store.load().equals(Map.of("pulse", pulse)),
                    "Authored pulse edges survive version two persistence");
            store.save(Map.of("demo", definition));
            check(
                    store.load().get("demo").equals(definition),
                    "Valid replacement remains readable");
        } finally {
            Files.deleteIfExists(file);
            Files.deleteIfExists(
                    file.resolveSibling(file.getFileName() + ".before-ownership-v4.bak"));
            Files.deleteIfExists(directory);
        }
        ownershipExchangeChecks();
        System.out.println("Layout Debugger model/storage checks: " + checks + " passed.");
    }

    private static void ownershipExchangeChecks() throws Exception {
        var a = java.util.UUID.randomUUID();
        var b = java.util.UUID.randomUUID();
        var piston = new PistonTarget("p", new BlockPosition(10, 64, -5), "east", true);
        var first =
                new SequenceDefinition(
                                "door",
                                "minecraft:overworld",
                                List.of(piston),
                                List.of(new SequenceAction(0, 0, "p", true)))
                        .withClosingActions(List.of(new SequenceAction(2, 0, "p", false)))
                        .withWarnings(false, List.of(new BlockPosition(11, 64, -5)))
                        .withOwner(a);
        var second = first.withOwner(b);
        check(
                !first.storageKey().equals(second.storageKey()),
                "Equal display names have separate owner keys");
        check(
                first.withName("renamed").owner().equals(a)
                        && first.timeline(true).owner().equals(a)
                        && first.withActions(List.of()).owner().equals(a),
                "Builders preserve creator");
        var decoded =
                SequenceStore.decode(
                        SequenceStore.encode(
                                Map.of(first.storageKey(), first, second.storageKey(), second)));
        check(
                decoded.size() == 2 && decoded.get(first.storageKey()).equals(first),
                "Two owners with equal names round-trip");
        byte[] bytes = fr.eligaimix.ltdr.storage.AnimationExchange.encode(first);
        var imported =
                fr.eligaimix.ltdr.storage.AnimationExchange.decode(
                        bytes, b, "copy", "minecraft:the_nether", new BlockPosition(100, 80, 20));
        check(
                imported.owner().equals(b)
                        && imported.name().equals("copy")
                        && imported.dimension().equals("minecraft:the_nether"),
                "Import derives creator and dimension from importer");
        check(
                imported.pistons().getFirst().position().equals(new BlockPosition(100, 80, 20))
                        && imported.ignoredWarnings()
                                .equals(List.of(new BlockPosition(101, 80, 20))),
                "Pistons and ignored warnings use relative anchor");
        check(
                imported.actions().equals(first.actions())
                        && imported.closingActions().equals(first.closingActions()),
                "Export preserves both timelines");
        String text = new String(bytes, java.nio.charset.StandardCharsets.UTF_8);
        check(
                !text.contains(a.toString()) && text.contains("\"includesBlocks\": false"),
                "Portable export omits creator and blocks");
        expect(
                IOException.class,
                () ->
                        fr.eligaimix.ltdr.storage.AnimationExchange.decode(
                                (text + " {}").getBytes(java.nio.charset.StandardCharsets.UTF_8),
                                b,
                                "bad",
                                "minecraft:overworld",
                                new BlockPosition(0, 0, 0)));
        expect(
                IOException.class,
                () ->
                        fr.eligaimix.ltdr.storage.AnimationExchange.decode(
                                new byte[] {(byte) 0xFF},
                                b,
                                "bad",
                                "minecraft:overworld",
                                new BlockPosition(0, 0, 0)));
        check(
                !fr.eligaimix.ltdr.storage.AnimationExchange.validFilename("../door.ldanim.json"),
                "Export refuses path traversal");
        var receiver = new fr.eligaimix.ltdr.storage.ExportReceiver();
        var folder = Files.createTempDirectory("ld-export-checks");
        try {
            var unsolicited =
                    fr.eligaimix.ltdr.network.ExportChunk.split(
                                    java.util.UUID.randomUUID().toString(),
                                    "door.ldanim.json",
                                    bytes)
                            .getFirst();
            check(receiver.accept(unsolicited, folder).isEmpty(), "Unsolicited download ignored");
            String ticket = receiver.begin();
            var chunk =
                    fr.eligaimix.ltdr.network.ExportChunk.split(ticket, "door.ldanim.json", bytes)
                            .getFirst();
            var wire = BridgeProtocol.readExportChunk(BridgeProtocol.exportChunk(chunk));
            check(
                    java.util.Arrays.equals(wire.data(), bytes) && wire.ticket().equals(ticket),
                    "Bounded export wire round-trip");
            var saved = receiver.accept(wire, folder).orElseThrow();
            check(
                    java.util.Arrays.equals(Files.readAllBytes(saved), bytes),
                    "Requested download creates exact JSON file");
            check(receiver.accept(wire, folder).isEmpty(), "Completed ticket cannot be reused");
            ticket = receiver.begin();
            var duplicate =
                    fr.eligaimix.ltdr.network.ExportChunk.split(ticket, "door.ldanim.json", bytes)
                            .getFirst();
            expect(IOException.class, () -> receiver.accept(duplicate, folder));
            check(
                    java.util.Arrays.equals(Files.readAllBytes(saved), bytes),
                    "Download never overwrites an existing file");
            Files.delete(saved);
            Files.delete(saved.getParent());
            Files.delete(saved.getParent().getParent());
        } finally {
            Files.delete(folder);
        }
        var storage = Files.createTempDirectory("ld-owner-backup");
        var file = storage.resolve("sequences.json");
        var legacy = new SequenceDefinition("legacy", "minecraft:overworld", List.of(), List.of());
        var old = SequenceStore.encode(Map.of("legacy", legacy));
        old.addProperty("schemaVersion", 3);
        Files.writeString(file, old.toString());
        var store = new SequenceStore(file);
        check(store.load().get("legacy").owner() == null, "Legacy documents remain unassigned");
        String prior = Files.readString(file);
        store.save(Map.of(first.storageKey(), first));
        var backup = file.resolveSibling("sequences.json.before-ownership-v4.bak");
        check(
                Files.readString(backup).equals(prior),
                "Ownership migration preserves original backup");
        store.save(Map.of(second.storageKey(), second));
        check(Files.readString(backup).equals(prior), "Subsequent saves preserve migration backup");

        var imports = storage.resolve("imports").resolve(b.toString());
        Files.createDirectories(imports);
        Files.write(imports.resolve("door.ldanim.json"), bytes);
        var fromDisk =
                fr.eligaimix.ltdr.storage.AnimationExchange.read(
                        storage,
                        b,
                        "door.ldanim.json",
                        "disk",
                        "minecraft:overworld",
                        new BlockPosition(5, 60, 5));
        check(
                fromDisk.owner().equals(b)
                        && fromDisk.pistons()
                                .getFirst()
                                .position()
                                .equals(new BlockPosition(5, 60, 5)),
                "Private imports folder reads relative portable document");
        expect(
                IOException.class,
                () ->
                        fr.eligaimix.ltdr.storage.AnimationExchange.read(
                                storage,
                                b,
                                "../sequences.json",
                                "bad",
                                "minecraft:overworld",
                                new BlockPosition(0, 0, 0)));
        expect(
                IOException.class,
                () ->
                        fr.eligaimix.ltdr.storage.AnimationExchange.read(
                                storage,
                                a,
                                "door.ldanim.json",
                                "bad",
                                "minecraft:overworld",
                                new BlockPosition(0, 0, 0)));
        expect(
                IOException.class,
                () ->
                        fr.eligaimix.ltdr.storage.AnimationExchange.decode(
                                new byte[fr.eligaimix.ltdr.storage.AnimationExchange.MAX_BYTES + 1],
                                b,
                                "bad",
                                "minecraft:overworld",
                                new BlockPosition(0, 0, 0)));
        byte[] large = new byte[fr.eligaimix.ltdr.network.ExportChunk.MAX_CHUNK + 1];
        String longTicket = java.util.UUID.randomUUID().toString();
        var fragments =
                fr.eligaimix.ltdr.network.ExportChunk.split(longTicket, "large.ldanim.json", large);
        check(
                fragments.size() == 2
                        && fragments.get(1).offset()
                                == fr.eligaimix.ltdr.network.ExportChunk.MAX_CHUNK,
                "Large export split into bounded contiguous fragments");
        var cloned = fragments.getFirst().data();
        cloned[0] = 99;
        check(
                fragments.getFirst().data()[0] == 0,
                "Export chunks do not expose mutable wire buffers");
        receiver.begin();
        check(
                receiver.accept(fragments.getFirst(), storage).isEmpty(),
                "Wrong ticket cannot allocate a download");
        var malformed =
                fr.eligaimix.ltdr.network.ExportChunk.split(
                                receiver.begin(), "large.ldanim.json", large)
                        .get(1);
        expect(IOException.class, () -> receiver.accept(malformed, storage));
        Files.delete(imports.resolve("door.ldanim.json"));
        Files.delete(imports);
        Files.delete(storage.resolve("imports").resolve(a.toString()));
        Files.delete(storage.resolve("imports"));
        Files.delete(file);
        Files.delete(backup);
        Files.delete(storage);
    }

    private static void bridgeChecks() throws Exception {
        var hello = new BridgeProtocol.Hello(1, BridgeProtocol.LIBRARY, "paper");
        check(
                BridgeProtocol.readHello(BridgeProtocol.hello(hello)).equals(hello),
                "Paper/Fabric handshake round-trip");
        var request = new EditorRequest("rename", "old:new", 123, 456);
        byte[] encoded = BridgeProtocol.request(request);
        check(
                BridgeProtocol.readRequest(encoded).equals(request),
                "Paper/Fabric intent round-trip preserves varints");
        check(
                BridgeProtocol.readRequest(
                                BridgeProtocol.request(new EditorRequest("open", "", 0, 0)))
                        .value()
                        .isEmpty(),
                "Empty values remain valid in bridge intents");
        String text = "{\"message\":\"\u00e9\uD83D\uDE80\"}";
        check(
                BridgeProtocol.readState(BridgeProtocol.state(text)).equals(text),
                "Bridge preserves UTF-8 including surrogate pairs");
        expect(IllegalArgumentException.class, () -> BridgeProtocol.readRequest(new byte[1025]));
        expect(
                IllegalArgumentException.class,
                () ->
                        BridgeProtocol.readRequest(
                                java.util.Arrays.copyOf(encoded, encoded.length - 1)));
        expect(
                IllegalArgumentException.class,
                () ->
                        BridgeProtocol.readRequest(
                                java.util.Arrays.copyOf(encoded, encoded.length + 1)));
        expect(
                IllegalArgumentException.class,
                () -> BridgeProtocol.readRequest(new byte[] {1, (byte) 255}));
        expect(
                IllegalArgumentException.class,
                () ->
                        BridgeProtocol.readHello(
                                new byte[] {
                                    (byte) 255, (byte) 255, (byte) 255, (byte) 255, (byte) 127
                                }));
        expect(
                IllegalArgumentException.class,
                () -> BridgeProtocol.state("a".repeat(BridgeProtocol.MAX_STATE_CHARACTERS + 1)));
        expect(IllegalArgumentException.class, () -> new BridgeProtocol.Hello(0, 1, "paper"));
        var random = new java.util.Random(262135);
        for (int i = 0; i < 10000; i++) {
            byte[] bytes = new byte[random.nextInt(128)];
            random.nextBytes(bytes);
            try {
                BridgeProtocol.readRequest(bytes);
            } catch (IllegalArgumentException expected) {
            }
            try {
                BridgeProtocol.readHello(bytes);
            } catch (IllegalArgumentException expected) {
            }
        }
        check(true, "Malformed bridge traffic is bounded and fails without unexpected exceptions");
    }

    private static void movementChecks() throws Exception {
        var original = new java.util.LinkedHashMap<String, Integer>();
        original.put("first", 0);
        original.put("second", 1);
        original.put("stationary", 9);
        var pushed = MovementBatch.translate(original, Set.of(0, 1), position -> position + 1);
        check(
                pushed.equals(Map.of("first", 1, "second", 2, "stationary", 9)),
                "A chain moves from one shared pre-movement position map");
        check(
                original.equals(Map.of("first", 0, "second", 1, "stationary", 9)),
                "Movement leaves the original identity map untouched");
        check(
                MovementBatch.translate(pushed, Set.of(1, 2), position -> position - 1)
                        .equals(original),
                "Pulling the same chain preserves each physical identity");
        check(
                MovementBatch.translate(original, Set.of(42), position -> position + 1)
                        .equals(original),
                "Unselected blocks moving do not alter selected identities");
        check(
                new ArrayList<>(pushed.keySet()).equals(new ArrayList<>(original.keySet())),
                "Movement preserves stable row ordering");
        expect(UnsupportedOperationException.class, () -> pushed.put("other", 42));
        expect(
                IllegalStateException.class,
                () -> MovementBatch.translate(original, Set.of(0), position -> position + 1));
    }

    private static void timelineChecks(PistonTarget first, PistonTarget second) throws Exception {
        var empty =
                new SequenceDefinition(
                        "editor", "minecraft:overworld", List.of(first, second), List.of());
        check(
                TimelineProgram.tickForPage(1) == 0 && TimelineProgram.tickForPage(2) == 2,
                "Pages begin with immediate signals separated by two game ticks");
        expect(IllegalArgumentException.class, () -> TimelineProgram.tickForPage(0));
        expect(
                IllegalArgumentException.class,
                () -> TimelineProgram.tickForPage(TimelineProgram.MAX_PAGE + 1));
        expect(
                IllegalArgumentException.class,
                () -> TimelineProgram.edit(empty, "left", TimelineProgram.MAX_PAGE, "pulse"));
        var pulse = TimelineProgram.edit(empty, "left", 1, "pulse");
        check(
                pulse.actions().size() == 2
                        && pulse.actions().getFirst().tick() == 0
                        && pulse.actions().getFirst().powered()
                        && pulse.actions().getLast().tick() == 2
                        && !pulse.actions().getLast().powered(),
                "Immediate pulse has exact two-game-tick duration");
        check(
                pulse.actions().stream().allMatch(SequenceAction::pulseEdge),
                "Both pulse edges retain their authored relationship");
        check(
                !TimelineProgram.modPowerBefore(pulse, "left", 0)
                        && TimelineProgram.modPowerBefore(pulse, "left", 2)
                        && !TimelineProgram.modPowerBefore(pulse, "left", 3),
                "Input before a boundary excludes the action at that boundary");
        expect(IllegalArgumentException.class, () -> TimelineProgram.edit(pulse, "left", 2, "on"));
        check(
                TimelineProgram.remove(pulse, 0, 0).actions().isEmpty(),
                "Deleting a pulse beginning removes its automatic end");
        check(
                TimelineProgram.remove(pulse, 2, 0).actions().isEmpty(),
                "Deleting a pulse end removes its matching beginning");
        check(
                TimelineProgram.edit(pulse, "left", 1, "clear").actions().isEmpty(),
                "Clearing an authored pulse leaves no orphan edge");
        var held = TimelineProgram.edit(empty, "left", 1, "on");
        check(
                TimelineProgram.toolMode(held, "left", 1, "on").equals("clear"),
                "Repeating ON with an item clears its page event");
        check(
                TimelineProgram.toolMode(held, "left", 1, "off").equals("off"),
                "Opposite item replaces the page event");
        var off = TimelineProgram.edit(empty, "left", 1, "off");
        check(
                TimelineProgram.toolMode(off, "left", 1, "off").equals("clear"),
                "Repeating OFF with an item clears its page event");
        check(
                TimelineProgram.toolMode(held, "left", 2, "on").equals("on"),
                "A previous-page held signal does not erase a new page event");
        var replaced = TimelineProgram.edit(pulse, "left", 1, "on");
        check(
                replaced.actions().size() == 1
                        && replaced.actions().getFirst().powered()
                        && !replaced.actions().getFirst().pulseEdge(),
                "Replacing a pulse with held on removes only its automatic edge");
        var independentOff = empty.withAction(new SequenceAction(2, 0, "left", false));
        var heldThenOff = TimelineProgram.edit(independentOff, "left", 1, "on");
        check(
                TimelineProgram.edit(heldThenOff, "left", 1, "clear")
                        .actions()
                        .equals(independentOff.actions()),
                "Removing held on preserves an unrelated explicit off at the next page");
        expect(
                IllegalArgumentException.class,
                () -> TimelineProgram.edit(independentOff, "left", 1, "pulse"));
        expect(
                IllegalArgumentException.class,
                () -> TimelineProgram.edit(replaced, "left", 3, "pulse"));
        var independent = TimelineProgram.edit(pulse, "right", 1, "on");
        check(
                independent.actions().size() == 3
                        && independent.actions().get(1).pistonId().equals("right"),
                "A different piston may act during a pulse with explicit order");
        var swapped = TimelineProgram.swap(independent, 0, 0, 1);
        check(
                swapped.actions().getFirst().pistonId().equals("right")
                        && swapped.actions().get(1).pulseEdge(),
                "Swapping simultaneous actions changes order while preserving pulse metadata");
        expect(
                IllegalArgumentException.class,
                () -> empty.withAction(new SequenceAction(0, 0, "left", true, true)));
        expect(
                IllegalArgumentException.class,
                () -> pulse.withAction(new SequenceAction(1, 0, "left", false)));
        expect(
                IllegalArgumentException.class,
                () -> pulse.withAction(new SequenceAction(2, 1, "left", false, true)));
        check(
                !new SequenceAction(0, 0, "left", true).pulseEdge(),
                "The old action constructor defaults to a held signal");
    }

    private static void invalidDocument(SequenceStore store, java.nio.file.Path file, String json)
            throws Exception {
        Files.writeString(file, json);
        expect(IOException.class, store::load);
        check(Files.readString(file).equals(json), "Invalid input preserved for recovery");
    }

    private static void check(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
        checks++;
    }

    private static void expect(Class<? extends Throwable> type, CheckedAction action)
            throws Exception {
        try {
            action.run();
        } catch (Throwable failure) {
            if (type.isInstance(failure)) {
                checks++;
                return;
            }
            throw new AssertionError("Expected " + type.getName() + ", got " + failure, failure);
        }
        throw new AssertionError("Expected " + type.getName());
    }

    @FunctionalInterface
    private interface CheckedAction {
        void run() throws Exception;
    }
}
