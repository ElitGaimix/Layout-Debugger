package fr.eligaimix.ltdr.mixin;

import fr.eligaimix.ltdr.client.editor.ScaledEditorScreen;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Tooltip wrapping and screen-edge placement must use the local logical viewport. */
@Mixin(GuiGraphicsExtractor.class)
public abstract class ScaledGuiGraphicsMixin {
	@Inject(method = {"guiWidth", "guiHeight"}, at = @At("RETURN"), cancellable = true)
	private void layoutDebugger$localViewport(CallbackInfoReturnable<Integer> callback) {
		callback.setReturnValue(ScaledEditorScreen.logicalDimension(callback.getReturnValueI()));
	}
}
