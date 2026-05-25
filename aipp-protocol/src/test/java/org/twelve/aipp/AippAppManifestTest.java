package org.example.aipp;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.assertThatCode;

/**
 * 验证 AippAppSpec.assertValidAppManifest() 的行为 —
 * 即 /api/app 端点响应必须符合 AIPP App Manifest 规格。
 */
@DisplayName("AIPP App Manifest 规格测试")
class AippAppManifestTest {

    private AippAppSpec spec;
    private ObjectMapper json;

    @BeforeEach
    void setUp() {
        spec = new AippAppSpec();
        json = new ObjectMapper();
    }

    // ── 合法 manifest ────────────────────────────────────────────────────────

    @Test
    @DisplayName("合法的 app manifest 应通过所有验证")
    void validAppManifest_passes() {
        JsonNode manifest = validManifest("memory-one");
        assertThatCode(() -> spec.assertValidAppManifest(manifest))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("app_id 必须是 kebab-case 格式")
    void appId_mustBeKebabCase() {
        assertThatCode(() -> spec.assertValidAppManifest(validManifest("memory-one")))
                .doesNotThrowAnyException();
        assertThatCode(() -> spec.assertValidAppManifest(validManifest("world-entitir")))
                .doesNotThrowAnyException();
    }

    // ── 缺少必填字段 ──────────────────────────────────────────────────────────

    @Test
    @DisplayName("缺少 app_id 时应报错")
    void missingAppId_fails() {
        ObjectNode m = validManifest("x").deepCopy();
        m.remove("app_id");
        assertThatThrownBy(() -> spec.assertValidAppManifest(m))
                .isInstanceOf(AssertionError.class)
                .hasMessageContaining("app_id");
    }

    @Test
    @DisplayName("缺少 app_name 时应报错")
    void missingAppName_fails() {
        ObjectNode m = validManifest("x").deepCopy();
        m.remove("app_name");
        assertThatThrownBy(() -> spec.assertValidAppManifest(m))
                .isInstanceOf(AssertionError.class)
                .hasMessageContaining("app_name");
    }

    @Test
    @DisplayName("缺少 app_icon 时应报错")
    void missingAppIcon_fails() {
        ObjectNode m = validManifest("x").deepCopy();
        m.remove("app_icon");
        assertThatThrownBy(() -> spec.assertValidAppManifest(m))
                .isInstanceOf(AssertionError.class)
                .hasMessageContaining("app_icon");
    }

    @Test
    @DisplayName("缺少 is_active 时应报错")
    void missingIsActive_fails() {
        ObjectNode m = validManifest("memory-one").deepCopy();
        m.remove("is_active");
        assertThatThrownBy(() -> spec.assertValidAppManifest(m))
                .isInstanceOf(AssertionError.class)
                .hasMessageContaining("is_active");
    }

    @Test
    @DisplayName("is_active 非 boolean 时应报错")
    void isActiveNotBoolean_fails() {
        ObjectNode m = validManifest("memory-one").deepCopy();
        m.put("is_active", "yes");
        assertThatThrownBy(() -> spec.assertValidAppManifest(m))
                .isInstanceOf(AssertionError.class)
                .hasMessageContaining("boolean");
    }

    @Test
    @DisplayName("app_id 含大写字母时应报错（必须 kebab-case）")
    void appIdWithUpperCase_fails() {
        ObjectNode m = validManifest("Memory-One").deepCopy();
        assertThatThrownBy(() -> spec.assertValidAppManifest(m))
                .isInstanceOf(AssertionError.class)
                .hasMessageContaining("kebab-case");
    }

    // ── 跨接口一致性 ──────────────────────────────────────────────────────────

    @Test
    @DisplayName("app_id 与 /api/skills.app 不一致时应报错")
    void appIdConsistency_fails() throws Exception {
        JsonNode manifest = validManifest("memory-one");
        JsonNode skills   = json.readTree("{\"app\":\"different-app\",\"version\":\"1.0\",\"skills\":[]}");
        assertThatThrownBy(() -> spec.assertAppIdConsistency(manifest, skills))
                .isInstanceOf(AssertionError.class)
                .hasMessageContaining("memory-one")
                .hasMessageContaining("different-app");
    }

    @Test
    @DisplayName("app_id 与 /api/skills.app 一致时通过")
    void appIdConsistency_passes() throws Exception {
        JsonNode manifest = validManifest("memory-one");
        JsonNode skills   = json.readTree("{\"app\":\"memory-one\",\"version\":\"1.0\",\"skills\":[]}");
        assertThatCode(() -> spec.assertAppIdConsistency(manifest, skills))
                .doesNotThrowAnyException();
    }

    // ── helpers ──────────────────────────────────────────────────────────────

    private ObjectNode validManifest(String appId) {
        ObjectNode m = json.createObjectNode();
        m.put("app_id",          appId);
        m.put("app_name",        "测试应用");
        m.put("app_icon",        "<svg viewBox='0 0 24 24'><circle cx='12' cy='12' r='10'/></svg>");
        m.put("app_description", "用于测试的 AIPP 示例应用");
        m.put("app_color",       "#7c6ff7");
        m.put("is_active",       true);
        m.put("version",         "1.0");
        return m;
    }
}
