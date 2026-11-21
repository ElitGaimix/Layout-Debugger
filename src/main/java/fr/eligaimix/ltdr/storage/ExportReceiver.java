package fr.eligaimix.ltdr.storage;

import fr.eligaimix.ltdr.network.ExportChunk;

import java.io.*;
import java.nio.file.*;
import java.util.*;

/** Rejects unsolicited, expired and out-of-order downloads. Never overwrites a local file. */
public final class ExportReceiver {
    private String ticket, filename;
    private long deadline;
    private int total;
    private ByteArrayOutputStream output;

    public String begin() {
        clear();
        ticket = UUID.randomUUID().toString();
        deadline = System.nanoTime() + 120_000_000_000L;
        return ticket;
    }

    public void clear() {
        ticket = null;
        filename = null;
        output = null;
        total = 0;
    }

    public Optional<Path> accept(ExportChunk chunk, Path gameDirectory) throws IOException {
        if (ticket == null || !ticket.equals(chunk.ticket())) return Optional.empty();
        if (System.nanoTime() > deadline) {
            clear();
            return Optional.empty();
        }
        try {
            if (output == null) {
                if (chunk.offset() != 0) throw new IOException("Unexpected offset");
                filename = chunk.filename();
                total = chunk.totalBytes();
                output = new ByteArrayOutputStream();
            }
            if (!filename.equals(chunk.filename())
                    || total != chunk.totalBytes()
                    || output.size() != chunk.offset())
                throw new IOException("Inconsistent export");
            output.writeBytes(chunk.data());
            if (output.size() != total) return Optional.empty();
            byte[] bytes = output.toByteArray();
            // A server cannot make the client store a different kind of document.
            AnimationExchange.decode(
                    bytes,
                    UUID.randomUUID(),
                    "validation",
                    "minecraft:overworld",
                    new fr.eligaimix.ltdr.sequence.BlockPosition(0, 0, 0));
            Path game = gameDirectory.toRealPath(),
                    root = game.resolve("layout-debugger"),
                    folder = root.resolve("exports");
            if (Files.isSymbolicLink(root) || Files.isSymbolicLink(folder))
                throw new IOException("Symbolic export directory refused");
            Files.createDirectories(folder);
            if (!folder.toRealPath().startsWith(game)) throw new IOException("Invalid directory");
            Path target = folder.resolve(filename);
            Files.write(target, bytes, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE);
            clear();
            return Optional.of(target);
        } catch (IOException | RuntimeException e) {
            clear();
            throw e;
        }
    }
}
