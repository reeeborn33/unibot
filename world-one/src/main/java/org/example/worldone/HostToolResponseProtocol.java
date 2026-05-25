package org.example.worldone;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.util.Map;

/**
 * Host-side interpretation of cross-app tool response fields ({@code new_session},
 * {@code session_mode}, {@code events}, UI session binding). Centralized for tests and
 * to avoid drift between {@link GenericAgentLoop} and {@link ToolProxyController}.
 */
public final class HostToolResponseProtocol {

    private HostToolResponseProtocol() {}

    public static boolean shouldOpenNewTaskSession(JsonNode root) {
        if (root == null || root.isMissingNode() || root.isNull()) return false;
        if (root.has("new_session") && !root.get("new_session").isNull()) return true;
        return "new_task".equalsIgnoreCase(root.path("session_mode").asText("").trim());
    }

    /**
     * Returns the {@code new_session} object to emit, synthesizing one when only
     * {@code session_mode=new_task} is present.
     */
    public static JsonNode newSessionDirective(JsonNode root, ObjectMapper json) {
        if (root == null || root.isMissingNode() || root.isNull()) {
            return json.missingNode();
        }
        if (root.has("new_session") && !root.get("new_session").isNull()) {
            return root.get("new_session");
        }
        if (!"new_task".equalsIgnoreCase(root.path("session_mode").asText("").trim())) {
            return json.missingNode();
        }
        ObjectNode ns = json.createObjectNode();
        String templateId = root.path("template_id").asText("").trim();
        String suggested = root.path("session_name").asText("").trim();
        if (suggested.isBlank() && !templateId.isBlank()) {
            suggested = "Decision: " + templateId;
        }
        if (suggested.isBlank()) suggested = "新任务";
        ns.put("name", suggested);
        ns.put("type", "task");
        return ns;
    }

    public static boolean hasParameterMissingEvent(JsonNode root) {
        if (root == null || root.isMissingNode() || root.isNull()) return false;
        JsonNode events = root.path("events");
        if (!events.isArray() || events.isEmpty()) return false;
        for (JsonNode ev : events) {
            if ("parameter_missing".equalsIgnoreCase(ev.path("type").asText(""))) {
                return true;
            }
        }
        return false;
    }

    /**
     * UI session id for world-event binding on direct tool proxy calls.
     * Precedence: body.session_id → _context.sessionId → args.ui_session_id.
     */
    @SuppressWarnings("unchecked")
    public static String resolveHostUiSessionId(Map<String, Object> requestBody,
                                               Map<String, Object> toolArgs) {
        if (requestBody != null) {
            String sid = str(requestBody.get("session_id"));
            if (!sid.isBlank()) return sid;
            Object ctxObj = requestBody.get("_context");
            if (ctxObj instanceof Map<?, ?> ctx) {
                sid = str(((Map<String, Object>) ctx).get("sessionId"));
                if (!sid.isBlank()) return sid;
            }
        }
        if (toolArgs != null) {
            String ui = str(toolArgs.get("ui_session_id"));
            if (!ui.isBlank()) return ui;
        }
        return "";
    }

    private static String str(Object v) {
        return v == null ? "" : String.valueOf(v).trim();
    }
}
