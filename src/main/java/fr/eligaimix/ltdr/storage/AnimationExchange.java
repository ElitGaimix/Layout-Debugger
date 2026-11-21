package fr.eligaimix.ltdr.storage;

import com.google.gson.*;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;

import fr.eligaimix.ltdr.sequence.*;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;

/** Portable timelines only. Never serializes blocks, inventories or a trusted ownership grant. */
public final class AnimationExchange {
    public static final int MAX_BYTES = 4 * 1024 * 1024;
    private static final Gson JSON =
            new GsonBuilder().setPrettyPrinting().setStrictness(Strictness.STRICT).create();

    public record Exported(String filename, byte[] bytes, Path path) {}

    private static final Map<UUID, Long> LAST_EXPORT = new LinkedHashMap<>(128, 0.75f, true);

    private AnimationExchange() {}

    public static byte[] encode(SequenceDefinition source) throws IOException {
        BlockPosition origin =
                source.pistons().isEmpty()
                        ? new BlockPosition(0, 0, 0)
                        : source.pistons().getFirst().position();
        var portable =
                translate(
                        source,
                        new BlockPosition(
                                Math.negateExact(origin.x()),
                                Math.negateExact(origin.y()),
                                Math.negateExact(origin.z())),
                        source.name(),
                        source.dimension(),
                        null);
        var root = new JsonObject();
        root.addProperty("format", "layout-debugger-animation");
        root.addProperty("formatVersion", 1);
        root.addProperty("minecraftVersion", "26.2");
        root.addProperty("includesBlocks", false);
        root.addProperty("coordinates", "relative-to-first-piston");
        root.add("data", SequenceStore.encode(Map.of(portable.storageKey(), portable)));
        byte[] bytes = (JSON.toJson(root) + "\n").getBytes(StandardCharsets.UTF_8);
        if (bytes.length > MAX_BYTES)
            throw new IOException("Export trop volumineux (maximum 4 Mio).");
        return bytes;
    }

    public static SequenceDefinition decode(
            byte[] bytes, UUID importer, String name, String dimension, BlockPosition anchor)
            throws IOException {
        if (bytes.length > MAX_BYTES) throw new IOException("Fichier trop volumineux.");
        try (var reader =
                new JsonReader(
                        new InputStreamReader(
                                new ByteArrayInputStream(bytes),
                                StandardCharsets.UTF_8
                                        .newDecoder()
                                        .onMalformedInput(
                                                java.nio.charset.CodingErrorAction.REPORT)))) {
            reader.setStrictness(Strictness.STRICT);
            var root = JsonParser.parseReader(reader).getAsJsonObject();
            if (reader.peek() != JsonToken.END_DOCUMENT
                    || !root.get("format").getAsString().equals("layout-debugger-animation")
                    || !root.get("formatVersion").getAsBigDecimal().equals(java.math.BigDecimal.ONE)
                    || !root.get("minecraftVersion").getAsString().equals("26.2")
                    || root.get("includesBlocks").getAsBoolean()
                    || !root.get("coordinates").getAsString().equals("relative-to-first-piston"))
                throw new IllegalArgumentException("Format d'export incompatible.");
            var entries = SequenceStore.decode(root.getAsJsonObject("data"));
            if (entries.size() != 1)
                throw new IllegalArgumentException("Le fichier doit contenir une seule animation.");
            return translate(
                    entries.values().iterator().next(),
                    anchor,
                    name,
                    dimension,
                    Objects.requireNonNull(importer));
        } catch (RuntimeException exception) {
            throw new IOException("Import invalide : " + exception.getMessage(), exception);
        }
    }

    private static SequenceDefinition translate(
            SequenceDefinition source,
            BlockPosition delta,
            String name,
            String dimension,
            UUID owner) {
        return new SequenceDefinition(
                name,
                dimension,
                source.pistons().stream()
                        .map(p ->
                                new PistonTarget(
                                        p.id(), add(p.position(), delta), p.facing(), p.sticky()))
                        .toList(),
                source.actions(),
                source.closingActions(),
                source.warningsEnabled(),
                source.ignoredWarnings().stream().map(p -> add(p, delta)).toList(),
                owner);
    }

    private static BlockPosition add(BlockPosition p, BlockPosition d) {
        return new BlockPosition(
                Math.addExact(p.x(), d.x()),
                Math.addExact(p.y(), d.y()),
                Math.addExact(p.z(), d.z()));
    }

    public static Exported write(Path directory, UUID requester, SequenceDefinition source)
            throws IOException {
        synchronized (LAST_EXPORT) {
            long now = System.nanoTime();
            Long previous = LAST_EXPORT.get(requester);
            if (previous != null && now - previous < 10_000_000_000L)
                throw new IOException("Please wait 10 seconds between exports.");
            LAST_EXPORT.put(requester, now);
            if (LAST_EXPORT.size() > 1024)
                LAST_EXPORT.remove(LAST_EXPORT.keySet().iterator().next());
        }
        Path folder = privateFolder(directory, "exports", requester);
        String filename =
                source.name() + "-" + UUID.randomUUID().toString().substring(0, 8) + ".ldanim.json";
        byte[] bytes = encode(source);
        Path path = folder.resolve(filename);
        Files.write(path, bytes, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE);
        return new Exported(filename, bytes, path);
    }

    public static SequenceDefinition read(
            Path directory,
            UUID importer,
            String filename,
            String name,
            String dimension,
            BlockPosition anchor)
            throws IOException {
        if (!validFilename(filename)) throw new IOException("Nom de fichier invalide.");
        Path folder = privateFolder(directory, "imports", importer);
        Path file = folder.resolve(filename);
        if (Files.isSymbolicLink(file) || !Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS))
            throw new IOException("Fichier absent du dossier imports/" + importer + ".");
        try (var input = Files.newInputStream(file)) {
            return decode(input.readNBytes(MAX_BYTES + 1), importer, name, dimension, anchor);
        }
    }

    public static boolean validFilename(String name) {
        return name != null && name.matches("[a-zA-Z0-9_-]{1,80}\\.ldanim\\.json");
    }

    private static Path privateFolder(Path directory, String kind, UUID owner) throws IOException {
        Path root = directory.toAbsolutePath().resolve(kind);
        Path folder = root.resolve(owner.toString());
        if (Files.isSymbolicLink(root) || Files.isSymbolicLink(folder))
            throw new IOException("Dossier symbolique refusé.");
        Files.createDirectories(folder);
        if (!folder.toRealPath().startsWith(directory.toRealPath()))
            throw new IOException("Dossier hors des données du mod.");
        return folder;
    }
}
