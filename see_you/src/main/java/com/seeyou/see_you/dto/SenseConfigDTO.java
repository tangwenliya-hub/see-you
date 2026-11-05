package com.seeyou.see_you.dto;

import lombok.Data;

/**
 * 无感记录开关 dto。
 *
 * 三个开关是**分级**的：总开关关 → 子开关全失效；总开关开 → 还能单独关掉某一路。
 * 这样用户可以只开「文本分析」而不开「行为采集」，把选择权真正交给用户。
 */
@Data
public class SenseConfigDTO {
    /** 总开关 0=关 1=开；null 表示不修改 */
    private Integer enabled;
    /** 行为采集子开关 0=关 1=开；null 表示不修改 */
    private Integer behavior;
    /** 文本分析子开关 0=关 1=开；null 表示不修改 */
    private Integer text;
}
