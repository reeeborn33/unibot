package org.example.worldone.db;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;

import java.time.Instant;

/**
 * Host-owned world event state. Business payloads are JSON text so events stay
 * schema-light while world-one owns status transitions.
 */
@Entity
@Table(name = "world_events",
       indexes = {
           @Index(name = "idx_world_event_status", columnList = "status, created_at"),
           @Index(name = "idx_world_event_world", columnList = "world_id, status, created_at"),
           @Index(name = "idx_world_event_scope", columnList = "scope_id, created_at")
       })
public class WorldEventEntity {
    @Id
    @Column(length = 80)
    private String id;

    @Column(nullable = false, length = 80)
    private String type;

    @Column(nullable = false, length = 40)
    private String status;

    @Column(name = "world_id", length = 160)
    private String worldId;

    @Column(name = "scope_id", length = 160)
    private String scopeId;

    /** Ui session where the originating widget was rendered (used to route the
     *  user back when they click the event entry in the task panel). */
    @Column(name = "ui_session_id", length = 160)
    private String uiSessionId;

    @Column(name = "source_json", columnDefinition = "TEXT")
    private String sourceJson;

    @Column(name = "business_data_json", columnDefinition = "TEXT")
    private String businessDataJson;

    @Column(name = "tags_json", columnDefinition = "TEXT")
    private String tagsJson;

    @Column(name = "widget_json", columnDefinition = "TEXT")
    private String widgetJson;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public WorldEventEntity() {}

    public String getId() { return id; }
    public String getType() { return type; }
    public String getStatus() { return status; }
    public String getWorldId() { return worldId; }
    public String getScopeId() { return scopeId; }
    public String getUiSessionId() { return uiSessionId; }
    public String getSourceJson() { return sourceJson; }
    public String getBusinessDataJson() { return businessDataJson; }
    public String getTagsJson() { return tagsJson; }
    public String getWidgetJson() { return widgetJson; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }

    public void setId(String id) { this.id = id; }
    public void setType(String type) { this.type = type; }
    public void setStatus(String status) { this.status = status; }
    public void setWorldId(String worldId) { this.worldId = worldId; }
    public void setScopeId(String scopeId) { this.scopeId = scopeId; }
    public void setUiSessionId(String uiSessionId) { this.uiSessionId = uiSessionId; }
    public void setSourceJson(String sourceJson) { this.sourceJson = sourceJson; }
    public void setBusinessDataJson(String businessDataJson) { this.businessDataJson = businessDataJson; }
    public void setTagsJson(String tagsJson) { this.tagsJson = tagsJson; }
    public void setWidgetJson(String widgetJson) { this.widgetJson = widgetJson; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
}
