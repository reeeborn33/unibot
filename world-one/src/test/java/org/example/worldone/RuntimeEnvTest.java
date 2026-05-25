package org.example.worldone;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class RuntimeEnvTest {

    @Test
    void normalize_defaultsToProduction() {
        assertThat(RuntimeEnv.normalize(null)).isEqualTo("production");
        assertThat(RuntimeEnv.normalize("")).isEqualTo("production");
        assertThat(RuntimeEnv.normalize("STAGING")).isEqualTo("staging");
    }

    @Test
    void matchesManifestEnv_blankEnvMeansAllEnvironments() {
        assertThat(RuntimeEnv.matchesManifestEnv(Map.of(), "production")).isTrue();
        assertThat(RuntimeEnv.matchesManifestEnv(Map.of("name", "x"), "staging")).isTrue();
    }

    @Test
    void matchesManifestEnv_singleEnv() {
        Map<String, Object> tool = Map.of("env", "staging");
        assertThat(RuntimeEnv.matchesManifestEnv(tool, "staging")).isTrue();
        assertThat(RuntimeEnv.matchesManifestEnv(tool, "production")).isFalse();
    }

    @Test
    void matchesManifestEnv_envsArray() {
        Map<String, Object> tool = Map.of("envs", List.of("production", "staging"));
        assertThat(RuntimeEnv.matchesManifestEnv(tool, "staging")).isTrue();
        assertThat(RuntimeEnv.matchesManifestEnv(tool, "draft")).isFalse();
    }
}
