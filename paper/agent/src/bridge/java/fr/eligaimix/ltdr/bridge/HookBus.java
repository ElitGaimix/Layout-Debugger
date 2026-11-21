package fr.eligaimix.ltdr.bridge;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/** Bootstrap-visible ABI. No references to Minecraft, Bukkit, Fabric, ASM or plugin classes. */
public final class HookBus {
    public static final int VERSION = 1;

    public static int version() {
        return VERSION;
    }

    public static volatile Handler handler;
    public static final Set<String> transformed = ConcurrentHashMap.newKeySet();
    public static volatile String failure;

    private HookBus() {}

    public interface Handler {
        boolean power(Object level, Object pos);

        void serverTick(Object server, boolean end);

        void levelTick(Object level, boolean end);

        boolean suppress(Object listener, Object packet, Object completion);

        void construction(Object listener, boolean end);

        void changed(Object level, Object pos);

        Object quiet(Object source);

        void sprintFinished(Object manager);
    }

    public static boolean power(Object level, Object pos) {
        var h = handler;
        return h != null && h.power(level, pos);
    }

    public static void serverStart(Object server) {
        var h = handler;
        if (h != null) h.serverTick(server, false);
    }

    public static void serverEnd(Object server) {
        var h = handler;
        if (h != null) h.serverTick(server, true);
    }

    public static void levelStart(Object level) {
        var h = handler;
        if (h != null) h.levelTick(level, false);
    }

    public static void levelEnd(Object level) {
        var h = handler;
        if (h != null) h.levelTick(level, true);
    }

    public static boolean suppress(Object listener, Object packet, Object completion) {
        var h = handler;
        return h != null && h.suppress(listener, packet, completion);
    }

    public static void constructionStart(Object listener) {
        var h = handler;
        if (h != null) h.construction(listener, false);
    }

    public static void constructionEnd(Object listener) {
        var h = handler;
        if (h != null) h.construction(listener, true);
    }

    public static void changed(boolean success, Object level, Object pos) {
        var h = handler;
        if (success && h != null) h.changed(level, pos);
    }

    public static Object quiet(Object source) {
        var h = handler;
        return h == null ? source : h.quiet(source);
    }

    public static void sprintFinished(Object manager) {
        var h = handler;
        if (h != null) h.sprintFinished(manager);
    }
}
