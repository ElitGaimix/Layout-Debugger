package fr.eligaimix.ltdr.network;

import fr.eligaimix.ltdr.storage.AnimationExchange;

import java.util.*;

/** Bounded, ordered transfer of one explicitly requested JSON export. */
public record ExportChunk(String ticket, String filename, int totalBytes, int offset, byte[] data) {
    public static final int MAX_CHUNK = 32768;

    public ExportChunk {
        if (!UUID.fromString(ticket).toString().equals(ticket)
                || !AnimationExchange.validFilename(filename)
                || totalBytes < 1
                || totalBytes > AnimationExchange.MAX_BYTES
                || offset < 0
                || offset >= totalBytes
                || data.length < 1
                || data.length > MAX_CHUNK
                || data.length > totalBytes - offset)
            throw new IllegalArgumentException("Fragment d'export invalide.");
        data = data.clone();
    }

    @Override
    public byte[] data() {
        return data.clone();
    }

    public static List<ExportChunk> split(String ticket, String filename, byte[] bytes) {
        var chunks = new ArrayList<ExportChunk>();
        for (int offset = 0; offset < bytes.length; offset += MAX_CHUNK)
            chunks.add(
                    new ExportChunk(
                            ticket,
                            filename,
                            bytes.length,
                            offset,
                            Arrays.copyOfRange(
                                    bytes, offset, Math.min(bytes.length, offset + MAX_CHUNK))));
        return List.copyOf(chunks);
    }
}
