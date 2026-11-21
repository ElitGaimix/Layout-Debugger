package fr.eligaimix.ltdr.client.editor;

import fr.eligaimix.ltdr.network.BridgeProtocol;
import fr.eligaimix.ltdr.network.EditorState;
import fr.eligaimix.ltdr.sequence.TimelineProgram;

import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** Native in-game editor. Every change is an intent; only the server changes the world. */
public final class TimelineScreen extends ScaledEditorScreen {
    private static final int MARGIN = 12;
    private static final int GRID_Y = 110;
    private static final int HEADER = 19;
    private static final int ROW = 25;
    private static final int CELL = 44;
    private static final int TEXT = 0xFFF0F5FB;
    private static final int MUTED = 0xFF9CACBF;
    private static final int CYAN = 0xFF73D8F4;
    private EditorState state = EditorClient.state();
    private EditBox radiusField;
    private EditBox pageField;
    private Button sequenceButton;
    private Button captureButton;
    private Button viewButton;
    private Button toolsButton;
    private Button endButton;
    private Button playButton;
    private Button onButton;
    private Button offButton;
    private Button orderButton;
    private Button timelineButton;
    private Button warningsButton;
    private final List<Button> seekButtons = new ArrayList<>();
    private int firstPage;
    private int firstRow;
    private String selectedPiston = "";
    private boolean orderMode;
    private String localMessage = "";

    public TimelineScreen() {
        super(Component.literal("Layout Debugger"));
    }

    @Override
    protected void initScaled() {
        state = EditorClient.state();
        String radius =
                radiusField == null ? Integer.toString(state.radius()) : radiusField.getValue();
        seekButtons.clear();
        firstPage = Math.min(firstPage, lastWindowStart());
        int currentPage = state.busy() ? state.targetPage() : state.page();
        if (currentPage < firstPage || currentPage >= firstPage + columns())
            firstPage = Math.max(0, Math.min(lastWindowStart(), currentPage - columns() / 2));
        int usable = width - MARGIN * 2;
        sequenceButton =
                button(
                        ClientText.tr("ui.animations_c77dcd"),
                        width - MARGIN - 57 - 4 - 74,
                        8,
                        74,
                        () -> minecraft.gui.setScreen(new AnimationLibraryScreen(this)),
                        ClientText.tr(
                                "ui.gerer_les_animations_choisir_creer_et_renommer_t_7f9e8f"));

        radiusField =
                new EditBox(
                        font,
                        MARGIN + 30,
                        32,
                        28,
                        20,
                        Component.literal(ClientText.tr("ui.rayon_de_capture_en_blocs_2210cc")));
        radiusField.setMaxLength(2);
        radiusField.setValue(radius);
        addRenderableWidget(radiusField);
        int setupWidth = Math.max(40, (usable - 67 - 12) / 4);
        int setupX = MARGIN + 67;
        captureButton =
                button(
                        ClientText.tr("ui.capturer_df00cf"),
                        setupX,
                        32,
                        setupWidth,
                        () -> {
                            startSession("begin");
                        },
                        ClientText.tr(
                                "ui.capturer_le_montage_autour_de_vous_rayon_8_a_31__a71acf"));
        viewButton =
                button(
                        ClientText.tr("ui.visionner_e17e84"),
                        setupX + setupWidth + 4,
                        32,
                        setupWidth,
                        () -> startSession("view"),
                        ClientText.tr(
                                "ui.lire_la_sequence_sans_entrer_en_capture_pages_le_48669a"));
        toolsButton =
                button(
                        ClientText.tr("ui.outils_e1fe47"),
                        setupX + (setupWidth + 4) * 2,
                        32,
                        setupWidth,
                        () -> EditorClient.send("tools", "", 0, 0),
                        ClientText.tr(
                                "ui.recevoir_les_outils_on_et_off_clic_droit_sur_la__15dc8e"));
        endButton =
                button(
                        ClientText.tr("ui.terminer_6bf1bd"),
                        setupX + (setupWidth + 4) * 3,
                        32,
                        setupWidth,
                        () -> EditorClient.send("end", "", 0, 0),
                        ClientText.tr(
                                "ui.terminer_restaurer_le_montage_initial_votre_inve_43a8a4"));

        int modeWidth = (usable - 16) / 5;
        onButton =
                button(
                        "ON",
                        MARGIN,
                        56,
                        modeWidth,
                        () -> {
                            if (state.readOnly() || EditorClient.liveAvailable())
                                EditorClient.animate("opening");
                            else chooseMode("on");
                        },
                        ClientText.tr(
                                "ui.maintenir_l_alimentation_du_mod_jusqu_a_une_cons_7e2d74"));
        offButton =
                button(
                        "OFF",
                        MARGIN + modeWidth + 4,
                        56,
                        modeWidth,
                        () -> {
                            if (state.readOnly() || EditorClient.liveAvailable())
                                EditorClient.animate("closing");
                            else chooseMode("off");
                        },
                        ClientText.tr(
                                "ui.retirer_uniquement_le_signal_du_mod_la_redstone__1821a3"));
        orderButton =
                button(
                        ClientText.tr("ui.ordres_2ad937"),
                        MARGIN + (modeWidth + 4) * 2,
                        56,
                        modeWidth,
                        () -> {
                            orderMode = !orderMode;
                            firstRow = 0;
                            updateControls();
                        },
                        ClientText.tr(
                                "ui.voir_reordonner_ou_supprimer_les_actions_de_la_p_64a41c"));

        timelineButton =
                button(
                        ClientText.tr("ui.ouvert_711886"),
                        MARGIN + (modeWidth + 4) * 3,
                        56,
                        modeWidth,
                        () ->
                                EditorClient.send(
                                        "timeline",
                                        state.timeline().equals("closing") ? "opening" : "closing",
                                        0,
                                        0),
                        ClientText.tr(
                                "ui.basculer_entre_ouverture_et_fermeture_la_fermetu_65e7e2"));
        warningsButton =
                button(
                        ClientText.tr("ui.param_66c1ff"),
                        MARGIN + (modeWidth + 4) * 4,
                        56,
                        modeWidth,
                        () -> minecraft.gui.setScreen(new EditorSettingsScreen(this)),
                        ClientText.tr(
                                "ui.parametres_touches_taille_du_menu_et_alertes_de__12fb3c"));

        int navigationX = MARGIN;
        seekButtons.add(
                button(
                        "0",
                        navigationX,
                        80,
                        25,
                        () -> EditorClient.seek(0),
                        ClientText.tr("ui.revenir_a_l_etat_initial_1711e0")));
        navigationX += 29;
        seekButtons.add(
                button(
                        "<",
                        navigationX,
                        80,
                        25,
                        () -> EditorClient.seek(Math.max(0, state.page() - 1)),
                        ClientText.tr("ui.page_precedente_9e1e04")));
        navigationX += 29;
        pageField =
                new EditBox(
                        font,
                        navigationX,
                        80,
                        39,
                        20,
                        Component.literal(ClientText.tr("ui.numero_de_page_83dd58")));
        pageField.setValue(Integer.toString(state.page()));
        pageField.setMaxLength(6);
        addRenderableWidget(pageField);
        navigationX += 43;
        seekButtons.add(
                button(
                        ClientText.tr("ui.aller_1980e0"),
                        navigationX,
                        80,
                        38,
                        this::seekTypedPage,
                        ClientText.tr(
                                "ui.rejouer_jusqu_a_cette_page_page_0_depart_une_pag_fc7ac1")));
        navigationX += 42;
        seekButtons.add(
                button(
                        ">",
                        navigationX,
                        80,
                        25,
                        () -> EditorClient.seek(state.page() + 1),
                        ClientText.tr("ui.page_suivante_7c6aa7")));
        navigationX += 29;
        seekButtons.add(
                button(
                        ClientText.tr("ui.fin_425106"),
                        navigationX,
                        80,
                        29,
                        () -> EditorClient.send("last", "", 0, 0),
                        ClientText.tr(
                                "ui.afficher_la_derniere_page_de_la_sequence_apres_l_bef76e")));
        navigationX += 33;
        playButton =
                button(
                        ClientText.tr("ui.lecture_3bdb36"),
                        navigationX,
                        80,
                        Math.max(59, width - MARGIN - navigationX),
                        EditorClient::togglePlayback,
                        ClientText.tr(
                                "ui.lecture_pause_u_ou_espace_dans_cet_ecran_r_reset_a9bf8f"));
        button(
                ClientText.tr("ui.monde_e573f4"),
                width - MARGIN - 57,
                8,
                57,
                this::onClose,
                ClientText.tr("ui.masquer_l_interface_pour_utiliser_les_outils_l_e_533be1"));
        updateControls();
    }

    private Button button(
            String label, int x, int y, int buttonWidth, Runnable action, String tooltip) {
        return addRenderableWidget(
                Button.builder(Component.literal(label), ignored -> action.run())
                        .bounds(x, y, buttonWidth, 20)
                        .tooltip(Tooltip.create(Component.literal(tooltip)))
                        .build());
    }

    private void chooseMode(String mode) {
        EditorClient.send("mode", mode, 0, 0);
    }

    private void startSession(String operation) {
        try {
            int radius = Integer.parseInt(radiusField.getValue());
            if (radius < 8 || radius > 31) throw new NumberFormatException();
            EditorClient.send(operation, "", radius, 0);
        } catch (NumberFormatException ignored) {
            localMessage =
                    ClientText.tr("ui.erreur_le_rayon_doit_etre_un_entier_entre_8_et_3_9359bd");
        }
    }

    private void seekTypedPage() {
        try {
            EditorClient.seek(
                    Math.max(
                            0,
                            Math.min(
                                    TimelineProgram.MAX_PAGE,
                                    Integer.parseInt(pageField.getValue()))));
        } catch (NumberFormatException ignored) {
            pageField.setValue(Integer.toString(state.page()));
        }
    }

    public void receiveState(EditorState incoming) {
        boolean changedSequence = !state.sequence().equals(incoming.sequence());
        state = incoming;
        localMessage = "";
        if (changedSequence) {
            firstRow = 0;
            firstPage = 0;
            selectedPiston = "";
        }
        if (pageField != null && !pageField.isFocused())
            pageField.setValue(
                    Integer.toString(incoming.busy() ? incoming.targetPage() : incoming.page()));
        int current = incoming.busy() ? incoming.targetPage() : incoming.page();
        if (current < firstPage || current >= firstPage + columns())
            firstPage = Math.max(0, Math.min(lastWindowStart(), current - columns() / 2));
        if (sequenceButton != null) updateControls();
    }

    private void updateControls() {
        sequenceButton.setMessage(Component.literal(ClientText.tr("ui.animations_c77dcd")));
        sequenceButton.active = true;
        captureButton.active =
                !state.sequence().isEmpty()
                        && !state.editing()
                        && !state.busy()
                        && !state.playing()
                        && EditorClient.supports(BridgeProtocol.EDIT);
        viewButton.active =
                !state.sequence().isEmpty()
                        && !state.editing()
                        && !state.busy()
                        && !state.playing()
                        && EditorClient.supports(BridgeProtocol.PLAYBACK);
        toolsButton.active = state.editing() && !state.readOnly() && !state.busy();
        endButton.active = state.editing();
        radiusField.setEditable(!state.editing());
        for (Button seek : seekButtons) seek.active = state.editing() && !state.busy();
        pageField.setEditable(state.editing() && !state.busy());
        playButton.active =
                EditorClient.liveAvailable()
                        ? state.playing()
                        : state.editing() && !state.seeking() && (!state.busy() || state.playing());
        playButton.setMessage(
                Component.literal(
                        EditorClient.liveAvailable()
                                ? ClientText.tr("ui.arreter_1b1cd2")
                                : state.playing() ? "Pause" : ClientText.tr("ui.lecture_3bdb36")));
        playButton.setTooltip(
                Tooltip.create(
                        Component.literal(
                                EditorClient.liveAvailable()
                                        ? ClientText.tr(
                                                "ui.arreter_la_lecture_en_direct_et_retirer_les_sign_ae768f")
                                        : ClientText.tr(
                                                "ui.lecture_pause_u_ou_espace_dans_cet_ecran_r_reset_a9bf8f"))));
        if (state.readOnly() || EditorClient.liveAvailable()) {
            onButton.setMessage(Component.literal(ClientText.tr("ui.ouvrir_42c077")));
            offButton.setMessage(Component.literal(ClientText.tr("ui.fermer_5ab4ec")));
            onButton.setTooltip(
                    Tooltip.create(
                            Component.literal(
                                    ClientText.tr(
                                            "ui.jouer_l_ouverture_depuis_son_depart_r_revient_au_505271"))));
            offButton.setTooltip(
                    Tooltip.create(
                            Component.literal(
                                    ClientText.tr(
                                            "ui.jouer_la_fermeture_depuis_le_resultat_de_l_ouver_dbae8b"))));
            onButton.active =
                    offButton.active =
                            !state.sequence().isEmpty()
                                    && !state.busy()
                                    && (state.editing() || EditorClient.liveAvailable());
            if (EditorClient.liveAvailable()) {
                onButton.setTooltip(
                        Tooltip.create(
                                Component.literal(
                                        ClientText.tr(
                                                "ui.jouer_l_ouverture_depuis_l_etat_reel_du_monde_sa_71bd6a"))));
                offButton.setTooltip(
                        Tooltip.create(
                                Component.literal(
                                        ClientText.tr(
                                                "ui.jouer_la_fermeture_depuis_l_etat_reel_executer_l_056b54"))));
            }
        } else {
            modeButton(onButton, "on", "ON");
            modeButton(offButton, "off", "OFF");
            onButton.setTooltip(
                    Tooltip.create(
                            Component.literal(
                                    ClientText.tr(
                                            "ui.ajouter_on_avec_l_objet_repeter_le_clic_efface_l_b26c84"))));
            offButton.setTooltip(
                    Tooltip.create(
                            Component.literal(
                                    ClientText.tr(
                                            "ui.ajouter_off_retire_seulement_le_signal_du_mod_av_efd26c"))));
        }
        orderButton.setMessage(
                Component.literal(
                        orderMode
                                ? ClientText.tr("ui.grille_25ee2c")
                                : ClientText.tr("ui.ordres_2ad937")));
        timelineButton.setMessage(
                Component.literal(
                        state.timeline().equals("closing")
                                ? ClientText.tr("ui.fermet_b9a6e1")
                                : ClientText.tr("ui.ouvert_711886")));
        timelineButton.active = state.editing() && !state.busy() && !state.playing();
        warningsButton.setMessage(Component.literal(ClientText.tr("ui.param_66c1ff")));
        warningsButton.active = true;
    }

    private void modeButton(Button button, String mode, String label) {
        button.active = state.editing() && !state.readOnly() && !state.busy();
        button.setMessage(
                Component.literal((state.mode().equals(mode) ? "• " : "") + label)
                        .withStyle(style -> style.withColor(EditorClient.modeColor(mode))));
    }

    @Override
    protected void extractScaledRenderState(
            GuiGraphicsExtractor graphics, int screenMouseX, int screenMouseY, float partialTick) {
        int mouseX = screenMouseX, mouseY = screenMouseY;
        graphics.fill(0, 0, this.width, this.height, 0x66000000);
        graphics.fillGradient(0, 0, width, height, 0xF7111824, 0xF20A1019);
        graphics.fill(0, 0, width, 2, CYAN);
        graphics.text(
                font,
                shorten(
                        (state.sequence().isEmpty() ? "Layout" : state.sequence())
                                + (state.timeline().equals("closing")
                                        ? ClientText.tr("ui.fermeture_7930d6")
                                        : ClientText.tr("ui.ouverture_106aa1")),
                        width - MARGIN * 2 - 140),
                MARGIN,
                9,
                TEXT);
        String subtitle =
                state.realtime() && !state.busy()
                        ? ClientText.tr("ui.shared")
                        : state.busy()
                                ? ClientText.tr("ui.chargement_de_la_page_bde8e9")
                                        + state.targetPage()
                                        + " RT"
                                : state.editing()
                                        ? "Page "
                                                + state.page()
                                                + " RT  ·  "
                                                + (state.readOnly()
                                                        ? ClientText.tr("ui.preview_lower")
                                                        : ClientText.tr("ui.edition_ea9e65"))
                                                + (state.playing()
                                                        ? ClientText.tr("ui.lecture_b07f9a")
                                                        : ClientText.tr("ui.pause_42d4f0"))
                                        : ClientText.tr(
                                                "ui.editeur_et_lecteur_de_sequences_8c3407");
        graphics.text(font, subtitle, MARGIN, 21, state.busy() ? 0xFFFFD069 : MUTED);
        graphics.text(font, ClientText.tr("ui.rayon_3d06ec"), MARGIN, 38, MUTED);
        if (orderMode) drawOrderList(graphics, mouseX, mouseY);
        else drawTimeline(graphics, mouseX, mouseY);
        drawFooter(graphics);
        extractWidgets(graphics, screenMouseX, screenMouseY, partialTick);
        String message =
                localMessage.isEmpty() ? ClientText.message(state.message()) : localMessage;
        if (inside(mouseX, mouseY, MARGIN, height - 31, width - MARGIN * 2, 13)
                && !message.isEmpty()) {
            graphics.setTooltipForNextFrame(font, Component.literal(message), mouseX, mouseY);
        }
    }

    private int rowLabelWidth() {
        return width < 450 ? 94 : 140;
    }

    private int gridX() {
        return MARGIN + rowLabelWidth();
    }

    private int gridBottom() {
        return Math.max(GRID_Y + HEADER + 1, height - 53);
    }

    private int rows() {
        return Math.max(1, (gridBottom() - GRID_Y - HEADER) / ROW);
    }

    private int columns() {
        return Math.max(1, (width - MARGIN - gridX()) / CELL);
    }

    private int lastWindowStart() {
        return Math.max(0, TimelineProgram.MAX_PAGE - columns() + 1);
    }

    private void drawTimeline(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        int bottom = gridBottom();
        graphics.fill(MARGIN, GRID_Y, width - MARGIN, bottom, 0xFF101B29);
        graphics.text(
                font, ClientText.tr("ui.piston_signal_2a4568"), MARGIN + 7, GRID_Y + 6, MUTED);
        for (int column = 0; column < columns(); column++) {
            int page = firstPage + column;
            int x = gridX() + column * CELL;
            boolean selected = page == (state.busy() ? state.targetPage() : state.page());
            graphics.fill(
                    x + 1,
                    GRID_Y,
                    x + CELL - 1,
                    GRID_Y + HEADER,
                    selected ? 0xFF21495B : 0xFF18283A);
            graphics.centeredText(
                    font,
                    page == 0 ? ClientText.tr("ui.depart_2fb998") : Integer.toString(page),
                    x + CELL / 2,
                    GRID_Y + 6,
                    selected ? CYAN : TEXT);
        }
        if (state.pistons().isEmpty()) {
            String text =
                    state.editing()
                            ? state.readOnly()
                                    ? ClientText.tr(
                                            "ui.cette_sequence_ne_contient_aucun_piston_712881")
                                    : ClientText.tr(
                                            "ui.outils_monde_clic_droit_sur_un_piston_94782b")
                            : ClientText.tr(
                                    "ui.choisir_une_sequence_puis_capturer_ou_visionner_a43ed7");
            graphics.textWithWordWrap(
                    font,
                    Component.literal(text),
                    MARGIN + 8,
                    GRID_Y + HEADER + 10,
                    width - MARGIN * 2 - 16,
                    MUTED);
            return;
        }
        firstRow = Math.min(firstRow, Math.max(0, state.pistons().size() - rows()));
        for (int row = 0; row < rows() && firstRow + row < state.pistons().size(); row++) {
            var target = state.pistons().get(firstRow + row);
            int y = GRID_Y + HEADER + row * ROW;
            graphics.fill(
                    MARGIN,
                    y,
                    width - MARGIN,
                    y + ROW - 1,
                    target.id().equals(selectedPiston)
                            ? 0xFF1A3345
                            : row % 2 == 0 ? 0xFF142131 : 0xFF111D2B);
            graphics.fill(
                    MARGIN + 6,
                    y + 8,
                    MARGIN + 10,
                    y + 12,
                    target.modPowered() ? 0xFF66D99A : 0xFF617187);
            graphics.text(
                    font,
                    shorten(target.id(), rowLabelWidth() - 25),
                    MARGIN + 15,
                    y + 4,
                    state.orphanPistons().contains(target.id()) ? 0xFFFFD166 : TEXT);
            graphics.text(
                    font,
                    state.orphanPistons().contains(target.id())
                            ? ClientText.tr("ui.manquant_1bdbb1")
                            : target.moving()
                                    ? ClientText.tr("ui.en_mouvement_34a277")
                                    : target.extended()
                                            ? ClientText.tr("ui.etendu_198c22")
                                            : ClientText.tr("ui.retracte_e2512e"),
                    MARGIN + 15,
                    y + 14,
                    MUTED);
            if (inside(mouseX, mouseY, MARGIN, y, rowLabelWidth(), ROW)) {
                graphics.setTooltipForNextFrame(
                        font,
                        Component.literal(
                                target.id()
                                        + " · "
                                        + target.x()
                                        + ", "
                                        + target.y()
                                        + ", "
                                        + target.z()
                                        + ClientText.tr("ui.signal_mod_0e75f2")
                                        + (target.modPowered() ? "ON" : "OFF")
                                        + (state.readOnly()
                                                ? ""
                                                : ClientText.tr(
                                                        "ui.clic_droit_reassocier_avec_on_ou_off_3cb987"))),
                        mouseX,
                        mouseY);
            }
            for (int column = 0; column < columns(); column++) {
                int page = firstPage + column;
                int x = gridX() + column * CELL;
                var actions = cellActions(target.id(), page);
                String kind = cellKind(actions);
                int color = EditorClient.modeColor(kind);
                boolean hovered = inside(mouseX, mouseY, x, y, CELL, ROW);
                graphics.fill(
                        x + 2,
                        y + 2,
                        x + CELL - 2,
                        y + ROW - 3,
                        kind.isEmpty()
                                ? hovered ? 0xFF26394D : 0xFF1A2A3B
                                : (color & 0x00FFFFFF) | 0x50000000);
                if (page == state.page())
                    graphics.outline(x + 1, y + 1, CELL - 2, ROW - 2, 0xFF4B728A);
                String label =
                        switch (kind) {
                            case "on" -> "ON";
                            case "off" -> "OFF";
                            default -> page == 0 ? "—" : "+";
                        };
                graphics.centeredText(
                        font, label, x + CELL / 2, y + 8, kind.isEmpty() ? 0xFF52657B : color);
                if (hovered) {
                    List<Component> tooltip = new ArrayList<>();
                    tooltip.add(
                            Component.literal(
                                    target.id() + ClientText.tr("ui.page_07be6b") + page));
                    for (var action : actions)
                        tooltip.add(
                                Component.literal(
                                        "t="
                                                + action.tick()
                                                + ClientText.tr("ui.ordre_53c034")
                                                + action.order()
                                                + " · "
                                                + (action.powered() ? "ON" : "OFF")));
                    tooltip.add(
                            Component.literal(
                                            page == 0
                                                    ? ClientText.tr(
                                                            "ui.etat_initial_sans_consigne_54b61b")
                                                    : state.readOnly()
                                                            ? ClientText.tr(
                                                                    "ui.clic_afficher_cette_page_96d8e4")
                                                            : ClientText.tr("ui.clic_d7ba31")
                                                                    + EditorClient.modeLabel(
                                                                            state.mode())
                                                                    + ClientText.tr(
                                                                            "ui.clic_droit_effacer_3c3d56"))
                                    .withStyle(ChatFormatting.GRAY));
                    graphics.setComponentTooltipForNextFrame(font, tooltip, mouseX, mouseY);
                }
            }
        }
    }

    private List<EditorState.ActionView> cellActions(String id, int page) {
        if (page <= 0) return List.of();
        int tick = (page - 1) * 2;
        return state.actions().stream()
                .filter(action ->
                        action.pistonId().equals(id)
                                && action.tick() >= tick
                                && action.tick() < tick + 2)
                .sorted(
                        Comparator.comparingInt(EditorState.ActionView::tick)
                                .thenComparingInt(EditorState.ActionView::order))
                .toList();
    }

    private static String cellKind(List<EditorState.ActionView> actions) {
        if (actions.isEmpty()) return "";
        var last = actions.getLast();
        return last.powered() ? "on" : "off";
    }

    private List<EditorState.ActionView> pageActions() {
        if (state.page() == 0) return List.of();
        int tick = (state.page() - 1) * 2;
        return state.actions().stream()
                .filter(action -> action.tick() >= tick && action.tick() < tick + 2)
                .sorted(
                        Comparator.comparingInt(EditorState.ActionView::tick)
                                .thenComparingInt(EditorState.ActionView::order))
                .toList();
    }

    private void drawOrderList(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        graphics.fill(MARGIN, GRID_Y, width - MARGIN, gridBottom(), 0xFF111F2E);
        graphics.text(
                font,
                ClientText.tr("ui.actions_de_la_page_733e7b")
                        + state.page()
                        + ClientText.tr("ui.ordre_d_execution_3ebc0a"),
                MARGIN + 7,
                GRID_Y + 6,
                CYAN);
        List<EditorState.ActionView> actions = pageActions();
        firstRow = Math.min(firstRow, Math.max(0, actions.size() - rows()));
        if (actions.isEmpty())
            graphics.text(
                    font,
                    ClientText.tr("ui.aucune_action_sur_cette_page_3b55c2"),
                    MARGIN + 7,
                    GRID_Y + HEADER + 9,
                    MUTED);
        for (int row = 0; row < rows() && firstRow + row < actions.size(); row++) {
            var action = actions.get(firstRow + row);
            int y = GRID_Y + HEADER + row * ROW;
            graphics.fill(
                    MARGIN + 1,
                    y,
                    width - MARGIN - 1,
                    y + ROW - 1,
                    row % 2 == 0 ? 0xFF192B3C : 0xFF152434);
            String kind = action.powered() ? "ON" : "OFF";
            graphics.text(
                    font,
                    shorten(
                            "t="
                                    + action.tick()
                                    + " / "
                                    + action.order()
                                    + "  "
                                    + action.pistonId()
                                    + "  "
                                    + kind,
                            width - (state.readOnly() ? 40 : 115)),
                    MARGIN + 7,
                    y + 8,
                    EditorClient.modeColor(action.powered() ? "on" : "off"));
            if (state.readOnly()) continue;
            int controls = width - MARGIN - 72;
            String[] labels = {"↑", "↓", "×"};
            for (int control = 0; control < 3; control++) {
                int x = controls + control * 24;
                graphics.fill(
                        x + 1,
                        y + 3,
                        x + 22,
                        y + ROW - 4,
                        inside(mouseX, mouseY, x, y, 24, ROW) ? 0xFF3C5369 : 0xFF293D52);
                graphics.centeredText(
                        font, labels[control], x + 12, y + 8, control == 2 ? 0xFFFF8794 : TEXT);
            }
        }
    }

    private void drawFooter(GuiGraphicsExtractor graphics) {
        int y = height - 46;
        graphics.horizontalLine(MARGIN, width - MARGIN, y - 3, 0xFF294158);
        String hint =
                state.readOnly()
                        ? ClientText.tr(
                                "ui.visionnage_pages_naviguer_0_depart_fin_derniere__a7018b")
                        : orderMode
                                ? ClientText.tr(
                                        "ui.ordre_au_meme_tick_supprimer_molette_liste_ded916")
                                : ClientText.tr(
                                        "ui.en_tete_aller_a_la_page_cellule_programmer_clic__ebdcf8");
        graphics.text(font, shorten(hint, width - MARGIN * 2), MARGIN, y + 1, MUTED);
        String message =
                localMessage.isEmpty() ? ClientText.message(state.message()) : localMessage;
        int color =
                message.startsWith("Erreur") || message.startsWith("!") ? 0xFFFF8794 : 0xFFBED2E3;
        graphics.text(font, shorten(message, width - MARGIN * 2), MARGIN, y + 14, color);
        String foot =
                state.pistons().size()
                        + ClientText.tr("ui.pistons_69dab5")
                        + state.actions().size()
                        + ClientText.tr("ui.changements_1_page_2_ticks_de_jeu_7930e9");
        graphics.text(font, shorten(foot, width - MARGIN * 2), MARGIN, y + 27, 0xFF71869E);
    }

    @Override
    protected boolean scaledMouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (state.seeking()) return true;
        if (event.button() != GLFW.GLFW_MOUSE_BUTTON_LEFT
                && event.button() != GLFW.GLFW_MOUSE_BUTTON_RIGHT)
            return super.scaledMouseClicked(event, doubleClick);
        double x = event.x();
        double y = event.y();
        if (inside(x, y, MARGIN, GRID_Y, width - MARGIN * 2, gridBottom() - GRID_Y)) {
            if (orderMode) return clickOrder(new MouseButtonEvent(x, y, event.buttonInfo()));
            if (x >= gridX()) {
                int column = ((int) x - gridX()) / CELL;
                if (column >= columns()) return true;
                int page = firstPage + column;
                if (y < GRID_Y + HEADER) {
                    if (state.editing() && !state.busy()) EditorClient.seek(page);
                    return true;
                }
                int row = ((int) y - GRID_Y - HEADER) / ROW + firstRow;
                if (row < state.pistons().size()
                        && state.readOnly()
                        && state.editing()
                        && !state.busy()) {
                    selectedPiston = state.pistons().get(row).id();
                    EditorClient.seek(page);
                } else if (row < state.pistons().size()
                        && page > 0
                        && state.editing()
                        && !state.busy()
                        && !state.playing()) {
                    selectedPiston = state.pistons().get(row).id();
                    EditorClient.send(
                            "edit",
                            selectedPiston
                                    + ":"
                                    + (event.button() == GLFW.GLFW_MOUSE_BUTTON_RIGHT
                                            ? "clear"
                                            : state.mode()),
                            page,
                            0);
                }
            } else if (y >= GRID_Y + HEADER) {
                int row = ((int) y - GRID_Y - HEADER) / ROW + firstRow;
                if (row < state.pistons().size()) {
                    selectedPiston = state.pistons().get(row).id();
                    if (event.button() == GLFW.GLFW_MOUSE_BUTTON_RIGHT
                            && state.editing()
                            && !state.readOnly()
                            && !state.busy()) EditorClient.send("bind", selectedPiston, 0, 0);
                }
            }
            return true;
        }
        return super.scaledMouseClicked(event, doubleClick);
    }

    private boolean clickOrder(MouseButtonEvent event) {
        if (!state.editing()
                || state.readOnly()
                || state.busy()
                || state.playing()
                || event.y() < GRID_Y + HEADER) return true;
        List<EditorState.ActionView> actions = pageActions();
        int index = ((int) event.y() - GRID_Y - HEADER) / ROW + firstRow;
        if (index >= actions.size()) return true;
        var action = actions.get(index);
        int control = ((int) event.x() - (width - MARGIN - 72)) / 24;
        if (event.x() < width - MARGIN - 72) return true;
        if (control == 2) EditorClient.send("remove", "", action.tick(), action.order());
        else {
            int otherIndex = index + (control == 0 ? -1 : 1);
            if (otherIndex >= 0 && otherIndex < actions.size()) {
                var other = actions.get(otherIndex);
                if (other.tick() == action.tick())
                    EditorClient.send(
                            "swap", action.order() + ":" + other.order(), action.tick(), 0);
            }
        }
        return true;
    }

    @Override
    protected boolean scaledMouseScrolled(
            double screenX, double screenY, double scrollX, double scrollY) {
        double x = screenX, y = screenY;
        if (inside(x, y, MARGIN, GRID_Y, width - MARGIN * 2, gridBottom() - GRID_Y)) {
            if (y < GRID_Y + HEADER || scrollX != 0) {
                firstPage =
                        Math.max(
                                0,
                                Math.min(
                                        lastWindowStart(),
                                        firstPage
                                                - (int)
                                                        Math.signum(
                                                                scrollX != 0 ? scrollX : scrollY)));
            } else {
                int count = orderMode ? pageActions().size() : state.pistons().size();
                firstRow =
                        Math.max(
                                0,
                                Math.min(
                                        Math.max(0, count - rows()),
                                        firstRow - (int) Math.signum(scrollY)));
            }
            return true;
        }
        return super.scaledMouseScrolled(screenX, screenY, scrollX, scrollY);
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        boolean editingText = getFocused() instanceof EditBox;
        if (!editingText && state.editing() && EditorClient.RESET.matches(event)) {
            EditorClient.reset();
            return true;
        }
        if (!editingText && state.editing() && state.readOnly()) {
            if (EditorClient.OPENING.matches(event)) {
                EditorClient.animate("opening");
                return true;
            }
            if (EditorClient.CLOSING.matches(event)) {
                EditorClient.animate("closing");
                return true;
            }
        }
        if (state.seeking()) {
            if (event.isEscape()) EditorClient.send("cancel_seek", "", 0, 0);
            return true;
        }
        if (!editingText) {
            if (EditorClient.OPEN.matches(event)) {
                onClose();
                return true;
            }
            if (state.editing() && !state.busy()) {
                if (EditorClient.PREVIOUS.matches(event)) {
                    EditorClient.seek(Math.max(0, state.page() - 1));
                    return true;
                }
                if (EditorClient.NEXT.matches(event)) {
                    EditorClient.seek(state.page() + 1);
                    return true;
                }
            }
            if (state.editing()
                    && (event.key() == GLFW.GLFW_KEY_SPACE || EditorClient.PLAY.matches(event))) {
                EditorClient.togglePlayback();
                return true;
            }
        }
        if (event.isConfirmation() && pageField.isFocused()) {
            seekTypedPage();
            return true;
        }
        return super.keyPressed(event);
    }

    private String shorten(String text, int limit) {
        if (font.width(text) <= limit) return text;
        return font.plainSubstrByWidth(text, Math.max(1, limit - font.width("…"))) + "…";
    }

    private static boolean inside(
            double x, double y, int left, int top, int boxWidth, int boxHeight) {
        return x >= left && x < left + boxWidth && y >= top && y < top + boxHeight;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
