package org.example.shared.llm;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.*;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Shared LLM HTTP infrastructure for OpenAI-compatible Chat Completions.
 */
public final class LLMCaller {

    public static final int DEFAULT_MAX_TOKENS_TOOLS     = 4096;
    public static final int DEFAULT_MAX_TOKENS_TEXT_ONLY = 2048;

    private static final String FINISH_TOOL_CALLS = "tool_calls";
    private static final String FINISH_STOP       = "stop";

    private static final Pattern FINISH_REASON_PAT =
            Pattern.compile("\"finish_reason\"\\s*:\\s*\"([^\"]+)\"");
    private static final Pattern CONTENT_PAT =
            Pattern.compile("\"content\"\\s*:\\s*\"((?:[^\"\\\\]|\\\\.)*)\"");
    private static final Pattern REASONING_PAT =
            Pattern.compile("\"reasoning_content\"\\s*:\\s*\"((?:[^\"\\\\]|\\\\.)*)\"");

    private static final ObjectMapper JSON_MAPPER = new ObjectMapper();

    private final LLMConfig  config;
    private final HttpClient httpClient;

    public LLMCaller(LLMConfig config) {
        this.config     = Objects.requireNonNull(config, "config");
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(15))
                .build();
    }

    public boolean hasKey() {
        return config.hasKey();
    }

    public LLMResponse call(List<Map<String, Object>> messages,
                            String toolsJson) throws Exception {
        return call(messages, toolsJson, DEFAULT_MAX_TOKENS_TOOLS, "auto");
    }

    public LLMResponse call(List<Map<String, Object>> messages,
                            String toolsJson,
                            int maxTokens) throws Exception {
        return call(messages, toolsJson, maxTokens, "auto");
    }

    public LLMResponse call(List<Map<String, Object>> messages,
                            String toolsJson,
                            int maxTokens,
                            String toolChoice) throws Exception {
        String body = buildBody(messages, toolsJson, maxTokens, toolChoice);
        return parseResponse(send(body));
    }

    public LLMResponse callTextOnly(List<Map<String, Object>> messages) throws Exception {
        return callTextOnly(messages, DEFAULT_MAX_TOKENS_TEXT_ONLY);
    }

    public LLMResponse callTextOnly(List<Map<String, Object>> messages,
                                    int maxTokens) throws Exception {
        String body = buildTextOnlyBody(messages, maxTokens);
        return parseResponse(send(body));
    }

    public LLMResponse callStream(List<Map<String, Object>> messages,
                                  String toolsJson, int maxTokens, String toolChoice,
                                  Consumer<String> textTokenCallback,
                                  Consumer<String> thinkingBatchCallback) throws Exception {
        String body = buildStreamBody(messages, toolsJson, maxTokens, toolChoice);

        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(config.chatCompletionsUrl()))
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer " + config.apiKey())
                .timeout(Duration.ofSeconds(config.timeoutSeconds()))
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();

        HttpResponse<java.util.stream.Stream<String>> resp =
                httpClient.send(req, HttpResponse.BodyHandlers.ofLines());

        if (resp.statusCode() != 200) {
            String hint = resp.body().limit(5)
                    .collect(java.util.stream.Collectors.joining(""));
            Matcher em = Pattern.compile("\"message\"\\s*:\\s*\"([^\"]+)\"").matcher(hint);
            throw new RuntimeException("LLM API error " + resp.statusCode()
                    + ": " + (em.find() ? em.group(1) : truncate(hint, 200)));
        }

        StringBuilder fullContent   = new StringBuilder();
        StringBuilder fullReasoning = new StringBuilder();
        String[]      finishReason  = { FINISH_STOP };
        boolean[]     thinkingEmitted = { false };

        Map<Integer, String[]> tcAccum = new LinkedHashMap<>();

        resp.body().forEach(line -> {
            if (!line.startsWith("data:")) return;
            String data = line.substring(5).trim();
            if (data.isEmpty() || "[DONE]".equals(data)) return;
            try {
                JsonNode chunk  = JSON_MAPPER.readTree(data);
                JsonNode choice = chunk.path("choices").path(0);
                String   fr     = choice.path("finish_reason").asText("");
                if (!fr.isEmpty() && !"null".equals(fr)) finishReason[0] = fr;

                JsonNode delta = choice.path("delta");

                JsonNode reasoningNode = delta.path("reasoning_content");
                if (!reasoningNode.isMissingNode() && !reasoningNode.isNull()) {
                    String token = reasoningNode.asText("");
                    if (!token.isEmpty()) fullReasoning.append(token);
                }

                JsonNode contentNode = delta.path("content");
                if (!contentNode.isMissingNode() && !contentNode.isNull()) {
                    String token = contentNode.asText("");
                    if (!token.isEmpty()) {
                        if (!thinkingEmitted[0] && fullReasoning.length() > 0
                                && thinkingBatchCallback != null) {
                            thinkingBatchCallback.accept(fullReasoning.toString());
                            thinkingEmitted[0] = true;
                        }
                        fullContent.append(token);
                        if (textTokenCallback != null) textTokenCallback.accept(token);
                    }
                }

                JsonNode toolCallsNode = delta.path("tool_calls");
                if (toolCallsNode.isArray()) {
                    for (JsonNode tc : toolCallsNode) {
                        int idx = tc.path("index").asInt(0);
                        String[] acc = tcAccum.computeIfAbsent(idx, k -> new String[]{"","",""});
                        String id = tc.path("id").asText("");
                        if (!id.isEmpty()) acc[0] = id;
                        JsonNode fn = tc.path("function");
                        String name = fn.path("name").asText("");
                        if (!name.isEmpty()) acc[1] = name;
                        acc[2] += fn.path("arguments").asText("");
                    }
                }
            } catch (Exception ignored) {}
        });

        if (!thinkingEmitted[0] && fullReasoning.length() > 0
                && thinkingBatchCallback != null) {
            thinkingBatchCallback.accept(fullReasoning.toString());
        }

        if (FINISH_TOOL_CALLS.equals(finishReason[0]) && !tcAccum.isEmpty()) {
            List<ToolCall> calls = new ArrayList<>();
            StringBuilder tcJson = new StringBuilder("[");
            boolean first = true;
            for (String[] acc : tcAccum.values()) {
                if (!first) tcJson.append(",");
                first = false;
                calls.add(new ToolCall(acc[0], acc[1], acc[2]));
                tcJson.append("{\"id\":\"").append(acc[0])
                      .append("\",\"type\":\"function\",\"function\":{\"name\":\"")
                      .append(acc[1]).append("\",\"arguments\":")
                      .append(jsonString(acc[2])).append("}}");
            }
            tcJson.append("]");
            Map<String, Object> assistantMsg = new LinkedHashMap<>();
            assistantMsg.put("role",       "assistant");
            assistantMsg.put("content",    null);
            assistantMsg.put("tool_calls", tcJson.toString());
            return new LLMResponse(FINISH_TOOL_CALLS, null, null, calls, assistantMsg);
        }

        String reasoning = fullReasoning.length() > 0 ? fullReasoning.toString() : null;
        return new LLMResponse(finishReason[0], fullContent.toString(), reasoning,
                               List.of(), Map.of());
    }

    public static String buildToolsJson(List<? extends LlmToolSpec> tools) {
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < tools.size(); i++) {
            if (i > 0) sb.append(",");
            sb.append("{\"type\":\"function\",\"function\":")
              .append(tools.get(i).toolDefinitionJson()).append("}");
        }
        return sb.append("]").toString();
    }

    public static String jsonString(String s) {
        if (s == null) return "null";
        return "\"" + s.replace("\\", "\\\\").replace("\"", "\\\"")
                        .replace("\n", "\\n").replace("\r", "\\r").replace("\t", "\\t") + "\"";
    }

    public static String unescape(String s) {
        if (s == null) return "";
        return s.replace("\\\"", "\"").replace("\\n", "\n")
                .replace("\\r", "\r").replace("\\t", "\t").replace("\\\\", "\\");
    }

    public static String messagesToJson(List<Map<String, Object>> messages) {
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < messages.size(); i++) {
            if (i > 0) sb.append(",");
            sb.append(messageToJson(messages.get(i)));
        }
        return sb.append("]").toString();
    }

    public static String messageToJson(Map<String, Object> msg) {
        StringBuilder sb = new StringBuilder("{");
        sb.append("\"role\":\"").append(msg.get("role")).append("\"");

        Object content = msg.get("content");
        if (content != null) {
            sb.append(",\"content\":").append(jsonString(String.valueOf(content)));
        } else {
            sb.append(",\"content\":null");
        }

        if (msg.containsKey("tool_calls"))
            sb.append(",\"tool_calls\":").append(msg.get("tool_calls"));
        if (msg.containsKey("tool_call_id"))
            sb.append(",\"tool_call_id\":\"").append(msg.get("tool_call_id")).append("\"");
        if (msg.containsKey("name"))
            sb.append(",\"name\":\"").append(msg.get("name")).append("\"");

        return sb.append("}").toString();
    }

    public static String truncate(String s, int maxLen) {
        if (s == null) return "";
        return s.length() <= maxLen ? s : s.substring(0, maxLen) + "…";
    }

    private String buildBody(List<Map<String, Object>> messages,
                             String toolsJson,
                             int maxTokens,
                             String toolChoice) {
        return "{\"model\":" + jsonString(config.model())
                + ",\"temperature\":0.1"
                + ",\"max_tokens\":" + maxTokens
                + ",\"messages\":" + messagesToJson(messages)
                + ",\"tools\":" + toolsJson
                + ",\"tool_choice\":\"" + toolChoice + "\"}";
    }

    private String buildTextOnlyBody(List<Map<String, Object>> messages, int maxTokens) {
        return "{\"model\":" + jsonString(config.model())
                + ",\"temperature\":0.1"
                + ",\"max_tokens\":" + maxTokens
                + ",\"messages\":" + messagesToJson(messages) + "}";
    }

    private String buildStreamBody(List<Map<String, Object>> messages,
                                   String toolsJson, int maxTokens, String toolChoice) {
        return "{\"model\":" + jsonString(config.model())
                + ",\"temperature\":0.1"
                + ",\"max_tokens\":" + maxTokens
                + ",\"stream\":true"
                + ",\"messages\":" + messagesToJson(messages)
                + ",\"tools\":" + toolsJson
                + ",\"tool_choice\":\"" + toolChoice + "\"}";
    }

    private String send(String body) throws Exception {
        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(config.chatCompletionsUrl()))
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer " + config.apiKey())
                .timeout(Duration.ofSeconds(config.timeoutSeconds()))
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();
        HttpResponse<String> resp = httpClient.send(req, HttpResponse.BodyHandlers.ofString());
        if (resp.statusCode() != 200) {
            String errBody = resp.body();
            Matcher em = Pattern.compile("\"message\"\\s*:\\s*\"([^\"]+)\"").matcher(errBody);
            String hint = em.find() ? em.group(1) : errBody;
            throw new RuntimeException("LLM API error " + resp.statusCode() + ": " + hint);
        }
        return resp.body();
    }

    private LLMResponse parseResponse(String responseBody) {
        Matcher fm = FINISH_REASON_PAT.matcher(responseBody);
        String finishReason = fm.find() ? fm.group(1) : FINISH_STOP;

        if (FINISH_TOOL_CALLS.equals(finishReason)) {
            String toolCallsJson = LlmToolCallParser.extractToolCallsArray(responseBody);
            List<ToolCall> calls = LlmToolCallParser.parseToolCalls(toolCallsJson).stream()
                    .map(tc -> new ToolCall(tc.id(), tc.name(), tc.arguments()))
                    .toList();

            Map<String, Object> assistantMsg = new LinkedHashMap<>();
            assistantMsg.put("role",       "assistant");
            assistantMsg.put("content",    null);
            assistantMsg.put("tool_calls", toolCallsJson);
            return new LLMResponse(finishReason, null, null, calls, assistantMsg);
        } else {
            Matcher cm = CONTENT_PAT.matcher(responseBody);
            String content = cm.find() ? unescape(cm.group(1)) : responseBody;
            Matcher rm = REASONING_PAT.matcher(responseBody);
            String reasoning = rm.find() ? unescape(rm.group(1)) : null;
            return new LLMResponse(finishReason, content, reasoning, List.of(), Map.of());
        }
    }

    public record LLMResponse(
            String finishReason,
            String content,
            String reasoning,
            List<ToolCall> toolCalls,
            Map<String, Object> rawAssistantMessage) {}

    public record ToolCall(String id, String name, String arguments) {
        public Map<String, Object> parsedArgs() {
            return new LlmToolCallParser.ToolCallInfo(id, name, arguments).parsedArgs();
        }
    }
}
