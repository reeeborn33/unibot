package org.example.aipp.frontend;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.*;
import java.time.Duration;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * Reusable scaffolding for AIPP frontend guard tests. Each AIPP repo subclasses
 * the concrete {@code WidgetUrlResolvesTest} / {@code WidgetNoHostCouplingTest}
 * to plug in its own base URL + static dir.
 *
 * <p>This class is intentionally framework-free — no JUnit annotations — so
 * downstream test classes can be plain JUnit 5 in the AIPP repo.
 */
public final class WidgetGuardSupport {

    private static final ObjectMapper JSON = new ObjectMapper();
    private static final HttpClient HTTP = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(2)).build();

    private WidgetGuardSupport() { }

    // ── Widget URL resolves ──────────────────────────────────────────────────

    public record UrlCheckResult(boolean skipped, List<String> failures) { }

    /** Returns failures (empty when all good). Skips if AIPP not reachable. */
    public static UrlCheckResult checkAllWidgetUrls(String baseUrl) {
        List<String> failures = new ArrayList<>();
        JsonNode widgets;
        try {
            widgets = httpJson(baseUrl + "/api/widgets");
        } catch (Exception e) {
            return new UrlCheckResult(true, List.of("AIPP not reachable at " + baseUrl + ": " + e.getMessage()));
        }
        if (widgets == null) return new UrlCheckResult(true, List.of("/api/widgets returned null"));

        for (JsonNode w : widgets.path("widgets")) {
            String url = w.path("render").path("url").asText("");
            if (url.isBlank()) continue;
            String full = url.startsWith("http") ? url : baseUrl + url;
            try {
                HttpRequest req = HttpRequest.newBuilder(URI.create(full))
                        .timeout(Duration.ofSeconds(3)).GET().build();
                HttpResponse<String> resp = HTTP.send(req, HttpResponse.BodyHandlers.ofString());
                if (resp.statusCode() != 200) {
                    failures.add(full + " → HTTP " + resp.statusCode());
                    continue;
                }
                String kind = w.path("render").path("kind").asText("");
                String body = resp.body().stripLeading().toLowerCase(Locale.ROOT);
                boolean looksOk = "esm".equals(kind)
                        ? (body.startsWith("export ") || body.contains("export function mount"))
                        : (body.startsWith("<!doctype html") || body.startsWith("<html"));
                if (!looksOk) {
                    failures.add(full + " → body does not look like " + ("esm".equals(kind) ? "an ES module" : "HTML")
                            + " (first 60 chars: '"
                            + resp.body().substring(0, Math.min(60, resp.body().length())) + "')");
                }
            } catch (Exception e) {
                failures.add(full + " → " + e.getMessage());
            }
        }
        return new UrlCheckResult(false, failures);
    }

    // ── Widget host-coupling scan ────────────────────────────────────────────

    /**
     * Forbidden patterns inside any widget asset:
     * <ol>
     *   <li>{@code parent.X}</li>
     *   <li>{@code window.top} access</li>
     *   <li>{@code top.X} access</li>
     *   <li>fetch / XHR to a path under {@code /api/} that is NOT prefixed with
     *       this AIPP's own base URL.  Heuristic: any string literal {@code "/api/...
     *       or 'api/...'} is suspect; route app calls through {@code hostApi.appProxyUrl(path)}
     *       or {@code hostApi.proxyTool(name, args)}.</li>
     * </ol>
     */
    public static List<String> scanWidgetCoupling(Path widgetsRoot) {
        if (!Files.isDirectory(widgetsRoot)) return List.of();
        List<String> hits = new ArrayList<>();

        Pattern parentBad = Pattern.compile("\\bparent\\s*\\.\\s*([A-Za-z_$][\\w$]*)");
        Pattern windowTop = Pattern.compile("\\bwindow\\s*\\.\\s*top\\b");
        Pattern topAccess = Pattern.compile("(?<![A-Za-z_$.])top\\s*\\.(?!toString\\b)");
        Pattern apiPath   = Pattern.compile("[\"'`]/api/[A-Za-z0-9_./{}-]+[\"'`]");

        try (Stream<Path> files = Files.walk(widgetsRoot)) {
            files.filter(Files::isRegularFile)
                 .filter(p -> {
                     String n = p.getFileName().toString().toLowerCase(Locale.ROOT);
                     return n.endsWith(".html") || n.endsWith(".js") || n.endsWith(".mjs");
                 })
                 .forEach(p -> {
                     String src;
                     try { src = Files.readString(p); }
                     catch (Exception e) { return; }

                     scanFor(p, src, parentBad,
                            m -> "uses `parent." + m.group(1) + "` (use hostApi instead)",
                             hits, widgetsRoot);
                     scanFor(p, src, windowTop,
                             m -> "uses `window.top` (host-coupling forbidden)",
                             hits, widgetsRoot);
                     scanFor(p, src, topAccess,
                             m -> "uses `top.<x>` (host-coupling forbidden)",
                             hits, widgetsRoot);
                    // Bare /api/ string — likely a host call. Widgets should go
                    // through hostApi so the Host can proxy the owning AIPP.
                     scanFor(p, src, apiPath,
                            m -> "contains bare host-style API path `" + m.group() + "`; route via hostApi.appProxyUrl/proxyTool instead",
                             hits, widgetsRoot);
                 });
        } catch (Exception e) {
            hits.add("scan error: " + e.getMessage());
        }
        return hits;
    }

    private static void scanFor(Path file, String src, Pattern p,
                                java.util.function.Function<Matcher, String> msg,
                                List<String> out, Path root) {
        Matcher m = p.matcher(src);
        while (m.find()) {
            int line = lineOf(src, m.start());
            out.add(root.relativize(file) + ":" + line + " — " + msg.apply(m));
        }
    }

    private static int lineOf(String src, int idx) {
        int line = 1;
        for (int i = 0; i < idx && i < src.length(); i++) {
            if (src.charAt(i) == '\n') line++;
        }
        return line;
    }

    private static JsonNode httpJson(String url) throws Exception {
        HttpRequest req = HttpRequest.newBuilder(URI.create(url))
                .timeout(Duration.ofSeconds(2)).GET().build();
        HttpResponse<String> resp = HTTP.send(req, HttpResponse.BodyHandlers.ofString());
        if (resp.statusCode() != 200) return null;
        return JSON.readTree(resp.body());
    }
}
