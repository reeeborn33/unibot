package org.example.aipp;

/**
 * AIPP 应用身份信息 — 由 {@code GET /api/app} 端点返回。
 *
 * <h2>用途</h2>
 * <p>Host（如 world-one）在注册外部 AIPP 应用时，除了读取 {@code /api/tools} / {@code /api/skills}
 * 和 {@code /api/widgets}，还会读取 {@code /api/app} 获取应用的展示元数据，
 * 用于 Apps 启动面板（icon、名称、描述）和主题配色。
 *
 * <h2>端点响应示例（/api/app）</h2>
 * <pre>
 * {
 *   "app_id":          "memory-one",
 *   "app_name":        "记忆管理",
 *   "app_icon":        "&lt;svg viewBox='0 0 24 24'&gt;...&lt;/svg&gt;",
 *   "app_description": "管理 AI Agent 的长期记忆（事实、目标、事件、关系）",
 *   "app_color":       "#7c6ff7",
 *   "is_active":       true,
 *   "version":         "1.0"
 * }
 * </pre>
 *
 * <h2>字段说明</h2>
 * <ul>
 *   <li>{@code appId}          — 唯一标识符，与 {@code /api/tools} / {@code /api/skills} 中的 {@code app} 字段一致</li>
 *   <li>{@code appName}        — 显示名称（中文或本地化名称）</li>
 *   <li>{@code appIcon}        — SVG inline 字符串或公网 URL（建议 SVG，避免外链依赖）</li>
 *   <li>{@code appDescription} — 一行描述（用于 tooltip 或 Apps 面板副标题）</li>
 *   <li>{@code appColor}       — 主题色 hex（用于 Apps 面板卡片着色，如 "#7c6ff7"）</li>
 *   <li>{@code isActive}       — 是否激活（false 时 Host 可在 Apps 面板中置灰或隐藏）</li>
 *   <li>{@code version}        — 版本号（纯展示，用于调试）</li>
 * </ul>
 *
 * @see AippAppSpec#assertValidAppManifest(com.fasterxml.jackson.databind.JsonNode)
 */
public record AippAppManifest(
        String appId,
        String appName,
        String appIcon,
        String appDescription,
        String appColor,
        boolean isActive,
        String version
) {}
