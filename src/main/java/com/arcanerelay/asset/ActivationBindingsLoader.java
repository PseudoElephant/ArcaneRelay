package com.arcanerelay.asset;

import javax.annotation.Nonnull;
import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

/**
 * Optional loader to populate an {@link ActivationBindingRegistry} from a JSON stream.
 * The registry itself does not reference any file; callers pass the stream (e.g. from a resource or path).
 */
public final class ActivationBindingsLoader {

    private ActivationBindingsLoader() {
    }

    /**
     * Loads bindings from a JSON stream into the binding registry.
     * Expected format: {@code { "bindings": [ { "pattern": "...", "activation": "..." }, ... ], "default": "..." } }
     *
     * @param registry the binding registry to add bindings to
     * @param inputStream JSON input (e.g. from {@code getClass().getResourceAsStream("/path.json")}); not closed by this method
     */
    public static void loadFromStream(@Nonnull ActivationBindingRegistry registry, @Nonnull InputStream inputStream) {
        StringBuilder sb = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line).append('\n');
            }
        } catch (Exception e) {
            return;
        }
        String json = sb.toString();
        int bindingsStart = json.indexOf("\"bindings\"");
        if (bindingsStart >= 0) {
            int arrayStart = json.indexOf('[', bindingsStart);
            if (arrayStart >= 0) {
                parseBindingEntries(registry, json, arrayStart);
            }
        }
        int defaultStart = json.indexOf("\"default\"");
        if (defaultStart >= 0) {
            int colon = json.indexOf(':', defaultStart);
            if (colon >= 0) {
                int quote = json.indexOf('"', colon);
                if (quote >= 0) {
                    int end = json.indexOf('"', quote + 1);
                    if (end > quote + 1) {
                        registry.setDefaultActivationId(json.substring(quote + 1, end).trim());
                    }
                }
            }
        }
    }

    private static void parseBindingEntries(ActivationBindingRegistry registry, String json, int arrayStart) {
        int depth = 1;
        int i = arrayStart + 1;
        while (i < json.length() && depth > 0) {
            char c = json.charAt(i);
            if (c == '[' || c == '{') depth++;
            else if (c == ']' || c == '}') depth--;
            else if (depth == 1 && c == '{') {
                int objEnd = findMatchingBrace(json, i);
                if (objEnd > i) {
                    parseOneBinding(registry, json.substring(i, objEnd + 1));
                }
                i = objEnd;
            }
            i++;
        }
    }

    private static int findMatchingBrace(String json, int start) {
        int depth = 1;
        for (int i = start + 1; i < json.length(); i++) {
            char c = json.charAt(i);
            if (c == '{') depth++;
            else if (c == '}') {
                depth--;
                if (depth == 0) return i;
            }
        }
        return -1;
    }

    private static void parseOneBinding(ActivationBindingRegistry registry, String objJson) {
        String pattern = extractString(objJson, "pattern");
        String activation = extractString(objJson, "activation");
        if (pattern != null && activation != null) {
            registry.registerBinding(pattern.trim(), activation.trim());
        }
    }

    private static String extractString(String json, String key) {
        String search = "\"" + key + "\"";
        int idx = json.indexOf(search);
        if (idx < 0) return null;
        int colon = json.indexOf(':', idx);
        if (colon < 0) return null;
        int quote = json.indexOf('"', colon);
        if (quote < 0) return null;
        int end = json.indexOf('"', quote + 1);
        if (end <= quote + 1) return null;
        return json.substring(quote + 1, end);
    }
}
