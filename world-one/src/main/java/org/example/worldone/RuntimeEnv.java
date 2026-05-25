package org.example.worldone;

import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Host runtime environment helpers.
 *
 * <p>Manifest entries (skills/tools) may declare optional {@code env}. When absent, the entry
 * is visible in all runtime environments. When present, only the matching env is exposed to LLM/UI.
 */
public final class RuntimeEnv {

    public static final String DEFAULT = "production";

    private RuntimeEnv() {}

    public static String normalize(String env) {
        if (env == null || env.isBlank()) return DEFAULT;
        String v = env.trim().toLowerCase(Locale.ROOT);
        return switch (v) {
            case "production", "staging", "draft" -> v;
            default -> DEFAULT;
        };
    }

    /** Whether a manifest entry is visible for the given runtime env. */
    public static boolean matchesManifestEnv(Map<String, Object> entry, String runtimeEnv) {
        if (entry == null || entry.isEmpty()) return true;
        String current = normalize(runtimeEnv);

        Object envsObj = entry.get("envs");
        if (envsObj instanceof List<?> envs && !envs.isEmpty()) {
            for (Object o : envs) {
                if (o != null && current.equals(normalize(o.toString()))) return true;
            }
            return false;
        }

        Object envObj = entry.get("env");
        if (envObj == null) return true;
        String declared = envObj.toString();
        if (declared.isBlank()) return true;
        return current.equals(normalize(declared));
    }
}
