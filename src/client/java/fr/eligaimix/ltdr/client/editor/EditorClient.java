package fr.eligaimix.ltdr.client.editor;

import com.mojang.blaze3d.platform.InputConstants;

import fr.eligaimix.ltdr.LayoutDebugger;
import fr.eligaimix.ltdr.network.BridgeProtocol;
import fr.eligaimix.ltdr.network.EditorNetworking;
import fr.eligaimix.ltdr.network.EditorRequest;
import fr.eligaimix.ltdr.network.EditorState;
import fr.eligaimix.ltdr.playback.EditorTools;
import fr.eligaimix.ltdr.sequence.TimelineProgram;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;

import org.lwjgl.glfw.GLFW;

public final class EditorClient {
    private static final KeyMapping.Category CATEGORY =
            KeyMapping.Category.register(LayoutDebugger.id("editor"));
    public static final KeyMapping OPEN = key("open", GLFW.GLFW_KEY_K);
    public static final KeyMapping PREVIOUS = key("previous", GLFW.GLFW_KEY_LEFT);
    public static final KeyMapping NEXT = key("next", GLFW.GLFW_KEY_RIGHT);
    public static final KeyMapping PLAY = key("play", GLFW.GLFW_KEY_U);
    public static final KeyMapping RESET = key("reset", GLFW.GLFW_KEY_R);
    public static final KeyMapping OPENING = key("opening", GLFW.GLFW_KEY_I);
    public static final KeyMapping CLOSING = key("closing", GLFW.GLFW_KEY_J);

    public static java.util.List<KeyMapping> bindings() {
        return java.util.List.of(OPEN, PREVIOUS, NEXT, PLAY, RESET, OPENING, CLOSING);
    }

    private static volatile BridgeProtocol.Hello paperPeer;

    public static boolean paperBackend() {
        return paperPeer != null;
    }

    public static boolean liveAvailable() {
        return paperBackend() && supports(BridgeProtocol.LIVE) && !state.editing();
    }

    public static boolean supports(int capability) {
        var peer = paperPeer;
        return peer == null
                || peer.version() == BridgeProtocol.VERSION
                        && (peer.capabilities() & capability) == capability;
    }

    private static volatile EditorState state = EditorState.empty();
    private static int settleTicks;
    private static final fr.eligaimix.ltdr.storage.ExportReceiver exports =
            new fr.eligaimix.ltdr.storage.ExportReceiver();

    public static void exportAnimation() {
        send("export", exports.begin(), 0, 0);
    }

    private EditorClient() {}

    private static KeyMapping key(String name, int key) {
        return KeyMappingHelper.registerKeyMapping(
                new KeyMapping(
                        "key.layout-debugger." + name, InputConstants.Type.KEYSYM, key, CATEGORY));
    }

    public static void initialize() {
        EditorTools.clientInput =
                (player, pos, mode) -> {
                    var client = Minecraft.getInstance();
                    if (!state.editing() || player.isShiftKeyDown() || client.level == null)
                        return false;
                    var start = player.getEyePosition(1.0F);
                    var end =
                            start.add(
                                    player.getViewVector(1.0F)
                                            .scale(player.blockInteractionRange()));
                    var target =
                            PistonPicking.pick(
                                    client.level,
                                    state.pistons(),
                                    start,
                                    end,
                                    client.hitResult,
                                    1.0F);
                    if (target == null) return false;
                    if (!state.busy() && !state.seeking())
                        send("worldtool", "@" + target.id() + ":" + mode, 0, 0);
                    if (state.busy() || state.seeking())
                        client.player.sendOverlayMessage(
                                Component.literal(ClientText.tr("ui.wait_for_page")));
                    return true;
                };
        ClientPlayConnectionEvents.JOIN.register(
                (handler, sender, client) -> send("language", EditorPreferences.language(), 0, 0));
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
            paperPeer = null;
            exports.clear();
        });
        ClientPlayNetworking.registerGlobalReceiver(
                EditorNetworking.BridgeHelloPayload.TYPE,
                (payload, context) ->
                        context.client()
                                .execute(() -> {
                                    if (!payload.hello().backend().equals("paper")) return;
                                    paperPeer = payload.hello();
                                    if (paperPeer.version() == BridgeProtocol.VERSION) {
                                        ClientPlayNetworking.send(
                                                new EditorNetworking.BridgeHelloPayload(
                                                        new BridgeProtocol.Hello(
                                                                BridgeProtocol.VERSION,
                                                                BridgeProtocol.ALL,
                                                                "fabric-client")));
                                        send("language", EditorPreferences.language(), 0, 0);
                                    }
                                }));
        ClientPlayNetworking.registerGlobalReceiver(
                EditorNetworking.ExportPayload.TYPE,
                (payload, context) ->
                        context.client()
                                .execute(() -> {
                                    try {
                                        exports.accept(
                                                        payload.chunk(),
                                                        context.client().gameDirectory.toPath())
                                                .ifPresent(path -> {
                                                    if (context.client().player != null)
                                                        context.client()
                                                                .player
                                                                .sendSystemMessage(
                                                                        Component.literal(
                                                                                ClientText.tr(
                                                                                                "ui.export_saved")
                                                                                        + path));
                                                });
                                    } catch (java.io.IOException | RuntimeException e) {
                                        if (context.client().player != null)
                                            context.client()
                                                    .player
                                                    .sendSystemMessage(
                                                            Component.literal(
                                                                    ClientText.tr(
                                                                                    "ui.export_failed")
                                                                            + e.getMessage()));
                                    }
                                }));
        ClientPlayNetworking.registerGlobalReceiver(
                EditorNetworking.OpenPayload.TYPE,
                (payload, context) ->
                        context.client()
                                .execute(() -> {
                                    if (state.editing()) {
                                        if (!(context.client().gui.screen()
                                                instanceof TimelineScreen))
                                            context.client().gui.setScreen(new TimelineScreen());
                                    } else if (!(context.client().gui.screen()
                                            instanceof AnimationLibraryScreen))
                                        context.client()
                                                .gui
                                                .setScreen(new AnimationLibraryScreen(null));
                                }));
        ClientPlayNetworking.registerGlobalReceiver(
                EditorNetworking.StatePayload.TYPE,
                (payload, context) -> {
                    try {
                        EditorState incoming =
                                EditorNetworking.JSON.fromJson(payload.json(), EditorState.class);
                        context.client()
                                .execute(() -> {
                                    if (state.seeking() && !incoming.seeking()) settleTicks = 2;
                                    state = incoming;
                                    if (context.client().gui.screen()
                                            instanceof TimelineScreen screen) {
                                        screen.receiveState(incoming);
                                    } else if (context.client().gui.screen()
                                            instanceof AnimationLibraryScreen library) {
                                        library.receiveState(incoming);
                                    }
                                });
                    } catch (RuntimeException exception) {
                        LayoutDebugger.LOGGER.warn("Vue d'éditeur invalide reçue", exception);
                    }
                });
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (!EditorPreferences.bindingsMigrated()) {
                // Old versions used R for play; reserve it for the newly requested reset.
                if (PLAY.saveString().equals("key.keyboard.r")) {
                    PLAY.setKey(PLAY.getDefaultKey());
                    KeyMapping.resetMapping();
                    client.options.save();
                }
                EditorPreferences.markBindingsMigrated();
            }
            if (client.player == null || client.level == null) {
                state = EditorState.empty();
                settleTicks = 0;
                return;
            }
            if (!state.seeking() && settleTicks > 0) settleTicks--;
            if (client.gui.screen() == null) {
                while (OPEN.consumeClick()) open();
                while (PREVIOUS.consumeClick())
                    if (state.editing() && !state.busy() && !state.seeking())
                        seek(Math.max(0, state.page() - 1));
                while (NEXT.consumeClick())
                    if (state.editing() && !state.busy() && !state.seeking())
                        seek(state.page() + 1);
                while (PLAY.consumeClick())
                    if (state.editing() && !state.seeking() || liveAvailable() && state.playing())
                        togglePlayback();
                while (RESET.consumeClick()) if (state.editing()) reset();
                while (OPENING.consumeClick())
                    if (state.editing() && state.readOnly()
                            || liveAvailable() && !state.sequence().isEmpty()) animate("opening");
                while (CLOSING.consumeClick())
                    if (state.editing() && state.readOnly()
                            || liveAvailable() && !state.sequence().isEmpty()) animate("closing");
            } else {
                for (var binding : bindings())
                    while (binding.consumeClick()) {
                        /* Screens handle their own input; never replay stale clicks in-world. */
                    }
            }
        });
        HudElementRegistry.addLast(
                LayoutDebugger.id("editor_hud"), (graphics, delta) -> drawHud(graphics));
        PistonHighlights.initialize();
    }

    public static EditorState state() {
        return state;
    }

    public static void open() {
        Minecraft client = Minecraft.getInstance();
        if (client.player == null || client.level == null || !client.player.isCreative()) return;
        if (!supports(BridgeProtocol.LIBRARY)) {
            client.player.sendOverlayMessage(
                    Component.literal(
                            ClientText.tr(
                                    "ui.version_ou_fonctions_du_pont_paper_incompatibles_86adcf")));
            return;
        }
        if (!ClientPlayNetworking.canSend(EditorNetworking.RequestPayload.TYPE)) {
            client.player.sendOverlayMessage(
                    Component.literal(
                            ClientText.tr(
                                    "ui.layout_debugger_doit_etre_present_sur_le_serveur_2387eb")));
            return;
        }
        send("open", "", 0, 0);
    }

    public static void send(String operation, String value, int page, int order) {
        Minecraft client = Minecraft.getInstance();
        if (client.player == null
                || !ClientPlayNetworking.canSend(EditorNetworking.RequestPayload.TYPE)) return;
        ClientPlayNetworking.send(
                new EditorNetworking.RequestPayload(
                        new EditorRequest(operation, value, page, order)));
    }

    public static void seek(int page) {
        send("seek", "", Math.max(0, Math.min(TimelineProgram.MAX_PAGE, page)), 0);
    }

    public static void reset() {
        send("reset", "", 0, 0);
    }

    public static void animate(String timeline) {
        if (liveAvailable())
            send(timeline.equals("closing") ? "live_close" : "live_open", state.sequence(), 0, 0);
        else send("animate", timeline, 0, 0);
    }

    public static void togglePlayback() {
        if (liveAvailable()) {
            if (state.playing()) send("live_stop", "", 0, 0);
        } else send(state.playing() ? "pause" : "play", "", 0, 0);
    }

    public static String modeLabel(String mode) {
        return switch (mode) {
            case "on" -> "ON";
            case "off" -> "OFF";
            case "clear" -> ClientText.tr("ui.effacer_fe23de");
            default -> "ON";
        };
    }

    public static int modeColor(String mode) {
        return switch (mode) {
            case "on" -> 0xFF66D99A;
            case "off" -> 0xFFFF8794;
            case "clear" -> 0xFFB3BDCB;
            default -> 0xFFFFD069;
        };
    }

    private static void drawHud(GuiGraphicsExtractor graphics) {
        Minecraft client = Minecraft.getInstance();
        if (!state.editing()
                || client.player == null
                || client.gui.hud.isHidden()
                || client.gui.screen() != null) return;
        int x = 9;
        int y = 9;
        String heldMode = EditorTools.mode(client.player.getMainHandItem());
        String mode = heldMode == null ? state.mode() : heldMode;
        String page =
                state.busy()
                        ? ClientText.tr("ui.calcul_7c5f96")
                                + state.page()
                                + " → "
                                + state.targetPage()
                        : "Page " + state.page();
        String line =
                state.sequence()
                        + " / "
                        + (state.timeline().equals("closing")
                                ? ClientText.tr("ui.fermeture_33b518")
                                : ClientText.tr("ui.ouverture_f2e324"))
                        + "  •  "
                        + page
                        + " RT  •  "
                        + (state.readOnly()
                                ? ClientText.tr("ui.visionnage_0b14e6")
                                : modeLabel(mode));
        String hint =
                OPEN.getTranslatedKeyMessage().getString()
                        + " : timeline   "
                        + PREVIOUS.getTranslatedKeyMessage().getString()
                        + "/"
                        + NEXT.getTranslatedKeyMessage().getString()
                        + " : pages";
        if (state.readOnly())
            hint =
                    OPENING.getTranslatedKeyMessage().getString()
                            + ClientText.tr("ui.ouvrir_6acb2e")
                            + CLOSING.getTranslatedKeyMessage().getString()
                            + ClientText.tr("ui.fermer_ed8820")
                            + RESET.getTranslatedKeyMessage().getString()
                            + " : reset   "
                            + OPEN.getTranslatedKeyMessage().getString()
                            + " : menu";
        else hint += "   " + RESET.getTranslatedKeyMessage().getString() + " : reset";
        int panelWidth = Math.max(client.font.width(line), client.font.width(hint)) + 20;
        String targetLine = "";
        var start = client.player.getEyePosition(1.0F);
        var end =
                start.add(
                        client.player
                                .getViewVector(1.0F)
                                .scale(client.player.blockInteractionRange()));
        var target =
                PistonPicking.pick(
                        client.level,
                        state.pistons().stream()
                                .filter(t -> !state.orphanPistons().contains(t.id()))
                                .toList(),
                        start,
                        end,
                        client.hitResult,
                        1.0F);
        if (target != null && !state.orphanPistons().contains(target.id())) {
            targetLine =
                    target.id()
                            + " @ "
                            + target.x()
                            + ","
                            + target.y()
                            + ","
                            + target.z()
                            + "  \u2022  "
                            + (target.moving()
                                    ? ClientText.tr("ui.en_mouvement_166d67")
                                    : target.extended()
                                            ? ClientText.tr("ui.etendu_69d7a7")
                                            : ClientText.tr("ui.retracte_406815"))
                            + ClientText.tr("ui.signal_mod_054d7f")
                            + (target.modPowered() ? "ON" : "OFF");
        }
        if (!targetLine.isEmpty())
            panelWidth = Math.max(panelWidth, client.font.width(targetLine) + 20);
        panelWidth = Math.min(panelWidth, graphics.guiWidth() - 18);
        int panelHeight = targetLine.isEmpty() ? 37 : 53;
        graphics.fill(x, y, x + panelWidth, y + panelHeight, 0xDD101822);
        graphics.fill(x, y, x + 3, y + panelHeight, modeColor(mode));
        graphics.text(
                client.font, fitHudText(client, line, panelWidth - 20), x + 10, y + 8, 0xFFF1F6FC);
        graphics.text(
                client.font, fitHudText(client, hint, panelWidth - 20), x + 10, y + 22, 0xFFA3B3C9);
        if (!targetLine.isEmpty())
            graphics.text(
                    client.font,
                    fitHudText(client, targetLine, panelWidth - 20),
                    x + 10,
                    y + 38,
                    0xFFBBD9EA);
    }

    private static String fitHudText(Minecraft client, String text, int width) {
        return client.font.width(text) <= width
                ? text
                : client.font.plainSubstrByWidth(text, Math.max(1, width - client.font.width("…")))
                        + "…";
    }
}
