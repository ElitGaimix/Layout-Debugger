package fr.eligaimix.ltdr.gametest;

import fr.eligaimix.ltdr.client.editor.TimelineScreen;
import fr.eligaimix.ltdr.client.editor.AnimationLibraryScreen;
import fr.eligaimix.ltdr.client.editor.EditorPreferences;
import fr.eligaimix.ltdr.client.editor.ScaledEditorScreen;
import fr.eligaimix.ltdr.client.editor.EditorSettingsScreen;
import fr.eligaimix.ltdr.client.editor.EditorClient;
import fr.eligaimix.ltdr.network.EditorState;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.screenshot.TestScreenshotOptions;
import net.minecraft.client.gui.components.AbstractWidget;

/** Real Minecraft rendering with synthetic server data; no world or network mutations. */
public final class TimelineClientGameTest implements FabricClientGameTest {
	@Override
	public void runTest(ClientGameTestContext context) {
		if (Boolean.parseBoolean(System.getenv("LAYOUT_DEBUGGER_PAPER_ONLY"))) return;
		Path output = context.computeOnClient(client -> client.gameDirectory.toPath().toAbsolutePath()
			.resolve("../../ui-qa").normalize());
		try { Files.createDirectories(output); }
		catch (IOException exception) { throw new UncheckedIOException(exception); }
		context.runOnClient(client -> client.options.guiScale().set(1));
		for (int[] size : new int[][]{{320, 240}, {854, 480}}) {
			context.getInput().resizeWindow(size[0], size[1]);
			context.setScreen(TimelineScreen::new);
			context.runOnClient(client -> {
				if (!(client.gui.screen() instanceof TimelineScreen screen)) throw new AssertionError("Timeline did not open");
				screen.receiveState(sample());
				checkWidgetBounds(screen);
			});
			context.waitTick();
			context.takeScreenshot(TestScreenshotOptions.of("timeline-" + size[0] + "x" + size[1])
				.disableCounterPrefix().withSize(size[0], size[1]).withDestinationDir(output));
			click(context,"Animations");
			context.waitForScreen(AnimationLibraryScreen.class);
			context.takeScreenshot(TestScreenshotOptions.of("animations-" + size[0] + "x" + size[1]).disableCounterPrefix().withDestinationDir(output));
			click(context,"Retour");
			context.runOnClient(client -> ((TimelineScreen)client.gui.screen()).receiveState(sample()));
			click(context, "Param.");
			context.waitForScreen(EditorSettingsScreen.class);
			context.takeScreenshot(TestScreenshotOptions.of("settings-" + size[0] + "x" + size[1]).disableCounterPrefix().withDestinationDir(output));
			click(context, "Touches");
			context.takeScreenshot(TestScreenshotOptions.of("bindings-" + size[0] + "x" + size[1]).disableCounterPrefix().withDestinationDir(output));
			click(context, EditorClient.OPEN.getTranslatedKeyMessage().getString());
			context.getInput().pressKey(EditorClient.NEXT);
			context.runOnClient(client -> {
				if (!EditorClient.OPEN.same(EditorClient.NEXT)) throw new AssertionError("Settings did not rebind a real Minecraft key");
				EditorClient.OPEN.setKey(EditorClient.OPEN.getDefaultKey()); net.minecraft.client.KeyMapping.resetMapping(); client.options.save();
			});
			click(context, "Interface");
			context.runOnClient(client -> {
				var screen = (ScaledEditorScreen)client.gui.screen();
				var slider = screen.children().stream().filter(net.minecraft.client.gui.components.AbstractSliderButton.class::isInstance).map(net.minecraft.client.gui.components.AbstractSliderButton.class::cast).findFirst().orElseThrow();
				for (int i = 0; i < 4; i++) screen.mouseScrolled((slider.getX()+slider.getWidth()/2.0)*screen.uiScale(),(slider.getY()+9)*screen.uiScale(),0,-1);
				if (EditorPreferences.zoomPercent() != 80) throw new AssertionError("Mouse wheel did not adjust the zoom slider");
			});
			click(context, "Retour");
			context.waitForScreen(TimelineScreen.class);
			context.runOnClient(client -> { var screen = (TimelineScreen) client.gui.screen(); screen.receiveState(sample()); checkWidgetBounds(screen); if (Math.abs(screen.uiScale()-0.8F)>0.001F) throw new AssertionError("Zoom did not scale the entire interface"); });
			context.takeScreenshot(TestScreenshotOptions.of("compact-" + size[0] + "x" + size[1]).disableCounterPrefix().withDestinationDir(output));
			// Widget coordinates must follow the scaled interface, not only its drawing.
			click(context, "Ordres"); click(context, "Grille");
			context.runOnClient(client -> EditorPreferences.setZoomPercent(150));
			context.setScreen(TimelineScreen::new);
			context.runOnClient(client -> {
				var screen = (TimelineScreen)client.gui.screen(); screen.receiveState(sample()); checkWidgetBounds(screen);
				float expected = Math.min(1.5F,Math.min(size[0]/320.0F,size[1]/240.0F));
				if (Math.abs(screen.uiScale()-expected)>0.001F) throw new AssertionError("Large zoom did not fit the screen");
			});
			context.takeScreenshot(TestScreenshotOptions.of("zoom-large-" + size[0] + "x" + size[1]).disableCounterPrefix().withDestinationDir(output));
			click(context,"Param.");
			context.runOnClient(client -> {
				var tabs = client.gui.screen().children().stream().filter(net.minecraft.client.gui.components.Button.class::isInstance).map(net.minecraft.client.gui.components.Button.class::cast).toList();
				if (tabs.stream().filter(b -> b.getMessage().getString().equals("Interface")).findFirst().orElseThrow().active) throw new AssertionError("Active Interface tab was not selected");
			});
			click(context,"Touches");
			context.runOnClient(client -> {
				var tabs = client.gui.screen().children().stream().filter(net.minecraft.client.gui.components.Button.class::isInstance).map(net.minecraft.client.gui.components.Button.class::cast).toList();
				if (tabs.stream().filter(b -> b.getMessage().getString().equals("Touches")).findFirst().orElseThrow().active) throw new AssertionError("Active Keys tab was not selected");
			});
			context.runOnClient(client -> EditorPreferences.setZoomPercent(100));
			context.setScreen(TimelineScreen::new);
			context.runOnClient(client -> ((TimelineScreen)client.gui.screen()).receiveState(sample()));
			click(context, "Ordres");
			context.takeScreenshot(TestScreenshotOptions.of("orders-" + size[0] + "x" + size[1])
				.disableCounterPrefix().withSize(size[0], size[1]).withDestinationDir(output));
			// This must update immediately, even when the server simulation is frozen.
			click(context, "Grille");
			context.runOnClient(client -> {
				TimelineScreen screen = (TimelineScreen) client.gui.screen();
				EditorState normal = sample();
				screen.receiveState(new EditorState(normal.sequence(), normal.page(), normal.targetPage(), false, false,
					"Visionnage · inventaire conservé · aucune édition", normal.sequences(), normal.pistons(), normal.actions(),
					normal.radius(), normal.mode(), true, true, normal.endPage(), false));
				checkWidgetBounds(screen);
				for (var child : screen.children()) {
					if (child instanceof AbstractWidget widget && widget.getMessage().getString().equals("Outils") && widget.active) {
						throw new AssertionError("Tools must be disabled in read-only playback");
					}
				}
			});
			context.takeScreenshot(TestScreenshotOptions.of("playback-" + size[0] + "x" + size[1])
				.disableCounterPrefix().withSize(size[0], size[1]).withDestinationDir(output));
			context.runOnClient(client -> {
				TimelineScreen screen = (TimelineScreen) client.gui.screen();
				EditorState normal = sample();
				screen.receiveState(new EditorState(normal.sequence(), 0, 3, true, false, "Calcul en cours", normal.sequences(),
					normal.pistons(), normal.actions(), normal.radius(), normal.mode(), true, false, normal.endPage(), true));
			});
			context.takeScreenshot(TestScreenshotOptions.of("seek-" + size[0] + "x" + size[1])
				.disableCounterPrefix().withSize(size[0], size[1]).withDestinationDir(output));
		}
		context.setScreen(() -> null);
	}

	static void click(ClientGameTestContext context, String text) {
		context.runOnClient(client -> {
			var screen = client.gui.screen();
			var widget = screen.children().stream().filter(net.minecraft.client.gui.components.Button.class::isInstance).map(net.minecraft.client.gui.components.Button.class::cast)
				.filter(w -> w.getMessage().getString().equals(text) && w.active).findFirst().orElseThrow(() -> new AssertionError("Missing active button: " + text));
			double scale = screen instanceof ScaledEditorScreen scaled ? scaled.uiScale() : 1;
			if (!screen.mouseClicked(new net.minecraft.client.input.MouseButtonEvent((widget.getX()+widget.getWidth()/2.0)*scale,(widget.getY()+widget.getHeight()/2.0)*scale,new net.minecraft.client.input.MouseButtonInfo(0,0)),false)) throw new AssertionError("Scaled button did not respond: " + text);
		});
	}

	private static void checkWidgetBounds(TimelineScreen screen) {
		var widgets = screen.children().stream().filter(AbstractWidget.class::isInstance).map(AbstractWidget.class::cast).toList();
		for (AbstractWidget widget : widgets) {
			if (widget.getX() < 0 || widget.getY() < 0 || widget.getWidth() <= 0 || widget.getHeight() <= 0
					|| widget.getX() + widget.getWidth() > screen.width || widget.getY() + widget.getHeight() > screen.height) {
				throw new AssertionError("Widget outside screen: " + widget.getMessage().getString());
			}
		}
		for (int first = 0; first < widgets.size(); first++) {
			AbstractWidget left = widgets.get(first);
			for (int second = first + 1; second < widgets.size(); second++) {
				AbstractWidget right = widgets.get(second);
				if (left.getX() < right.getX() + right.getWidth() && left.getX() + left.getWidth() > right.getX()
						&& left.getY() < right.getY() + right.getHeight() && left.getY() + left.getHeight() > right.getY()) {
					throw new AssertionError("Overlapping widgets: " + left.getMessage().getString() + " / " + right.getMessage().getString());
				}
			}
		}
	}

	private static EditorState sample() {
		List<EditorState.TargetView> pistons = new ArrayList<>();
		for (int index = 1; index <= 14; index++) {
			pistons.add(new EditorState.TargetView("p" + index, 100 + index, 80, 100 + index * 2,
				index == 3, index == 1 || index == 3, index == 3));
		}
		return new EditorState("Double_extension", 3, 3, false, false,
			"Page 3 · signaux appliqués ; simulation en pause. Les pistons déplacés conservent leur identité.",
			List.of("Double_extension", "Triple_extension", "Porte_3x3"), pistons,
			List.of(new EditorState.ActionView(0, 10, "p1", true, false),
				new EditorState.ActionView(2, 10, "p1", false, false),
				new EditorState.ActionView(2, 20, "p2", true, true),
				new EditorState.ActionView(4, 10, "p2", false, true),
				new EditorState.ActionView(4, 20, "p3", true, false),
				new EditorState.ActionView(8, 10, "p3", false, false)), 16, "pulse", true);
	}
}
