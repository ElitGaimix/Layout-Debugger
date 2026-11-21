package fr.eligaimix.ltdr.client.editor;

import fr.eligaimix.ltdr.network.EditorState;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

import java.util.List;
import java.util.Locale;

/**
 * Animation management lives outside the tick editor. All mutations are validated by the server.
 */
public final class AnimationLibraryScreen extends ScaledEditorScreen {
    private final Screen parent;
    private EditorState state = EditorClient.state();
    private EditBox search, name;
    private Button create, rename, open, export;
    private int offset;
    private String localMessage = "";
    private boolean pending;

    public AnimationLibraryScreen(Screen parent) {
        super(Component.literal(ClientText.tr("ui.animations_c77dcd")));
        this.parent = parent;
    }

    @Override
    protected void initScaled() {
        String filter = search == null ? "" : search.getValue(),
                draft = name == null ? "" : name.getValue();
        if (state != EditorClient.state()) pending = false;
        state = EditorClient.state();
        search =
                new EditBox(
                        font,
                        12,
                        34,
                        width - 24,
                        20,
                        Component.literal(ClientText.tr("ui.rechercher_une_animation_0b54e0")));
        search.setHint(Component.literal(ClientText.tr("ui.rechercher_une_animation_bec444")));
        search.setMaxLength(48);
        search.setValue(filter);
        search.setResponder(ignored -> offset = 0);
        addRenderableWidget(search);
        name =
                new EditBox(
                        font,
                        12,
                        height - 91,
                        width - 24,
                        20,
                        Component.literal(ClientText.tr("ui.nom_de_l_animation_957204")));
        name.setHint(Component.literal(ClientText.tr("ui.nom_lettres_chiffres_ou_37803c")));
        name.setMaxLength(48);
        name.setValue(draft);
        name.setResponder(ignored -> refresh());
        addRenderableWidget(name);
        int w = (width - 32) / 3;
        create = button(ClientText.tr("ui.creer_1a6861"), 12, height - 67, w, () -> submit(false));
        create.setTooltip(
                net.minecraft.client.gui.components.Tooltip.create(
                        Component.literal(
                                ClientText.tr(
                                        "ui.saisir_un_nom_libre_pour_creer_une_animation_vid_8e9119"))));
        rename =
                button(
                        ClientText.tr("ui.renommer_8e8a86"),
                        16 + w,
                        height - 67,
                        w,
                        () -> submit(true));
        rename.setTooltip(
                net.minecraft.client.gui.components.Tooltip.create(
                        Component.literal(
                                ClientText.tr(
                                        "ui.selectionner_une_animation_puis_saisir_un_nouvea_c9f13d"))));
        open =
                button(
                        ClientText.tr("ui.ouvrir_42c077"),
                        20 + w * 2,
                        height - 67,
                        width - 32 - w * 2,
                        () -> minecraft.gui.setScreen(new TimelineScreen()));
        button(
                ClientText.tr("ui.param_66c1ff"),
                width - 80,
                8,
                68,
                () -> minecraft.gui.setScreen(new EditorSettingsScreen(this)));
        export =
                button(
                        ClientText.tr("ui.export"),
                        12,
                        height - 26,
                        76,
                        EditorClient::exportAnimation);
        button(
                parent == null
                        ? ClientText.tr("ui.monde_e573f4")
                        : ClientText.tr("ui.retour_e5befb"),
                width / 2 - 45,
                height - 26,
                90,
                this::onClose);
        refresh();
    }

    private Button button(String text, int x, int y, int w, Runnable action) {
        return addRenderableWidget(
                Button.builder(Component.literal(text), ignored -> action.run())
                        .bounds(x, y, w, 20)
                        .build());
    }

    private List<String> visibleNames() {
        String filter = search == null ? "" : search.getValue().toLowerCase(Locale.ROOT);
        return state.sequences().stream()
                .filter(s -> s.toLowerCase(Locale.ROOT).contains(filter))
                .sorted(String.CASE_INSENSITIVE_ORDER)
                .toList();
    }

    private int rows() {
        return Math.max(1, (height - 170) / 22);
    }

    private void refresh() {
        if (create == null || rename == null || open == null) return;
        String draft = name.getValue().strip();
        boolean valid = draft.matches("[a-zA-Z0-9_-]{1,48}");
        boolean unlocked = !state.editing() && !state.busy() && !state.playing() && !pending;
        create.active = unlocked && valid && !state.sequences().contains(draft);
        rename.active =
                unlocked
                        && valid
                        && !state.sequence().isEmpty()
                        && !state.sequences().contains(draft);
        if (export != null) export.active = unlocked && !state.sequence().isEmpty();
        name.setEditable(unlocked);
        open.active = !state.sequence().isEmpty() && !pending;
        offset = Math.max(0, Math.min(offset, Math.max(0, visibleNames().size() - rows())));
    }

    public void receiveState(EditorState incoming) {
        state = incoming;
        pending = false;
        localMessage = "";
        refresh();
    }

    private void submit(boolean renaming) {
        String draft = name.getValue().strip();
        if (state.editing() || state.busy()) return;
        if (!draft.matches("[a-zA-Z0-9_-]{1,48}")) {
            localMessage = ClientText.tr("ui.nom_invalide_1_a_48_lettres_chiffres_ou_e07e19");
            return;
        }
        pending = true;
        refresh();
        EditorClient.send(
                renaming ? "rename" : "create",
                renaming ? state.sequence() + ":" + draft : draft,
                0,
                0);
    }

    @Override
    public void tick() {
        refresh();
    }

    @Override
    protected boolean scaledMouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (event.button() == 0
                && event.x() >= 12
                && event.x() < width - 12
                && event.y() >= 60
                && event.y() < 60 + rows() * 22) {
            var list = visibleNames();
            int index = offset + ((int) event.y() - 60) / 22;
            if (index < list.size()) {
                if (state.editing() || state.playing())
                    localMessage =
                            ClientText.tr(
                                    "ui.terminer_la_session_ou_arreter_la_lecture_avant__0ac06f");
                else {
                    String selected = list.get(index);
                    name.setValue(selected);
                    pending = true;
                    refresh();
                    EditorClient.send("select", selected, 0, 0);
                }
            }
            return true;
        }
        return super.scaledMouseClicked(event, doubleClick);
    }

    @Override
    protected boolean scaledMouseScrolled(double x, double y, double dx, double dy) {
        if (x >= 12 && x < width - 12 && y >= 60 && y < 60 + rows() * 22) {
            offset -= (int) Math.signum(dy);
            refresh();
            return true;
        }
        return super.scaledMouseScrolled(x, y, dx, dy);
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (event.isConfirmation() && name.isFocused()) {
            submit(false);
            return true;
        }
        if (!(getFocused() instanceof EditBox) && EditorClient.OPEN.matches(event)) {
            onClose();
            return true;
        }
        return super.keyPressed(event);
    }

    @Override
    public void onClose() {
        minecraft.gui.setScreen(parent);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    protected void extractScaledRenderState(
            GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        graphics.fillGradient(0, 0, width, height, 0xF7111824, 0xF20A1019);
        graphics.text(font, "ANIMATIONS", 12, 13, 0xFF73D8F4);
        var list = visibleNames();
        graphics.fill(12, 60, width - 12, 60 + rows() * 22, 0xFF101B29);
        if (list.isEmpty())
            graphics.text(
                    font,
                    state.sequences().isEmpty()
                            ? ClientText.tr("ui.creer_une_animation_pour_commencer_0ee34a")
                            : ClientText.tr("ui.aucun_resultat_53a595"),
                    20,
                    69,
                    0xFF9CACBF);
        for (int row = 0; row < rows() && offset + row < list.size(); row++) {
            String entry = list.get(offset + row);
            int y = 60 + row * 22;
            graphics.fill(
                    13,
                    y + 1,
                    width - 13,
                    y + 21,
                    entry.equals(state.sequence())
                            ? 0xFF21495B
                            : row % 2 == 0 ? 0xFF142131 : 0xFF111D2B);
            graphics.text(
                    font,
                    shorten(entry, width - 44),
                    20,
                    y + 7,
                    entry.equals(state.sequence()) ? 0xFF73D8F4 : 0xFFF0F5FB);
            if (mouseX >= 12 && mouseX < width - 12 && mouseY >= y && mouseY < y + 22)
                graphics.setTooltipForNextFrame(font, Component.literal(entry), mouseX, mouseY);
        }
        graphics.text(
                font,
                shorten(
                        ClientText.tr("ui.selection_84c25c")
                                + (state.sequence().isEmpty() ? "aucune" : state.sequence()),
                        width - 24),
                12,
                height - 105,
                0xFFBED2E3);
        String message =
                localMessage.isEmpty()
                        ? state.editing()
                                ? ClientText.tr("ui.gestion_verrouillee_pendant_la_session_723b5b")
                                : ClientText.message(state.message())
                        : localMessage;
        graphics.text(
                font,
                shorten(message, width - 24),
                12,
                height - 42,
                message.startsWith("Erreur") ? 0xFFFF8794 : 0xFF9CACBF);
        extractWidgets(graphics, mouseX, mouseY, delta);
    }

    private String shorten(String value, int limit) {
        return font.width(value) <= limit
                ? value
                : font.plainSubstrByWidth(value, Math.max(1, limit - font.width("…"))) + "…";
    }
}
