package fr.eligaimix.ltdr.i18n;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.regex.Pattern;

/** Shared text catalog. Legacy string messages are localized at presentation boundaries. */
public final class TextCatalog {
    private static final Map<String, String> FR = load("fr_fr"), EN = load("en_us");
    private static final Map<String, String> DIRECT = new HashMap<>();

    private record Template(String key, Pattern pattern) {}

    private static final List<Template> TEMPLATES = new ArrayList<>();

    static {
        FR.forEach((key, value) -> {
            if (!EN.containsKey(key))
                throw new IllegalStateException("Missing English text: " + key);
            if (value.contains("{0}")) {
                StringBuilder regex = new StringBuilder();
                int offset = 0;
                var matcher = Pattern.compile("\\{[0-9]+\\}").matcher(value);
                while (matcher.find()) {
                    regex.append(Pattern.quote(value.substring(offset, matcher.start())))
                            .append("(.+?)");
                    offset = matcher.end();
                }
                regex.append(Pattern.quote(value.substring(offset)));
                TEMPLATES.add(new Template(key, Pattern.compile(regex.toString(), Pattern.DOTALL)));
            } else DIRECT.put(value, EN.get(key));
        });
        TEMPLATES.sort(
                Comparator.comparingInt((Template t) -> FR.get(t.key()).length()).reversed());
    }

    private TextCatalog() {}

    private static Map<String, String> load(String locale) {
        try (var input =
                TextCatalog.class.getResourceAsStream(
                        "/assets/layout-debugger/lang/" + locale + ".json")) {
            if (input == null)
                throw new IllegalStateException("Missing language resource " + locale);
            return Map.copyOf(
                    new Gson()
                            .fromJson(
                                    new InputStreamReader(input, StandardCharsets.UTF_8),
                                    new TypeToken<Map<String, String>>() {}.getType()));
        } catch (java.io.IOException exception) {
            throw new IllegalStateException(exception);
        }
    }

    public static String normalize(String locale) {
        return "en_us".equals(locale) ? "en_us" : "fr_fr";
    }

    public static String text(String locale, String key, Object... args) {
        String value = (normalize(locale).equals("en_us") ? EN : FR).getOrDefault(key, key);
        for (int i = 0; i < args.length; i++)
            value = value.replace("{" + i + "}", String.valueOf(args[i]));
        return value;
    }

    public static String message(String locale, String raw) {
        return translate(normalize(locale), raw, 0);
    }

    private static String translate(String locale, String raw, int depth) {
        if (!locale.equals("en_us") || raw == null || raw.length() > 4096 || depth > 5) return raw;
        String direct = DIRECT.get(raw);
        if (direct != null) return direct;
        String prefix = "[Layout Debugger] ";
        if (raw.startsWith(prefix))
            return prefix + translate(locale, raw.substring(prefix.length()), depth + 1);
        for (var template : TEMPLATES) {
            var match = template.pattern().matcher(raw);
            if (!match.matches()) continue;
            Object[] args = new Object[match.groupCount()];
            for (int i = 0; i < args.length; i++)
                args[i] =
                        template.key().endsWith(".nested")
                                ? translate(locale, match.group(i + 1), depth + 1)
                                : match.group(i + 1);
            return text(locale, template.key(), args);
        }
        return raw;
    }

    public static Set<String> keys() {
        return FR.keySet();
    }
}
