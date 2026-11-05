package com.seeyou.see_you.util;

import java.util.List;

/**
 * CSV 导出工具（日记导出 / 数据看板导出共用）。
 *
 * <p>自己拼 CSV 看似简单，实际有三个坑，这里统一处理掉：
 * <ol>
 *   <li><b>Excel 中文乱码</b>：Excel 打开 UTF-8 的 CSV 时会按本地编码（GBK）解析，
 *       必须在文件开头写 UTF-8 BOM（EF BB BF），见 {@link #BOM}。</li>
 *   <li><b>双引号破坏列结构</b>：正文里的 {@code "} 必须写成 {@code ""}，单元格整体用引号包裹，
 *       否则一个引号会让后面所有列错位。</li>
 *   <li><b>CSV 公式注入</b>：以 {@code = + - @} 开头的单元格，Excel 会当成公式执行
 *       （经典 payload：{@code =cmd|'/c calc'!A0}）。这里在最前面补一个单引号强制转为文本。</li>
 * </ol>
 *
 * <p>纯静态工具，无外部依赖，便于单元测试覆盖（见 CsvUtilTest）。
 */
public final class CsvUtil {

    /** UTF-8 BOM：Excel 识别 UTF-8 编码的标记，不加则中文乱码 */
    public static final String BOM = "\uFEFF";

    /** 会被 Excel / WPS 当作公式执行的前导字符 */
    private static final String RISKY_PREFIX = "=+-@\t\r";

    private CsvUtil() {
    }

    /**
     * 普通单元格：转义双引号 → 防公式注入 → 加引号包裹。
     *
     * @param value 原始值，null 输出空单元格
     */
    public static String cell(Object value) {
        return quote(guard(value == null ? "" : String.valueOf(value)));
    }

    /**
     * 正文单元格：在 {@link #cell(Object)} 的基础上折叠空白。
     * 日记正文里的换行会把一行 CSV 撑成多行，必须先压平成单空格。
     */
    public static String text(Object value) {
        if (value == null) return quote("");
        return cell(String.valueOf(value).replaceAll("\\s+", " ").trim());
    }

    /** 拼一行：各单元格用英文逗号连接，行尾换行 */
    public static String line(String... cells) {
        return String.join(",", cells) + "\n";
    }

    /** 把若干行拼成完整 CSV 文本（含 BOM） */
    public static String build(List<String> rows) {
        StringBuilder sb = new StringBuilder(BOM);
        if (rows != null) sb.append(String.join("", rows));
        return sb.toString();
    }

    private static String quote(String s) {
        return "\"" + s.replace("\"", "\"\"") + "\"";
    }

    private static String guard(String s) {
        if (s.isEmpty()) return s;
        return RISKY_PREFIX.indexOf(s.charAt(0)) >= 0 ? "'" + s : s;
    }
}
