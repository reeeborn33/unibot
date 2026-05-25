package org.example.aipp;

/**
 * AIPP 系统内置 Widget 类型常量。
 *
 * <p>系统 Widget 由 world-one（AIPP host）内置实现，AIPP 应用无需也不能注册这些类型。
 * AIPP 应用可以在 tool response 的 {@code canvas.widget_type} 字段中直接引用，
 * world-one 识别 {@code sys.*} 前缀后自动渲染对应的系统交互界面。
 *
 * <h2>保留前缀约定</h2>
 * <p>所有系统 widget 以 {@value #SYS_PREFIX} 开头。AIPP 应用注册自己的 widget 时
 * 不得使用此前缀（{@link AippAppSpec} 会在合规检查时报错）。
 *
 * <h2>对应 Windows 对话框模式</h2>
 * <pre>
 *   sys.confirm  ←→  MB_YESNO / MB_OKCANCEL   （需要用户决策）
 *   sys.alert    ←→  MB_OK                     （信息通知）
 *   sys.prompt   ←→  InputBox                  （需要用户输入）
 *   sys.selection←→  自定义选项选择            （从列表选择一项，推荐）
 *   sys.choice   ←→  旧别名（兼容）
 *   sys.progress ←→  ProgressDialog            （工具执行进度）
 * </pre>
 *
 * <h2>渲染规则</h2>
 * <p>系统 Widget 始终以<b>模态覆盖层（Modal Overlay）</b>渲染，而非推入 canvas 导航栈。
 * 用户必须响应（点击按钮）后才能继续操作，关闭后自动回到原状态。
 *
 * <h2>canvas 指令格式</h2>
 * <pre>
 * {
 *   "canvas": {
 *     "action":      "open",
 *     "widget_type": "sys.confirm",      // 或其他 sys.* 类型
 *     "data": { ... }                    // 各类型的专属数据结构，见下方说明
 *   }
 * }
 * </pre>
 *
 * <h2>sys.confirm data 结构</h2>
 * <pre>
 * {
 *   "mode":    "yes_no",               // "yes_no" | "ok_cancel"
 *   "title":   "确认删除",
 *   "message": "确定删除这 3 条记忆？此操作不可撤销。",
 *   "danger":  true,                   // true = 确认按钮显示为危险红色
 *   "yes": {
 *     "tool": "memory_delete_confirmed",
 *     "args": { "memory_ids": ["m1", "m2", "m3"] }
 *   },
 *   "no": {
 *     "message": "已取消删除操作"       // 发送到 chat 的消息（可选）
 *   }
 * }
 * </pre>
 *
 * <h2>sys.alert data 结构</h2>
 * <pre>
 * {
 *   "title":         "操作完成",
 *   "message":       "已成功归档 World #42。",
 *   "close_message": "用户已确认归档完成"   // 关闭时发送到 chat（可选）
 * }
 * </pre>
 *
 * <h2>sys.prompt data 结构</h2>
 * <pre>
 * {
 *   "title":       "设置记忆指令",
 *   "message":     "请输入你希望 AI 长期遵循的记忆指令：",
 *   "placeholder": "例如：回答时总是使用中文",
 *   "submit": {
 *     "tool":     "memory_set_instruction",
 *     "arg_name": "instruction"         // 用户输入值映射到的 tool 参数名
 *   },
 *   "cancel": {
 *     "message": "已取消设置"
 *   }
 * }
 * </pre>
 *
 * <h2>sys.selection data 结构</h2>
 * <pre>
 * {
 *   "title":   "选择导出格式",
 *   "message": "请选择 World 的导出格式：",
 *   "options": [
 *     { "label": "JSON",     "tool": "world_export", "args": { "format": "json" } },
 *     { "label": "Markdown", "tool": "world_export", "args": { "format": "md"   } },
 *     { "label": "取消",     "message": "已取消导出" }
 *   ]
 * }
 * </pre>
 *
 * <h2>sys.progress data 结构</h2>
 * <pre>
 * {
 *   "title":         "正在构建 World #42",
 *   "indeterminate": true,              // true=转圈 false=百分比进度条
 *   "poll_tool":     "world_build_status",  // 轮询进度的 tool（可选）
 *   "poll_interval": 2000               // 轮询间隔 ms（默认 2000）
 * }
 * </pre>
 *
 * @see AippEvent
 * @see AippAppSpec#assertSystemWidgetExempt(String)
 */
public final class AippSystemWidget {

    /** 所有系统 widget 的保留前缀，AIPP 应用不得注册此前缀的 widget。 */
    public static final String SYS_PREFIX = "sys.";

    // ── 对话框类（需要用户响应）─────────────────────────────────────────────

    /**
     * 二选一确认框（Yes/No 或 OK/Cancel）。
     *
     * <p>用于危险操作（删除、覆盖）或需要用户明确授权的场景。
     * {@code danger=true} 时确认按钮显示为红色。
     *
     * <p>对应 Windows：{@code MB_YESNO} / {@code MB_OKCANCEL}
     */
    public static final String CONFIRM = "sys.confirm";

    /**
     * 纯信息提示框（仅 OK 按钮）。
     *
     * <p>用于操作完成通知、重要提示。关闭时可选发送一条 chat 消息。
     *
     * <p>对应 Windows：{@code MB_OK}
     */
    public static final String ALERT = "sys.alert";

    /**
     * 单行文本输入框（输入框 + OK/Cancel 按钮）。
     *
     * <p>用于需要用户输入短文本的场景，如设置指令、输入名称。
     * 提交时将输入值作为参数调用指定 tool。
     *
     * <p>对应 Windows：{@code InputBox}
     */
    public static final String PROMPT = "sys.prompt";

    /**
     * 单项选择框（从列表中选择一个选项）。
     *
     * <p>每个选项可以关联一个 tool call 或一条 chat 消息。
     * 用于替代需要 LLM 介入的选择场景，直接由用户决策。
     */
    public static final String SELECTION = "sys.selection";

    /**
     * 旧别名：sys.choice（兼容）。
     *
     * <p>新协议推荐使用 {@link #SELECTION}（sys.selection）。
     */
    @Deprecated
    public static final String CHOICE = "sys.choice";

    // ── 进度类（后台执行可视化）──────────────────────────────────────────────

    /**
     * 进度指示器（spinner 或进度条）。
     *
     * <p>用于展示后台工具执行状态。
     * {@code indeterminate=true} 时显示转圈动画（不确定时长），
     * {@code indeterminate=false} 时显示百分比进度条（需 poll_tool 返回进度）。
     *
     * <p>world-one 在只有 {@code tool_id} 没有 {@code widget_type} 的事件中
     * 自动使用此 widget 作为默认展示。
     */
    public static final String PROGRESS = "sys.progress";

    // ── 工具方法 ─────────────────────────────────────────────────────────────

    /** 检查给定的 widget_type 是否为系统内置 widget（sys.* 前缀）。 */
    public static boolean isSystemWidget(String widgetType) {
        return widgetType != null && widgetType.startsWith(SYS_PREFIX);
    }

    private AippSystemWidget() {}
}
