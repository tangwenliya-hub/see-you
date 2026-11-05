package com.seeyou.see_you.dto;

import com.alibaba.excel.annotation.ExcelProperty;
import com.alibaba.excel.annotation.write.style.ColumnWidth;
import lombok.Data;

/**
 * 情绪预警报表导出行（EasyExcel）。
 *
 * <h3>隐私红线</h3>
 * 导出内容与后台页面一致：只有统计指标与处置状态，<b>不含日记正文、不含行为明细</b>；
 * 「判定依据」列只带命中的词条 / 统计口径描述（这些本来就展示在后台详情里）。
 */
@Data
public class AlertExportDTO {

    @ExcelProperty("预警日期")
    @ColumnWidth(14)
    private String alertDate;

    @ExcelProperty("用户")
    @ColumnWidth(14)
    private String userName;

    @ExcelProperty("等级")
    @ColumnWidth(12)
    private String levelName;

    @ExcelProperty("触发规则")
    @ColumnWidth(24)
    private String triggerRuleName;

    @ExcelProperty("判定依据")
    @ColumnWidth(46)
    private String triggerDetail;

    @ExcelProperty("低落天数")
    @ColumnWidth(10)
    private String maxDowncastDays;

    @ExcelProperty("负面占比")
    @ColumnWidth(10)
    private String negativeRatio;

    @ExcelProperty("平均指数")
    @ColumnWidth(10)
    private String avgIndex;

    @ExcelProperty("已读")
    @ColumnWidth(8)
    private String isRead;

    @ExcelProperty("已知晓")
    @ColumnWidth(8)
    private String ackStatus;

    @ExcelProperty("误报标记")
    @ColumnWidth(10)
    private String misreport;

    @ExcelProperty("处置方式")
    @ColumnWidth(14)
    private String disposition;

    @ExcelProperty("处置备注")
    @ColumnWidth(30)
    private String dispositionNote;

    @ExcelProperty("处置时间")
    @ColumnWidth(18)
    private String dispositionTime;

    @ExcelProperty("创建时间")
    @ColumnWidth(18)
    private String createTime;
}
