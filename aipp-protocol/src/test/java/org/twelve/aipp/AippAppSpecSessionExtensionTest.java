package org.example.aipp;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Layer 3 {@code session} 扩展：支持 creates_on/loads_on 与 app session（session_type + app_id）两类声明。
 */
@DisplayName("AippAppSpec — session 扩展")
class AippAppSpecSessionExtensionTest {

    private AippAppSpec spec;
    private ObjectMapper json;

    @BeforeEach
    void setUp() {
        spec = new AippAppSpec();
        json = new ObjectMapper();
    }

    @Test
    @DisplayName("仅 creates_on 时通过")
    void createsOnOnly_passes() {
        JsonNode skill = minimalSkillWithSession(s -> s.put("creates_on", "name"));
        assertThatCode(() -> spec.assertValidSkillSessionExtension(skill)).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("仅 loads_on 时通过")
    void loadsOnOnly_passes() {
        JsonNode skill = minimalSkillWithSession(s -> s.put("loads_on", "session_id"));
        assertThatCode(() -> spec.assertValidSkillSessionExtension(skill)).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("session_type=app 且 app_id 非空时通过（无需 creates_on/loads_on）")
    void appSessionDeclaration_passes() {
        JsonNode skill = minimalSkillWithSession(s -> {
            s.put("session_type", "app");
            s.put("app_id", "my-app");
        });
        assertThatCode(() -> spec.assertValidSkillSessionExtension(skill)).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("session_type=app 但 app_id 为空时失败")
    void appSessionWithoutAppId_fails() {
        JsonNode skill = minimalSkillWithSession(s -> s.put("session_type", "app"));
        assertThatThrownBy(() -> spec.assertValidSkillSessionExtension(skill))
                .isInstanceOf(AssertionError.class)
                .hasMessageContaining("creates_on/loads_on")
                .hasMessageContaining("app session");
    }

    @Test
    @DisplayName("声明了 session 但既无路由条件也无合法 app 声明时失败")
    void emptySessionObject_fails() {
        JsonNode skill = minimalSkillWithSession(s -> { /* empty object */ });
        assertThatThrownBy(() -> spec.assertValidSkillSessionExtension(skill))
                .isInstanceOf(AssertionError.class);
    }

    @Test
    @DisplayName("creates_on + session_type=app + app_id 组合通过")
    void combinedAppAndRouteConditions_passes() {
        JsonNode skill = minimalSkillWithSession(s -> {
            s.put("session_type", "app");
            s.put("app_id", "x");
            s.put("creates_on", "name");
            s.put("loads_on", "session_id");
        });
        assertThatCode(() -> spec.assertValidSkillSessionExtension(skill)).doesNotThrowAnyException();
    }

    private JsonNode minimalSkillWithSession(java.util.function.Consumer<ObjectNode> sessionConfigurer) {
        ObjectNode skill = json.createObjectNode();
        skill.put("name", "test_skill");
        skill.put("description", "d");
        ObjectNode params = json.createObjectNode();
        params.put("type", "object");
        params.set("properties", json.createObjectNode());
        params.set("required", json.createArrayNode());
        skill.set("parameters", params);
        ObjectNode canvas = json.createObjectNode();
        canvas.put("triggers", false);
        skill.set("canvas", canvas);
        ObjectNode session = json.createObjectNode();
        sessionConfigurer.accept(session);
        skill.set("session", session);
        return skill;
    }
}
