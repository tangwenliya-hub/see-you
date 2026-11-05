package com.seeyou.see_you.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import java.time.LocalDateTime;

/**
 * 系统操作日志：记录管理员在后台的每一次写操作与登录行为。
 * 表结构见 sql/migration/sys_log_table.sql，此前一直只有表没有实体。
 */
@Data
@TableName("sys_log")
public class SysLog {
    @TableId(type = IdType.AUTO)
    private Long logId;
    /** 操作人 ID */
    private Long userId;
    /** 操作人账号（冗余存储，用户被删后仍可追溯） */
    private String username;
    private String nickname;
    /** 操作模块：用户管理 / 情绪预警 / 系统参数 ... */
    private String module;
    /** 操作类型：新增 / 修改 / 删除 / 登录 / 导出 ... */
    private String operation;
    private String description;
    private String ip;
    private String method;
    private String params;
    /** 1=成功 0=失败 */
    private Integer status;
    private String errorMsg;
    /** 耗时（毫秒） */
    private Long costTime;
    private LocalDateTime createTime;
}
