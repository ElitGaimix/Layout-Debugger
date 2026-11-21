package fr.eligaimix.ltdr.network;

import com.google.gson.Gson;

import fr.eligaimix.ltdr.LayoutDebugger;

import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;

import java.util.function.BiConsumer;

public final class EditorNetworking {
    // A UTF-8 code point may use three bytes; this stays below the 1 MiB payload limit.
    public static final int MAX_STATE_CHARACTERS = 262_144;
    public static final Gson JSON = new Gson();

    private EditorNetworking() {}

    public static void init(BiConsumer<ServerPlayer, EditorRequest> handler) {
        PayloadTypeRegistry.serverboundPlay().register(RequestPayload.TYPE, RequestPayload.CODEC);
        PayloadTypeRegistry.serverboundPlay()
                .register(BridgeHelloPayload.TYPE, BridgeHelloPayload.CODEC);
        PayloadTypeRegistry.clientboundPlay()
                .register(BridgeHelloPayload.TYPE, BridgeHelloPayload.CODEC);
        PayloadTypeRegistry.clientboundPlay().register(StatePayload.TYPE, StatePayload.CODEC);
        PayloadTypeRegistry.clientboundPlay().register(OpenPayload.TYPE, OpenPayload.CODEC);
        PayloadTypeRegistry.clientboundPlay().register(ExportPayload.TYPE, ExportPayload.CODEC);
        ServerPlayNetworking.registerGlobalReceiver(
                RequestPayload.TYPE,
                (payload, context) ->
                        context.server()
                                .execute(
                                        () -> handler.accept(context.player(), payload.request())));
    }

    /** An explicit open event keeps normal synchronization from reopening a hidden screen. */
    public static void open(ServerPlayer player) {
        if (ServerPlayNetworking.canSend(player, OpenPayload.TYPE))
            ServerPlayNetworking.send(player, new OpenPayload());
    }

    public static void send(ServerPlayer player, EditorState state) {
        if (!ServerPlayNetworking.canSend(player, StatePayload.TYPE)) return;
        String json = JSON.toJson(state);
        if (json.length() > MAX_STATE_CHARACTERS) {
            throw new IllegalStateException(
                    "Séquence trop volumineuse pour être affichée dans l'éditeur.");
        }
        ServerPlayNetworking.send(player, new StatePayload(json));
    }

    public record BridgeHelloPayload(BridgeProtocol.Hello hello) implements CustomPacketPayload {
        public static final Type<BridgeHelloPayload> TYPE =
                new Type<>(LayoutDebugger.id("bridge_hello"));
        public static final StreamCodec<RegistryFriendlyByteBuf, BridgeHelloPayload> CODEC =
                StreamCodec.of(
                        (buffer, payload) -> {
                            buffer.writeVarInt(payload.hello().version());
                            buffer.writeVarInt(payload.hello().capabilities());
                            buffer.writeUtf(payload.hello().backend(), 32);
                        },
                        buffer ->
                                new BridgeHelloPayload(
                                        new BridgeProtocol.Hello(
                                                buffer.readVarInt(),
                                                buffer.readVarInt(),
                                                buffer.readUtf(32))));

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    public record RequestPayload(EditorRequest request) implements CustomPacketPayload {
        public static final Type<RequestPayload> TYPE =
                new Type<>(LayoutDebugger.id("editor_request"));
        public static final StreamCodec<RegistryFriendlyByteBuf, RequestPayload> CODEC =
                StreamCodec.of(
                        (buffer, payload) -> {
                            buffer.writeUtf(payload.request.operation(), 24);
                            buffer.writeUtf(payload.request.value(), 128);
                            buffer.writeVarInt(payload.request.page());
                            buffer.writeVarInt(payload.request.order());
                        },
                        buffer ->
                                new RequestPayload(
                                        new EditorRequest(
                                                buffer.readUtf(24),
                                                buffer.readUtf(128),
                                                buffer.readVarInt(),
                                                buffer.readVarInt())));

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    public record StatePayload(String json) implements CustomPacketPayload {
        public static final Type<StatePayload> TYPE = new Type<>(LayoutDebugger.id("editor_state"));
        public static final StreamCodec<RegistryFriendlyByteBuf, StatePayload> CODEC =
                StreamCodec.of(
                        (buffer, payload) -> buffer.writeUtf(payload.json(), MAX_STATE_CHARACTERS),
                        buffer -> new StatePayload(buffer.readUtf(MAX_STATE_CHARACTERS)));

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    public static void export(ServerPlayer player, String ticket, String filename, byte[] bytes) {
        if (!ServerPlayNetworking.canSend(player, ExportPayload.TYPE)) return;
        for (var chunk : ExportChunk.split(ticket, filename, bytes))
            ServerPlayNetworking.send(player, new ExportPayload(chunk));
    }

    public record ExportPayload(ExportChunk chunk) implements CustomPacketPayload {
        public static final Type<ExportPayload> TYPE =
                new Type<>(LayoutDebugger.id("animation_export"));
        public static final StreamCodec<RegistryFriendlyByteBuf, ExportPayload> CODEC =
                StreamCodec.of(
                        (b, p) -> b.writeByteArray(BridgeProtocol.exportChunk(p.chunk())),
                        b ->
                                new ExportPayload(
                                        BridgeProtocol.readExportChunk(
                                                b.readByteArray(ExportChunk.MAX_CHUNK + 512))));

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    public record OpenPayload() implements CustomPacketPayload {
        public static final Type<OpenPayload> TYPE = new Type<>(LayoutDebugger.id("editor_open"));
        public static final StreamCodec<RegistryFriendlyByteBuf, OpenPayload> CODEC =
                StreamCodec.unit(new OpenPayload());

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }
}
