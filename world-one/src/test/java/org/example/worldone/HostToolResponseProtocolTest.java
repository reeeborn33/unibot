package org.example.worldone;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class HostToolResponseProtocolTest {

    private static final ObjectMapper JSON = new ObjectMapper();

    @Test
    void shouldOpenNewTaskSession_whenNewSessionPresent() throws Exception {
        JsonNode root = JSON.readTree("{\"new_session\":{\"name\":\"T\",\"type\":\"task\"}}");
        assertThat(HostToolResponseProtocol.shouldOpenNewTaskSession(root)).isTrue();
    }

    @Test
    void shouldOpenNewTaskSession_whenSessionModeNewTaskOnly() throws Exception {
        JsonNode root = JSON.readTree("{\"session_mode\":\"new_task\",\"template_id\":\"onboarding_started\"}");
        assertThat(HostToolResponseProtocol.shouldOpenNewTaskSession(root)).isTrue();
    }

    @Test
    void shouldNotOpenNewTaskSession_forMainSessionMode() throws Exception {
        JsonNode root = JSON.readTree("{\"session_mode\":\"main_session\"}");
        assertThat(HostToolResponseProtocol.shouldOpenNewTaskSession(root)).isFalse();
    }

    @Test
    void newSessionDirective_synthesizesFromSessionMode() throws Exception {
        JsonNode root = JSON.readTree("{\"session_mode\":\"new_task\",\"template_id\":\"hire_flow\"}");
        JsonNode ns = HostToolResponseProtocol.newSessionDirective(root, JSON);
        assertThat(ns.isMissingNode()).isFalse();
        assertThat(ns.path("type").asText()).isEqualTo("task");
        assertThat(ns.path("name").asText()).isEqualTo("Decision: hire_flow");
    }

    @Test
    void newSessionDirective_prefersExplicitBlock() throws Exception {
        JsonNode root = JSON.readTree(
                "{\"session_mode\":\"new_task\",\"new_session\":{\"name\":\"自定义\",\"type\":\"task\"}}");
        JsonNode ns = HostToolResponseProtocol.newSessionDirective(root, JSON);
        assertThat(ns.path("name").asText()).isEqualTo("自定义");
    }

    @Test
    void resolveHostUiSessionId_fromContext() {
        Map<String, Object> body = Map.of(
                "_context", Map.of("sessionId", "ui-task-42", "userId", "default"));
        assertThat(HostToolResponseProtocol.resolveHostUiSessionId(body, Map.of()))
                .isEqualTo("ui-task-42");
    }

    @Test
    void resolveHostUiSessionId_prefersBodySessionId() {
        Map<String, Object> body = Map.of(
                "session_id", "ui-from-body",
                "_context", Map.of("sessionId", "ui-from-ctx"));
        assertThat(HostToolResponseProtocol.resolveHostUiSessionId(body, Map.of()))
                .isEqualTo("ui-from-body");
    }

    @Test
    void hasParameterMissingEvent_detectsEventsArray() throws Exception {
        JsonNode root = JSON.readTree("{\"events\":[{\"type\":\"parameter_missing\"}]}");
        assertThat(HostToolResponseProtocol.hasParameterMissingEvent(root)).isTrue();
    }
}
