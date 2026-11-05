package com.seeyou.see_you.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import java.time.LocalDateTime;

/**
 * 无感记录 - 隐私操作审计日志。
 *
 * 记录用户对本人无感数据的每一次授权、撤回、导出与删除动作。
 * 这张表的存在意义是「可证明」——可直接展示用户对本人数据全程可控。
 */
@Data
@TableName("psy_sense_privacy_log")
public class SensePrivacyLog {
    @TableId(type = IdType.AUTO)
    private Long logId;
    private Long userId;
    private String action;
    private String detail;
    /** 脱敏 IP：IPv4 末段置 0 */
    private String ipMasked;
    private LocalDateTime createTime;
}
