package fr.eligaimix.ltdr.client.editor;

import fr.eligaimix.ltdr.i18n.TextCatalog;

/** Local mod language, independent of Minecraft's global language and GUI scale. */
public final class ClientText {
    private ClientText() {}

    public static String tr(String key, Object... args) {
        return TextCatalog.text(EditorPreferences.language(), key, args);
    }

    public static String message(String raw) {
        return TextCatalog.message(EditorPreferences.language(), raw);
    }
}
