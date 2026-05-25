package org.example.aipp;

import com.fasterxml.jackson.databind.JsonNode;

import java.util.HashSet;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 通用 AIPP 协议规格验证器。
 *
 * <h2>Host 解耦原则（强制约束）</h2>
 *
 * <p>Host（World One 等通用 agent shell）<b>不得</b>对任何具体 AIPP 名字、tool 名、
 * widget 类型或领域词（如 "world / memory / 本体 / 入职 / decision / action / HR" 等）
 * 做特判或 if/else 分支。所有 AIPP 特化行为必须通过本协议字段自描述：
 *
 * <ul>
 *   <li><b>{@code lifecycle}</b>（skill 级，可选）：声明 skill 自动调度时机。
 *       合法值：{@code "on_demand"}（默认，由 LLM 驱动）/ {@code "post_turn"}
 *       （每轮对话结束后 host 异步执行，替代 memory_consolidate 的硬编码）/
 *       {@code "pre_turn"}（每轮对话开始前注入上下文，等价旧 {@code auto_pre_turn=true}）。</li>
 *
 *   <li><b>{@code output_widget_rules}</b>（skill 级，可选）：声明工具响应到 widget 的渲染规则。
 *       <pre>
 *       {
 *         "force_canvas_when": ["graph","session_id"],  // 响应中所有这些字段都非空 → 强制 canvas
 *         "default_widget":    "entity-graph"           // 兜底 widget_type
 *       }
 *       </pre>
 *       替代 host 对具体 skill+字段组合的硬编码（旧：world_design + graph + session_id）。</li>
 *
 *   <li><b>{@code runtime_event_callback}</b>（skill / app 级，可选）：声明本 AIPP 接收
 *       哪些通用运行时事件，以及收件 HTTP 路径。
 *       <pre>
 *       {
 *         "events": ["decision_result","action_resume"],
 *         "path":   "/api/worlds/{worldId}/decision-result"
 *       }
 *       </pre>
 *       Host 的 ExecutorRoutingService 据此路由，{@code {worldId}} 等占位符由调用上下文填充。
 *       替代 ExecutorRoutingService 中对 world_register_action 的硬编码定位。</li>
 *
 *   <li><b>{@code event_subscriptions}</b>（app 级，{@code /api/tools} 顶层数组）：
 *       声明本 AIPP 订阅哪些 host 事件，host 通过通用事件总线 POST 到 app 的
 *       {@code /api/events}（payload: {@code {"type": "...", "data": {...}}}）。
 *       例如 memory-one 声明 {@code ["workspace.changed"]} 替代 host 硬编码调用
 *       {@code /api/memory_workspace_join}。</li>
 *
 *   <li><b>{@code display_label_zh}</b> / <b>{@code display_name}</b>（skill / tool 级，可选）：
 *       UI 中显示的人话名。Host 通过 {@code GET /api/tool-labels} 暴露聚合字典，前端动态查表，
 *       替代前端硬编码的 {@code TOOL_LABELS} 字典。</li>
 * </ul>
 *
 * <p>注释 / Javadoc 中可以保留 AIPP 名字举例作为说明性文字，但<b>运行代码不准依赖名字</b>。
 *
 * <p>任何 AI Plugin Program（.aipp 应用）的测试类均可使用本工具类，
 * 通过调用其方法来验证自身 API 是否符合 AIPP 协议三层规范。
 *
 * <h2>用法</h2>
 * <pre>
 *   AippAppSpec spec = new AippAppSpec();
 *   spec.assertValidToolsApiStructure(toolsJson);
 *   spec.assertValidSkillsApiStructure(skillsJson);
 *   spec.assertValidWidgetsApiStructure(widgetsJson);
 *   spec.assertWidgetTypesRegistered(toolsJson, widgetsJson);
 * </pre>
 *
 * <h2>Tool / Skill 二元模型（README §3 / §4）</h2>
 * <ul>
 *   <li><b>Tool</b>：原子能力，单次调用单次响应。LLM 看到完整 schema（name/description/parameters）。
 *       服务端可在 Java/Python 内部串多个原子调用，但对 LLM 是黑盒。Tool entry <b>禁止</b>
 *       含 {@code prompt} / {@code tools[]} / {@code resources}（旧 mini-agent 残留字段）。</li>
 *   <li><b>Skill</b>：渐进披露的多步剧本。索引仅含 {@code name + description + allowed_tools +
 *       playbook_url}，按需加载 SKILL.md 正文。LLM 编排责任在 Skill，不在 Tool description。</li>
 * </ul>
 *
 * <h2>AIPP 核心接口</h2>
 * <ul>
 *   <li>GET /api/app                      — app 元信息</li>
 *   <li>GET /api/tools                    — 原子 tool 清单</li>
 *   <li>GET /api/skills                   — Skill 索引（progressive disclosure）</li>
 *   <li>GET /api/skills/{name}/playbook   — SKILL.md 正文（text/markdown）</li>
 *   <li>GET /api/widgets                  — widget 清单</li>
 *   <li>POST /api/tools/{name}            — tool 执行（LLM/UI/Host 三方均可调）</li>
 *   <li>POST /api/events                  — 事件订阅方接收 host 事件（仅订阅方实现）</li>
 * </ul>
 *
 * <h2>Canvas 触发规则</h2>
 * <ul>
 *   <li>canvas.triggers=true  → host 根据 Widget Manifest 生成 canvas 事件（tool 响应为纯数据）</li>
 *   <li>canvas.triggers=false → agent 保持 Chat Mode 或 fallback 生成 HTML</li>
 *   <li>canvas.widget_type 必须在 /api/widgets 中已注册</li>
 * </ul>
 */
public class AippAppSpec {

    /**
     * canvas.action 合法值集合。
     * <ul>
     *   <li>{@code open}    — 打开新 widget（推入导航栈）；sys.* widget 以模态覆盖层渲染</li>
     *   <li>{@code patch}   — 增量更新已有 widget 状态</li>
     *   <li>{@code replace} — 替换当前 widget（同类型时等同 patch）</li>
     *   <li>{@code close}   — 关闭当前 widget（弹出导航栈）</li>
     *   <li>{@code inline}  — 在 chat 消息流中嵌入轻量卡片（不进入导航栈）</li>
     * </ul>
     */
    private static final Set<String> VALID_CANVAS_ACTIONS = Set.of("open", "patch", "replace", "close", "inline");

    /**
     * 合法的 {@code lifecycle} 值。
     * <ul>
     *   <li>{@code on_demand}：默认；由 LLM 在工具列表中按需调用。</li>
     *   <li>{@code post_turn}：每轮对话结束后 host 异步执行（替代 memory_consolidate 硬编码）。</li>
     *   <li>{@code pre_turn}：每轮对话开始前 host 注入上下文（等价旧 auto_pre_turn=true）。</li>
     * </ul>
     */
    public static final Set<String> VALID_LIFECYCLES = Set.of("on_demand", "post_turn", "pre_turn");

    // ══════════════════════════════════════════════════════════════════════════
    // Host 解耦协议字段断言（lifecycle / output_widget_rules / runtime_event_callback / event_subscriptions）
    // ══════════════════════════════════════════════════════════════════════════

    /**
     * 断言 skill 的 {@code lifecycle} 字段（若存在）取自 {@link #VALID_LIFECYCLES}。
     * 缺省视为 {@code "on_demand"}。
     */
    public void assertValidLifecycle(JsonNode skill) {
        String name = skill.path("name").asText("(unknown)");
        if (!skill.has("lifecycle")) return;
        String v = skill.get("lifecycle").asText("");
        assertThat(VALID_LIFECYCLES)
                .as("[AIPP] skill '%s' lifecycle='%s' 不合法，必须是 %s 之一",
                        name, v, VALID_LIFECYCLES)
                .contains(v);
    }

    /**
     * 断言 skill 的 {@code output_widget_rules}（若存在）结构合法。
     * <ul>
     *   <li>{@code force_canvas_when}（可选）：字符串数组，非空字符串。</li>
     *   <li>{@code default_widget}（可选）：非空字符串。</li>
     * </ul>
     */
    public void assertValidOutputWidgetRules(JsonNode skill) {
        String name = skill.path("name").asText("(unknown)");
        if (!skill.has("output_widget_rules")) return;
        JsonNode rules = skill.get("output_widget_rules");
        assertThat(rules.isObject())
                .as("[AIPP] skill '%s' output_widget_rules 必须是对象", name).isTrue();
        if (rules.has("force_canvas_when")) {
            JsonNode arr = rules.get("force_canvas_when");
            assertThat(arr.isArray())
                    .as("[AIPP] skill '%s' output_widget_rules.force_canvas_when 必须是数组", name)
                    .isTrue();
            for (JsonNode f : arr) {
                assertThat(f.isTextual() && !f.asText().isBlank())
                        .as("[AIPP] skill '%s' force_canvas_when 项必须为非空字符串", name)
                        .isTrue();
            }
        }
        if (rules.has("default_widget")) {
            assertThat(rules.get("default_widget").asText(""))
                    .as("[AIPP] skill '%s' output_widget_rules.default_widget 不能为空", name)
                    .isNotBlank();
        }
    }

    /**
     * 断言 {@code runtime_event_callback} 结构合法：必须含非空的 {@code events}（字符串数组）
     * 与非空的 {@code path} 字符串。
     */
    public void assertValidRuntimeEventCallback(JsonNode skill) {
        String name = skill.path("name").asText("(unknown)");
        if (!skill.has("runtime_event_callback")) return;
        JsonNode cb = skill.get("runtime_event_callback");
        assertThat(cb.isObject())
                .as("[AIPP] skill '%s' runtime_event_callback 必须是对象", name).isTrue();
        assertThat(cb.has("events") && cb.get("events").isArray() && cb.get("events").size() > 0)
                .as("[AIPP] skill '%s' runtime_event_callback.events 必须为非空字符串数组", name)
                .isTrue();
        for (JsonNode e : cb.get("events")) {
            assertThat(e.isTextual() && !e.asText().isBlank())
                    .as("[AIPP] skill '%s' runtime_event_callback.events 元素必须为非空字符串", name)
                    .isTrue();
        }
        assertThat(cb.path("path").asText(""))
                .as("[AIPP] skill '%s' runtime_event_callback.path 不能为空", name)
                .isNotBlank();
    }

    /**
     * 断言 {@code event_subscriptions} 是非空字符串组成的数组。
     */
    public void assertValidEventSubscriptions(JsonNode subs) {
        assertThat(subs != null && subs.isArray())
                .as("[AIPP] event_subscriptions 必须是数组").isTrue();
        for (JsonNode s : subs) {
            assertThat(s.isTextual() && !s.asText().isBlank())
                    .as("[AIPP] event_subscriptions 元素必须为非空字符串").isTrue();
        }
    }

    // ══════════════════════════════════════════════════════════════════════════
    // 1. GET /api/skills 结构规格
    // ══════════════════════════════════════════════════════════════════════════

    /**
     * 验证 {@code /api/tools} 响应的顶层结构。
     *
     * <p>必须包含：{@code app} / {@code version} / {@code tools}；{@code tools}
     * 为数组且非空。每个 tool 通过 {@link #assertValidSkillStructure} 校验为
     * 原子 Tool 形态（name / description / parameters / canvas + 顶层 visibility/scope；
     * **禁止** {@code prompt} / {@code tools[]} / {@code resources}）。
     * Widget-scoped UI-only tool 仅校验 name + parameters（UI 通过 ToolProxy 调用，
     * 不需要完整 schema）。
     */
    public void assertValidToolsApiStructure(JsonNode toolsResponse) {
        assertThat(toolsResponse.has("app"))
                .as("[AIPP] /api/tools 响应缺少 'app' 字段").isTrue();
        assertThat(toolsResponse.has("version"))
                .as("[AIPP] /api/tools 响应缺少 'version' 字段").isTrue();
        assertThat(toolsResponse.has("tools"))
                .as("[AIPP] /api/tools 响应缺少 'tools' 字段").isTrue();
        assertThat(toolsResponse.get("tools").isArray())
                .as("[AIPP] 'tools' 字段必须是数组").isTrue();
        assertThat(toolsResponse.get("tools").size())
                .as("[AIPP] 'tools' 数组不能为空").isGreaterThan(0);

        for (JsonNode tool : toolsResponse.get("tools")) {
            // widget-scoped UI-only 工具可能只有最小占位 schema（无 canvas/prompt/tools），
            // 跳过三层校验；其余条目按 Tool 三层规格校验。
            JsonNode scope = tool.path("scope");
            boolean widgetUiOnly = "widget".equals(scope.path("level").asText())
                    && isUiOnly(tool.path("visibility"));
            if (widgetUiOnly) {
                assertThat(tool.has("name"))
                        .as("[AIPP] widget-scoped tool 缺少 'name'").isTrue();
                assertValidParametersSchema(tool.path("name").asText("(unknown)"),
                                            tool.path("parameters"));
            } else {
                assertValidSkillStructure(tool);
            }
        }
    }

    private static boolean isUiOnly(JsonNode visibility) {
        if (!visibility.isArray() || visibility.size() == 0) return false;
        for (JsonNode v : visibility) {
            if (!"ui".equals(v.asText())) return false;
        }
        return true;
    }

    /**
     * 验证 {@code /api/skills} 响应的顶层结构（Skill Playbook 索引）。
     *
     * <p>必须包含：{@code app} / {@code version} / {@code skills}；{@code skills}
     * 为数组，允许为空（尚未定义任何 Skill playbook 时）。若非空，则每条必须满足：
     * <ul>
     *   <li>{@code name}：非空字符串（snake_case，与 playbook URL 路径一致）</li>
     *   <li>{@code description}：40 - 1024 字符，含 WHEN clause（详见 README §4.4）</li>
     *   <li>{@code allowed_tools}：非空字符串数组</li>
     *   <li>{@code playbook_url}：非空字符串</li>
     * </ul>
     */
    public void assertValidSkillsApiStructure(JsonNode skillsResponse) {
        assertThat(skillsResponse.has("app"))
                .as("[AIPP] /api/skills 响应缺少 'app' 字段").isTrue();
        assertThat(skillsResponse.has("version"))
                .as("[AIPP] /api/skills 响应缺少 'version' 字段").isTrue();
        assertThat(skillsResponse.has("skills"))
                .as("[AIPP] /api/skills 响应缺少 'skills' 字段").isTrue();
        assertThat(skillsResponse.get("skills").isArray())
                .as("[AIPP] 'skills' 字段必须是数组").isTrue();

        for (JsonNode skill : skillsResponse.get("skills")) {
            assertThat(skill.has("name"))
                    .as("[AIPP] skill-index 条目缺少 'name' 字段").isTrue();
            String name = skill.path("name").asText();
            assertThat(name)
                    .as("[AIPP] skill-index 条目 'name' 不能为空").isNotBlank();
            assertThat(skill.has("description"))
                    .as("[AIPP] skill '%s' 缺少 'description'", name).isTrue();
            int descLen = skill.path("description").asText("").length();
            assertThat(descLen)
                    .as("[AIPP] skill '%s' description 长度 %d 越界 [40,1024]", name, descLen)
                    .isBetween(40, 1024);
            assertThat(skill.has("allowed_tools") && skill.get("allowed_tools").isArray()
                            && skill.get("allowed_tools").size() > 0)
                    .as("[AIPP] skill '%s' 'allowed_tools' 必须为非空数组", name).isTrue();
            assertThat(skill.path("playbook_url").asText(""))
                    .as("[AIPP] skill '%s' 缺少非空 'playbook_url'", name).isNotBlank();
        }
    }

    /**
     * 验证单个 skill 对象的完整三层结构。
     *
     * <ul>
     *   <li>Layer 1：name、description、parameters（OpenAI function schema）</li>
     *   <li>Layer 2：prompt（执行指令）、tools（依赖 tool 列表）</li>
     *   <li>Layer 3：canvas（AIPP widget 绑定声明）</li>
     * </ul>
     */
    public void assertValidSkillStructure(JsonNode skill) {
        String skillName = skill.has("name") ? skill.get("name").asText() : "(unknown)";

        // Tool/Skill 拆分（aipp-protocol README §4.8）后，tool entry 只需要：
        //   name + description + parameters（OpenAI function-calling 兼容）
        //   canvas（AIPP 扩展，声明该 tool 是否触发 canvas 模式）
        // 并且**禁止**含 mini-agent 时代的 prompt / tools[] / resources 字段 ——
        // 跨 tool 的多步编排请用 /api/skills 暴露 Skill + SKILL.md。
        assertThat(skill.has("name"))
                .as("[AIPP] tool 缺少 'name' 字段").isTrue();
        assertThat(skill.has("description"))
                .as("[AIPP] tool '%s' 缺少 'description' 字段", skillName).isTrue();
        assertThat(skill.get("description").asText())
                .as("[AIPP] tool '%s' 的 description 不能为空", skillName).isNotBlank();
        assertThat(skill.has("parameters"))
                .as("[AIPP] tool '%s' 缺少 'parameters' 字段", skillName).isTrue();
        assertThat(skill.has("canvas"))
                .as("[AIPP] tool '%s' 缺少 'canvas' 字段（AIPP 规格要求声明 canvas 元数据）", skillName)
                .isTrue();

        assertValidParametersSchema(skillName, skill.get("parameters"));
        assertValidSkillCanvasDeclaration(skillName, skill.get("canvas"));

        for (String forbidden : java.util.List.of("prompt", "tools", "resources")) {
            assertThat(skill.has(forbidden))
                    .as("[AIPP] tool '%s' 含已禁用的 mini-agent 字段 '%s'：编排请放进 /api/skills 的 SKILL.md，不要挂在 tool 上",
                            skillName, forbidden)
                    .isFalse();
        }
    }

    /**
     * @deprecated Tool/Skill 拆分后已不存在 mini-agent Layer 2。Tool 是单次调用单次响应的
     *             原子能力，跨 tool 编排归 Skill（{@code /api/skills} + SKILL.md）。本方法
     *             保留为空实现以兼容旧测试调用，后续将移除。
     */
    @Deprecated
    public void assertValidSkillLayer2(JsonNode skill) {
        // no-op: see assertValidSkillStructure for the new tool-entry contract.
    }

    /**
     * Layer 3 规格：验证 AIPP skill 的 session 扩展声明（可选字段）。
     *
     * <p>若 skill 声明了 session 扩展，满足以下任一语义即可：
     * <ul>
     *   <li>路由语义：包含 creates_on 或 loads_on（按参数创建/加载会话）</li>
     *   <li>App 会话语义：包含 session_type=app 且 app_id（路由到 app session）</li>
     * </ul>
     */
    public void assertValidSkillSessionExtension(JsonNode skill) {
        String skillName = skill.path("name").asText("(unknown)");
        if (!skill.has("session")) return;

        JsonNode session = skill.get("session");
        boolean hasRouteCondition = session.has("creates_on") || session.has("loads_on");
        boolean hasAppSession = "app".equals(session.path("session_type").asText(""))
                && session.has("app_id")
                && !session.path("app_id").asText("").isBlank();
        assertThat(hasRouteCondition || hasAppSession)
                .as("[AIPP Layer 3] skill '%s' 声明了 session 扩展，"
                        + "但既不包含 creates_on/loads_on，也不是合法的 app session 声明(session_type=app + app_id)",
                        skillName)
                .isTrue();
    }

    /**
     * 验证 skill parameters 是合法的 OpenAI function schema 格式。
     */
    public void assertValidParametersSchema(String skillName, JsonNode parameters) {
        assertThat(parameters.has("type"))
                .as("[AIPP] skill '%s' parameters 缺少 'type' 字段", skillName).isTrue();
        assertThat(parameters.get("type").asText())
                .as("[AIPP] skill '%s' parameters.type 必须为 'object'", skillName)
                .isEqualTo("object");
        assertThat(parameters.has("properties"))
                .as("[AIPP] skill '%s' parameters 缺少 'properties' 字段", skillName).isTrue();
        assertThat(parameters.has("required"))
                .as("[AIPP] skill '%s' parameters 缺少 'required' 字段", skillName).isTrue();
        assertThat(parameters.get("required").isArray())
                .as("[AIPP] skill '%s' parameters.required 必须是数组", skillName).isTrue();
    }

    /**
     * 验证 skill canvas 声明结构。
     *
     * <p>规则：
     * <ul>
     *   <li>canvas.triggers 必须存在且为 boolean</li>
     *   <li>triggers=true 时：必须有 widget_type；如果有 action，必须合法</li>
     *   <li>triggers=false 时：不应有 widget_type 和 action</li>
     * </ul>
     */
    public void assertValidSkillCanvasDeclaration(String skillName, JsonNode canvas) {
        assertThat(canvas.has("triggers"))
                .as("[AIPP] skill '%s' canvas 缺少 'triggers' 字段", skillName).isTrue();
        assertThat(canvas.get("triggers").isBoolean())
                .as("[AIPP] skill '%s' canvas.triggers 必须是 boolean 类型", skillName).isTrue();

        boolean triggers = canvas.get("triggers").asBoolean();
        if (triggers) {
            assertThat(canvas.has("widget_type"))
                    .as("[AIPP] skill '%s' canvas.triggers=true 时必须声明 widget_type", skillName)
                    .isTrue();
            assertThat(canvas.get("widget_type").asText())
                    .as("[AIPP] skill '%s' canvas.widget_type 不能为空", skillName)
                    .isNotBlank();
            if (canvas.has("action")) {
                assertThat(VALID_CANVAS_ACTIONS)
                        .as("[AIPP] skill '%s' canvas.action 必须为 %s 之一", skillName, VALID_CANVAS_ACTIONS)
                        .contains(canvas.get("action").asText());
            }
        } else {
            assertThat(canvas.has("widget_type"))
                    .as("[AIPP] skill '%s' canvas.triggers=false 时不应有 widget_type（agent 走 Chat/HTML fallback 路径）",
                            skillName)
                    .isFalse();
        }
    }

    // ══════════════════════════════════════════════════════════════════════════
    // 2. GET /api/widgets 结构规格
    // ══════════════════════════════════════════════════════════════════════════

    /**
     * 验证 /api/widgets 响应的结构。
     *
     * <p>必须包含：app、widgets（数组）。
     * 每个 widget 必须包含：id（全局唯一）、source（资源路径）。
     */
    public void assertValidWidgetsApiStructure(JsonNode widgetsResponse) {
        assertThat(widgetsResponse.has("app"))
                .as("[AIPP] /api/widgets 响应缺少 'app' 字段").isTrue();
        assertThat(widgetsResponse.has("widgets"))
                .as("[AIPP] /api/widgets 响应缺少 'widgets' 字段").isTrue();
        assertThat(widgetsResponse.get("widgets").isArray())
                .as("[AIPP] 'widgets' 字段必须是数组").isTrue();
        assertThat(widgetsResponse.get("widgets").size())
                .as("[AIPP] 'widgets' 数组不能为空").isGreaterThan(0);

        for (JsonNode widget : widgetsResponse.get("widgets")) {
            assertValidWidgetStructure(widget);
        }
    }

    /**
     * 验证单个 widget 对象的结构。
     *
     * <p>Widget 用 {@code type} 作为全局唯一标识符（如 "entity-graph"）。
     * {@code source} 声明 widget 来源（例如 system / external），渲染方式由 {@code render.kind}
     * 声明。
     */
    public void assertValidWidgetStructure(JsonNode widget) {
        String widgetType = widget.has("type") ? widget.get("type").asText() : "(unknown)";
        assertThat(widget.has("type"))
                .as("[AIPP] widget 缺少 'type' 字段（全局唯一标识符）").isTrue();
        assertThat(widget.get("type").asText())
                .as("[AIPP] widget type 不能为空").isNotBlank();
        assertThat(widget.has("source"))
                .as("[AIPP] widget '%s' 缺少 'source' 字段", widgetType).isTrue();
        assertThat(widget.get("source").asText())
                .as("[AIPP] widget '%s' source 不能为空", widgetType).isNotBlank();
    }

    // ══════════════════════════════════════════════════════════════════════════
    // 3. 跨接口一致性校验
    // ══════════════════════════════════════════════════════════════════════════

    /**
     * 验证 skills 中引用的所有 widget_type 均已在 /api/widgets 中注册。
     *
     * <p>AIPP 规格要求：canvas.widget_type 必须与 Widget Manifest 中的 type 对应，
     * 否则 agent 无法找到 renderer，会 fallback 到生成 HTML。
     *
     * <p><b>豁免规则</b>：{@code sys.*} 前缀的 widget 类型为 world-one 系统内置，
     * 无需也不能在 /api/widgets 中注册，检查时自动跳过。
     *
     * @see AippSystemWidget
     */
    public void assertWidgetTypesRegistered(JsonNode skillsResponse, JsonNode widgetsResponse) {
        Set<String> registeredIds = new HashSet<>();
        for (JsonNode w : widgetsResponse.get("widgets")) {
            if (w.has("type")) registeredIds.add(w.get("type").asText());
        }

        for (JsonNode skill : skillsResponse.get("skills")) {
            JsonNode canvas = skill.get("canvas");
            if (canvas.get("triggers").asBoolean() && canvas.has("widget_type")) {
                String widgetType = canvas.get("widget_type").asText();
                String skillName  = skill.get("name").asText();

                // sys.* 为系统内置 widget，豁免注册检查
                if (AippSystemWidget.isSystemWidget(widgetType)) continue;

                assertThat(registeredIds)
                        .as("[AIPP] skill '%s' 引用了 widget_type='%s'，但该类型未在 /api/widgets 中注册。"
                                + "Agent 将 fallback 到生成 HTML，可能影响用户体验。",
                                skillName, widgetType)
                        .contains(widgetType);
            }
        }
    }

    /**
     * 断言给定的 widget_type 不使用系统保留前缀 {@code sys.*}。
     *
     * <p>AIPP 应用注册自己的 widget 时不得使用 {@code sys.*} 前缀，
     * 该前缀为 world-one 系统内置 widget 保留（{@link AippSystemWidget}）。
     *
     * @param widgetType 要检查的 widget 类型字符串
     */
    public void assertSystemWidgetExempt(String widgetType) {
        assertThat(AippSystemWidget.isSystemWidget(widgetType))
                .as("[AIPP] widget_type='%s' 使用了系统保留前缀 'sys.'，"
                        + "AIPP 应用不得注册 sys.* 类型的 widget。"
                        + "系统内置 widget 类型请参考 AippSystemWidget 常量。", widgetType)
                .isFalse();
    }

    // ══════════════════════════════════════════════════════════════════════════
    // 4. 工具响应 vs skill 声明一致性
    // ══════════════════════════════════════════════════════════════════════════

    /**
     * 验证工具的实际响应与 skill 的 canvas 声明一致。
     *
     * <p>核心规则：
     * <ul>
     *   <li>triggers=true  → 响应必须有 canvas 字段，且 action/widget_type 与声明匹配</li>
     *   <li>triggers=false → 响应不得有 canvas 字段（Chat Mode 或 HTML fallback）</li>
     * </ul>
     *
     * @param toolName     工具名，用于错误信息
     * @param skillCanvas  来自 /api/skills 的 canvas 声明对象
     * @param toolResponse 工具执行返回的 JSON 响应
     */
    public void assertToolResponseMatchesSkillCanvas(
            String toolName, JsonNode skillCanvas, JsonNode toolResponse) {

        boolean triggersDeclared = skillCanvas.get("triggers").asBoolean();

        if (triggersDeclared) {
            assertThat(toolResponse.has("canvas"))
                    .as("[AIPP] '%s'：skill 声明 triggers=true，但工具响应中没有 canvas 字段。"
                            + "Agent 将停留在 Chat Mode，Widget 不会渲染。", toolName)
                    .isTrue();

            assertValidToolResponseCanvas(toolName, toolResponse.get("canvas"));

            if (skillCanvas.has("action")) {
                String expectedAction = skillCanvas.get("action").asText();
                String actualAction   = toolResponse.get("canvas").get("action").asText();
                assertThat(actualAction)
                        .as("[AIPP] '%s'：canvas.action 与 skill 声明不匹配（期望=%s，实际=%s）",
                                toolName, expectedAction, actualAction)
                        .isEqualTo(expectedAction);
            }

            if (skillCanvas.has("widget_type")) {
                String expectedType = skillCanvas.get("widget_type").asText();
                String actualType   = toolResponse.get("canvas").get("widget_type").asText();
                assertThat(actualType)
                        .as("[AIPP] '%s'：canvas.widget_type 与 skill 声明不匹配（期望=%s，实际=%s）",
                                toolName, expectedType, actualType)
                        .isEqualTo(expectedType);
            }

        } else {
            assertThat(toolResponse.has("canvas"))
                    .as("[AIPP] '%s'：skill 声明 triggers=false（Chat Mode），"
                            + "但工具响应中存在 canvas 字段。"
                            + "这会导致 agent 误判进入 Canvas Mode，违反 AIPP P2 原则。", toolName)
                    .isFalse();
        }
    }

    /**
     * 验证工具响应中 canvas 对象的内部结构。
     */
    public void assertValidToolResponseCanvas(String toolName, JsonNode canvas) {
        assertThat(canvas.has("action"))
                .as("[AIPP] '%s'：canvas 缺少 'action' 字段", toolName).isTrue();
        assertThat(VALID_CANVAS_ACTIONS)
                .as("[AIPP] '%s'：canvas.action='%s' 不合法，必须为 %s 之一",
                        toolName, canvas.path("action").asText(), VALID_CANVAS_ACTIONS)
                .contains(canvas.get("action").asText());
        assertThat(canvas.has("widget_type"))
                .as("[AIPP] '%s'：canvas 缺少 'widget_type' 字段", toolName).isTrue();
        assertThat(canvas.get("widget_type").asText())
                .as("[AIPP] '%s'：canvas.widget_type 不能为空", toolName).isNotBlank();
    }

    // ══════════════════════════════════════════════════════════════════════════
    // 5. 具名场景断言（语义级）
    // ══════════════════════════════════════════════════════════════════════════

    /**
     * 断言响应处于 Chat Mode（无 canvas，无 new_session）。
     *
     * <p>适用于纯查询类 API（如 world_list）：agent 保持对话，不触发 Widget 渲染。
     * 若需展示数据，由 LLM 使用自然语言描述，或 fallback 到生成 HTML。
     *
     * @param toolName 工具名（用于错误提示）
     * @param response 工具执行返回的 JSON 响应
     */
    public void assertChatModeResponse(String toolName, JsonNode response) {
        assertThat(response.has("canvas"))
                .as("[AIPP] '%s'：Chat Mode 响应不应包含 canvas 字段（canvas ≠ open）", toolName)
                .isFalse();
        assertThat(response.has("new_session"))
                .as("[AIPP] '%s'：Chat Mode 响应不应包含 new_session 字段（session ≠ new）", toolName)
                .isFalse();
    }

    /**
     * 断言响应以 canvas.action=open 进入 Canvas Mode，并创建了新 session。
     *
     * <p>适用于创建类 API（如 world_create_session）：响应必须包含：
     * <ul>
     *   <li>canvas.action = "open"（打开新 Widget 实例）</li>
     *   <li>canvas.widget_type = expectedWidgetType（对应注册的 Widget）</li>
     *   <li>canvas.widget_id（非空，由服务端生成）</li>
     *   <li>new_session（表示创建了新会话，session = new）</li>
     * </ul>
     *
     * @param toolName           工具名
     * @param response           工具响应 JSON
     * @param expectedWidgetType 期望的 widget 类型（如 "entity-graph"）
     */
    public void assertCanvasOpenWithNewSession(
            String toolName, JsonNode response, String expectedWidgetType) {

        assertThat(response.has("canvas"))
                .as("[AIPP] '%s'：应进入 Canvas Mode，但响应中没有 canvas 字段", toolName)
                .isTrue();
        assertThat(response.has("new_session"))
                .as("[AIPP] '%s'：应创建新 session，但响应中没有 new_session 字段（session = new 规格失败）",
                        toolName)
                .isTrue();

        JsonNode canvas = response.get("canvas");
        assertThat(canvas.get("action").asText())
                .as("[AIPP] '%s'：canvas.action 应为 'open'（canvas == open 规格）", toolName)
                .isEqualTo("open");
        assertThat(canvas.get("widget_type").asText())
                .as("[AIPP] '%s'：canvas.widget_type 应为 '%s'（widget == %s 规格）",
                        toolName, expectedWidgetType, expectedWidgetType)
                .isEqualTo(expectedWidgetType);
        assertThat(canvas.has("widget_id"))
                .as("[AIPP] '%s'：canvas 应包含 widget_id", toolName).isTrue();
        assertThat(canvas.get("widget_id").asText())
                .as("[AIPP] '%s'：canvas.widget_id 不能为空", toolName).isNotBlank();
    }

    /**
     * 断言响应以 canvas.action=patch 增量更新已有 Widget。
     *
     * <p>适用于修改类 API（如 world_add_definition）。
     *
     * @param toolName           工具名
     * @param response           工具响应 JSON
     * @param expectedWidgetType 期望的 widget 类型
     */
    public void assertCanvasPatchResponse(
            String toolName, JsonNode response, String expectedWidgetType) {

        assertThat(response.has("canvas"))
                .as("[AIPP] '%s'：应返回 canvas.patch 指令，但响应中没有 canvas 字段", toolName)
                .isTrue();

        JsonNode canvas = response.get("canvas");
        assertThat(canvas.get("action").asText())
                .as("[AIPP] '%s'：canvas.action 应为 'patch'", toolName)
                .isEqualTo("patch");
        assertThat(canvas.get("widget_type").asText())
                .as("[AIPP] '%s'：canvas.widget_type 应为 '%s'", toolName, expectedWidgetType)
                .isEqualTo(expectedWidgetType);
    }

    // ══════════════════════════════════════════════════════════════════════════
    // 6. 辅助工具方法
    // ══════════════════════════════════════════════════════════════════════════

    /**
     * 验证 {@code GET /api/app} 响应的结构（AIPP App Manifest 规格）。
     *
     * <p>必须包含：app_id、app_name、app_icon、app_description、app_color、is_active、version。
     *
     * <pre>
     * {
     *   "app_id":          "memory-one",
     *   "app_name":        "记忆管理",
     *   "app_icon":        "&lt;svg ...&gt;...&lt;/svg&gt;",
     *   "app_description": "管理 AI Agent 的长期记忆",
     *   "app_color":       "#7c6ff7",
     *   "is_active":       true,
     *   "version":         "1.0"
     * }
     * </pre>
     */
    public void assertValidAppManifest(JsonNode appManifest) {
        for (String required : new String[]{"app_id", "app_name", "app_icon", "app_description", "app_color", "is_active", "version"}) {
            assertThat(appManifest.has(required))
                    .as("[AIPP App] /api/app 响应缺少 '%s' 字段", required)
                    .isTrue();
        }
        assertThat(appManifest.path("app_id").asText())
                .as("[AIPP App] 'app_id' 不能为空").isNotBlank();
        assertThat(appManifest.path("app_name").asText())
                .as("[AIPP App] 'app_name' 不能为空").isNotBlank();
        assertThat(appManifest.path("app_icon").asText())
                .as("[AIPP App] 'app_icon' 不能为空").isNotBlank();
        assertThat(appManifest.path("app_description").asText())
                .as("[AIPP App] 'app_description' 不能为空").isNotBlank();
        assertThat(appManifest.path("app_color").asText())
                .as("[AIPP App] 'app_color' 不能为空（期望 hex 格式，如 #7c6ff7）").isNotBlank();
        assertThat(appManifest.get("is_active").isBoolean())
                .as("[AIPP App] 'is_active' 必须是 boolean 类型").isTrue();
        assertThat(appManifest.path("version").asText())
                .as("[AIPP App] 'version' 不能为空").isNotBlank();

        // app_id 必须与 /api/skills 的 app 字段一致（跨接口一致性）
        String appId = appManifest.path("app_id").asText();
        assertThat(appId)
                .as("[AIPP App] 'app_id' 格式应为 kebab-case（小写字母+连字符），"
                        + "如 'memory-one'、'world-entitir'")
                .matches("[a-z][a-z0-9\\-]*");
    }

    /**
     * 验证 {@code /api/app.app_id} 与 Tool/Skill 端点的 {@code app} 字段一致。
     *
     * <p>跨接口一致性约束：同一个 AIPP 应用在所有端点中应使用相同的标识符。
     * 此方法既可用于校验与 {@code /api/tools} 的一致性（Phase 4 之后），
     * 也兼容校验与 {@code /api/skills} 的一致性（向后兼容的调用方）。
     */
    public void assertAppIdConsistency(JsonNode appManifest, JsonNode appScopedResponse) {
        String manifestAppId = appManifest.path("app_id").asText();
        String endpointApp   = appScopedResponse.path("app").asText();
        assertThat(manifestAppId)
                .as("[AIPP App] /api/app.app_id（'%s'）与 app 端点的 app 字段（'%s'）不一致，"
                        + "同一 AIPP 应用在所有端点中应使用相同标识符。", manifestAppId, endpointApp)
                .isEqualTo(endpointApp);
    }

    /**
     * 验证 /api/widgets 中每个 widget 都声明了 App Identity 字段（app_id / is_main / is_canvas_mode）。
     *
     * <p>补充 {@link #assertValidWidgetsApiStructure(JsonNode)} 的验证，
     * 新增对 App Identity 三字段的存在性和类型校验。
     */
    public void assertWidgetsHaveAppIdentityFields(JsonNode widgetsResponse) {
        assertThat(widgetsResponse.has("widgets")).isTrue();
        for (JsonNode widget : widgetsResponse.get("widgets")) {
            String type = widget.path("type").asText("(unknown)");

            assertThat(widget.has("app_id"))
                    .as("[AIPP App Identity] widget '%s' 缺少 'app_id' 字段", type).isTrue();
            assertThat(widget.path("app_id").asText())
                    .as("[AIPP App Identity] widget '%s' 'app_id' 不能为空", type).isNotBlank();

            assertThat(widget.has("is_main"))
                    .as("[AIPP App Identity] widget '%s' 缺少 'is_main' 字段", type).isTrue();
            assertThat(widget.get("is_main").isBoolean())
                    .as("[AIPP App Identity] widget '%s' 'is_main' 必须是 boolean", type).isTrue();

            assertThat(widget.has("is_canvas_mode"))
                    .as("[AIPP App Identity] widget '%s' 缺少 'is_canvas_mode' 字段", type).isTrue();
            assertThat(widget.get("is_canvas_mode").isBoolean())
                    .as("[AIPP App Identity] widget '%s' 'is_canvas_mode' 必须是 boolean", type).isTrue();
        }
    }

    /**
     * 验证每个 app 在 /api/widgets 中恰好有一个 is_main=true 的 widget。
     *
     * <p>AIPP 协议强制要求：每个注册了 {@code GET /api/app} 的 AIPP 服务，
     * 必须在其 {@code /api/widgets} 中声明恰好一个 {@code is_main=true} 的 widget，
     * 作为用户从 Apps 面板点击进入的主入口。
     *
     * <p>没有 UI 的纯工具服务不应注册 app manifest，只提供 /api/skills 即可。
     *
     * @param widgetsResponse {@code /api/widgets} 的完整响应
     * @param appIds          本次需要验证的 app_id 集合（通常来自 /api/app）
     */
    public void assertExactlyOneMainWidget(JsonNode widgetsResponse,
                                           java.util.Collection<String> appIds) {
        java.util.Map<String, Integer> mainCount = new java.util.HashMap<>();
        for (String id : appIds) mainCount.put(id, 0);
        for (JsonNode widget : widgetsResponse.get("widgets")) {
            String appId = widget.path("app_id").asText();
            if (mainCount.containsKey(appId) && widget.path("is_main").asBoolean(false)) {
                mainCount.merge(appId, 1, Integer::sum);
            }
        }
        for (java.util.Map.Entry<String, Integer> e : mainCount.entrySet()) {
            assertThat(e.getValue())
                    .as("[AIPP] app '%s' 有 %d 个 is_main=true 的 widget，"
                            + "每个 AIPP 必须恰好声明一个主 widget（is_main=true）。",
                            e.getKey(), e.getValue())
                    .isEqualTo(1);
        }
    }

    /**
     * @deprecated 弱约束，请改用 {@link #assertExactlyOneMainWidget}。
     */
    @Deprecated
    public void assertAtMostOneMainWidget(JsonNode widgetsResponse) {
        java.util.Map<String, Integer> mainCount = new java.util.HashMap<>();
        for (JsonNode widget : widgetsResponse.get("widgets")) {
            String appId = widget.path("app_id").asText();
            if (widget.path("is_main").asBoolean(false)) {
                mainCount.merge(appId, 1, Integer::sum);
            }
        }
        for (java.util.Map.Entry<String, Integer> e : mainCount.entrySet()) {
            assertThat(e.getValue())
                    .as("[AIPP App Identity] app '%s' 有 %d 个 is_main=true 的 widget，"
                            + "每个 app 最多只能有一个主 widget。", e.getKey(), e.getValue())
                    .isLessThanOrEqualTo(1);
        }
    }

    // ══════════════════════════════════════════════════════════════════════════
    // 7. 辅助工具方法
    // ══════════════════════════════════════════════════════════════════════════

    /**
     * 在 /api/skills 响应中查找指定名称的 skill。
     * 若找不到，直接断言失败。
     */
    public JsonNode findSkill(JsonNode skillsResponse, String skillName) {
        for (JsonNode skill : skillsResponse.get("skills")) {
            if (skillName.equals(skill.path("name").asText())) {
                return skill;
            }
        }
        throw new AssertionError("[AIPP] /api/skills 中未找到 skill: " + skillName);
    }
}
