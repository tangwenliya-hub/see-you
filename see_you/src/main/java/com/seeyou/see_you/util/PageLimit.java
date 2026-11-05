package com.seeyou.see_you.util;

/**
 * 分页参数安全化工具。
 *
 * <p>分页 size 直接取自请求参数时，攻击者可以用 {@code ?size=999999} 一次性拉全表，
 * 造成数据库与内存压力（本项目列表接口多为登录后可访问，风险更实际）。
 * 这里统一做上下界收敛：小于 1 取默认值，超过上限截断。
 */
public final class PageLimit {

    /** 单页默认条数 */
    public static final int DEFAULT_SIZE = 10;

    /** 单页最大条数：正常业务列表不会超过这个值 */
    public static final int MAX_SIZE = 100;

    private PageLimit() {
    }

    /**
     * 收敛分页大小。
     *
     * @param size 请求传入的 size，允许为 null
     * @return 落在 [1, MAX_SIZE] 区间内的值
     */
    public static int of(Integer size) {
        return of(size, DEFAULT_SIZE);
    }

    /**
     * @param size         请求传入的 size
     * @param defaultSize  为空或非法时的默认值
     * @return 落在 [1, MAX_SIZE] 区间内的值
     */
    public static int of(Integer size, int defaultSize) {
        if (size == null || size < 1) return defaultSize;
        return Math.min(size, MAX_SIZE);
    }

    /** 收敛页码，避免负数页码 */
    public static int page(Integer page) {
        if (page == null || page < 1) return 1;
        return page;
    }

    /**
     * long 版本重载：部分管理端接口的分页参数声明为 long。
     * 语义必须与 {@link #of(Integer)} 完全一致：size&lt;1 回落默认值（不是 clamp 到 1）。
     * 注意 int 字面量调用会优先匹配本重载（拓宽优先于装箱），两版语义不一致会静默改变行为。
     */
    public static int of(long size) {
        return of(size, DEFAULT_SIZE);
    }

    /** long 版本重载，非法值回落 defaultSize */
    public static int of(long size, int defaultSize) {
        if (size < 1) return defaultSize;
        return (int) Math.min(size, MAX_SIZE);
    }

    /** long 版本重载：避免负数页码 */
    public static int page(long page) {
        if (page < 1) return 1;
        return (int) Math.min(page, Integer.MAX_VALUE);
    }
}
