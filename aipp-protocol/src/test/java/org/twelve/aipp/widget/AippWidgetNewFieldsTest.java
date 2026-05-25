package org.example.aipp.widget;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 验证 AippWidgetSpec 中新增字段的规格行为：
 * <ul>
 *   <li>App Identity：app_id / is_main / is_canvas_mode</li>
 *   <li>html_widget 响应结构（is_canvas_mode=false 场景）</li>
 * </ul>
 */
@DisplayName("AIPP Widget 新字段规格测试")
class AippWidgetNewFieldsTest {

    private AippWidgetSpec spec;
    private ObjectMapper   json;

    @BeforeEach
    void setUp() {
        spec = new AippWidgetSpec();
        json = new ObjectMapper();
    }

    // ══════════════════════════════════════════════════════════════════════════
    // app_id
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("声明了 app_id 的 widget 应通过验证")
    void widgetWithAppId_passes() {
        assertThatCode(() -> spec.assertWidgetHasAppId(widgetWith("app_id", "memory-one")))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("缺少 app_id 时应报错")
    void widgetMissingAppId_fails() {
        ObjectNode w = baseWidget();
        assertThatThrownBy(() -> spec.assertWidgetHasAppId(w))
                .isInstanceOf(AssertionError.class)
                .hasMessageContaining("app_id");
    }

    @Test
    @DisplayName("app_id 为空字符串时应报错")
    void widgetEmptyAppId_fails() {
        assertThatThrownBy(() -> spec.assertWidgetHasAppId(widgetWith("app_id", "")))
                .isInstanceOf(AssertionError.class);
    }

    // ══════════════════════════════════════════════════════════════════════════
    // is_main
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("is_main=true 应通过验证")
    void widgetIsMainTrue_passes() {
        ObjectNode w = baseWidget();
        w.put("is_main", true);
        assertThatCode(() -> spec.assertWidgetDeclaresIsMain(w)).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("is_main=false 应通过验证")
    void widgetIsMainFalse_passes() {
        ObjectNode w = baseWidget();
        w.put("is_main", false);
        assertThatCode(() -> spec.assertWidgetDeclaresIsMain(w)).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("缺少 is_main 时应报错")
    void widgetMissingIsMain_fails() {
        assertThatThrownBy(() -> spec.assertWidgetDeclaresIsMain(baseWidget()))
                .isInstanceOf(AssertionError.class)
                .hasMessageContaining("is_main");
    }

    @Test
    @DisplayName("is_main 为字符串时应报错（必须 boolean）")
    void widgetIsMainString_fails() {
        assertThatThrownBy(() -> spec.assertWidgetDeclaresIsMain(widgetWith("is_main", "true")))
                .isInstanceOf(AssertionError.class)
                .hasMessageContaining("boolean");
    }

    // ══════════════════════════════════════════════════════════════════════════
    // is_canvas_mode
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("is_canvas_mode=true（Canvas 模式）应通过验证")
    void widgetCanvasModeTrue_passes() {
        ObjectNode w = baseWidget();
        w.put("is_canvas_mode", true);
        assertThatCode(() -> spec.assertWidgetDeclaresIsCanvasMode(w)).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("is_canvas_mode=false（Chat 内嵌模式）应通过验证")
    void widgetCanvasModeFalse_passes() {
        ObjectNode w = baseWidget();
        w.put("is_canvas_mode", false);
        assertThatCode(() -> spec.assertWidgetDeclaresIsCanvasMode(w)).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("缺少 is_canvas_mode 时应报错")
    void widgetMissingCanvasMode_fails() {
        assertThatThrownBy(() -> spec.assertWidgetDeclaresIsCanvasMode(baseWidget()))
                .isInstanceOf(AssertionError.class)
                .hasMessageContaining("is_canvas_mode");
    }

    // ══════════════════════════════════════════════════════════════════════════
    // assertWidgetHasFullAppIdentity
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("包含全部 App Identity 字段的 widget 应一次通过")
    void fullAppIdentity_passes() {
        ObjectNode w = baseWidget();
        w.put("app_id",         "memory-one");
        w.put("is_main",        true);
        w.put("is_canvas_mode", true);
        assertThatCode(() -> spec.assertWidgetHasFullAppIdentity(w)).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("缺少任意一个 App Identity 字段时应报错")
    void partialAppIdentity_fails() {
        ObjectNode w = baseWidget();
        w.put("app_id",  "memory-one");
        w.put("is_main", true);
        // 故意不加 is_canvas_mode
        assertThatThrownBy(() -> spec.assertWidgetHasFullAppIdentity(w))
                .isInstanceOf(AssertionError.class)
                .hasMessageContaining("is_canvas_mode");
    }

    // ══════════════════════════════════════════════════════════════════════════
    // app-owned renderer
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("AIPP app widget 声明 render.kind/url 时应通过验证")
    void appOwnedRenderer_passes() {
        ObjectNode w = baseWidget();
        ObjectNode render = json.createObjectNode();
        render.put("kind", "esm");
        render.put("url", "/widgets/action-list/action-list.js");
        w.set("render", render);

        assertThatCode(() -> spec.assertWidgetDeclaresAppOwnedRenderer(w))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("AIPP app widget 缺少 render 时应报错")
    void appWidgetMissingRenderer_fails() {
        assertThatThrownBy(() -> spec.assertWidgetDeclaresAppOwnedRenderer(baseWidget()))
                .isInstanceOf(AssertionError.class)
                .hasMessageContaining("render");
    }

    @Test
    @DisplayName("render.kind 非法时应报错")
    void appWidgetRendererKindInvalid_fails() {
        ObjectNode w = baseWidget();
        ObjectNode render = json.createObjectNode();
        render.put("kind", "builtin");
        render.put("url", "/widgets/action-list/index.html");
        w.set("render", render);

        assertThatThrownBy(() -> spec.assertWidgetDeclaresAppOwnedRenderer(w))
                .isInstanceOf(AssertionError.class)
                .hasMessageContaining("kind");
    }

    @Test
    @DisplayName("sys.* widget 不适用 app-owned renderer 断言")
    void systemWidgetRendererAssertion_fails() {
        ObjectNode w = baseWidget();
        w.put("type", "sys.confirm");

        assertThatThrownBy(() -> spec.assertWidgetDeclaresAppOwnedRenderer(w))
                .isInstanceOf(AssertionError.class)
                .hasMessageContaining("sys.*");
    }

    // ══════════════════════════════════════════════════════════════════════════
    // html_widget 响应
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("包含合法 html_widget 的响应应通过验证")
    void validHtmlWidgetResponse_passes() throws Exception {
        JsonNode resp = json.readTree("""
            {
              "html_widget": {
                "widget_type": "action-list",
                "title": "Actions",
                "data": { "items": [] }
              }
            }
            """);
        assertThatCode(() -> spec.assertHtmlWidgetResponse("my_tool", resp))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("html_widget 中没有 widget_type/data 字段时应报错")
    void htmlWidgetMissingRendererPayload_fails() throws Exception {
        JsonNode resp = json.readTree("{\"html_widget\":{\"height\":\"300px\"}}");
        assertThatThrownBy(() -> spec.assertHtmlWidgetResponse("my_tool", resp))
                .isInstanceOf(AssertionError.class)
                .hasMessageContaining("widget_type");
    }

    @Test
    @DisplayName("widget_type 为空字符串时应报错")
    void htmlWidgetEmptyWidgetType_fails() throws Exception {
        JsonNode resp = json.readTree("{\"html_widget\":{\"widget_type\":\"\",\"data\":{}}}");
        assertThatThrownBy(() -> spec.assertHtmlWidgetResponse("my_tool", resp))
                .isInstanceOf(AssertionError.class);
    }

    @Test
    @DisplayName("响应无 html_widget 字段时应报错")
    void missingHtmlWidget_fails() throws Exception {
        JsonNode resp = json.readTree("{\"ok\":true}");
        assertThatThrownBy(() -> spec.assertHtmlWidgetResponse("my_tool", resp))
                .isInstanceOf(AssertionError.class)
                .hasMessageContaining("html_widget");
    }

    @Test
    @DisplayName("inline widget 响应含 canvas 时应报错（不允许触发 Canvas Mode）")
    void inlineWidgetWithCanvas_fails() throws Exception {
        JsonNode resp = json.readTree("{\"canvas\":{\"action\":\"open\",\"widget_type\":\"x\"}}");
        assertThatThrownBy(() -> spec.assertInlineWidgetResponseHasNoCanvas("my_tool", resp))
                .isInstanceOf(AssertionError.class)
                .hasMessageContaining("canvas");
    }

    // ── helpers ──────────────────────────────────────────────────────────────

    private ObjectNode baseWidget() {
        ObjectNode w = json.createObjectNode();
        w.put("type",   "test-widget");
        w.put("source", "external");
        return w;
    }

    private ObjectNode widgetWith(String key, String value) {
        ObjectNode w = baseWidget();
        w.put(key, value);
        return w;
    }
}
