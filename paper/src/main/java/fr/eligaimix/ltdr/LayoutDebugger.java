package fr.eligaimix.ltdr;

import fr.eligaimix.ltdr.playback.LayoutService;

import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Paper entry adapter; engine and documents are shared with Fabric. */
public final class LayoutDebugger {
    public static final String MOD_ID = "layout-debugger";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);
    public static LayoutService active;

    private LayoutDebugger() {}

    public static LayoutService service(MinecraftServer server) {
        if (active == null) throw new IllegalStateException("Layout Debugger indisponible.");
        return active;
    }

    public static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath(MOD_ID, path);
    }
}
