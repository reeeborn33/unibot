package org.example.aipp;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 验证 Host 解耦相关协议字段的断言方法。
 *
 * <p>覆盖 lifecycle / output_widget_rules / runtime_event_callback / event_subscriptions。
 * 这些字段是世界（world-entitir / memory-one 等）AIPP 自描述特化行为的契约，
 * Host 不得对名字或领域词做特判。
 */
class HostDecouplingProtocolFieldsTest {

    private static final ObjectMapper M = new ObjectMapper();
    private final AippAppSpec spec = new AippAppSpec();

    private JsonNode parse(String json) throws Exception { return M.readTree(json); }

    @Test
    void lifecycle_legalValues_pass() throws Exception {
        for (String v : new String[]{"on_demand", "post_turn", "pre_turn"}) {
            JsonNode skill = parse("{\"name\":\"x\",\"lifecycle\":\"" + v + "\"}");
            assertThatNoException().isThrownBy(() -> spec.assertValidLifecycle(skill));
        }
    }

    @Test
    void lifecycle_illegalValue_fails() throws Exception {
        JsonNode skill = parse("{\"name\":\"x\",\"lifecycle\":\"forever\"}");
        assertThatThrownBy(() -> spec.assertValidLifecycle(skill))
                .isInstanceOf(AssertionError.class);
    }

    @Test
    void lifecycle_absent_isOk() throws Exception {
        JsonNode skill = parse("{\"name\":\"x\"}");
        assertThatNoException().isThrownBy(() -> spec.assertValidLifecycle(skill));
    }

    @Test
    void outputWidgetRules_validShape_pass() throws Exception {
        JsonNode skill = parse(
                "{\"name\":\"world_design\",\"output_widget_rules\":{" +
                "\"force_canvas_when\":[\"graph\",\"session_id\"]," +
                "\"default_widget\":\"entity-graph\"}}");
        assertThatNoException().isThrownBy(() -> spec.assertValidOutputWidgetRules(skill));
    }

    @Test
    void outputWidgetRules_blankDefaultWidget_fails() throws Exception {
        JsonNode skill = parse(
                "{\"name\":\"x\",\"output_widget_rules\":{\"default_widget\":\"\"}}");
        assertThatThrownBy(() -> spec.assertValidOutputWidgetRules(skill))
                .isInstanceOf(AssertionError.class);
    }

    @Test
    void runtimeEventCallback_validShape_pass() throws Exception {
        JsonNode skill = parse(
                "{\"name\":\"x\",\"runtime_event_callback\":{" +
                "\"events\":[\"decision_result\",\"action_resume\"]," +
                "\"path\":\"/api/worlds/{worldId}/decision-result\"}}");
        assertThatNoException().isThrownBy(() -> spec.assertValidRuntimeEventCallback(skill));
    }

    @Test
    void runtimeEventCallback_emptyEvents_fails() throws Exception {
        JsonNode skill = parse(
                "{\"name\":\"x\",\"runtime_event_callback\":{\"events\":[],\"path\":\"/foo\"}}");
        assertThatThrownBy(() -> spec.assertValidRuntimeEventCallback(skill))
                .isInstanceOf(AssertionError.class);
    }

    @Test
    void runtimeEventCallback_blankPath_fails() throws Exception {
        JsonNode skill = parse(
                "{\"name\":\"x\",\"runtime_event_callback\":{\"events\":[\"a\"],\"path\":\"\"}}");
        assertThatThrownBy(() -> spec.assertValidRuntimeEventCallback(skill))
                .isInstanceOf(AssertionError.class);
    }

    @Test
    void eventSubscriptions_validArray_pass() throws Exception {
        JsonNode subs = parse("[\"workspace.changed\",\"session.opened\"]");
        assertThatNoException().isThrownBy(() -> spec.assertValidEventSubscriptions(subs));
    }

    @Test
    void eventSubscriptions_blankElement_fails() throws Exception {
        JsonNode subs = parse("[\"workspace.changed\",\"\"]");
        assertThatThrownBy(() -> spec.assertValidEventSubscriptions(subs))
                .isInstanceOf(AssertionError.class);
    }

    @Test
    void eventSubscriptions_notArray_fails() throws Exception {
        JsonNode subs = parse("{\"a\":1}");
        assertThatThrownBy(() -> spec.assertValidEventSubscriptions(subs))
                .isInstanceOf(AssertionError.class);
    }

    /**
     * 协议常量集合：lifecycle 取值确认。
     * 一旦增删合法值，本测试会失败，提醒同步更新所有 host/AIPP 调用方。
     */
    @Test
    void validLifecyclesIsAuthoritativeSet() {
        assertThat(AippAppSpec.VALID_LIFECYCLES)
                .containsExactlyInAnyOrder("on_demand", "post_turn", "pre_turn");
    }
}
