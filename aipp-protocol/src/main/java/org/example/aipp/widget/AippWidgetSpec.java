package org.example.aipp.widget;

import com.fasterxml.jackson.databind.JsonNode;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * AIPP Widget 协议规格验证器。
 *
 * <p>对应 {@link org.example.aipp.AippAppSpec} 在 App 层面的规格验证，
 * 本类专注于 Widget 维度的两大契约：
 * <ol>
 *   <li><b>Disable 契约</b>：widget 声明支持 disable，且变更类 tool 被禁用时应拒绝执行。</li>
 *   <li><b>Theme 契约</b>：widget 声明支持 theme，且正确映射 {@code --aipp-*} CSS 变量。</li>
 * </ol>
 *
 * <h2>用法</h2>
 * <pre>
 *   AippWidgetSpec spec = new AippWidgetSpec();
 *
 *   // 1. 验证 widget manifest 声明
 *   spec.assertWidgetSupportsDisable(widgetNode);
 *   spec.assertWidgetThemeCoversProperties(widgetNode, "background", "font", "language");
 *
 *   // 2. 验证 disable 行为：变更操作被拦截
 *   spec.assertMutatingToolBlockedWhenDisabled("memory_create", mutateResponse);
 *   spec.assertReadToolWorksWhenDisabled("memory_view", viewResponse);
 *
 *   // 3. 验证 tool 响应携带 disabled 原因
 *   spec.assertDisabledErrorResponse("memory_create", mutateResponse);
 * </pre>
 *
 * <h2>Widget Manifest 结构（/api/widgets 条目）</h2>
 * <pre>
 * {
 *   "type": "entity-graph",
 *   "source": "builtin",
 *   "supports": {
 *     "disable": true,
 *     "theme": ["background", "surface", "text", "textDim",
 *               "border", "accent", "font", "fontSize", "radius", "language"]
 *   }
 * }
 * </pre>
 *
 * @see AippWidget
 * @see AippWidgetTheme
 */
public class AippWidgetSpec {

    /** 所有合法的 AippWidgetTheme 字段名（与 record 字段严格对应）。 */
    public static final Set<String> THEME_FIELDS = Set.of(
            "background", "surface", "text", "textDim",
            "border", "accent", "font", "fontSize", "radius",
            "language", "darkMode"
    );

    /** 变更类 tool 被 disabled 拦截时，响应中必须包含的 error 值。 */
    public static final String DISABLED_ERROR_CODE = "widget_disabled";

    // ══════════════════════════════════════════════════════════════════════════
    // 1. Widget Manifest 结构规格
    // ══════════════════════════════════════════════════════════════════════════

    /**
     * 验证 widget manifest 包含 {@code supports} 声明块。
     *
     * <p>AIPP 标准要求每个 widget 在 {@code /api/widgets} 响应中
     * 使用 {@code supports} 字段明确声明其能力边界（disable、theme 等）。
     */
    public void assertWidgetHasSupportsDeclaration(JsonNode widget) {
        String type = widget.path("type").asText("(unknown)");
        assertThat(widget.has("supports"))
                .as("[AIPP Widget] '%s'：缺少 'supports' 声明块。"
                        + "Widget Manifest 必须明确声明支持 disable 和 theme 的能力边界。", type)
                .isTrue();
        assertThat(widget.get("supports").isObject())
                .as("[AIPP Widget] '%s'：'supports' 字段必须是对象类型", type)
                .isTrue();
    }

    /**
     * 断言 widget 声明支持 disable 契约（{@code supports.disable = true}）。
     *
     * <p>声明后，widget 实现必须保证：
     * <ul>
     *   <li>变更类 tool 在 disabled 时返回 {@code {"ok": false, "error": "widget_disabled"}}</li>
     *   <li>只读类 tool 在 disabled 时仍正常返回数据</li>
     * </ul>
     */
    public void assertWidgetSupportsDisable(JsonNode widget) {
        assertWidgetHasSupportsDeclaration(widget);
        String type = widget.path("type").asText("(unknown)");
        JsonNode supports = widget.get("supports");
        assertThat(supports.has("disable"))
                .as("[AIPP Widget] '%s'：supports 缺少 'disable' 字段", type).isTrue();
        assertThat(supports.get("disable").isBoolean())
                .as("[AIPP Widget] '%s'：supports.disable 必须是 boolean", type).isTrue();
        assertThat(supports.get("disable").asBoolean())
                .as("[AIPP Widget] '%s'：supports.disable 必须为 true（widget 需实现 disable 契约）", type)
                .isTrue();
    }

    /**
     * 断言 widget 的 {@code supports.theme} 声明覆盖了所有指定字段。
     *
     * @param widget         来自 /api/widgets 的 widget manifest 节点
     * @param requiredFields 要求声明支持的 theme 字段名（必须是 {@link #THEME_FIELDS} 中的值）
     */
    public void assertWidgetThemeCoversProperties(JsonNode widget, String... requiredFields) {
        assertWidgetHasSupportsDeclaration(widget);
        String type = widget.path("type").asText("(unknown)");
        JsonNode supports = widget.get("supports");

        assertThat(supports.has("theme"))
                .as("[AIPP Widget] '%s'：supports 缺少 'theme' 字段（需声明支持的主题属性列表）", type)
                .isTrue();
        assertThat(supports.get("theme").isArray())
                .as("[AIPP Widget] '%s'：supports.theme 必须是数组", type)
                .isTrue();

        java.util.Set<String> declared = new java.util.HashSet<>();
        for (JsonNode n : supports.get("theme")) declared.add(n.asText());

        // Validate declared fields are all known THEME_FIELDS
        declared.forEach(f ->
                assertThat(THEME_FIELDS)
                        .as("[AIPP Widget] '%s'：supports.theme 中的 '%s' 不是合法的 AippWidgetTheme 字段。"
                                + "合法值为：%s", type, f, THEME_FIELDS)
                        .contains(f));

        // Check required fields are declared
        for (String req : requiredFields) {
            assertThat(declared)
                    .as("[AIPP Widget] '%s'：supports.theme 未声明必要字段 '%s'", type, req)
                    .contains(req);
        }
    }

    // ══════════════════════════════════════════════════════════════════════════
    // 2. Disable 行为契约验证
    // ══════════════════════════════════════════════════════════════════════════

    /**
     * 断言变更类 tool 在 widget disabled 时被正确拦截。
     *
     * <p>合规响应必须满足：
     * <ul>
     *   <li>{@code ok == false}</li>
     *   <li>{@code error == "widget_disabled"}</li>
     * </ul>
     *
     * @param toolName         工具名（用于错误提示，如 "memory_create"）
     * @param toolResponse     disabled 状态下调用该工具的实际响应
     */
    public void assertMutatingToolBlockedWhenDisabled(String toolName, JsonNode toolResponse) {
        assertThat(toolResponse.has("ok"))
                .as("[AIPP Widget Disable] '%s'：disabled 时响应缺少 'ok' 字段", toolName)
                .isTrue();
        assertThat(toolResponse.get("ok").asBoolean())
                .as("[AIPP Widget Disable] '%s'：disabled 时变更类 tool 的 ok 必须为 false", toolName)
                .isFalse();
        assertThat(toolResponse.has("error"))
                .as("[AIPP Widget Disable] '%s'：disabled 时响应必须包含 'error' 字段", toolName)
                .isTrue();
        assertThat(toolResponse.get("error").asText())
                .as("[AIPP Widget Disable] '%s'：error 必须为 '%s'", toolName, DISABLED_ERROR_CODE)
                .isEqualTo(DISABLED_ERROR_CODE);
    }

    /**
     * 断言只读类 tool 在 widget disabled 时仍正常返回数据（不被拦截）。
     *
     * <p>合规响应必须满足：不包含 {@code "widget_disabled"} 错误码。
     *
     * @param toolName     工具名（如 "memory_view"）
     * @param toolResponse disabled 状态下调用该只读工具的实际响应
     */
    public void assertReadToolWorksWhenDisabled(String toolName, JsonNode toolResponse) {
        // Read tools must NOT return widget_disabled error
        if (toolResponse.has("error")) {
            assertThat(toolResponse.get("error").asText())
                    .as("[AIPP Widget Disable] '%s'：只读 tool 在 disabled 时不应返回 widget_disabled 错误。"
                            + "只有变更类 tool 需要被拦截。", toolName)
                    .isNotEqualTo(DISABLED_ERROR_CODE);
        }
        // If ok field exists, it should be true for read tools
        if (toolResponse.has("ok")) {
            assertThat(toolResponse.get("ok").asBoolean())
                    .as("[AIPP Widget Disable] '%s'：只读 tool 在 disabled 时 ok 应为 true", toolName)
                    .isTrue();
        }
    }

    // ══════════════════════════════════════════════════════════════════════════
    // 3. Theme CSS 变量映射规格
    // ══════════════════════════════════════════════════════════════════════════

    /**
     * 断言主题 JSON 对象包含所有必要的 CSS 变量键（{@code --aipp-*} 格式）。
     *
     * <p>适用于验证 host 在 DOM 注入时生成的 CSS 变量 Map 是否完整。
     * 合规的 CSS 变量 Map 应至少包含以下键：
     * {@code --aipp-bg, --aipp-surface, --aipp-text, --aipp-text-dim,
     *          --aipp-border, --aipp-accent, --aipp-font, --aipp-font-size, --aipp-radius}
     *
     * @param cssVarsNode 主题 CSS 变量 JSON 对象（key 为 CSS property name）
     */
    public void assertThemeCssVarsComplete(JsonNode cssVarsNode) {
        String[] required = {
                "--aipp-bg", "--aipp-surface", "--aipp-text", "--aipp-text-dim",
                "--aipp-border", "--aipp-accent", "--aipp-font",
                "--aipp-font-size", "--aipp-radius"
        };
        for (String key : required) {
            assertThat(cssVarsNode.has(key))
                    .as("[AIPP Widget Theme] CSS 变量 Map 缺少必要字段 '%s'。"
                            + "AippWidgetTheme.toCssVars() 应覆盖所有标准变量。", key)
                    .isTrue();
            assertThat(cssVarsNode.get(key).asText())
                    .as("[AIPP Widget Theme] CSS 变量 '%s' 值不能为空", key)
                    .isNotBlank();
        }
    }

    /**
     * 断言主题 record 中颜色字段均为合法 hex 格式（{@code #rrggbb} 或 {@code #rrggbbaa}）。
     *
     * @param theme 要验证的主题对象
     */
    public void assertThemeColorsAreValidHex(AippWidgetTheme theme) {
        assertValidHex("background", theme.background());
        assertValidHex("surface",    theme.surface());
        assertValidHex("text",       theme.text());
        assertValidHex("textDim",    theme.textDim());
        assertValidHex("border",     theme.border());
        assertValidHex("accent",     theme.accent());
    }

    private void assertValidHex(String field, String value) {
        assertThat(value)
                .as("[AIPP Widget Theme] theme.%s='%s' 不是合法的 hex 颜色值（期望 #rrggbb 格式）",
                        field, value)
                .matches("#[0-9a-fA-F]{6}([0-9a-fA-F]{2})?");
    }

    /**
     * 断言语言代码为合法的 IETF 语言标签（如 {@code "zh"}、{@code "en"}、{@code "zh-CN"}）。
     */
    public void assertThemeLanguageValid(AippWidgetTheme theme) {
        assertThat(theme.language())
                .as("[AIPP Widget Theme] language='%s' 不是合法的语言代码（期望 IETF 标签如 zh, en, zh-CN）",
                        theme.language())
                .matches("[a-z]{2,3}(-[A-Z]{2})?");
    }

    // ══════════════════════════════════════════════════════════════════════════
    // 4. 辅助：widget manifest 中查找指定 type 的 widget
    // ══════════════════════════════════════════════════════════════════════════

    /**
     * 在 /api/widgets 响应中查找指定类型的 widget manifest。
     * 若未找到直接断言失败。
     */
    public JsonNode findWidget(JsonNode widgetsResponse, String widgetType) {
        for (JsonNode w : widgetsResponse.get("widgets")) {
            if (widgetType.equals(w.path("type").asText())) return w;
        }
        throw new AssertionError("[AIPP Widget] /api/widgets 中未找到 widget type: " + widgetType);
    }

    // ══════════════════════════════════════════════════════════════════════════
    // 5. View / Refresh 协议规格验证
    // ══════════════════════════════════════════════════════════════════════════

    /**
     * 断言 widget manifest 包含 {@code views} 数组，且每个视图具备必要字段。
     *
     * <p>每个视图条目必须包含：
     * <ul>
     *   <li>{@code id}       — 视图唯一标识（非空字符串）</li>
     *   <li>{@code label}    — 人类可读标签（非空字符串）</li>
     *   <li>{@code llm_hint} — LLM 上下文指令（非空字符串）</li>
     * </ul>
     *
     * @param widget widget manifest 节点
     */
    public void assertWidgetDeclaresViews(JsonNode widget) {
        String type = widget.path("type").asText("(unknown)");
        assertThat(widget.has("views"))
                .as("[AIPP Widget View] '%s'：缺少 'views' 字段。"
                        + "Widget 应声明支持的视图列表（多视图 widget）或空数组（单视图 widget）。", type)
                .isTrue();
        assertThat(widget.get("views").isArray())
                .as("[AIPP Widget View] '%s'：'views' 必须是数组", type)
                .isTrue();

        int idx = 0;
        for (JsonNode view : widget.get("views")) {
            final int i = idx++;
            assertThat(view.has("id") && !view.get("id").asText().isBlank())
                    .as("[AIPP Widget View] '%s'.views[%d] 缺少非空 'id' 字段", type, i).isTrue();
            assertThat(view.has("label") && !view.get("label").asText().isBlank())
                    .as("[AIPP Widget View] '%s'.views[%d] 缺少非空 'label' 字段", type, i).isTrue();
            assertThat(view.has("llm_hint") && !view.get("llm_hint").asText().isBlank())
                    .as("[AIPP Widget View] '%s'.views[%d] 缺少非空 'llm_hint' 字段", type, i).isTrue();
        }
    }

    /**
     * 断言 widget manifest 中声明了 {@code refresh_skill}（非空字符串）。
     *
     * <p>{@code refresh_skill} 指定了当 {@code mutating_tools} 执行后，
     * Host 用来刷新 widget 数据展示的 skill 名称。
     */
    public void assertWidgetDeclaresRefreshSkill(JsonNode widget) {
        String type = widget.path("type").asText("(unknown)");
        assertThat(widget.has("refresh_skill"))
                .as("[AIPP Widget View] '%s'：缺少 'refresh_skill' 字段。"
                        + "Widget 应声明用于刷新展示的 skill 名称。", type)
                .isTrue();
        assertThat(widget.get("refresh_skill").asText())
                .as("[AIPP Widget View] '%s'：'refresh_skill' 值不能为空", type)
                .isNotBlank();
    }

    /**
     * 断言 widget manifest 包含 {@code mutating_tools} 数组（至少一个工具）。
     *
     * <p>{@code mutating_tools} 列出所有会改变 widget 数据的工具名，
     * Host 检测到它们被调用后会自动触发 {@code refresh_skill} 兜底刷新。
     */
    public void assertWidgetDeclareMutatingTools(JsonNode widget) {
        String type = widget.path("type").asText("(unknown)");
        assertThat(widget.has("mutating_tools"))
                .as("[AIPP Widget View] '%s'：缺少 'mutating_tools' 字段。", type)
                .isTrue();
        assertThat(widget.get("mutating_tools").isArray())
                .as("[AIPP Widget View] '%s'：'mutating_tools' 必须是数组", type)
                .isTrue();
        assertThat(widget.get("mutating_tools").size())
                .as("[AIPP Widget View] '%s'：'mutating_tools' 不能为空数组", type)
                .isGreaterThan(0);
        for (JsonNode tool : widget.get("mutating_tools")) {
            assertThat(tool.asText())
                    .as("[AIPP Widget View] '%s'：mutating_tools 中包含空工具名", type)
                    .isNotBlank();
        }
    }

    /**
     * 断言 widget 包含指定的视图 id。
     *
     * @param widget    widget manifest
     * @param viewIds   期望存在的视图 id 列表
     */
    public void assertWidgetHasViews(JsonNode widget, String... viewIds) {
        assertWidgetDeclaresViews(widget);
        String type = widget.path("type").asText("(unknown)");
        java.util.Set<String> declared = new java.util.HashSet<>();
        for (JsonNode v : widget.get("views")) declared.add(v.path("id").asText());
        for (String viewId : viewIds) {
            assertThat(declared)
                    .as("[AIPP Widget View] '%s'：views 中未声明必要视图 id '%s'", type, viewId)
                    .contains(viewId);
        }
    }

    // ══════════════════════════════════════════════════════════════════════════
    // 5. App Identity 规格（app_id / is_main / is_canvas_mode）
    // ══════════════════════════════════════════════════════════════════════════

    /**
     * 验证 widget manifest 包含 {@code app_id} 字段，且值非空。
     *
     * <p>每个 widget 必须声明归属的 AIPP 应用 ID，供 Host 的 Apps 面板做应用-widget 关联。
     */
    public void assertWidgetHasAppId(JsonNode widget) {
        String type = widget.path("type").asText("(unknown)");
        assertThat(widget.has("app_id"))
                .as("[AIPP App Identity] '%s'：缺少 'app_id' 字段。"
                        + "Widget 必须声明所属 AIPP 应用 ID（与 /api/app.app_id 一致）。", type)
                .isTrue();
        assertThat(widget.path("app_id").asText())
                .as("[AIPP App Identity] '%s'：'app_id' 不能为空", type)
                .isNotBlank();
    }

    /**
     * 验证 widget manifest 声明了 {@code is_main} 字段（boolean 类型）。
     *
     * <p>每个 app 最多只有一个 {@code is_main=true} 的 widget；
     * 没有主 widget 的 app 在 Apps 面板中点击图标时无法跳转。
     */
    public void assertWidgetDeclaresIsMain(JsonNode widget) {
        String type = widget.path("type").asText("(unknown)");
        assertThat(widget.has("is_main"))
                .as("[AIPP App Identity] '%s'：缺少 'is_main' 字段。"
                        + "Widget 必须显式声明是否为所属 app 的主界面（入口 widget）。", type)
                .isTrue();
        assertThat(widget.get("is_main").isBoolean())
                .as("[AIPP App Identity] '%s'：'is_main' 必须是 boolean 类型", type)
                .isTrue();
    }

    /**
     * 验证 widget manifest 声明了 {@code is_canvas_mode} 字段（boolean 类型）。
     *
     * <p>此字段决定 Host 如何渲染 widget 的输出：
     * <ul>
     *   <li>{@code true}  — Canvas 模式（全屏替换聊天区）</li>
     *   <li>{@code false} — Chat 内嵌模式（html_widget 卡片嵌入聊天流）</li>
     * </ul>
     */
    public void assertWidgetDeclaresIsCanvasMode(JsonNode widget) {
        String type = widget.path("type").asText("(unknown)");
        assertThat(widget.has("is_canvas_mode"))
                .as("[AIPP App Identity] '%s'：缺少 'is_canvas_mode' 字段。"
                        + "Widget 必须声明展示模式：true=Canvas 全屏，false=Chat 内嵌 HTML 卡片。", type)
                .isTrue();
        assertThat(widget.get("is_canvas_mode").isBoolean())
                .as("[AIPP App Identity] '%s'：'is_canvas_mode' 必须是 boolean 类型", type)
                .isTrue();
    }

    /**
     * 一次验证 widget 的全部 App Identity 字段（app_id + is_main + is_canvas_mode）。
     */
    public void assertWidgetHasFullAppIdentity(JsonNode widget) {
        assertWidgetHasAppId(widget);
        assertWidgetDeclaresIsMain(widget);
        assertWidgetDeclaresIsCanvasMode(widget);
    }

    // ══════════════════════════════════════════════════════════════════════════
    // 6. App-owned renderer 规格（render.kind/url）
    // ══════════════════════════════════════════════════════════════════════════

    /**
     * 验证 widget manifest 声明了由 AIPP app 自己提供的前端 renderer。
     *
     * <p>Host 只能根据此声明挂载 widget，不能在 host 代码中内置 app 专属 UI。
     * 合规结构：
     * <pre>
     * {
     *   "render": {
     *     "kind": "esm",
     *     "url":  "/widgets/action-list/action-list.js"
     *   }
     * }
     * </pre>
     *
     * <p>{@code sys.*} widget 是 host/system widget，可不声明 app-owned renderer。
     */
    public void assertWidgetDeclaresAppOwnedRenderer(JsonNode widget) {
        String type = widget.path("type").asText("(unknown)");
        assertThat(type.startsWith("sys."))
                .as("[AIPP Widget Renderer] '%s' 是 sys.* host widget，不应使用 app-owned renderer 断言。", type)
                .isFalse();
        assertThat(widget.has("render"))
                .as("[AIPP Widget Renderer] '%s'：缺少 'render'。"
                        + "AIPP app 自己相关的 UI 必须由 app 声明 renderer，Host 只负责挂载。", type)
                .isTrue();
        JsonNode render = widget.get("render");
        assertThat(render.isObject())
                .as("[AIPP Widget Renderer] '%s'：'render' 必须是对象", type)
                .isTrue();
        assertThat(render.has("kind"))
                .as("[AIPP Widget Renderer] '%s'：render 缺少 'kind'", type)
                .isTrue();
        String kind = render.path("kind").asText();
        assertThat(kind)
                .as("[AIPP Widget Renderer] '%s'：render.kind 必须是 esm", type)
                .isEqualTo("esm");
        assertThat(render.has("url"))
                .as("[AIPP Widget Renderer] '%s'：render 缺少 'url'", type)
                .isTrue();
        String url = render.path("url").asText();
        assertThat(url)
                .as("[AIPP Widget Renderer] '%s'：render.url 不能为空", type)
                .isNotBlank();
        assertThat(url.startsWith("http://") || url.startsWith("https://") || url.startsWith("/"))
                .as("[AIPP Widget Renderer] '%s'：render.url 必须是绝对 URL 或 app-relative path", type)
                .isTrue();
    }

    // ══════════════════════════════════════════════════════════════════════════
    // 7. html_widget 响应规格（is_canvas_mode=false 的 widget 适用）
    // ══════════════════════════════════════════════════════════════════════════

    /**
     * 验证工具响应中包含合法的 {@code html_widget} 字段。
     *
     * <p>适用于 {@code is_canvas_mode=false} 的 widget：工具响应以 app-owned widget 卡片形式嵌入聊天流。
     * 响应格式：
     * <pre>
     * {
     *   "html_widget": {
     *     "widget_type": "action-list",
     *     "title": "Action List",
     *     "data": { ... }
     *   }
     * }
     * </pre>
     *
     * @param toolName  工具名（用于错误信息）
     * @param response  工具响应 JSON
     */
    public void assertHtmlWidgetResponse(String toolName, JsonNode response) {
        assertThat(response.has("html_widget"))
                .as("[AIPP html_widget] '%s'：响应缺少 'html_widget' 字段。"
                        + "is_canvas_mode=false 的 widget 必须通过 html_widget 返回 HTML 卡片内容。", toolName)
                .isTrue();

        JsonNode hw = response.get("html_widget");
        assertThat(hw.isObject())
                .as("[AIPP html_widget] '%s'：'html_widget' 必须是对象类型", toolName)
                .isTrue();

        assertThat(hw.has("widget_type") && hw.has("data"))
                .as("[AIPP html_widget] '%s'：'html_widget' 必须包含 Plan-D 字段 'widget_type' + 'data'",
                        toolName)
                .isTrue();

        assertThat(hw.path("widget_type").asText())
                .as("[AIPP html_widget] '%s'：'html_widget.widget_type' 不能为空", toolName)
                .isNotBlank();
        assertThat(hw.get("data").isObject())
                .as("[AIPP html_widget] '%s'：'html_widget.data' 必须是对象", toolName)
                .isTrue();

        if (hw.has("height")) {
            String h = hw.get("height").asText();
            assertThat(h)
                    .as("[AIPP html_widget] '%s'：'height' 不能为空字符串", toolName)
                    .isNotBlank();
        }
    }

    /**
     * 断言 is_canvas_mode=false 的工具响应不包含 canvas 字段。
     *
     * <p>Chat 内嵌模式的 widget 工具不应触发 canvas 事件，避免误切换到 Canvas Mode。
     */
    public void assertInlineWidgetResponseHasNoCanvas(String toolName, JsonNode response) {
        assertThat(response.has("canvas"))
                .as("[AIPP html_widget] '%s'：is_canvas_mode=false 的工具响应不应包含 'canvas' 字段，"
                        + "否则会误触发 Canvas Mode 切换。", toolName)
                .isFalse();
    }

    // ══════════════════════════════════════════════════════════════════════════
    // 8. Upload 能力规格验证
    // ══════════════════════════════════════════════════════════════════════════

    /**
     * 断言 widget manifest 包含合法的顶层 {@code upload} 配置块。
     *
     * <p>合规的 upload 配置必须满足：
     * <ul>
     *   <li>存在 {@code upload} 字段且为对象</li>
     *   <li>{@code upload.accept} 为非空数组</li>
     *   <li>{@code upload.prompt} 为非空字符串</li>
     *   <li>{@code upload.tools} 为数组（可为空）</li>
     * </ul>
     */
    public void assertWidgetSupportsUpload(JsonNode widget) {
        String type = widget.path("type").asText("(unknown)");
        assertThat(widget.has("upload"))
                .as("[AIPP Widget Upload] '%s'：缺少顶层 'upload' 配置块。"
                        + "声明上传能力的 widget 必须在 manifest 中包含 upload 字段。", type)
                .isTrue();
        assertThat(widget.get("upload").isObject())
                .as("[AIPP Widget Upload] '%s'：'upload' 字段必须是对象", type)
                .isTrue();

        JsonNode upload = widget.get("upload");

        // accept
        assertThat(upload.has("accept") && upload.get("accept").isArray())
                .as("[AIPP Widget Upload] '%s'：upload.accept 必须是数组", type).isTrue();
        assertThat(upload.get("accept").size())
                .as("[AIPP Widget Upload] '%s'：upload.accept 不能为空", type).isGreaterThan(0);

        // prompt
        assertThat(upload.has("prompt") && !upload.get("prompt").asText().isBlank())
                .as("[AIPP Widget Upload] '%s'：upload.prompt 必须是非空字符串，"
                        + "用于告知 LLM 如何处理上传的文件内容。", type)
                .isTrue();

        // tools（允许为空数组，但必须是数组类型）
        if (upload.has("tools")) {
            assertThat(upload.get("tools").isArray())
                    .as("[AIPP Widget Upload] '%s'：upload.tools 必须是数组", type).isTrue();
        }
    }

    /**
     * 断言 upload.accept 中每个扩展名格式合法：必须以 {@code .} 开头、全小写、长度 2-10。
     *
     * <p>不支持二进制格式（如 .xlsx、.docx、.pdf），upload 仅用于文本文件。
     * 常见合法值：{@code .json}、{@code .csv}、{@code .txt}、{@code .yaml}。
     */
    public void assertUploadExtensionsWellFormed(JsonNode widget) {
        assertWidgetSupportsUpload(widget);
        String type = widget.path("type").asText("(unknown)");
        for (JsonNode ext : widget.get("upload").get("accept")) {
            String s = ext.asText();
            assertThat(s)
                    .as("[AIPP Widget Upload] '%s'：accept 中的扩展名 '%s' 格式不合法。"
                            + "必须以 '.' 开头、全小写字母、总长度 2–10，如 .json / .csv。", type, s)
                    .matches("\\.[a-z]{1,9}");
        }
    }

    /**
     * 断言 upload.accept 覆盖了所有指定的扩展名。
     *
     * @param widget     widget manifest
     * @param extensions 期望被声明的扩展名（如 {@code ".json", ".csv"}）
     */
    public void assertUploadAccepts(JsonNode widget, String... extensions) {
        assertWidgetSupportsUpload(widget);
        String type = widget.path("type").asText("(unknown)");
        java.util.Set<String> declared = new java.util.HashSet<>();
        for (JsonNode ext : widget.get("upload").get("accept")) declared.add(ext.asText());
        for (String required : extensions) {
            assertThat(declared)
                    .as("[AIPP Widget Upload] '%s'：upload.accept 未声明扩展名 '%s'", type, required)
                    .contains(required);
        }
    }

    /**
     * 断言 upload.tools 中包含指定的工具名。
     *
     * @param widget    widget manifest
     * @param toolNames 期望存在的工具名
     */
    public void assertUploadTools(JsonNode widget, String... toolNames) {
        assertWidgetSupportsUpload(widget);
        String type = widget.path("type").asText("(unknown)");
        JsonNode uploadNode = widget.get("upload");
        assertThat(uploadNode.has("tools") && uploadNode.get("tools").isArray())
                .as("[AIPP Widget Upload] '%s'：upload.tools 字段缺失或非数组", type).isTrue();
        java.util.Set<String> declared = new java.util.HashSet<>();
        for (JsonNode t : uploadNode.get("tools")) declared.add(t.asText());
        for (String required : toolNames) {
            assertThat(declared)
                    .as("[AIPP Widget Upload] '%s'：upload.tools 未声明工具 '%s'", type, required)
                    .contains(required);
        }
    }

    /**
     * 使用 {@link AippWidgetUpload} 对象验证消息组装结果的完整性。
     *
     * <p>组装后的消息必须同时包含文件名和文件内容。
     *
     * @param upload      upload 配置对象
     * @param fileName    文件名
     * @param fileContent 文件内容
     */
    public void assertUploadMessageAssembly(AippWidgetUpload upload,
                                            String fileName, String fileContent) {
        String msg = upload.assembleMessage(fileName, 0, fileContent);
        assertThat(msg)
                .as("[AIPP Widget Upload] assembleMessage 结果应包含文件名 '%s'", fileName)
                .contains(fileName);
        assertThat(msg)
                .as("[AIPP Widget Upload] assembleMessage 结果应包含文件内容")
                .contains(fileContent);
        assertThat(msg)
                .as("[AIPP Widget Upload] assembleMessage 结果应包含 upload.prompt 内容")
                .contains(upload.prompt());
    }
}
