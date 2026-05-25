package org.example.shared.llm;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Parses {@code tool_calls} from OpenAI-compatible Chat Completions HTTP responses.
 */
public final class LlmToolCallParser {

    private static final Pattern TC_ID   = Pattern.compile("\"id\"\\s*:\\s*\"([^\"]+)\"");
    private static final Pattern TC_NAME = Pattern.compile("\"name\"\\s*:\\s*\"([^\"]+)\"");
    private static final Pattern TC_ARGS = Pattern.compile("\"arguments\"\\s*:\\s*\"((?:[^\"\\\\]|\\\\.)*)\"");

    private static final ObjectMapper JACKSON = new ObjectMapper();

    private LlmToolCallParser() {}

    public static String extractToolCallsArray(String responseBody) {
        int keyIdx = responseBody.indexOf("\"tool_calls\"");
        if (keyIdx < 0) return "[]";
        int arrStart = responseBody.indexOf('[', keyIdx);
        if (arrStart < 0) return "[]";
        return extractBalanced(responseBody, arrStart, '[', ']');
    }

    public static List<ToolCallInfo> parseToolCalls(String toolCallsJson) {
        List<ToolCallInfo> result = new ArrayList<>();
        if (toolCallsJson == null || toolCallsJson.isBlank()) return result;

        int i = 0;
        while (i < toolCallsJson.length()) {
            int objStart = toolCallsJson.indexOf('{', i);
            if (objStart < 0) break;

            String obj = extractBalanced(toolCallsJson, objStart, '{', '}');
            i = objStart + obj.length();

            Matcher idM   = TC_ID.matcher(obj);
            Matcher nameM = TC_NAME.matcher(obj);
            Matcher argsM = TC_ARGS.matcher(obj);

            String id   = idM.find()   ? idM.group(1)              : java.util.UUID.randomUUID().toString();
            String name = nameM.find() ? nameM.group(1)             : "";
            String args = argsM.find() ? unescape(argsM.group(1)) : "{}";

            if (!name.isBlank()) result.add(new ToolCallInfo(id, name, args));
        }
        return result;
    }

    public static String extractBalanced(String s, int start, char open, char close) {
        int     depth    = 0;
        boolean inString = false;
        boolean escaped  = false;

        for (int i = start; i < s.length(); i++) {
            char c = s.charAt(i);
            if (escaped) {
                escaped = false;
                continue;
            }
            if (inString) {
                if (c == '\\') escaped = true;
                else if (c == '"') inString = false;
            } else {
                if      (c == '"')   inString = true;
                else if (c == open)  depth++;
                else if (c == close) {
                    depth--;
                    if (depth == 0) return s.substring(start, i + 1);
                }
            }
        }
        return s.substring(start);
    }

    static String unescape(String s) {
        return s.replace("\\\"", "\"")
                .replace("\\n", "\n")
                .replace("\\t", "\t")
                .replace("\\\\", "\\");
    }

    public record ToolCallInfo(String id, String name, String arguments) {

        @SuppressWarnings("unchecked")
        public Map<String, Object> parsedArgs() {
            if (arguments == null || arguments.isBlank()) return new LinkedHashMap<>();
            try {
                Object parsed = JACKSON.readValue(arguments, Object.class);
                if (parsed instanceof Map<?, ?> m) {
                    Map<String, Object> out = new LinkedHashMap<>();
                    for (var e : m.entrySet()) {
                        if (e.getKey() != null) out.put(e.getKey().toString(), e.getValue());
                    }
                    return out;
                }
            } catch (Exception ignored) {
                // regex fallback below
            }
            return parseSimpleArgsJsonFallback(arguments);
        }

        private static Map<String, Object> parseSimpleArgsJsonFallback(String json) {
            Map<String, Object> result = new LinkedHashMap<>();
            Pattern kvPat = Pattern.compile(
                    "\"([^\"]+)\"\\s*:\\s*(?:\"((?:[^\"\\\\]|\\\\.)*)\"|(-?\\d+(?:\\.\\d+)?)|true|false)");
            Matcher m = kvPat.matcher(json);
            while (m.find()) {
                String key = m.group(1);
                if (m.group(2) != null) {
                    result.put(key, unescape(m.group(2)));
                } else if (m.group(3) != null) {
                    String num = m.group(3);
                    result.put(key, num.contains(".") ? Double.parseDouble(num) : Long.parseLong(num));
                } else {
                    result.put(key, m.group(0).endsWith("true") ? Boolean.TRUE : Boolean.FALSE);
                }
            }
            return result;
        }
    }
}
