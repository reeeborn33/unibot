package org.example.shared.llm;

/**
 * Agent 聊天 SSE 事件（Host ↔ 前端协议）。
 *
 * <p>与 {@link LLMCaller} 同属轻量 shared 层，供 world-one、entitir aip 等复用，
 * 避免 UI Host 为一条 record 依赖整包 aip。
 *
 * <h2>事件类型</h2>
 * <ul>
 *   <li>{@code tool_call}   — 即将调用某个工具（工具名），用于前端实时进度展示</li>
 *   <li>{@code thinking}    — LLM 推理过程（完整 reasoning_content，推理模型专用）</li>
 *   <li>{@code text_token}  — LLM 文本回复的单个流式 token（streaming 模式）</li>
 *   <li>{@code text}        — 完整文本（后端持久化，通常不下发 SSE）</li>
 *   <li>{@code html_widget} — HTML 卡片内嵌聊天流（JSON 含 html/height）</li>
 *   <li>{@code canvas}      — canvas 指令 JSON，前端渲染 widget</li>
 *   <li>{@code session}     — 新 session 信号：{ui_session_id, name, type}</li>
 *   <li>{@code annotation}  — 灰色过程注解（路由、AIPP 匹配等）</li>
 *   <li>{@code error}       — 错误信息</li>
 *   <li>{@code done}        — 流结束信号</li>
 * </ul>
 */
public record ChatEvent(Type type, String content) {

    public enum Type {
        TOOL_CALL, THINKING, TEXT_TOKEN, TEXT, CANVAS, HTML_WIDGET, SESSION, ANNOTATION, ERROR, DONE
    }

    public static ChatEvent toolCall(String name)       { return new ChatEvent(Type.TOOL_CALL,  name); }
    public static ChatEvent thinking(String content)    { return new ChatEvent(Type.THINKING,   content); }
    public static ChatEvent textToken(String token)     { return new ChatEvent(Type.TEXT_TOKEN, token); }
    /** 完整文本信号（用于后端持久化，不下发前端）。 */
    public static ChatEvent text(String content)        { return new ChatEvent(Type.TEXT,       content); }
    public static ChatEvent canvas(String json)         { return new ChatEvent(Type.CANVAS,     json); }
    /** HTML 卡片内嵌聊天流（is_canvas_mode=false）；content 为 {"html":"...","height":"400px"} JSON。 */
    public static ChatEvent htmlWidget(String json)     { return new ChatEvent(Type.HTML_WIDGET, json); }
    public static ChatEvent session(String json)        { return new ChatEvent(Type.SESSION,    json); }
    /**
     * 灰色注解行（不属于最终回答），用于展示 AIPP 匹配、阶段跳转等过程信息。
     * content 格式：{"label":"world-entitir","detail":"EAI 员工入职链路"}
     */
    public static ChatEvent annotation(String content)  { return new ChatEvent(Type.ANNOTATION, content); }
    public static ChatEvent error(String message)       { return new ChatEvent(Type.ERROR,      message); }
    public static ChatEvent done()                      { return new ChatEvent(Type.DONE,       ""); }

    /** SSE data 格式：{type, content} JSON。 */
    public String toSseData() {
        String escaped = content == null ? "" :
                content.replace("\\", "\\\\").replace("\"", "\\\"")
                       .replace("\n", "\\n").replace("\r", "");
        return "{\"type\":\"" + type.name().toLowerCase()
                + "\",\"content\":\"" + escaped + "\"}";
    }
}
