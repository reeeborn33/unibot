package org.example.aipp.widget;

/**
 * AIPP 标准 Widget 主题配置。
 *
 * <p>当 host（如 world-one）嵌入一个 widget 时，将此对象序列化并通过以下两种方式传递：
 * <ol>
 *   <li><b>CSS 变量</b>：在 widget 根容器上设置 {@code --aipp-*} 命名的 CSS 自定义属性，
 *       widget 内部样式直接读取这些变量，无需感知 host 的主题系统。</li>
 *   <li><b>data 属性</b>：{@code data-aipp-language} 用于国际化，widget 读取后加载对应语言包。</li>
 * </ol>
 *
 * <h2>与 host CSS 变量的映射约定</h2>
 * <pre>
 *   AippWidgetTheme.background  →  --aipp-bg         (e.g. #0a0b10)
 *   AippWidgetTheme.surface     →  --aipp-surface    (e.g. #13151f)
 *   AippWidgetTheme.text        →  --aipp-text       (e.g. #d0d8f0)
 *   AippWidgetTheme.textDim     →  --aipp-text-dim   (e.g. #6b7a9e)
 *   AippWidgetTheme.border      →  --aipp-border     (e.g. #272b3e)
 *   AippWidgetTheme.accent      →  --aipp-accent     (e.g. #7c6ff7)
 *   AippWidgetTheme.font        →  --aipp-font       (e.g. "system-ui")
 *   AippWidgetTheme.fontSize    →  --aipp-font-size  (e.g. 13px)
 *   AippWidgetTheme.radius      →  --aipp-radius     (e.g. 8px)
 *   AippWidgetTheme.language    →  data-aipp-language
 * </pre>
 *
 * <h2>前端应用示例</h2>
 * <pre>
 *   function applyAippTheme(containerEl, theme) {
 *     containerEl.style.setProperty('--aipp-bg',       theme.background);
 *     containerEl.style.setProperty('--aipp-surface',  theme.surface);
 *     containerEl.style.setProperty('--aipp-text',     theme.text);
 *     containerEl.style.setProperty('--aipp-text-dim', theme.textDim);
 *     containerEl.style.setProperty('--aipp-border',   theme.border);
 *     containerEl.style.setProperty('--aipp-accent',   theme.accent);
 *     containerEl.style.setProperty('--aipp-font',     theme.font);
 *     containerEl.style.setProperty('--aipp-font-size', theme.fontSize + 'px');
 *     containerEl.style.setProperty('--aipp-radius',   theme.radius + 'px');
 *     containerEl.dataset.aippLanguage = theme.language;
 *   }
 * </pre>
 *
 * @param background  画布/页面背景色（hex）
 * @param surface     面板/卡片背景色（hex）
 * @param text        主文字颜色（hex）
 * @param textDim     次要文字颜色（hex）
 * @param border      边框颜色（hex）
 * @param accent      强调色 / 主操作色（hex）
 * @param font        字体族（CSS font-family 值，如 {@code "system-ui, sans-serif"}）
 * @param fontSize    基础字号（px，如 {@code 13}）
 * @param radius      默认圆角（px，如 {@code 8}）
 * @param language    语言代码（如 {@code "zh"}、{@code "en"}）
 * @param darkMode    是否深色主题（widget 可据此调整图标/对比度策略）
 */
public record AippWidgetTheme(
        String  background,
        String  surface,
        String  text,
        String  textDim,
        String  border,
        String  accent,
        String  font,
        int     fontSize,
        int     radius,
        String  language,
        boolean darkMode
) {

    /** 内置的深色默认主题（与 world-one 暗色模式一致）。 */
    public static AippWidgetTheme darkDefault() {
        return new AippWidgetTheme(
                "#0a0b10", "#13151f", "#d0d8f0", "#6b7a9e",
                "#272b3e", "#7c6ff7",
                "system-ui, -apple-system, sans-serif",
                13, 8, "zh", true
        );
    }

    /** 内置的浅色默认主题。 */
    public static AippWidgetTheme lightDefault() {
        return new AippWidgetTheme(
                "#f5f5f5", "#ffffff", "#1a1a2e", "#6b7a9e",
                "#e0e0e0", "#6c5ce7",
                "system-ui, -apple-system, sans-serif",
                13, 8, "zh", false
        );
    }

    /**
     * 返回完整的 CSS 变量 Map，key 为 CSS property name（含 {@code --aipp-} 前缀）。
     * 可直接用于前端 {@code style.setProperty(k, v)} 遍历。
     */
    public java.util.Map<String, String> toCssVars() {
        java.util.LinkedHashMap<String, String> map = new java.util.LinkedHashMap<>();
        map.put("--aipp-bg",        background);
        map.put("--aipp-surface",   surface);
        map.put("--aipp-text",      text);
        map.put("--aipp-text-dim",  textDim);
        map.put("--aipp-border",    border);
        map.put("--aipp-accent",    accent);
        map.put("--aipp-font",      font);
        map.put("--aipp-font-size", fontSize + "px");
        map.put("--aipp-radius",    radius + "px");
        return map;
    }
}
