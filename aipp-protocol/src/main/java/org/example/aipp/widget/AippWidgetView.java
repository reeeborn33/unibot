package org.example.aipp.widget;

/**
 * 声明 AIPP Widget 的一个可导航视图（如 "关系图谱" Tab、"列表" Tab）。
 *
 * <h2>用途</h2>
 * <p>Widget 通过 {@code /api/widgets} manifest 中的 {@code views} 数组声明自己有哪些视图。
 * Host（world-one）在用户发消息时，将当前活跃视图的 {@link #llmHint} 注入到 LLM system prompt
 * 的最高优先级位置，让 LLM 了解用户的 UI 上下文，从而给出更精准的操作指令。
 *
 * <h2>Widget Manifest 声明示例</h2>
 * <pre>
 * "views": [
 *   { "id": "ALL",      "label": "全部记忆",  "llm_hint": "用户正在查看所有记忆列表。" },
 *   { "id": "RELATION", "label": "关系图谱",
 *     "llm_hint": "用户正在查看实体关系图谱。实体合并时请使用 IS_SAME_AS 关系，操作后调用 {refresh_skill} 刷新。" }
 * ],
 * "refresh_skill":  "memory_view",
 * "mutating_tools": ["memory_create", "memory_update", "memory_delete", "memory_supersede"]
 * </pre>
 *
 * <h2>前端协议</h2>
 * <p>Widget 前端在用户切换视图时调用全局函数：
 * <pre>
 *   aippReportView('memory-manager', 'RELATION');
 * </pre>
 * Host JS 将 {@code (widgetType, viewId)} 随消息发到后端，后端查 registry 拼 LLM hints。
 * 前端完全不需要知道 hint 文本——hint 是 widget 自己声明的，归属于 widget 端。
 *
 * <h2>{refresh_skill} 占位符</h2>
 * <p>{@link #llmHint} 中可使用 {@code {refresh_skill}} 占位符，
 * Host 在注入时替换为实际的 refresh skill 名称。
 *
 * @param id        视图唯一标识，如 {@code "RELATION"}、{@code "graph"}
 * @param label     人类可读标签，如 {@code "关系图谱"}（用于 UX 和日志）
 * @param llmHint   用户在此视图时注入 LLM 的上下文指令；支持 {@code {refresh_skill}} 占位符
 */
public record AippWidgetView(String id, String label, String llmHint) {}
