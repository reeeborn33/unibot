package org.example.worldone;

import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class AppRegistryEnvFilterTest {

    @Test
    void toolVisibleForRuntimeEnv_filtersByDeclaredEnv() {
        AppRegistry reg = new AppRegistry();
        WorldOneConfigStore cfg = new WorldOneConfigStore();
        cfg.save("", "", "", 0, List.of(Map.of(
                "key", "env",
                "globalValue", "production",
                "appValues", Map.of())));
        inject(reg, cfg);

        Map<String, Object> prodOnly = tool("decision_manual__w__t__production", "production");
        Map<String, Object> stagingOnly = tool("decision_manual__w__t__staging", "staging");
        Map<String, Object> global = tool("world_design", null);

        assertThat(reg.toolVisibleForRuntimeEnv(prodOnly)).isTrue();
        assertThat(reg.toolVisibleForRuntimeEnv(stagingOnly)).isFalse();
        assertThat(reg.toolVisibleForRuntimeEnv(global)).isTrue();
    }

    private static Map<String, Object> tool(String name, String env) {
        Map<String, Object> t = new LinkedHashMap<>();
        t.put("name", name);
        t.put("visibility", List.of("llm"));
        if (env != null) t.put("env", env);
        return t;
    }

    private static void inject(AppRegistry reg, WorldOneConfigStore cfg) {
        try {
            var f = AppRegistry.class.getDeclaredField("configStore");
            f.setAccessible(true);
            f.set(reg, cfg);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
