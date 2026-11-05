package com.seeyou.see_you.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import java.time.LocalDateTime;

/**
 * 系统运行参数。
 *
 * 存在的意义：毕设演示与真实运维都不应该靠改 yml + 重启来调阈值。
 * 这里把「预警窗口天数 / 连续低落天数 / 最低置信度」等高频调整项落到库里，
 * 后台改完立即生效，同时保留 yml 作为兜底默认值。
 */
@Data
@TableName("sys_config")
public class SysConfig {
    /**
     * 主键就是配置键（字符串）。
     * ⚠️ 必须显式标注 @TableId：MyBatis-Plus 的 selectById / updateById / deleteById
     *    依赖它识别主键，否则运行时抛
     *    BindingException: Invalid bound statement (not found): ...selectById
     */
    @TableId
    private String configKey;
    private String configValue;
    private String configGroup;
    private String configRemark;
    private LocalDateTime updateTime;
    private String updateBy;
}
