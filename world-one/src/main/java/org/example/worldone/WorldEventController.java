package org.example.worldone;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/world-events")
public class WorldEventController {
    private final WorldEventService events;

    public WorldEventController(WorldEventService events) {
        this.events = events;
    }

    @GetMapping
    public Map<String, Object> list(@RequestParam(value = "status", required = false) String status,
                                    @RequestParam(value = "world_id", required = false) String worldId) {
        var list = events.list(status, worldId);
        return Map.of("events", list, "total", list.size());
    }

    @PostMapping
    public ResponseEntity<Map<String, Object>> create(@RequestBody Map<String, Object> body) {
        return ResponseEntity.ok(events.create(body == null ? Map.of() : body));
    }

    @PostMapping("/{id}/submit")
    public ResponseEntity<Map<String, Object>> submit(@PathVariable("id") String id,
                                                      @RequestBody(required = false) Map<String, Object> body) {
        @SuppressWarnings("unchecked")
        Map<String, Object> params = body != null && body.get("parameters") instanceof Map<?, ?> m
                ? (Map<String, Object>) m
                : (body == null ? Map.of() : body);
        try {
            return ResponseEntity.ok(events.submit(id, params));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("status", "failed", "error", e.getMessage()));
        }
    }

    @PatchMapping("/{id}/abort")
    public ResponseEntity<Map<String, Object>> abort(@PathVariable("id") String id) {
        try {
            return ResponseEntity.ok(events.abort(id));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("status", "failed", "error", e.getMessage()));
        }
    }

    @PatchMapping("/{id}/rename")
    public ResponseEntity<Map<String, Object>> rename(@PathVariable("id") String id,
                                                       @RequestBody Map<String, Object> body) {
        String displayName = body == null ? "" : String.valueOf(body.getOrDefault("display_name", ""));
        try {
            return ResponseEntity.ok(events.rename(id, displayName));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("status", "failed", "error", e.getMessage()));
        }
    }
}
