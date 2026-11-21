package fr.eligaimix.ltdr.mixin;

import fr.eligaimix.ltdr.preview.ReplayVisibility;
import net.minecraft.network.protocol.Packet;
import net.minecraft.server.network.ServerCommonPacketListenerImpl;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ServerCommonPacketListenerImpl.class)
public abstract class ReplayPacketsMixin {
	@Inject(method = "send(Lnet/minecraft/network/protocol/Packet;Lio/netty/channel/ChannelFutureListener;)V", at = @At("HEAD"), cancellable = true)
	private void layoutDebugger$hideReplay(Packet<?> packet, io.netty.channel.ChannelFutureListener listener, CallbackInfo callback) {
		if (listener == null && (Object)this instanceof ServerGamePacketListenerImpl handler && ReplayVisibility.suppress(handler.player,packet)) {
			callback.cancel();
		}
	}
}
