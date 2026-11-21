package fr.eligaimix.ltdr.network;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;

/** Shared, bounded wire encoding for Fabric payloads and Paper plugin messages. */
public final class BridgeProtocol {
    public static final int VERSION = 3;
    public static final int LIBRARY = 1, EDIT = 2, PLAYBACK = 4, TOOLS = 8;
    public static final int LIVE = 16;
    public static final int ALL = LIBRARY | EDIT | PLAYBACK | TOOLS | LIVE;
    public static final int MAX_STATE_CHARACTERS = 262_144;
    public static final int MAX_REQUEST_BYTES = 1024;

    private BridgeProtocol() {}

    public record Hello(int version, int capabilities, String backend) {
        public Hello {
            if (version < 1
                    || version > 65535
                    || capabilities < 0
                    || capabilities > 65535
                    || backend == null
                    || !backend.matches("[a-z][a-z0-9_-]{0,31}"))
                throw new IllegalArgumentException("Handshake invalide.");
        }
    }

    public static byte[] hello(Hello hello) {
        var out = new ByteArrayOutputStream();
        writeVarInt(out, hello.version());
        writeVarInt(out, hello.capabilities());
        writeUtf(out, hello.backend(), 32);
        return out.toByteArray();
    }

    public static Hello readHello(byte[] bytes) {
        if (bytes.length > 128) throw new IllegalArgumentException("Handshake trop volumineux.");
        var in = new ByteArrayInputStream(bytes);
        var hello = new Hello(readVarInt(in), readVarInt(in), readUtf(in, 32));
        exhausted(in);
        return hello;
    }

    public static byte[] request(EditorRequest request) {
        var out = new ByteArrayOutputStream();
        writeUtf(out, request.operation(), 24);
        writeUtf(out, request.value(), 128);
        writeVarInt(out, request.page());
        writeVarInt(out, request.order());
        return out.toByteArray();
    }

    public static EditorRequest readRequest(byte[] bytes) {
        if (bytes.length > MAX_REQUEST_BYTES)
            throw new IllegalArgumentException("Requête trop volumineuse.");
        var in = new ByteArrayInputStream(bytes);
        var request =
                new EditorRequest(
                        readUtf(in, 24), readUtf(in, 128), readVarInt(in), readVarInt(in));
        exhausted(in);
        return request;
    }

    public static byte[] state(String json) {
        var out = new ByteArrayOutputStream();
        writeUtf(out, json, MAX_STATE_CHARACTERS);
        return out.toByteArray();
    }

    public static String readState(byte[] bytes) {
        if (bytes.length > MAX_STATE_CHARACTERS * 3 + 5)
            throw new IllegalArgumentException("Vue trop volumineuse.");
        var in = new ByteArrayInputStream(bytes);
        String result = readUtf(in, MAX_STATE_CHARACTERS);
        exhausted(in);
        return result;
    }

    public static byte[] exportPayload(ExportChunk chunk) {
        var out = new ByteArrayOutputStream();
        byte[] data = exportChunk(chunk);
        writeVarInt(out, data.length);
        out.writeBytes(data);
        return out.toByteArray();
    }

    public static byte[] exportChunk(ExportChunk chunk) {
        var out = new ByteArrayOutputStream();
        writeUtf(out, chunk.ticket(), 36);
        writeUtf(out, chunk.filename(), 96);
        writeVarInt(out, chunk.totalBytes());
        writeVarInt(out, chunk.offset());
        byte[] data = chunk.data();
        writeVarInt(out, data.length);
        out.writeBytes(data);
        return out.toByteArray();
    }

    public static ExportChunk readExportChunk(byte[] bytes) {
        if (bytes.length > ExportChunk.MAX_CHUNK + 512)
            throw new IllegalArgumentException("Export fragment too large");
        var in = new ByteArrayInputStream(bytes);
        String ticket = readUtf(in, 36), filename = readUtf(in, 96);
        int total = readVarInt(in), offset = readVarInt(in), length = readVarInt(in);
        if (length > ExportChunk.MAX_CHUNK || length != in.available())
            throw new IllegalArgumentException("Invalid fragment length");
        return new ExportChunk(ticket, filename, total, offset, in.readAllBytes());
    }

    private static void exhausted(ByteArrayInputStream in) {
        if (in.available() != 0)
            throw new IllegalArgumentException("Données supplémentaires après le message.");
    }

    private static void writeUtf(ByteArrayOutputStream out, String text, int max) {
        if (text == null || text.length() > max)
            throw new IllegalArgumentException("Texte trop long.");
        byte[] bytes = text.getBytes(StandardCharsets.UTF_8);
        if (bytes.length > max * 3) throw new IllegalArgumentException("Texte UTF-8 trop long.");
        writeVarInt(out, bytes.length);
        out.writeBytes(bytes);
    }

    private static String readUtf(ByteArrayInputStream in, int max) {
        int length = readVarInt(in);
        if (length > max * 3 || length > in.available())
            throw new IllegalArgumentException("Longueur de texte invalide.");
        byte[] bytes = new byte[length];
        if (length > 0 && in.read(bytes, 0, length) != length)
            throw new IllegalArgumentException("Message tronqué.");
        try {
            String text =
                    StandardCharsets.UTF_8
                            .newDecoder()
                            .onMalformedInput(CodingErrorAction.REPORT)
                            .onUnmappableCharacter(CodingErrorAction.REPORT)
                            .decode(ByteBuffer.wrap(bytes))
                            .toString();
            if (text.length() > max) throw new IllegalArgumentException("Texte décodé trop long.");
            return text;
        } catch (CharacterCodingException exception) {
            throw new IllegalArgumentException("UTF-8 invalide.", exception);
        }
    }

    private static void writeVarInt(ByteArrayOutputStream out, int number) {
        if (number < 0) throw new IllegalArgumentException("Entier négatif.");
        do {
            int part = number & 127;
            number >>>= 7;
            out.write(number == 0 ? part : part | 128);
        } while (number != 0);
    }

    private static int readVarInt(ByteArrayInputStream in) {
        int value = 0;
        for (int i = 0; i < 5; i++) {
            int next = in.read();
            if (next < 0 || (i == 4 && (next & 0xF8) != 0))
                throw new IllegalArgumentException("VarInt invalide.");
            value |= (next & 127) << (i * 7);
            if ((next & 128) == 0) return value;
        }
        throw new IllegalArgumentException("VarInt trop long.");
    }
}
