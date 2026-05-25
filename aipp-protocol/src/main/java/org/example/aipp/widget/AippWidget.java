package org.example.aipp.widget;

/**
 * AIPP 标准 Widget 生命周期接口。
 *
 * <p>任何可嵌入 AIPP host（如 world-one）的 Widget 服务端代理对象均需实现此接口，
 * 以保证 host 可统一管理 widget 的 disabled 状态和主题。
 *
 * <h2>核心协议约定</h2>
 * <ol>
 *   <li><b>Disable 契约</b>：{@link #setDisabled(boolean)} 被调用后，所有
 *       <em>变更类</em>（create / update / delete）tool 调用必须被拦截，
 *       返回 {@code {"ok": false, "error": "widget_disabled"}}；
 *       <em>只读类</em>（query / view）tool 调用不受影响。</li>
 *   <li><b>Theme 契约</b>：{@link #applyTheme(AippWidgetTheme)} 被调用后，
 *       widget 下次渲染时必须使用新主题的 CSS 变量（通过 {@code --aipp-*} 注入）。</li>
 *   <li><b>类型唯一性</b>：{@link #widgetType()} 必须与 {@code /api/widgets} 中注册的
 *       {@code type} 字段完全一致。</li>
 * </ol>
 *
 * <h2>前端对应协议</h2>
 * <p>后端实现此接口后，host 通过以下方式在前端传递状态：
 * <pre>
 *   // disabled 状态
 *   containerEl.dataset.aippDisabled = 'true';  // 或 'false'
 *   // 主题变量
 *   Object.entries(theme.toCssVars()).forEach(([k, v]) =>
 *     containerEl.style.setProperty(k, v));
 * </pre>
 * Widget 前端代码监听 {@code data-aipp-disabled} 变化即可响应只读模式切换。
 *
 * <h2>Widget Manifest 声明</h2>
 * <p>在 {@code /api/widgets} 响应中，每个 widget 需声明其支持能力：
 * <pre>
 * {
 *   "type": "entity-graph",
 *   "source": "builtin",
 *   "supports": {
 *     "disable": true,
 *     "theme": ["background", "surface", "text", "accent", "font", "language"]
 *   }
 * }
 * </pre>
 *
 * @see AippWidgetTheme
 * @see AippWidgetSpec
 */
public interface AippWidget {

    /**
     * 返回此 widget 的全局唯一类型标识符，必须与 {@code /api/widgets} 注册的 type 一致。
     * 例如：{@code "entity-graph"}、{@code "memory-manager"}。
     */
    String widgetType();

    /**
     * 设置 widget 的 disabled 状态。
     *
     * <p>{@code disabled=true} 时，所有变更类 tool（create/update/delete）
     * 必须被拦截，返回：
     * <pre>{"ok": false, "error": "widget_disabled"}</pre>
     * 只读类 tool（query/view）不受影响。
     *
     * <p>此方法应幂等，多次设置相同值不产生副作用。
     */
    void setDisabled(boolean disabled);

    /** 返回当前 disabled 状态。 */
    boolean isDisabled();

    /**
     * 应用新主题配置。
     *
     * <p>调用后 widget 应将主题的 CSS 变量（通过 {@link AippWidgetTheme#toCssVars()}）
     * 注入到其 DOM 根容器，下次渲染即生效。
     *
     * <p>此方法应幂等，重复应用相同主题不应有副作用。
     */
    void applyTheme(AippWidgetTheme theme);

    /** 返回当前生效的主题，若未显式设置则返回 {@link AippWidgetTheme#darkDefault()}。 */
    AippWidgetTheme currentTheme();

    // ── View / Refresh 契约（可选，default 实现为"不支持"）──────────────────

    /**
     * 声明此 widget 支持的可导航视图列表（如多个 Tab）。
     *
     * <p>返回的列表来源于 {@code /api/widgets} manifest 中的 {@code views} 数组。
     * 若 widget 没有多视图概念（只有一种展示形式），返回空列表。
     *
     * <p>Host 根据此列表在 LLM system prompt 中注入对应视图的 {@link AippWidgetView#llmHint}。
     */
    default java.util.List<AippWidgetView> views() { return java.util.List.of(); }

    /**
     * 返回用于刷新此 widget 数据展示的 skill 名称。
     *
     * <p>当 Host 检测到 {@link #mutatingTools()} 中的工具被调用后，
     * 若 LLM 未主动调用此 skill，Host 会在本轮结束后自动调用一次（兜底刷新）。
     *
     * @return skill 名称（如 {@code "memory_view"}），不支持时返回 {@code null}
     */
    default String refreshSkill() { return null; }

    /**
     * 返回会变更此 widget 数据的工具名称集合。
     *
     * <p>Host 检测到这些工具被调用后，会自动触发 {@link #refreshSkill()} 兜底刷新。
     * 来源于 {@code /api/widgets} manifest 的 {@code mutating_tools} 数组。
     */
    default java.util.Set<String> mutatingTools() { return java.util.Set.of(); }

    // ── App Identity 契约（可选，default 实现为"无归属"）────────────────────

    /**
     * 返回此 widget 归属的 AIPP 应用 ID（与 {@code /api/app} 中的 {@code app_id} 一致）。
     *
     * <p>Host 利用此字段在 Apps 启动面板中将 widget 与其所属应用关联，
     * 点击应用图标时可找到对应的 main widget。
     *
     * @return 应用 ID（如 {@code "memory-one"}），未归属时返回 {@code null}
     */
    default String appId() { return null; }

    /**
     * 返回此 widget 是否为所属 AIPP 应用的主界面（入口 widget）。
     *
     * <p>一个 AIPP 应用可有多个 widget，{@code isMain=true} 标记用户从 Apps 面板点击
     * 应用图标时默认打开的 widget。每个 app 最多只有一个 {@code isMain=true} widget。
     */
    default boolean isMain() { return false; }

    /**
     * 返回此 widget 是否以 Canvas 模式展示（全屏替换聊天区）。
     *
     * <p>
     * <ul>
     *   <li>{@code true}（默认）— Canvas 模式：widget 替换聊天区，进入专属操作视图</li>
     *   <li>{@code false}       — Chat 内嵌模式：widget 以 HTML 卡片形式渲染在聊天消息流中，
     *       聊天区仍然可用，不会产生 task session</li>
     * </ul>
     *
     * <p>Chat 内嵌模式的 widget 响应通过工具返回 {@code html_widget} 字段触发渲染：
     * <pre>
     * { "html_widget": { "widget_type": "stats-card", "title": "统计摘要", "data": { ... } } }
     * </pre>
     * Host 根据 {@code widget_type} 的 {@code render.kind=esm} 声明加载 app-owned ES module，
     * 并将 {@code data} 传给 widget。
     * {@code title} 字段为必选，Host 在聊天历史的"已处理"卡片（如"统计摘要 · 已在界面上打开"）
     * 及调试日志中使用；应为 2–8 字的名词短语，无需包含动词。
     */
    default boolean isCanvasMode() { return true; }

    // ── Upload 契约（可选）────────────────────────────────────────────────────

    /**
     * 返回此 widget 的上传能力配置。
     *
     * <p>非 null 时，Host 在聊天输入框旁显示上传按钮；用户选择文件后，
     * Host 将文件内容与 {@link AippWidgetUpload#prompt()} 组装成消息交给 LLM 处理，
     * LLM 根据 prompt 指示调用 {@link AippWidgetUpload#tools()} 中声明的工具。
     *
     * <p>对应 widget manifest 的顶层 {@code upload} 字段。
     * 返回 {@code null} 表示此 widget 不支持文件上传。
     */
    default AippWidgetUpload upload() { return null; }
}
