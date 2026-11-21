package fr.eligaimix.ltdr.storage;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.Strictness;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;

import fr.eligaimix.ltdr.sequence.BlockPosition;
import fr.eligaimix.ltdr.sequence.PistonTarget;
import fr.eligaimix.ltdr.sequence.SequenceAction;
import fr.eligaimix.ltdr.sequence.SequenceDefinition;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/** Versioned document storage. Runtime clocks and power sources are deliberately not persisted. */
public final class SequenceStore {
    private static final int SCHEMA_VERSION = 4;
    private static final Gson JSON =
            new GsonBuilder().setPrettyPrinting().setStrictness(Strictness.STRICT).create();
    private final Path file;

    public SequenceStore(Path file) {
        this.file = file.toAbsolutePath();
    }

    public Map<String, SequenceDefinition> load() throws IOException {
        if (!Files.exists(file)) {
            return Map.of();
        }
        try (var reader = new JsonReader(Files.newBufferedReader(file, StandardCharsets.UTF_8))) {
            reader.setStrictness(Strictness.STRICT);
            JsonObject document = object(JsonParser.parseReader(reader));
            if (reader.peek() != JsonToken.END_DOCUMENT) {
                throw new IllegalArgumentException("Données après la fin du document JSON.");
            }
            return decode(document);
        } catch (RuntimeException exception) {
            // Never replace malformed input by an empty document: the caller must disable edits.
            throw new IOException(
                    "Impossible de lire " + file + " : " + exception.getMessage(), exception);
        }
    }

    public static Map<String, SequenceDefinition> decode(JsonObject document) {
        int version = integer(document, "schemaVersion");
        if (version < 1 || version > SCHEMA_VERSION) {
            throw new IllegalArgumentException(
                    "Version de données non prise en charge : " + version);
        }
        var result = new LinkedHashMap<String, SequenceDefinition>();
        for (JsonElement element : array(document, "sequences")) {
            JsonObject sequence = object(element);
            var pistons = new ArrayList<PistonTarget>();
            for (JsonElement pistonElement : array(sequence, "pistons")) {
                JsonObject piston = object(pistonElement);
                JsonObject pos = object(required(piston, "position"));
                pistons.add(
                        new PistonTarget(
                                string(piston, "id"),
                                new BlockPosition(
                                        integer(pos, "x"), integer(pos, "y"), integer(pos, "z")),
                                string(piston, "facing"),
                                bool(piston, "sticky")));
            }
            var actions = new ArrayList<SequenceAction>();
            for (JsonElement actionElement : array(sequence, "actions")) {
                JsonObject action = object(actionElement);
                actions.add(
                        new SequenceAction(
                                integer(action, "tick"),
                                integer(action, "order"),
                                string(action, "pistonId"),
                                bool(action, "powered"),
                                version >= 2 && bool(action, "pulseEdge")));
            }
            var closing = new ArrayList<SequenceAction>();
            var ignored = new ArrayList<BlockPosition>();
            if (version >= 3) {
                for (JsonElement e : array(sequence, "closingActions")) {
                    JsonObject a = object(e);
                    closing.add(
                            new SequenceAction(
                                    integer(a, "tick"),
                                    integer(a, "order"),
                                    string(a, "pistonId"),
                                    bool(a, "powered")));
                }
                for (JsonElement e : array(sequence, "ignoredWarnings")) {
                    JsonObject v = object(e);
                    ignored.add(
                            new BlockPosition(integer(v, "x"), integer(v, "y"), integer(v, "z")));
                }
            }
            var definition =
                    new SequenceDefinition(
                            string(sequence, "name"),
                            string(sequence, "dimension"),
                            pistons,
                            actions,
                            closing,
                            version < 3 || bool(sequence, "warningsEnabled"),
                            ignored,
                            version >= 4
                                            && sequence.has("owner")
                                            && !sequence.get("owner").isJsonNull()
                                    ? java.util.UUID.fromString(string(sequence, "owner"))
                                    : null);
            if (result.putIfAbsent(definition.storageKey(), definition) != null) {
                throw new IllegalArgumentException(
                        "Nom de séquence dupliqué : " + definition.name());
            }
        }
        return Collections.unmodifiableMap(result);
    }

    public void save(Map<String, SequenceDefinition> sequences) throws IOException {
        JsonObject document = encode(sequences);
        Files.createDirectories(file.getParent());
        if (Files.isRegularFile(file)) {
            JsonObject previous =
                    JsonParser.parseString(Files.readString(file, StandardCharsets.UTF_8))
                            .getAsJsonObject();
            if (previous.get("schemaVersion").getAsInt() < 4) {
                Path backup = file.resolveSibling(file.getFileName() + ".before-ownership-v4.bak");
                if (!Files.exists(backup)) Files.copy(file, backup);
            }
        }
        Path temporary = Files.createTempFile(file.getParent(), "layout-debugger-", ".tmp");
        try {
            Files.writeString(
                    temporary,
                    JSON.toJson(document) + System.lineSeparator(),
                    StandardCharsets.UTF_8);
            try {
                Files.move(
                        temporary,
                        file,
                        StandardCopyOption.REPLACE_EXISTING,
                        StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException exception) {
                Files.move(temporary, file, StandardCopyOption.REPLACE_EXISTING);
            }
        } finally {
            Files.deleteIfExists(temporary);
        }
    }

    public static JsonObject encode(Map<String, SequenceDefinition> sequences) throws IOException {
        JsonObject document = new JsonObject();
        document.addProperty("schemaVersion", SCHEMA_VERSION);
        JsonArray entries = new JsonArray();
        for (var entry :
                sequences.entrySet().stream().sorted(Map.Entry.comparingByKey()).toList()) {
            SequenceDefinition sequence = entry.getValue();
            if (!entry.getKey().equals(sequence.storageKey())) {
                throw new IOException(
                        "La clé de séquence ne correspond pas à son nom : " + entry.getKey());
            }
            JsonObject value = new JsonObject();
            value.addProperty("name", sequence.name());
            if (sequence.owner() != null) value.addProperty("owner", sequence.owner().toString());
            else value.add("owner", com.google.gson.JsonNull.INSTANCE);
            value.addProperty("dimension", sequence.dimension());
            JsonArray pistons = new JsonArray();
            for (PistonTarget piston : sequence.pistons()) {
                JsonObject target = new JsonObject();
                target.addProperty("id", piston.id());
                JsonObject position = new JsonObject();
                position.addProperty("x", piston.position().x());
                position.addProperty("y", piston.position().y());
                position.addProperty("z", piston.position().z());
                target.add("position", position);
                target.addProperty("facing", piston.facing());
                target.addProperty("sticky", piston.sticky());
                pistons.add(target);
            }
            value.add("pistons", pistons);
            JsonArray actions = new JsonArray();
            for (SequenceAction action : sequence.actions()) {
                JsonObject change = new JsonObject();
                change.addProperty("tick", action.tick());
                change.addProperty("order", action.order());
                change.addProperty("pistonId", action.pistonId());
                change.addProperty("powered", action.powered());
                change.addProperty("pulseEdge", action.pulseEdge());
                actions.add(change);
            }
            value.add("actions", actions);
            JsonArray closing = new JsonArray();
            for (SequenceAction a : sequence.closingActions()) {
                JsonObject v = new JsonObject();
                v.addProperty("tick", a.tick());
                v.addProperty("order", a.order());
                v.addProperty("pistonId", a.pistonId());
                v.addProperty("powered", a.powered());
                closing.add(v);
            }
            value.add("closingActions", closing);
            value.addProperty("warningsEnabled", sequence.warningsEnabled());
            JsonArray ignored = new JsonArray();
            for (BlockPosition pos : sequence.ignoredWarnings()) {
                JsonObject v = new JsonObject();
                v.addProperty("x", pos.x());
                v.addProperty("y", pos.y());
                v.addProperty("z", pos.z());
                ignored.add(v);
            }
            value.add("ignoredWarnings", ignored);
            entries.add(value);
        }
        document.add("sequences", entries);

        return document;
    }

    private static JsonElement required(JsonObject object, String field) {
        JsonElement value = object.get(field);
        if (value == null || value.isJsonNull()) {
            throw new IllegalArgumentException("Champ requis absent : " + field);
        }
        return value;
    }

    private static JsonObject object(JsonElement element) {
        if (!element.isJsonObject()) {
            throw new IllegalArgumentException("Un objet JSON est attendu.");
        }
        return element.getAsJsonObject();
    }

    private static JsonArray array(JsonObject object, String field) {
        JsonElement value = required(object, field);
        if (!value.isJsonArray()) {
            throw new IllegalArgumentException("Un tableau est attendu : " + field);
        }
        return value.getAsJsonArray();
    }

    private static String string(JsonObject object, String field) {
        JsonElement value = required(object, field);
        if (!value.isJsonPrimitive() || !value.getAsJsonPrimitive().isString()) {
            throw new IllegalArgumentException("Une chaîne est attendue : " + field);
        }
        return value.getAsString();
    }

    private static int integer(JsonObject object, String field) {
        JsonElement value = required(object, field);
        if (!value.isJsonPrimitive() || !value.getAsJsonPrimitive().isNumber()) {
            throw new IllegalArgumentException("Un entier est attendu : " + field);
        }
        return value.getAsBigDecimal().intValueExact();
    }

    private static boolean bool(JsonObject object, String field) {
        JsonElement value = required(object, field);
        if (!value.isJsonPrimitive() || !value.getAsJsonPrimitive().isBoolean()) {
            throw new IllegalArgumentException("Un booléen est attendu : " + field);
        }
        return value.getAsBoolean();
    }
}
