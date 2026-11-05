package com.seeyou.see_you.util;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * 分页参数收敛工具测试。
 *
 * <p>对应【安全】清单里的一条：列表接口的 size 若不做收敛，{@code ?size=999999}
 * 一次就能把整张表拖出来（本项目列表接口多为登录后可访问，风险更实际）。
 */
class PageLimitTest {

    @Test
    @DisplayName("size 为 null / 0 / 负数时回落到默认值 10")
    void sizeFallsBackToDefault() {
        assertEquals(10, PageLimit.of(null));
        assertEquals(10, PageLimit.of(0));
        assertEquals(10, PageLimit.of(-20));
    }

    @Test
    @DisplayName("size 超过上限 100 时截断为 100")
    void sizeTruncatedAtMax() {
        assertEquals(100, PageLimit.of(100));
        assertEquals(100, PageLimit.of(101));
        assertEquals(100, PageLimit.of(999999));
    }

    @Test
    @DisplayName("size 在区间内时原样返回")
    void sizeKeptInRange() {
        assertEquals(1, PageLimit.of(1));
        assertEquals(20, PageLimit.of(20));
        assertEquals(99, PageLimit.of(99));
    }

    @Test
    @DisplayName("of(size, default) 使用调用方指定的默认值")
    void sizeUsesCustomDefault() {
        assertEquals(5, PageLimit.of(null, 5));
        assertEquals(5, PageLimit.of(0, 5));
        assertEquals(3, PageLimit.of(3, 5));
    }

    @Test
    @DisplayName("页码非法时回落到第 1 页")
    void pageFallsBackToOne() {
        assertEquals(1, PageLimit.page(null));
        assertEquals(1, PageLimit.page(0));
        assertEquals(1, PageLimit.page(-3));
        assertEquals(7, PageLimit.page(7));
    }
}
