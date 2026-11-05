package com.seeyou.see_you.util;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * CSV 导出工具测试。
 *
 * <p>覆盖三个真实踩过的坑：Excel 中文乱码（BOM）、一个引号毁掉整行（转义）、
 * CSV 公式注入（{@code =cmd|...} 会被 Excel 当公式执行）。
 */
class CsvUtilTest {

    @Test
    @DisplayName("正文里的双引号转义成两个双引号，并整体加引号包裹")
    void escapeQuotes() {
        assertEquals("\"他说\"\"你好\"\"\"", CsvUtil.cell("他说\"你好\""));
        assertEquals("\"今天很开心\"", CsvUtil.cell("今天很开心"));
    }

    @Test
    @DisplayName("正文里的换行 / 连续空白折叠成单个空格，避免撑成多行")
    void collapseWhitespace() {
        assertEquals("\"第一行 第二行\"", CsvUtil.text("第一行\n第二行"));
        assertEquals("\"a b c\"", CsvUtil.text("a\t\t\r\n  b   c "));
    }

    @Test
    @DisplayName("null 输出空单元格，不出现字符串 \"null\"")
    void nullBecomesEmpty() {
        assertEquals("\"\"", CsvUtil.cell(null));
        assertEquals("\"\"", CsvUtil.text(null));
        assertEquals("\"0\"", CsvUtil.cell(0));
    }

    @Test
    @DisplayName("以 = + - @ 开头的单元格补前导单引号（防 Excel 公式注入）")
    void guardFormulaInjection() {
        assertEquals("\"'=cmd|'/c calc'!A0\"", CsvUtil.cell("=cmd|'/c calc'!A0"));
        assertEquals("\"'+123456\"", CsvUtil.cell("+123456"));
        assertEquals("\"'-2+3\"", CsvUtil.cell("-2+3"));
        assertEquals("\"'@sum(x)\"", CsvUtil.cell("@sum(x)"));
        // 中间的等号不受影响
        assertEquals("\"1+1=2\"", CsvUtil.cell("1+1=2"));
    }

    @Test
    @DisplayName("行拼接：单元格用逗号连接并以换行结尾")
    void joinLine() {
        assertEquals("\"a\",\"b\",\"c\"\n", CsvUtil.line(CsvUtil.cell("a"), CsvUtil.cell("b"), CsvUtil.cell("c")));
    }

    @Test
    @DisplayName("完整 CSV 以 UTF-8 BOM 开头，行数为 表头+N")
    void buildWithBom() {
        List<String> rows = Arrays.asList(
                CsvUtil.line("日期", "情绪"),
                CsvUtil.line(CsvUtil.cell("2026-01-01"), CsvUtil.cell("开心")));
        String csv = CsvUtil.build(rows);
        assertTrue(csv.startsWith("\uFEFF"), "缺少 BOM，Excel 打开会中文乱码");
        assertEquals(2, csv.split("\n", -1).length - 1, "除 BOM 外应有 2 行");
    }
}
