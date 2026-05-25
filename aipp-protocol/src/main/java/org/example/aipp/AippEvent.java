package org.example.aipp;

/**
 * AIPP 外部事件载荷 — 由外部系统（或 AIPP 应用后台任务）POST 到 world-one。
 *
 * <h2>事件 vs 任务</h2>
 * <ul>
 *   <li><b>Event（事件）</b>：外部系统主动推送到 world-one，触发交互或工具执行</li>
 *   <li><b>Task（任务）</b>：由 world-one 内部（LLM 或用户）创建的异步任务</li>
 * </ul>
 * 两者行为完全一致，只是来源不同。都在独立 session 中运行。
 *
 * <h2>端点</h2>
 * <pre>POST /api/events</pre>
 * world-one 接收后，按以下规则路由：
 * <ol>
 *   <li>有 {@code widget_type} → 打开对应 widget（canvas 或 chat 内嵌）</li>
 *   <li>只有 {@code tool_id}  → 直接执行工具（ToolProxy），world-one 显示系统 spinner</li>
 *   <li>两者都有              → 打开 widget，widget 内部驱动工具执行</li>
 * </ol>
 *
 * <h2>Session 规则</h2>
 * <p>每个事件都在独立 session 中运行，与当前对话 session 隔离，互不干扰。
 * world-one 自动创建 Task Session，关闭后回到原状态。
 *
 * <h2>载荷示例</h2>
 * <pre>
 * // 1. 仅触发工具（显示系统 spinner）
 * {
 *   "event_id":   "evt-001",
 *   "source_app": "world-entitir",
 *   "title":      "正在构建 World #42",
 *   "tool_id":    "world_build",
 *   "tool_args":  { "world_id": "42" }
 * }
 *
 * // 2. 打开自定义 widget
 * {
 *   "event_id":    "evt-002",
 *   "source_app":  "ci-agent",
 *   "title":       "Pipeline #123 完成",
 *   "widget_type": "pipeline-result",
 *   "data":        { "pipeline_id": "123", "status": "success" }
 * }
 *
 * // 3. 打开系统确认框（用于需要用户决策的事件）
 * {
 *   "event_id":    "evt-003",
 *   "source_app":  "quality-agent",
 *   "title":       "发现严重质量问题",
 *   "widget_type": "sys.confirm",
 *   "data": {
 *     "mode":    "yes_no",
 *     "title":   "发现 3 个严重漏洞",
 *     "message": "质量扫描发现 3 个高危漏洞，是否立即中止发布？",
 *     "danger":  true,
 *     "yes": { "tool": "deploy_abort", "args": { "pipeline_id": "123" } },
 *     "no":  { "message": "已忽略警告，继续发布" }
 *   }
 * }
 * </pre>
 *
 * <h2>字段说明</h2>
 * <ul>
 *   <li>{@code eventId}    — 事件唯一标识符（幂等性保证，重复 POST 同 eventId 应忽略）</li>
 *   <li>{@code sourceApp}  — 发送方 app_id（用于显示来源标识）</li>
 *   <li>{@code title}      — 在 Task Panel 中显示的标题</li>
 *   <li>{@code widgetType} — 要打开的 widget 类型；可以是 AIPP 注册的类型，
 *       也可以是 {@link AippSystemWidget} 中的系统内置类型（{@code sys.*}）</li>
 *   <li>{@code toolId}     — 要直接执行的工具名（无需 LLM 介入）；
 *       widgetType 为空时显示系统 spinner</li>
 *   <li>{@code toolArgs}   — toolId 执行时传入的参数（JSON 对象字符串）</li>
 *   <li>{@code data}       — 传给 widget 的初始化数据（JSON 对象字符串）</li>
 *   <li>{@code priority}   — 优先级：NORMAL（默认）/ HIGH / URGENT</li>
 * </ul>
 *
 * @see AippSystemWidget
 */
public record AippEvent(
        String eventId,
        String sourceApp,
        String title,
        String widgetType,
        String toolId,
        String toolArgs,
        String data,
        Priority priority
) {
    /**
     * 事件优先级。
     * <ul>
     *   <li>{@code NORMAL}  — 后台静默，进入 Task Panel 队列</li>
     *   <li>{@code HIGH}    — Task Panel 高亮提示用户</li>
     *   <li>{@code URGENT}  — 立即弹出（用于需要即时决策的事件，如确认框）</li>
     * </ul>
     */
    public enum Priority {
        NORMAL, HIGH, URGENT
    }

    /** 是否为"仅执行工具"型事件（无自定义 widget，world-one 显示系统 spinner）。 */
    public boolean isToolOnly() {
        return (widgetType == null || widgetType.isBlank()) && toolId != null && !toolId.isBlank();
    }

    /** 是否引用了系统内置 widget（sys.* 前缀）。 */
    public boolean usesSystemWidget() {
        return widgetType != null && widgetType.startsWith(AippSystemWidget.SYS_PREFIX);
    }
}
