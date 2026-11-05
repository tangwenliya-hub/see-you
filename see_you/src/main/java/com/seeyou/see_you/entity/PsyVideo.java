package com.seeyou.see_you.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("psy_video")
public class PsyVideo {
    @TableId(type = IdType.AUTO)
    private Long videoId;
    private String videoName;
    private String videoUrl;
    private String category;
    private String coverImg;
    private Integer playCount;
    private String description;
    /**
     * 时长（秒）。数据库 duration 是 int 列；
     * 历史版本实体写成 String，前端传 "01:39" 会触发 MySQL 的 Data truncated for column 'duration'，
     * 所以这里改成 Integer，并提供兼容解析：无论前端传 "01:39"、"1:02:03" 还是 99（秒），都能正确落库。
     */
    @TableField("duration")
    @JsonIgnore
    private Integer durationSec;
    private LocalDateTime createTime;

    /** 对外字段名仍是 duration，读取时返回秒数 */
    public Integer getDuration() {
        return durationSec;
    }

    /** 兼容 String / Number 两种入参 */
    public void setDuration(Object duration) {
        this.durationSec = parseDuration(duration);
    }

    public static Integer parseDuration(Object val) {
        if (val == null) return 0;
        if (val instanceof Number) return Math.max(0, ((Number) val).intValue());
        String s = String.valueOf(val).trim();
        if (s.isEmpty()) return 0;
        if (s.matches("\\d+")) return Math.max(0, Integer.parseInt(s));
        String[] parts = s.split(":");
        try {
            int[] n = new int[parts.length];
            for (int i = 0; i < parts.length; i++) n[i] = Integer.parseInt(parts[i].trim());
            if (n.length == 3) return n[0] * 3600 + n[1] * 60 + n[2];
            if (n.length == 2) return n[0] * 60 + n[1];
            if (n.length == 1) return n[0];
        } catch (NumberFormatException ignore) { }
        return 0;
    }
}
