package fr.eligaimix.ltdr.i18n;

import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerPlayer;

import java.util.*;

/** Per-connection preference, supplied by the optional client; never grants permissions. */
public final class ServerLanguage {
    private static final Map<UUID, String> LOCALES = new HashMap<>();

    private ServerLanguage() {}

    public static void set(ServerPlayer player, String locale) {
        if (!Set.of("fr_fr", "en_us").contains(locale))
            throw new IllegalArgumentException("Unsupported language");
        LOCALES.put(player.getUUID(), locale);
    }

    public static void forget(ServerPlayer player) {
        LOCALES.remove(player.getUUID());
    }

    public static String text(ServerPlayer player, String raw) {
        return TextCatalog.message(LOCALES.getOrDefault(player.getUUID(), "fr_fr"), raw);
    }

    public static MutableComponent component(ServerPlayer player, String raw) {
        return Component.literal(text(player, raw));
    }
}
