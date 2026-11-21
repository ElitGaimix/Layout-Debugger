package fr.eligaimix.ltdr.network;

import com.google.gson.Gson;

import fr.eligaimix.ltdr.paper.LayoutDebuggerPaper;

import net.minecraft.server.level.ServerPlayer;

/** Bukkit plugin messages carry the exact same bounded wire payloads as Fabric. */
public final class EditorNetworking {
    private EditorNetworking() {}

    public static final Gson JSON = new Gson();

    public static void export(ServerPlayer player, String ticket, String filename, byte[] bytes) {
        LayoutDebuggerPaper.instance.export(player, ticket, filename, bytes);
    }

    public static void open(ServerPlayer player) {
        LayoutDebuggerPaper.instance.open(player);
    }

    public static void send(ServerPlayer player, EditorState state) {
        LayoutDebuggerPaper.instance.send(player, state);
    }
}
