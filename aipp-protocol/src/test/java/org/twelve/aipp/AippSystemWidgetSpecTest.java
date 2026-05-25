package org.example.aipp;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;

/**
 * 系统内置 Widget 规范示例测试。
 *
 * <p>本测试类作为 AIPP 协议文档的可执行示例，展示以下场景中系统 Widget 的正确使用方式：
 *
 * <ol>
 *   <li><b>sys.confirm</b>：LLM 发起 memory 删除，显示确认框（Yes/No）</li>
 *   <li><b>sys.alert</b>：操作完成通知（仅 OK 按钮）</li>
 *   <li><b>sys.prompt</b>：用户输入型对话框（输入 + OK/Cancel）</li>
 *   <li><b>sys.selection</b>：多选项选择框（sys.choice 兼容）</li>
 *   <li><b>sys.progress</b>：后台工具执行进度（spinner）</li>
 * </ol>
 *
 * <h2>memory 删除完整流程</h2>
 * <pre>
 *   用户 → "删除关于 John 会议的记忆"
 *   LLM  → tool_call: memory_delete_request({ memory_ids: ["m1","m2"] })
 *   Tool → { canvas: { action:"open", widget_type:"sys.confirm", data:{...} } }
 *   world-one → 渲染确认框（模态覆盖层）
 *   用户点击"确认删除"
 *   world-one → ToolProxy: memory_delete_confirmed({ memory_ids: ["m1","m2"] })
 *   用户点击"取消"
 *   world-one → chat 消息: "已取消删除操作"
 * </pre>
 */
@DisplayName("AIPP System Widget 规范示例")
class AippSystemWidgetSpecTest {

    private final ObjectMapper mapper = new ObjectMapper();
    private final AippAppSpec  spec   = new AippAppSpec();

    // ══════════════════════════════════════════════════════════════════════════
    // sys.confirm — 危险操作确认框（memory 删除场景）
    // ══════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("sys.confirm — 二选一确认框")
    class SysConfirm {

        @Test
        @DisplayName("memory_delete_request tool 响应：返回 sys.confirm canvas 指令")
        void memoryDelete_toolResponse_returnsSysConfirmCanvas() throws Exception {
            // GIVEN：memory_delete_request 工具返回的响应（AIPP 应用实际返回此结构）
            String toolResponseJson = """
                    {
                      "ok": true,
                      "canvas": {
                        "action":      "open",
                        "widget_type": "sys.confirm",
                        "data": {
                          "mode":    "yes_no",
                          "title":   "确认删除记忆",
                          "message": "确定要删除关于 John 会议的 2 条记忆吗？此操作不可撤销。",
                          "danger":  true,
                          "yes": {
                            "tool": "memory_delete_confirmed",
                            "args": { "memory_ids": ["m1", "m2"] }
                          },
                          "no": {
                            "message": "已取消删除操作"
                          }
                        }
                      }
                    }
                    """;

            JsonNode response = mapper.readTree(toolResponseJson);
            JsonNode canvas   = response.get("canvas");

            // THEN：canvas 结构合法
            assertThat(canvas.get("action").asText()).isEqualTo("open");
            assertThat(canvas.get("widget_type").asText()).isEqualTo(AippSystemWidget.CONFIRM);

            // sys.confirm 的 data 必须有 mode、title、message
            JsonNode data = canvas.get("data");
            assertThat(data.get("mode").asText()).isIn("yes_no", "ok_cancel");
            assertThat(data.get("title").asText()).isNotBlank();
            assertThat(data.get("message").asText()).isNotBlank();

            // yes 分支必须声明 tool（真实执行删除）
            assertThat(data.get("yes").has("tool")).isTrue();
            assertThat(data.get("yes").get("tool").asText()).isEqualTo("memory_delete_confirmed");

            // sys.* widget 豁免注册检查
            assertThatNoException().isThrownBy(() ->
                    assertThat(AippSystemWidget.isSystemWidget("sys.confirm")).isTrue()
            );
        }

        @Test
        @DisplayName("sys.confirm widget_type 不应出现在 /api/widgets 注册列表中")
        void sysConfirm_notRegisteredInWidgetManifest_isCorrect() throws Exception {
            // GIVEN：AIPP 应用的 /api/widgets 响应（不含任何 sys.* 类型）
            String widgetsJson = """
                    {
                      "app": "memory-one",
                      "widgets": [
                        { "type": "memory-manager", "source": "builtin",
                          "app_id": "memory-one", "is_main": true, "is_canvas_mode": true }
                      ]
                    }
                    """;

            // GIVEN：skill 引用 sys.confirm（通过 memory_delete_request 触发）
            String skillsJson = """
                    {
                      "app": "memory-one", "version": "1.0",
                      "skills": [
                        {
                          "name":        "memory_delete_request",
                          "description": "请求删除指定记忆，触发用户确认",
                          "parameters":  { "type": "object", "properties": {}, "required": [] },
                          "canvas":      { "triggers": true, "widget_type": "sys.confirm" },
                          "prompt":      "当用户要求删除记忆时，调用此 skill",
                          "tools":       ["memory_delete_request"]
                        }
                      ]
                    }
                    """;

            JsonNode skills  = mapper.readTree(skillsJson);
            JsonNode widgets = mapper.readTree(widgetsJson);

            // THEN：sys.confirm 豁免注册检查，不报错
            assertThatNoException().isThrownBy(() ->
                    spec.assertWidgetTypesRegistered(skills, widgets)
            );
        }

        @Test
        @DisplayName("AIPP 应用不得注册 sys.* 前缀的 widget")
        void aippApp_mustNotRegisterSysPrefixWidget() {
            assertThat(AippSystemWidget.isSystemWidget("sys.confirm")).isTrue();
            assertThat(AippSystemWidget.isSystemWidget("sys.alert")).isTrue();
            assertThat(AippSystemWidget.isSystemWidget("sys.my-custom")).isTrue();
            assertThat(AippSystemWidget.isSystemWidget("memory-manager")).isFalse();
            assertThat(AippSystemWidget.isSystemWidget("entity-graph")).isFalse();
        }
    }

    // ══════════════════════════════════════════════════════════════════════════
    // sys.alert — 纯信息提示框
    // ══════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("sys.alert — 信息提示框（仅 OK）")
    class SysAlert {

        @Test
        @DisplayName("记忆整合完成后，显示 alert 通知")
        void memoryConsolidate_completed_showsAlert() throws Exception {
            String toolResponseJson = """
                    {
                      "ok": true,
                      "canvas": {
                        "action":      "open",
                        "widget_type": "sys.alert",
                        "data": {
                          "title":         "记忆整合完成",
                          "message":       "已将 12 条短期记忆整合为 3 条长期记忆。",
                          "close_message": "用户已确认记忆整合结果"
                        }
                      }
                    }
                    """;

            JsonNode canvas = mapper.readTree(toolResponseJson).get("canvas");
            assertThat(canvas.get("widget_type").asText()).isEqualTo(AippSystemWidget.ALERT);
            assertThat(canvas.get("data").get("title").asText()).isNotBlank();
            assertThat(canvas.get("data").get("message").asText()).isNotBlank();
        }
    }

    // ══════════════════════════════════════════════════════════════════════════
    // sys.prompt — 用户输入框
    // ══════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("sys.prompt — 文本输入框")
    class SysPrompt {

        @Test
        @DisplayName("设置记忆指令时，弹出输入框")
        void memorySetInstruction_showsPrompt() throws Exception {
            String toolResponseJson = """
                    {
                      "ok": true,
                      "canvas": {
                        "action":      "open",
                        "widget_type": "sys.prompt",
                        "data": {
                          "title":       "设置记忆指令",
                          "message":     "请输入你希望 AI 长期遵循的行为指令：",
                          "placeholder": "例如：回答时总是使用中文，不使用 Markdown",
                          "submit": {
                            "tool":     "memory_set_instruction",
                            "arg_name": "instruction"
                          },
                          "cancel": {
                            "message": "已取消设置记忆指令"
                          }
                        }
                      }
                    }
                    """;

            JsonNode canvas = mapper.readTree(toolResponseJson).get("canvas");
            assertThat(canvas.get("widget_type").asText()).isEqualTo(AippSystemWidget.PROMPT);
            assertThat(canvas.get("data").get("submit").get("tool").asText())
                    .isEqualTo("memory_set_instruction");
            assertThat(canvas.get("data").get("submit").get("arg_name").asText())
                    .isEqualTo("instruction");
        }
    }

    // ══════════════════════════════════════════════════════════════════════════
    // sys.progress — 工具执行进度
    // ══════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("sys.progress — 后台执行进度")
    class SysProgress {

        @Test
        @DisplayName("事件仅含 tool_id 时，world-one 默认用 sys.progress")
        void event_toolOnlyWithNoWidgetType_usesSystemProgress() {
            AippEvent event = new AippEvent(
                    "evt-001",
                    "world-entitir",
                    "正在构建 World #42",
                    null,                    // widgetType = null → world-one 用 sys.progress
                    "world_build",
                    "{\"world_id\":\"42\"}",
                    null,
                    AippEvent.Priority.NORMAL
            );

            assertThat(event.isToolOnly()).isTrue();
            assertThat(event.usesSystemWidget()).isFalse();
            // world-one 检测 isToolOnly() == true 时自动渲染 sys.progress
        }

        @Test
        @DisplayName("事件显式指定 sys.progress，可携带 poll_tool 配置")
        void event_explicitProgress_withPollTool() throws Exception {
            String toolResponseJson = """
                    {
                      "ok": true,
                      "canvas": {
                        "action":      "open",
                        "widget_type": "sys.progress",
                        "data": {
                          "title":         "正在构建 World #42",
                          "indeterminate": true,
                          "poll_tool":     "world_build_status",
                          "poll_interval": 2000
                        }
                      }
                    }
                    """;

            JsonNode canvas = mapper.readTree(toolResponseJson).get("canvas");
            assertThat(canvas.get("widget_type").asText()).isEqualTo(AippSystemWidget.PROGRESS);
            assertThat(canvas.get("data").get("indeterminate").asBoolean()).isTrue();
        }
    }

    // ══════════════════════════════════════════════════════════════════════════
    // AippEvent 结构验证
    // ══════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("AippEvent — 外部事件载荷")
    class AippEventStructure {

        @Test
        @DisplayName("外部 CI 完成事件：打开自定义 widget")
        void externalCiEvent_opensCustomWidget() {
            AippEvent event = new AippEvent(
                    "evt-ci-123",
                    "ci-agent",
                    "Pipeline #123 构建成功",
                    "pipeline-result",       // 自定义 widget
                    null,
                    null,
                    "{\"pipeline_id\":\"123\",\"status\":\"success\"}",
                    AippEvent.Priority.HIGH
            );

            assertThat(event.isToolOnly()).isFalse();
            assertThat(event.usesSystemWidget()).isFalse();
            assertThat(event.widgetType()).isEqualTo("pipeline-result");
            assertThat(event.priority()).isEqualTo(AippEvent.Priority.HIGH);
        }

        @Test
        @DisplayName("quality-agent 发现高危漏洞：URGENT 事件打开 sys.confirm")
        void urgentQualityEvent_opensSysConfirm() {
            AippEvent event = new AippEvent(
                    "evt-qa-456",
                    "quality-agent",
                    "发现 3 个高危漏洞",
                    AippSystemWidget.CONFIRM,
                    null,
                    null,
                    "{\"mode\":\"yes_no\",\"title\":\"是否中止发布？\"}",
                    AippEvent.Priority.URGENT
            );

            assertThat(event.usesSystemWidget()).isTrue();
            assertThat(event.priority()).isEqualTo(AippEvent.Priority.URGENT);
        }
    }
}
