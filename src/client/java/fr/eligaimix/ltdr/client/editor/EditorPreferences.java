package fr.eligaimix.ltdr.client.editor;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import fr.eligaimix.ltdr.LayoutDebugger;

import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

/** Local presentation preferences; key assignments stay in Minecraft's options. */
public final class EditorPreferences {
    private static final Path FILE =
            FabricLoader.getInstance().getConfigDir().resolve("layout-debugger-client.json");
    private static int zoomPercent = 100;
    private static String language = "fr_fr";
    private static boolean bindingsMigrated;

    static {
        if (Files.exists(FILE))
            try {
                var json = JsonParser.parseString(Files.readString(FILE)).getAsJsonObject();
                if (json.has("language"))
                    language =
                            fr.eligaimix.ltdr.i18n.TextCatalog.normalize(
                                    json.get("language").getAsString());
                if (json.has("zoomPercent"))
                    zoomPercent = normalize(json.get("zoomPercent").getAsInt());
                bindingsMigrated =
                        json.has("bindingsMigrated") && json.get("bindingsMigrated").getAsBoolean();
            } catch (IOException | RuntimeException exception) {
                LayoutDebugger.LOGGER.warn("Lecture des préférences client", exception);
            }
    }

    private EditorPreferences() {}

    public static String language() {
        return language;
    }

    public static void setLanguage(String value) {
        language = fr.eligaimix.ltdr.i18n.TextCatalog.normalize(value);
        save();
    }

    public static int zoomPercent() {
        return zoomPercent;
    }

    public static void setZoomPercent(int percent) {
        zoomPercent = normalize(percent);
        save();
    }

    public static boolean bindingsMigrated() {
        return bindingsMigrated;
    }

    public static void markBindingsMigrated() {
        bindingsMigrated = true;
        save();
    }

    private static int normalize(int value) {
        return Math.clamp(Math.round(value / 5.0F) * 5, 50, 200);
    }

    private static void save() {
        Path temporary = null;
        try {
            Files.createDirectories(FILE.getParent());
            var json = new JsonObject();
            json.addProperty("language", language);
            json.addProperty("zoomPercent", zoomPercent);
            json.addProperty("bindingsMigrated", bindingsMigrated);
            temporary = Files.createTempFile(FILE.getParent(), "layout-debugger-client-", ".tmp");
            Files.writeString(
                    temporary,
                    new com.google.gson.GsonBuilder().setPrettyPrinting().create().toJson(json));
            Files.move(temporary, FILE, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException exception) {
            LayoutDebugger.LOGGER.warn("Enregistrement des préférences client", exception);
        } finally {
            if (temporary != null)
                try {
                    Files.deleteIfExists(temporary);
                } catch (IOException ignored) {
                }
        }
    }
}
