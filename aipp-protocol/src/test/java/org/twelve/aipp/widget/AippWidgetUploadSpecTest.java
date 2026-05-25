package org.example.aipp.widget;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.*;

/**
 * AippWidgetSpec Upload 能力规格测试。
 *
 * <p>覆盖：
 * <ul>
 *   <li>合规 upload manifest 通过所有断言</li>
 *   <li>缺少必要字段时断言失败，且错误信息描述清晰</li>
 *   <li>扩展名格式校验（合法 / 非法 / 大写 / 无点）</li>
 *   <li>{@link AippWidgetUpload#accepts(String)} 文件名匹配逻辑</li>
 *   <li>{@link AippWidgetUpload#assembleMessage} 消息组装完整性</li>
 * </ul>
 */
@DisplayName("AIPP Widget Upload 规格测试")
class AippWidgetUploadSpecTest {

    private static final ObjectMapper JSON = new ObjectMapper();
    private AippWidgetSpec spec;

    @BeforeEach
    void setUp() { spec = new AippWidgetSpec(); }

    // ── helpers ───────────────────────────────────────────────────────────────

    private ObjectNode baseWidget() {
        ObjectNode w = JSON.createObjectNode();
        w.put("type", "entity-graph");
        ObjectNode supports = w.putObject("supports");
        supports.put("disable", true);
        return w;
    }

    private ObjectNode validUpload(String... extraTools) {
        ObjectNode u = JSON.createObjectNode();
        ArrayNode accept = u.putArray("accept");
        accept.add(".json");
        u.put("prompt", "请验证上传的 JSON 包含 class 字段，验证通过后调用 world_add_definition。");
        ArrayNode tools = u.putArray("tools");
        tools.add("world_add_definition");
        for (String t : extraTools) tools.add(t);
        return u;
    }

    // ══════════════════════════════════════════════════════════════════════════
    // 1. 合规 manifest 通过断言
    // ══════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("合规 upload 配置通过所有规格断言")
    class ValidManifest {

        @Test
        @DisplayName("完整 upload 配置通过 assertWidgetSupportsUpload")
        void full_upload_config_passes() {
            ObjectNode widget = baseWidget();
            widget.set("upload", validUpload());
            assertThatCode(() -> spec.assertWidgetSupportsUpload(widget)).doesNotThrowAnyException();
        }

        @Test
        @DisplayName("扩展名格式合法时通过 assertUploadExtensionsWellFormed")
        void well_formed_extensions_pass() {
            ObjectNode widget = baseWidget();
            ObjectNode u = validUpload();
            ArrayNode accept = (ArrayNode) u.get("accept");
            accept.add(".csv").add(".txt").add(".yaml");
            widget.set("upload", u);
            assertThatCode(() -> spec.assertUploadExtensionsWellFormed(widget)).doesNotThrowAnyException();
        }

        @Test
        @DisplayName("assertUploadAccepts 在 accept 覆盖指定扩展名时通过")
        void upload_accepts_declared_extension() {
            ObjectNode widget = baseWidget();
            widget.set("upload", validUpload());
            assertThatCode(() -> spec.assertUploadAccepts(widget, ".json")).doesNotThrowAnyException();
        }

        @Test
        @DisplayName("assertUploadTools 在 tools 包含指定工具时通过")
        void upload_tools_contains_declared_tool() {
            ObjectNode widget = baseWidget();
            widget.set("upload", validUpload("world_validate_def"));
            assertThatCode(() -> spec.assertUploadTools(widget, "world_add_definition", "world_validate_def"))
                    .doesNotThrowAnyException();
        }

        @Test
        @DisplayName("upload.tools 为空数组时也通过 assertWidgetSupportsUpload")
        void empty_tools_array_passes() {
            ObjectNode widget = baseWidget();
            ObjectNode u = JSON.createObjectNode();
            u.putArray("accept").add(".txt");
            u.put("prompt", "分析上传内容并汇报。");
            u.putArray("tools"); // 空
            widget.set("upload", u);
            assertThatCode(() -> spec.assertWidgetSupportsUpload(widget)).doesNotThrowAnyException();
        }
    }

    // ══════════════════════════════════════════════════════════════════════════
    // 2. 缺失必要字段时断言失败
    // ══════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("缺少必要字段时断言失败")
    class MissingFields {

        @Test
        @DisplayName("widget 无 upload 字段时 assertWidgetSupportsUpload 失败")
        void missing_upload_block_fails() {
            ObjectNode widget = baseWidget();
            assertThatThrownBy(() -> spec.assertWidgetSupportsUpload(widget))
                    .isInstanceOf(AssertionError.class)
                    .hasMessageContaining("upload");
        }

        @Test
        @DisplayName("upload.accept 为空数组时失败")
        void empty_accept_fails() {
            ObjectNode widget = baseWidget();
            ObjectNode u = JSON.createObjectNode();
            u.putArray("accept"); // 空
            u.put("prompt", "处理上传文件。");
            widget.set("upload", u);
            assertThatThrownBy(() -> spec.assertWidgetSupportsUpload(widget))
                    .isInstanceOf(AssertionError.class)
                    .hasMessageContaining("accept");
        }

        @Test
        @DisplayName("upload.prompt 为空字符串时失败")
        void blank_prompt_fails() {
            ObjectNode widget = baseWidget();
            ObjectNode u = validUpload();
            u.put("prompt", "  "); // 空白
            widget.set("upload", u);
            assertThatThrownBy(() -> spec.assertWidgetSupportsUpload(widget))
                    .isInstanceOf(AssertionError.class)
                    .hasMessageContaining("prompt");
        }

        @Test
        @DisplayName("upload.prompt 缺失时失败")
        void missing_prompt_fails() {
            ObjectNode widget = baseWidget();
            ObjectNode u = JSON.createObjectNode();
            u.putArray("accept").add(".json");
            // 不加 prompt
            widget.set("upload", u);
            assertThatThrownBy(() -> spec.assertWidgetSupportsUpload(widget))
                    .isInstanceOf(AssertionError.class)
                    .hasMessageContaining("prompt");
        }

        @Test
        @DisplayName("assertUploadAccepts 未声明指定扩展名时失败")
        void upload_missing_required_extension_fails() {
            ObjectNode widget = baseWidget();
            widget.set("upload", validUpload()); // only .json
            assertThatThrownBy(() -> spec.assertUploadAccepts(widget, ".csv"))
                    .isInstanceOf(AssertionError.class)
                    .hasMessageContaining(".csv");
        }

        @Test
        @DisplayName("assertUploadTools 未声明指定工具时失败")
        void upload_missing_required_tool_fails() {
            ObjectNode widget = baseWidget();
            widget.set("upload", validUpload());
            assertThatThrownBy(() -> spec.assertUploadTools(widget, "world_missing_tool"))
                    .isInstanceOf(AssertionError.class)
                    .hasMessageContaining("world_missing_tool");
        }
    }

    // ══════════════════════════════════════════════════════════════════════════
    // 3. 扩展名格式校验
    // ══════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("扩展名格式规格")
    class ExtensionFormat {

        @Test
        @DisplayName("无 . 前缀的扩展名失败")
        void extension_without_dot_fails() {
            ObjectNode widget = baseWidget();
            ObjectNode u = JSON.createObjectNode();
            u.putArray("accept").add("json"); // 缺少 .
            u.put("prompt", "处理文件。");
            widget.set("upload", u);
            assertThatThrownBy(() -> spec.assertUploadExtensionsWellFormed(widget))
                    .isInstanceOf(AssertionError.class)
                    .hasMessageContaining("json");
        }

        @Test
        @DisplayName("大写扩展名失败（规范要求全小写）")
        void uppercase_extension_fails() {
            ObjectNode widget = baseWidget();
            ObjectNode u = JSON.createObjectNode();
            u.putArray("accept").add(".JSON");
            u.put("prompt", "处理文件。");
            widget.set("upload", u);
            assertThatThrownBy(() -> spec.assertUploadExtensionsWellFormed(widget))
                    .isInstanceOf(AssertionError.class)
                    .hasMessageContaining(".JSON");
        }
    }

    // ══════════════════════════════════════════════════════════════════════════
    // 4. AippWidgetUpload 对象行为
    // ══════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("AippWidgetUpload 对象行为")
    class UploadObjectBehavior {

        private AippWidgetUpload upload() {
            return AippWidgetUpload.of(
                    List.of(".json", ".csv"),
                    "请验证格式后调用 world_add_definition。",
                    List.of("world_add_definition")
            );
        }

        @Test
        @DisplayName("accepts() 对合法扩展名返回 true")
        void accepts_valid_extension() {
            assertThat(upload().accepts("schema.json")).isTrue();
            assertThat(upload().accepts("data.CSV")).isTrue(); // 大小写不敏感
        }

        @Test
        @DisplayName("accepts() 对不在列表中的扩展名返回 false")
        void rejects_unknown_extension() {
            assertThat(upload().accepts("report.xlsx")).isFalse();
            assertThat(upload().accepts("script.js")).isFalse();
        }

        @Test
        @DisplayName("accepts() 对 null 返回 false")
        void accepts_null_returns_false() {
            assertThat(upload().accepts(null)).isFalse();
        }

        @Test
        @DisplayName("assembleMessage 包含文件名、内容、prompt")
        void assemble_message_contains_all_parts() {
            AippWidgetUpload u = upload();
            String msg = u.assembleMessage("entities.json", 12, "{\"class\":\"Employee\"}");
            assertThat(msg).contains("entities.json");
            assertThat(msg).contains("{\"class\":\"Employee\"}");
            assertThat(msg).contains(u.prompt());
            assertThat(msg).contains("json"); // extension in code fence
        }

        @Test
        @DisplayName("assembleMessage 文件大小为 0 时正常组装（不显示 KB 信息）")
        void assemble_message_zero_size() {
            String msg = upload().assembleMessage("test.json", 0, "{}");
            assertThat(msg).contains("test.json");
            assertThat(msg).doesNotContain("KB"); // size=0 时不显示
        }

        @Test
        @DisplayName("assertUploadMessageAssembly 通过规格验证")
        void spec_validates_assembled_message() {
            AippWidgetUpload u = upload();
            assertThatCode(() -> spec.assertUploadMessageAssembly(u, "test.json", "{\"class\":\"E\"}"))
                    .doesNotThrowAnyException();
        }

        @Test
        @DisplayName("of() 工厂方法产生默认 maxSizeKb 和 null buttonLabel")
        void factory_defaults() {
            AippWidgetUpload u = upload();
            assertThat(u.maxSizeKb()).isEqualTo(AippWidgetUpload.DEFAULT_MAX_SIZE_KB);
            assertThat(u.buttonLabel()).isNull();
        }
    }
}
