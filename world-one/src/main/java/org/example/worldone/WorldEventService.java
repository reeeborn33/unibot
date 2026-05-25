package org.example.worldone;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.example.worldone.db.WorldEventEntity;
import org.example.worldone.db.WorldEventRepository;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class WorldEventService {
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {};

    private final WorldEventRepository repo;
    private final AippRegistry registry;
    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    public WorldEventService(WorldEventRepository repo, AippRegistry registry) {
        this.repo = repo;
        this.registry = registry;
    }

    @Transactional
    public Map<String, Object> createParameterMissingEvent(Map<String, Object> toolResult,
                                                           String toolName,
                                                           Map<String, Object> originalArgs) {
        return createParameterMissingEvent(toolResult, toolName, originalArgs, "");
    }

    @Transactional
    public Map<String, Object> createParameterMissingEvent(Map<String, Object> toolResult,
                                                           String toolName,
                                                           Map<String, Object> originalArgs,
                                                           String uiSessionId) {
        Map<String, Object> args = originalArgs == null ? Map.of() : originalArgs;
        String worldId = str(firstNonBlank(toolResult.get("world_id"), args.get("world_id"), args.get("session_id")));
        String templateId = str(firstNonBlank(toolResult.get("template_id"), args.get("template_id"), args.get("decision_id"), capabilityId(toolResult)));
        String scopeId = str(firstNonBlank(toolResult.get("scope_id"), args.get("scope_id")));
        String env = str(firstNonBlank(toolResult.get("env"), args.get("env"), "production"));
        List<String> missing = stringList(firstNonBlank(toolResult.get("missing_params"), toolResult.get("missing_parameters")));
        List<Map<String, Object>> chainSnapshot = mergeChainSnapshots(
                mapList(args.get("__chain_snapshot")),
                mapList(toolResult.get("chain")));

        Map<String, Object> existingParams = new LinkedHashMap<>(mapValue(firstNonBlank(args.get("parameters"), args.get("params"))));
        existingParams.putAll(mapValue(toolResult.get("provided_parameters")));
        Map<String, Object> resumeArgs = new LinkedHashMap<>(args);
        if (!worldId.isBlank()) resumeArgs.put("world_id", worldId);
        if (!templateId.isBlank()) resumeArgs.put("template_id", templateId);
        if (!env.isBlank()) resumeArgs.put("env", env);
        if (!scopeId.isBlank()) resumeArgs.put("scope_id", scopeId);
        resumeArgs.put("parameters", existingParams);

        Map<String, Object> businessData = new LinkedHashMap<>();
        businessData.put("missing_parameters", missing);
        businessData.put("upstream_context_ref", upstreamContextRef(worldId, env, templateId, scopeId));
        businessData.put("resume_tool", toolName == null || toolName.isBlank() ? "manual_decision_execute" : toolName);
        businessData.put("resume_args", resumeArgs);
        businessData.put("provided_parameters", existingParams);
        if (!chainSnapshot.isEmpty()) businessData.put("chain_snapshot", chainSnapshot);

        Map<String, Object> tags = new LinkedHashMap<>();
        if (!templateId.isBlank()) tags.put("decision", templateId);
        if (!worldId.isBlank()) tags.put("world_id", worldId);
        if (!env.isBlank()) tags.put("env", env);

        Map<String, Object> source = new LinkedHashMap<>();
        source.put("kind", "decision");
        source.put("id", templateId);

        Map<String, Object> widget = resolveWidget(toolResult, missing, templateId);

        WorldEventEntity e = new WorldEventEntity();
        Instant now = Instant.now();
        e.setId("evt_" + UUID.randomUUID().toString().replace("-", ""));
        e.setType("parameter_missing");
        e.setStatus("pending");
        e.setWorldId(worldId);
        e.setScopeId(scopeId);
        e.setUiSessionId(uiSessionId == null ? "" : uiSessionId);
        e.setSourceJson(writeJson(source));
        e.setBusinessDataJson(writeJson(businessData));
        e.setTagsJson(writeJson(tags));
        e.setWidgetJson(writeJson(widget));
        e.setCreatedAt(now);
        e.setUpdatedAt(now);
        repo.save(e);
        return toMap(e);
    }

    /**
     * Protocol-driven event creation: extracts the first recognized event from the tool response's
     * {@code events} array and persists it. Returns the created event, or null if none recognized.
     * This replaces hardcoded tool-name checks with a content-based protocol.
     */
    @Transactional
    @SuppressWarnings("unchecked")
    public Map<String, Object> createFromRecognizedEvent(Map<String, Object> toolResult,
                                                          String toolName,
                                                          Map<String, Object> originalArgs,
                                                          String uiSessionId) {
        Object eventsObj = toolResult.get("events");
        if (!(eventsObj instanceof List<?> eventsList) || eventsList.isEmpty()) {
            return createParameterMissingEvent(toolResult, toolName, originalArgs, uiSessionId);
        }
        for (Object item : eventsList) {
            if (!(item instanceof Map<?, ?> rawEvent)) continue;
            Map<String, Object> ev = (Map<String, Object>) rawEvent;
            String type = str(ev.get("type"));
            if (!"parameter_missing".equalsIgnoreCase(type)) continue;

            String worldId = str(firstNonBlank(ev.get("world_id"), toolResult.get("world_id")));
            String scopeId = str(firstNonBlank(ev.get("scope_id"), toolResult.get("scope_id")));
            // Host 侧 active UI session（new_task 后已切到 task）优先于协议中的 ui_session_id（常为 main）。
            String evSessionId = str(firstNonBlank(
                    uiSessionId != null && !uiSessionId.isBlank() ? uiSessionId : null,
                    ev.get("ui_session_id")));
            Map<String, Object> source = mapValue(ev.get("source"));
            Map<String, Object> businessData = mapValue(ev.get("business_data"));
            Map<String, Object> tags = mapValue(ev.get("tags"));
            Map<String, Object> widget = mapValue(ev.get("widget"));

            WorldEventEntity e = new WorldEventEntity();
            Instant now = Instant.now();
            e.setId("evt_" + UUID.randomUUID().toString().replace("-", ""));
            e.setType("parameter_missing");
            e.setStatus("pending");
            e.setWorldId(worldId);
            e.setScopeId(scopeId);
            e.setUiSessionId(evSessionId);
            e.setSourceJson(writeJson(source.isEmpty() ? Map.of("kind", "decision", "id", "") : source));
            e.setBusinessDataJson(writeJson(businessData));
            e.setTagsJson(writeJson(tags));
            e.setWidgetJson(writeJson(widget));
            e.setCreatedAt(now);
            e.setUpdatedAt(now);
            repo.save(e);
            return toMap(e);
        }
        return createParameterMissingEvent(toolResult, toolName, originalArgs, uiSessionId);
    }

    public Map<String, Object> widgetPayload(Map<String, Object> event) {
        Map<String, Object> out = new LinkedHashMap<>();
        Map<String, Object> widget = mapValue(event.get("widget"));
        Map<String, Object> schema = mapValue(widget.get("schema"));
        String title = str(firstNonBlank(
                schema.get("title"),
                widget.get("title"),
                "补充参数后继续执行"));
        out.put("widget_type", str(firstNonBlank(widget.get("type"), "sys.parameter-missing")));
        out.put("title", title);
        out.put("data", Map.of("event", event));
        return out;
    }

    public List<Map<String, Object>> list(String status, String worldId) {
        List<WorldEventEntity> rows;
        if (status != null && !status.isBlank() && worldId != null && !worldId.isBlank()) {
            rows = repo.findByWorldIdAndStatusOrderByCreatedAtAsc(worldId, status);
        } else if (status != null && !status.isBlank()) {
            rows = repo.findByStatusOrderByCreatedAtAsc(status);
        } else if (worldId != null && !worldId.isBlank()) {
            rows = repo.findByWorldIdOrderByCreatedAtDesc(worldId);
        } else {
            rows = repo.findAll();
        }
        return rows.stream().map(this::toMap).toList();
    }

    /**
     * 用户主动放弃待处理的 world event（不从 DB 删除，仅标记为 aborted）。
     */
    @Transactional
    public Map<String, Object> abort(String eventId) {
        WorldEventEntity event = repo.findById(eventId)
                .orElseThrow(() -> new IllegalArgumentException("World event not found: " + eventId));
        if (!"pending".equals(event.getStatus())) {
            return Map.of("status", "ignored", "reason", "event is " + event.getStatus(), "event", toMap(event));
        }
        event.setStatus("aborted");
        event.setUpdatedAt(Instant.now());
        repo.save(event);
        return Map.of("status", "aborted", "event", toMap(event));
    }

    /** 更新任务面板展示名（存入 tags.display_name，不改变 decision id）。 */
    @Transactional
    public Map<String, Object> rename(String eventId, String displayName) {
        WorldEventEntity event = repo.findById(eventId)
                .orElseThrow(() -> new IllegalArgumentException("World event not found: " + eventId));
        String name = displayName == null ? "" : displayName.strip();
        if (name.isBlank()) {
            throw new IllegalArgumentException("display_name must not be blank");
        }
        Map<String, Object> tags = new LinkedHashMap<>(readMap(event.getTagsJson()));
        tags.put("display_name", name);
        event.setTagsJson(writeJson(tags));
        event.setUpdatedAt(Instant.now());
        repo.save(event);
        return Map.of("status", "ok", "event", toMap(event));
    }

    @Transactional
    public Map<String, Object> submit(String eventId, Map<String, Object> submittedParams) {
        WorldEventEntity event = repo.findById(eventId)
                .orElseThrow(() -> new IllegalArgumentException("World event not found: " + eventId));
        if (!"parameter_missing".equals(event.getType())) {
            throw new IllegalArgumentException("Unsupported event type: " + event.getType());
        }
        if (!"pending".equals(event.getStatus())) {
            return Map.of("status", "ignored", "reason", "event is " + event.getStatus(), "event", toMap(event));
        }

        event.setStatus("submitted");
        event.setUpdatedAt(Instant.now());
        repo.save(event);

        Map<String, Object> business = readMap(event.getBusinessDataJson());
        List<Map<String, Object>> chainSoFar = mapList(business.get("chain_snapshot"));
        String resumeTool = str(firstNonBlank(business.get("resume_tool"), "manual_decision_execute"));
        Map<String, Object> resumeArgs = new LinkedHashMap<>(mapValue(business.get("resume_args")));
        Map<String, Object> mergedParams = new LinkedHashMap<>(mapValue(resumeArgs.get("parameters")));
        if (submittedParams != null) mergedParams.putAll(submittedParams);
        resumeArgs.put("parameters", mergedParams);

        Map<String, Object> result = callTool(resumeTool, resumeArgs);
        String resultStatus = str(result.get("status")).toLowerCase();
        List<Map<String, Object>> mergedChain = mergeChainSnapshots(chainSoFar, mapList(result.get("chain")));

        if ("need_input".equals(resultStatus)) {
            event.setStatus("resolved");
            event.setUpdatedAt(Instant.now());
            repo.save(event);
            resumeArgs.put("__chain_snapshot", mergedChain);
            Map<String, Object> nextEvent = createParameterMissingEvent(result, resumeTool, resumeArgs, event.getUiSessionId());
            return Map.of(
                    "status", "need_input",
                    "event", toMap(event),
                    "pre_widget", chainSummaryWidget(str(result.get("template_id")), "need_input", mergedChain),
                    "next_event", nextEvent,
                    "widget", widgetPayload(nextEvent),
                    "resume_result", result
            );
        }

        if ("success".equals(resultStatus) || "done".equals(resultStatus)) {
            event.setStatus("resolved");
            event.setUpdatedAt(Instant.now());
            repo.save(event);
            Map<String, Object> done = createDecisionDoneEvent(event, result);
            Map<String, Object> downstreamNeedInput = downstreamNeedInput(result, event);
            if (!downstreamNeedInput.isEmpty()) {
                resumeArgs.put("__chain_snapshot", mergedChain);
                Map<String, Object> nextEvent = createParameterMissingEvent(downstreamNeedInput, resumeTool, resumeArgs, event.getUiSessionId());
                return Map.of(
                        "status", "need_input",
                        "event", toMap(event),
                        "done_event", done,
                        "pre_widget", chainSummaryWidget(str(result.get("template_id")), "need_input", mergedChain),
                        "next_event", nextEvent,
                        "widget", widgetPayload(nextEvent),
                        "resume_result", result
                );
            }
            Map<String, Object> finalWidget = chainSummaryWidget(str(result.get("template_id")), "resolved", mergedChain);
            if (finalWidget.isEmpty()) {
                return Map.of("status", "resolved", "event", toMap(event), "done_event", done, "resume_result", result);
            }
            return Map.of("status", "resolved", "event", toMap(event), "done_event", done, "resume_result", result, "final_widget", finalWidget);
        }

        event.setStatus("failed");
        event.setUpdatedAt(Instant.now());
        repo.save(event);
        Map<String, Object> failed = createExecutionFailedEvent(event, result);
        return Map.of("status", "failed", "event", toMap(event), "failed_event", failed, "resume_result", result);
    }

    @Transactional
    public Map<String, Object> create(Map<String, Object> body) {
        WorldEventEntity e = new WorldEventEntity();
        Instant now = Instant.now();
        e.setId(str(firstNonBlank(body.get("id"), "evt_" + UUID.randomUUID().toString().replace("-", ""))));
        e.setType(str(firstNonBlank(body.get("type"), "business_event")));
        e.setStatus(str(firstNonBlank(body.get("status"), "pending")));
        e.setWorldId(str(body.get("world_id")));
        e.setScopeId(str(body.get("scope_id")));
        e.setSourceJson(writeJson(mapValue(body.get("source"))));
        e.setBusinessDataJson(writeJson(mapValue(body.get("business_data"))));
        e.setTagsJson(writeJson(mapValue(body.get("tags"))));
        e.setWidgetJson(writeJson(mapValue(body.get("widget"))));
        e.setCreatedAt(now);
        e.setUpdatedAt(now);
        repo.save(e);
        return toMap(e);
    }

    private Map<String, Object> createDecisionDoneEvent(WorldEventEntity parent, Map<String, Object> result) {
        Map<String, Object> parentMap = toMap(parent);
        Map<String, Object> source = mapValue(parentMap.get("source"));
        Map<String, Object> business = new LinkedHashMap<>();
        business.put("parent_event_id", parent.getId());
        business.put("resume_result", result);
        business.put("scope_id", parent.getScopeId());
        return createChild("decision_done", parent, source, business, mapValue(parentMap.get("tags")));
    }

    private Map<String, Object> createExecutionFailedEvent(WorldEventEntity parent, Map<String, Object> result) {
        Map<String, Object> parentMap = toMap(parent);
        Map<String, Object> business = new LinkedHashMap<>();
        business.put("parent_event_id", parent.getId());
        business.put("resume_result", result);
        business.put("scope_id", parent.getScopeId());
        return createChild("execution_failed", parent, mapValue(parentMap.get("source")), business, mapValue(parentMap.get("tags")));
    }

    private Map<String, Object> downstreamNeedInput(Map<String, Object> result, WorldEventEntity parent) {
        Object chainObj = result.get("chain");
        if (!(chainObj instanceof List<?> chain)) return Map.of();
        // Use the last NEED_INPUT entry — chain is chronological; downstream resume must target
        // the most recent paused template (never a compact/summary row).
        Map<String, Object> lastNeedInput = null;
        for (Object item : chain) {
            if (!(item instanceof Map<?, ?> raw)) continue;
            @SuppressWarnings("unchecked")
            Map<String, Object> entry = (Map<String, Object>) raw;
            if (!"NEED_INPUT".equalsIgnoreCase(str(entry.get("type")))) continue;
            String templateId = str(entry.get("template_id"));
            List<String> missing = stringList(firstNonBlank(entry.get("need_input_params"), entry.get("missing_params"), entry.get("verdict")));
            if (templateId.isBlank() || missing.isEmpty()) continue;
            lastNeedInput = entry;
        }
        if (lastNeedInput == null) return Map.of();
        String templateId = str(lastNeedInput.get("template_id"));
        List<String> missing = stringList(firstNonBlank(
                lastNeedInput.get("need_input_params"), lastNeedInput.get("missing_params"), lastNeedInput.get("verdict")));

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("status", "need_input");
        out.put("world_id", parent.getWorldId());
        out.put("template_id", templateId);
        out.put("scope_id", parent.getScopeId());
        out.put("missing_params", missing);
        out.put("reason", "downstream decision requires input");
        out.put("widget", Map.of(
                "widget_type", "auto_generated_form",
                "schema", Map.of(
                        "title", "补充参数后继续执行",
                        "template_id", templateId,
                        "fields", missing.stream()
                                .map(name -> Map.of("name", name, "type", "String", "required", true, "editable", true))
                                .toList()
                )
        ));
        return out;
    }

    private Map<String, Object> createChild(String type,
                                           WorldEventEntity parent,
                                           Map<String, Object> source,
                                           Map<String, Object> business,
                                           Map<String, Object> tags) {
        WorldEventEntity e = new WorldEventEntity();
        Instant now = Instant.now();
        e.setId("evt_" + UUID.randomUUID().toString().replace("-", ""));
        e.setType(type);
        e.setStatus("resolved");
        e.setWorldId(parent.getWorldId());
        e.setScopeId(parent.getScopeId());
        e.setUiSessionId(parent.getUiSessionId() == null ? "" : parent.getUiSessionId());
        e.setSourceJson(writeJson(source));
        e.setBusinessDataJson(writeJson(business));
        e.setTagsJson(writeJson(tags));
        e.setWidgetJson(writeJson(Map.of()));
        e.setCreatedAt(now);
        e.setUpdatedAt(now);
        repo.save(e);
        return toMap(e);
    }

    private Map<String, Object> callTool(String toolName, Map<String, Object> args) {
        try {
            AppRegistration app = registry.findAppForTool(toolName);
            String url = app.toolUrl(toolName);
            Map<String, Object> injectedArgs = registry.injectEnvVars(app.appId(), args == null ? Map.of() : args);
            HttpRequest req = HttpRequest.newBuilder(URI.create(url))
                    .header("Content-Type", "application/json")
                    .timeout(Duration.ofSeconds(30))
                    .POST(HttpRequest.BodyPublishers.ofString(JSON.writeValueAsString(Map.of("args", injectedArgs))))
                    .build();
            HttpResponse<String> resp = http.send(req, HttpResponse.BodyHandlers.ofString());
            Map<String, Object> body = JSON.readValue(resp.body(), MAP_TYPE);
            if (resp.statusCode() >= 200 && resp.statusCode() < 300) return body;
            Map<String, Object> out = new LinkedHashMap<>(body);
            out.putIfAbsent("status", "failed");
            out.put("http_status", resp.statusCode());
            return out;
        } catch (Exception e) {
            return Map.of("status", "failed", "error", e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage());
        }
    }

    private Map<String, Object> resolveWidget(Map<String, Object> toolResult, List<String> missing, String templateId) {
        Map<String, Object> rawWidget = mapValue(toolResult.get("widget"));
        String configuredType = str(rawWidget.get("widget_type"));
        String type = configuredType;
        if (type.isBlank() || "auto_generated_form".equals(type) || "configured".equals(type)) {
            type = "sys.parameter-missing";
        }
        Map<String, Object> widget = new LinkedHashMap<>();
        widget.put("type", type);
        widget.put("schema", resolveSchema(rawWidget, missing, templateId));
        return widget;
    }

    private Map<String, Object> resolveSchema(Map<String, Object> rawWidget, List<String> missing, String templateId) {
        Map<String, Object> schema = new LinkedHashMap<>(mapValue(rawWidget.get("schema")));
        if (schema.isEmpty()) {
            schema.put("title", "补充参数后继续执行");
            schema.put("template_id", templateId == null ? "" : templateId);
            List<Map<String, Object>> fields = new ArrayList<>();
            for (String name : missing) {
                fields.add(Map.of("name", name, "type", "String", "required", true, "editable", true));
            }
            schema.put("fields", fields);
        }
        return schema;
    }

    private Map<String, Object> toMap(WorldEventEntity e) {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("id", e.getId());
        out.put("type", e.getType());
        out.put("status", e.getStatus());
        out.put("world_id", e.getWorldId());
        out.put("scope_id", e.getScopeId());
        out.put("ui_session_id", e.getUiSessionId() == null ? "" : e.getUiSessionId());
        out.put("source", readMap(e.getSourceJson()));
        out.put("business_data", readMap(e.getBusinessDataJson()));
        Map<String, Object> tags = readMap(e.getTagsJson());
        out.put("tags", tags);
        String displayName = str(tags.get("display_name"));
        if (!displayName.isBlank()) out.put("display_name", displayName);
        out.put("widget", readMap(e.getWidgetJson()));
        out.put("created_at", e.getCreatedAt() == null ? "" : e.getCreatedAt().toString());
        out.put("updated_at", e.getUpdatedAt() == null ? "" : e.getUpdatedAt().toString());
        return out;
    }

    private static String upstreamContextRef(String worldId, String env, String templateId, String scopeId) {
        return String.join(":",
                worldId == null ? "" : worldId,
                env == null ? "" : env,
                templateId == null ? "" : templateId,
                scopeId == null ? "" : scopeId);
    }

    private static Object firstNonBlank(Object... values) {
        if (values == null) return "";
        for (Object v : values) {
            if (v == null) continue;
            if (v instanceof String s && s.isBlank()) continue;
            return v;
        }
        return "";
    }

    private static String str(Object v) {
        return v == null ? "" : String.valueOf(v);
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> mapValue(Object v) {
        if (v instanceof Map<?, ?> m) return (Map<String, Object>) m;
        return Map.of();
    }

    private static List<String> stringList(Object v) {
        if (v instanceof List<?> list) {
            List<String> out = new ArrayList<>();
            for (Object item : list) {
                if (item instanceof Map<?, ?> m) {
                    Object name = m.get("name");
                    if (name != null && !String.valueOf(name).isBlank()) out.add(String.valueOf(name));
                } else if (item != null && !String.valueOf(item).isBlank()) {
                    out.add(String.valueOf(item));
                }
            }
            return out;
        }
        if (v instanceof String s && !s.isBlank()) {
            return List.of(s.split(",")).stream().map(String::trim).filter(x -> !x.isBlank()).toList();
        }
        return List.of();
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> mapList(Object v) {
        if (!(v instanceof List<?> list)) return List.of();
        List<Map<String, Object>> out = new ArrayList<>();
        for (Object item : list) {
            if (item instanceof Map<?, ?> m) out.add((Map<String, Object>) m);
        }
        return out;
    }

    private static List<Map<String, Object>> mergeChainSnapshots(List<Map<String, Object>> previous,
                                                                 List<Map<String, Object>> current) {
        List<Map<String, Object>> out = new ArrayList<>();
        java.util.Set<String> seen = new java.util.LinkedHashSet<>();
        for (Map<String, Object> entry : previous == null ? List.<Map<String, Object>>of() : previous) {
            String key = chainEntryKey(entry);
            if (seen.add(key)) out.add(entry);
        }
        for (Map<String, Object> entry : current == null ? List.<Map<String, Object>>of() : current) {
            String key = chainEntryKey(entry);
            if (seen.add(key)) out.add(entry);
        }
        return out;
    }

    private static String chainEntryKey(Map<String, Object> entry) {
        if (entry == null) return "";
        return str(entry.get("template_id")) + "|" + str(entry.get("type")) + "|" + str(entry.get("at")) + "|" + str(entry.get("activated_by"));
    }

    private static Map<String, Object> chainSummaryWidget(String templateId,
                                                          String status,
                                                          List<Map<String, Object>> chain) {
        if (chain == null || chain.isEmpty()) return Map.of();
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("template_id", templateId == null ? "" : templateId);
        data.put("status", status == null ? "" : status);
        data.put("chain", chain);
        Map<String, Object> widget = new LinkedHashMap<>();
        widget.put("widget_type", "decision-chain-summary");
        widget.put("title", "决策执行链路 · " + (templateId == null ? "" : templateId));
        widget.put("data", data);
        return widget;
    }

    private static String capabilityId(Map<String, Object> toolResult) {
        Object capability = toolResult == null ? null : toolResult.get("capability");
        if (capability instanceof Map<?, ?> m) {
            Object id = m.get("id");
            if (id != null && !String.valueOf(id).isBlank()) return String.valueOf(id);
        }
        return "";
    }

    private static String writeJson(Map<String, Object> value) {
        try {
            return JSON.writeValueAsString(value == null ? Map.of() : value);
        } catch (Exception e) {
            return "{}";
        }
    }

    private static Map<String, Object> readMap(String json) {
        if (json == null || json.isBlank()) return Map.of();
        try {
            return JSON.readValue(json, MAP_TYPE);
        } catch (Exception e) {
            return Map.of();
        }
    }
}
