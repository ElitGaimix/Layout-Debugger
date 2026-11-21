package fr.eligaimix.ltdr;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;

import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import fr.eligaimix.ltdr.command.LayoutCommands;
import fr.eligaimix.ltdr.playback.LayoutService;
import fr.eligaimix.ltdr.playback.EditorTools;
import fr.eligaimix.ltdr.network.EditorNetworking;
import fr.eligaimix.ltdr.piston.RedstonePistonController;
import fr.eligaimix.ltdr.preview.SeekSprint;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import java.util.IdentityHashMap;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class LayoutDebugger implements ModInitializer {
	public static final String MOD_ID = "layout-debugger";

	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);
	private static final Map<MinecraftServer, LayoutService> SERVICES = new IdentityHashMap<>();

	@Override
	public void onInitialize() {
		fr.eligaimix.ltdr.platform.EditorAccess.install(player ->
            ((net.fabricmc.fabric.api.permission.v1.PermissionContextOwner)(Object)player).checkPermission(id("use"),net.minecraft.server.permissions.PermissionLevel.GAMEMASTERS));
        net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents.ENTITY_LOAD.register((entity,level) -> {
            var service=SERVICES.get(level.getServer());if(service!=null)service.editor().recordSimulationItem(entity);
        });
        EditorTools.initialize();
		EditorNetworking.init((player, request) -> service(player.level().getServer()).editor().request(player, request));
		CommandRegistrationCallback.EVENT.register((dispatcher, context, environment) -> LayoutCommands.register(dispatcher));
		ServerLifecycleEvents.SERVER_STARTED.register(server -> SERVICES.put(server, new LayoutService(server)));
		ServerTickEvents.START_SERVER_TICK.register(server -> {
			LayoutService service = SERVICES.get(server);
			if (service != null) service.editor().prepareTick();
		});
		ServerTickEvents.END_SERVER_TICK.register(server -> {
			LayoutService service = SERVICES.get(server);
			if (service != null) service.editor().endServerTick();
		});
		ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
			LayoutService service = SERVICES.get(server);
			if (service != null) service.editor().recoverInventory(handler.player);
		});
		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
			LayoutService service = SERVICES.get(server);
			try { if (service != null) service.editor().disconnect(handler.player); }
			catch (RuntimeException exception) { LOGGER.error("Restauration de l'inventaire à la déconnexion : sauvegarde conservée", exception); }
		});
		ServerTickEvents.START_LEVEL_TICK.register(level -> {
			LayoutService service = SERVICES.get(level.getServer());
			if (service != null) { service.editor().startLevelTick(level); service.tick(level); }
		});
		ServerTickEvents.END_LEVEL_TICK.register(level -> {
			LayoutService service = SERVICES.get(level.getServer());
			if (service != null) service.editor().endLevelTick(level);
		});
		ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
			LayoutService service = SERVICES.get(server);
			try { if (service != null) service.shutdown(); }
			finally { RedstonePistonController.releaseAll(server); }
		});
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> { SERVICES.remove(server); SeekSprint.forget(server); });
		LOGGER.info("Layout Debugger: éditeur K /ld edit, alimentation virtuelle additive et mouvements vanilla.");
	}

	public static LayoutService service(MinecraftServer server) {
		LayoutService service = SERVICES.get(server);
		if (service == null) throw new IllegalStateException("Layout Debugger : serveur pas encore prêt.");
		return service;
	}

	public static Identifier id(String path) {
		return Identifier.fromNamespaceAndPath(MOD_ID, path);
	}
}
