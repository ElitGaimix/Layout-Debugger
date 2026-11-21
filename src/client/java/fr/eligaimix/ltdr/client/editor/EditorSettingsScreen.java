package fr.eligaimix.ltdr.client.editor;

import com.mojang.blaze3d.platform.InputConstants;

import net.minecraft.ChatFormatting;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

import java.util.LinkedHashMap;
import java.util.Map;

/** Mod-only bindings, using vanilla key storage, conflict detection and mouse bindings. */
public final class EditorSettingsScreen extends ScaledEditorScreen {
    private final Screen parent;
    private final Map<KeyMapping, Button> bindingButtons = new LinkedHashMap<>();
    private boolean keysTab;
    private KeyMapping selected;
    private net.minecraft.client.gui.components.AbstractSliderButton zoomSlider;
    private Button warningsButton;

    public EditorSettingsScreen(Screen parent) {
        super(Component.literal(ClientText.tr("ui.parametres_layout_debugger_37d025")));
        this.parent = parent;
    }

    @Override
    protected void initScaled() {
        bindingButtons.clear();
        int left = Math.max(12, (width - 410) / 2), usable = width - left * 2;
        var interfaceTab =
                button(
                        "Interface",
                        left,
                        34,
                        usable / 2 - 2,
                        () -> {
                            keysTab = false;
                            selected = null;
                            rebuildWidgets();
                        });
        interfaceTab.active = keysTab;
        if (!keysTab)
            interfaceTab.setMessage(
                    Component.literal("Interface")
                            .withStyle(ChatFormatting.AQUA, ChatFormatting.BOLD));
        var keysTabButton =
                button(
                        ClientText.tr("ui.touches_fa41b1"),
                        left + usable / 2 + 2,
                        34,
                        usable / 2 - 2,
                        () -> {
                            keysTab = true;
                            rebuildWidgets();
                        });
        keysTabButton.active = !keysTab;
        if (keysTab)
            keysTabButton.setMessage(
                    Component.literal(ClientText.tr("ui.touches_fa41b1"))
                            .withStyle(ChatFormatting.AQUA, ChatFormatting.BOLD));
        zoomSlider = null;
        warningsButton = null;
        if (keysTab) {
            int row = 0;
            for (var key : EditorClient.bindings()) {
                int y = 66 + row++ * 20;
                bindingButtons.put(
                        key,
                        button(
                                "",
                                width - left - 118,
                                y,
                                92,
                                () -> {
                                    selected = key;
                                    refresh();
                                }));
                button("↺", width - left - 22, y, 22, () -> assign(key, key.getDefaultKey()));
            }
        } else {
            zoomSlider =
                    addRenderableWidget(
                            new net.minecraft.client.gui.components.AbstractSliderButton(
                                    left,
                                    70,
                                    usable,
                                    20,
                                    Component.empty(),
                                    (EditorPreferences.zoomPercent() - 50) / 150.0) {
                                {
                                    updateMessage();
                                }

                                @Override
                                protected void updateMessage() {
                                    int requested = EditorPreferences.zoomPercent();
                                    int fit =
                                            Math.round(
                                                    Math.min(
                                                                    minecraft
                                                                                    .getWindow()
                                                                                    .getGuiScaledWidth()
                                                                            / 320.0F,
                                                                    minecraft
                                                                                    .getWindow()
                                                                                    .getGuiScaledHeight()
                                                                            / 240.0F)
                                                            * 100);
                                    setMessage(
                                            Component.literal(
                                                    ClientText.tr("ui.zoom_4cb473")
                                                            + requested
                                                            + "%"
                                                            + (requested > fit
                                                                    ? ClientText.tr(
                                                                                    "ui.affiche_d61809")
                                                                            + fit
                                                                            + "%)"
                                                                    : "")));
                                }

                                @Override
                                protected void applyValue() {
                                    EditorPreferences.setZoomPercent(
                                            (int) Math.round(50 + value * 150));
                                }

                                @Override
                                public boolean mouseScrolled(
                                        double x, double y, double dx, double dy) {
                                    setValue(value + Math.signum(dy) / 30.0);
                                    return true;
                                }
                            });
            zoomSlider.setTooltip(
                    Tooltip.create(
                            Component.literal(
                                    ClientText.tr(
                                            "ui.glisser_le_curseur_ou_utiliser_la_molette_texte__e0d353"))));
            button(
                    ClientText.tr("ui.language"),
                    left,
                    124,
                    usable,
                    () -> {
                        EditorPreferences.setLanguage(
                                EditorPreferences.language().equals("fr_fr") ? "en_us" : "fr_fr");
                        EditorClient.send("language", EditorPreferences.language(), 0, 0);
                        if (parent instanceof ScaledEditorScreen scaled) scaled.refreshLanguage();
                        rebuildWidgets();
                    });
            warningsButton =
                    button("", left, 100, usable, () -> EditorClient.send("warnings", "", 0, 0));
            warningsButton.setTooltip(
                    Tooltip.create(
                            Component.literal(
                                    ClientText.tr(
                                            "ui.reglage_de_la_sequence_capturee_maj_clic_droit_a_0211a7"))));
        }
        button(ClientText.tr("ui.retour_e5befb"), width / 2 - 70, height - 26, 140, this::onClose);
        refresh();
    }

    private Button button(String text, int x, int y, int w, Runnable action) {
        return addRenderableWidget(
                Button.builder(Component.literal(text), ignored -> action.run())
                        .bounds(x, y, w, 18)
                        .build());
    }

    private void refresh() {
        if (warningsButton != null) {
            var state = EditorClient.state();
            warningsButton.setMessage(
                    Component.literal(
                            ClientText.tr("ui.alertes_de_trajet_03ec05")
                                    + (state.warningsEnabled()
                                            ? ClientText.tr("ui.activees_85f6e6")
                                            : ClientText.tr("ui.desactivees_8d7143"))));
            warningsButton.active =
                    state.editing() && !state.readOnly() && !state.busy() && !state.playing();
        }
        bindingButtons.forEach((key, button) -> {
            boolean conflict =
                    java.util.Arrays.stream(minecraft.options.keyMappings)
                            .anyMatch(other ->
                                    other != key
                                            && other.getCategory() != KeyMapping.Category.DEBUG
                                            && !key.isUnbound()
                                            && key.same(other));
            button.setMessage(
                    selected == key
                            ? Component.literal("> ... <").withStyle(ChatFormatting.YELLOW)
                            : key.getTranslatedKeyMessage()
                                    .copy()
                                    .withStyle(
                                            conflict ? ChatFormatting.RED : ChatFormatting.WHITE));
            button.setTooltip(
                    Tooltip.create(
                            Component.literal(
                                    selected == key
                                            ? ClientText.tr(
                                                    "ui.appuyer_sur_une_touche_ou_cliquer_avec_la_souris_d843fd")
                                            : conflict
                                                    ? ClientText.tr(
                                                            "ui.conflit_avec_une_autre_commande_minecraft_choisi_2e9e4c")
                                                    : ClientText.tr(
                                                            "ui.cliquer_pour_reassigner_touche_par_defaut_d64715"))));
        });
    }

    private void assign(KeyMapping key, InputConstants.Key input) {
        key.setKey(input);
        selected = null;
        KeyMapping.resetMapping();
        minecraft.options.save();
        refresh();
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (selected != null) {
            assign(
                    selected,
                    event.isEscape() ? InputConstants.UNKNOWN : InputConstants.getKey(event));
            return true;
        }
        return super.keyPressed(event);
    }

    @Override
    protected boolean scaledMouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (selected != null) {
            assign(selected, InputConstants.Type.MOUSE.getOrCreate(event.button()));
            return true;
        }
        return super.scaledMouseClicked(event, doubleClick);
    }

    @Override
    public void tick() {
        refresh();
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
        graphics.centeredText(
                font,
                Component.literal(ClientText.tr("ui.settings_title")),
                width / 2,
                13,
                0xFFF0F5FB);
        int left = Math.max(12, (width - 410) / 2);
        int tabWidth = (width - left * 2) / 2 - 2,
                selectedX = keysTab ? left + (width - left * 2) / 2 + 2 : left;
        graphics.fill(selectedX, 52, selectedX + tabWidth, 54, 0xFF73D8F4);
        if (keysTab) {
            int row = 0;
            for (var key : EditorClient.bindings())
                graphics.text(
                        font,
                        Component.literal(ClientText.tr(key.getName())),
                        left,
                        71 + row++ * 20,
                        0xFFBED2E3);
        } else {
            graphics.textWithWordWrap(
                    font,
                    Component.literal(
                            ClientText.tr(
                                    "ui.le_zoom_agrandit_ou_reduit_tout_boutons_texte_et_34eab8")),
                    left,
                    153,
                    width - left * 2,
                    0xFF9CACBF);
        }
        extractWidgets(graphics, mouseX, mouseY, delta);
    }
}
