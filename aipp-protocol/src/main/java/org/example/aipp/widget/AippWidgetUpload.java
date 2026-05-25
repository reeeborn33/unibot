package org.example.aipp.widget;

import java.util.List;

/**
 * AIPP Widget 上传能力配置。
 *
 * <p>当 widget manifest 中声明了顶层 {@code upload} 字段时，
 * Host 在聊天输入框旁渲染上传按钮。用户选择文件后，Host 将
 * 文件内容与 {@link #prompt()} 组装成一条用户消息发送给 LLM，
 * LLM 根据 prompt 的指示完成验证并调用 {@link #tools()} 中的工具。
 *
 * <h2>Widget Manifest 示例</h2>
 * <pre>
 * {
 *   "type": "entity-graph",
 *   "supports": { ... },
 *   "upload": {
 *     "accept": [".json"],
 *     "max_size_kb": 256,
 *     "button_label": "导入定义",
 *     "prompt": "用户上传了实体定义文件，请检查每个 JSON 对象是否包含 class 和 class type 字段。
 *                验证通过后，对每个实体依次调用 world_add_definition 工具完成导入。
 *                如有错误，直接告知用户，不要调用工具。",
 *     "tools": ["world_add_definition"]
 *   }
 * }
 * </pre>
 *
 * <h2>Host 组装的消息结构</h2>
 * <pre>
 * 用户上传了文件「{fileName}」（{fileSizeKb} KB）：
 *
 * ```{extension}
 * {fileContent}
 * ```
 *
 * {prompt}
 * </pre>
 *
 * <h2>设计约定</h2>
 * <ul>
 *   <li>{@link #prompt()} 是完整的 LLM 指示，不区分"检查阶段"和"工具调用阶段"——
 *       如何分步骤、调用哪些工具、出错怎么处理，全部在 prompt 中描述。</li>
 *   <li>{@link #tools()} 是此上传流 <em>可用</em> 的工具集合，Host 应在 LLM 系统上下文中
 *       将这些工具声明为可调用；不在此列表中的工具不应在上传流中被调用。</li>
 *   <li>不支持二进制文件，{@link #accept()} 中只应包含文本格式扩展名（.json、.csv、.txt 等）。</li>
 * </ul>
 *
 * @param accept      允许的文件扩展名列表，每项必须以 {@code .} 开头且为小写，如 {@code [".json", ".csv"]}
 * @param maxSizeKb   客户端允许的最大文件大小（KB），默认 256；超出时 Host 拒绝上传并提示用户
 * @param buttonLabel 上传按钮显示的文案；可为 null，Host 默认显示"上传文件"
 * @param prompt      完整的 LLM 指示文本，说明如何处理上传的文件（包括验证步骤和工具调用方式）
 * @param tools       此上传流中 LLM 可使用的工具名称列表；为空列表时 LLM 仅做分析，不调用工具
 */
public record AippWidgetUpload(
        List<String> accept,
        int maxSizeKb,
        String buttonLabel,
        String prompt,
        List<String> tools
) {
    /** 默认最大文件大小（256 KB）。 */
    public static final int DEFAULT_MAX_SIZE_KB = 256;

    /** 默认上传按钮文案。 */
    public static final String DEFAULT_BUTTON_LABEL = "上传文件";

    /**
     * 使用完整参数构造上传配置。
     * {@code buttonLabel} 为 null 时，Host 应使用 {@link #DEFAULT_BUTTON_LABEL}。
     */
    public AippWidgetUpload {
        accept    = accept    != null ? List.copyOf(accept) : List.of();
        tools     = tools     != null ? List.copyOf(tools)  : List.of();
        maxSizeKb = maxSizeKb > 0 ? maxSizeKb : DEFAULT_MAX_SIZE_KB;
    }

    /**
     * 便捷工厂：无按钮自定义文案的简单配置。
     */
    public static AippWidgetUpload of(List<String> accept, String prompt, List<String> tools) {
        return new AippWidgetUpload(accept, DEFAULT_MAX_SIZE_KB, null, prompt, tools);
    }

    /**
     * 判断给定文件名是否在允许的扩展名范围内。
     *
     * @param fileName 文件名，如 {@code "schema.json"}
     * @return 文件扩展名在 {@link #accept()} 中时返回 true
     */
    public boolean accepts(String fileName) {
        if (fileName == null || accept.isEmpty()) return false;
        String lower = fileName.toLowerCase();
        return accept.stream().anyMatch(ext -> lower.endsWith(ext.toLowerCase()));
    }

    /**
     * 组装 Host 发送给 LLM 的完整消息。
     *
     * @param fileName    用户选择的文件名
     * @param fileSizeKb  文件大小（KB，可传 0 表示未知）
     * @param fileContent 文件内容（纯文本）
     * @return 完整的用户消息字符串
     */
    public String assembleMessage(String fileName, int fileSizeKb, String fileContent) {
        String ext = fileName != null && fileName.contains(".")
                ? fileName.substring(fileName.lastIndexOf('.') + 1) : "txt";
        String sizeStr = fileSizeKb > 0 ? " （" + fileSizeKb + " KB）" : "";
        return "用户上传了文件「" + fileName + "」" + sizeStr + "：\n\n"
                + "```" + ext + "\n"
                + fileContent + "\n"
                + "```\n\n"
                + prompt;
    }
}
