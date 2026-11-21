package fr.eligaimix.ltdr.client.editor;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

/** A local UI scale: rendering, widgets, mouse input and tooltips use the same coordinates. */
public abstract class ScaledEditorScreen extends Screen {
    public void refreshLanguage(){rebuildWidgets();}

	private static final ThreadLocal<Float> EXTRACTION_SCALE = ThreadLocal.withInitial(() -> 1.0F);
	public static int logicalDimension(int actual) { return (int)Math.floor(actual / EXTRACTION_SCALE.get()); }
	private float uiScale = 1;
	protected ScaledEditorScreen(Component title) { super(title); }
	public final float uiScale() { return uiScale; }
	@Override protected final void init() {
		int actualWidth = minecraft.getWindow().getGuiScaledWidth(), actualHeight = minecraft.getWindow().getGuiScaledHeight();
		float fit = Math.min(actualWidth / 320.0F, actualHeight / 240.0F);
		uiScale = Math.max(0.25F,Math.min(EditorPreferences.zoomPercent() / 100.0F,fit));
		width = (int)Math.floor(actualWidth / uiScale); height = (int)Math.floor(actualHeight / uiScale);
		initScaled();
	}
	protected abstract void initScaled();
	protected abstract void extractScaledRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta);
	@Override public final void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
		int x = (int)(mouseX / uiScale), y = (int)(mouseY / uiScale);
		float previous = EXTRACTION_SCALE.get(); EXTRACTION_SCALE.set(uiScale);
		graphics.pose().pushMatrix(); graphics.pose().scale(uiScale,uiScale);
		try {
			extractScaledRenderState(graphics,x,y,delta);
			// Flush while the local pose is active; Screen's subsequent flush finds no queued tooltip.
			graphics.extractDeferredElements(x,y,delta);
		} finally { graphics.pose().popMatrix(); EXTRACTION_SCALE.set(previous); }
	}
	protected final void extractWidgets(GuiGraphicsExtractor graphics, int x, int y, float delta) { super.extractRenderState(graphics,x,y,delta); }
	private MouseButtonEvent local(MouseButtonEvent event) { return new MouseButtonEvent(event.x() / uiScale,event.y() / uiScale,event.buttonInfo()); }
	@Override public final boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) { return scaledMouseClicked(local(event),doubleClick); }
	protected boolean scaledMouseClicked(MouseButtonEvent event, boolean doubleClick) { return super.mouseClicked(event,doubleClick); }
	@Override public final boolean mouseReleased(MouseButtonEvent event) { return super.mouseReleased(local(event)); }
	@Override public final boolean mouseDragged(MouseButtonEvent event, double dx, double dy) { return super.mouseDragged(local(event),dx / uiScale,dy / uiScale); }
	@Override public final void mouseMoved(double x, double y) { super.mouseMoved(x / uiScale,y / uiScale); }
	@Override public final boolean mouseScrolled(double x, double y, double dx, double dy) { return scaledMouseScrolled(x / uiScale,y / uiScale,dx,dy); }
	protected boolean scaledMouseScrolled(double x, double y, double dx, double dy) { return super.mouseScrolled(x,y,dx,dy); }
}
