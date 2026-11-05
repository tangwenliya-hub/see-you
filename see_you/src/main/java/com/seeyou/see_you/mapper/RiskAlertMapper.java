package com.seeyou.see_you.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.seeyou.see_you.entity.RiskAlert;
import org.apache.ibatis.annotations.Select;

import java.util.Map;

public interface RiskAlertMapper extends BaseMapper<RiskAlert> {

    /** 未读数量（首页小红点 / 提醒卡片用） */
    @Select("""
        SELECT COUNT(*) FROM psy_risk_alert
        WHERE user_id = #{userId} AND is_read = 0
        """)
    Long countUnread(Long userId);

    /** 概览：累计预警次数、最近一次预警等级 */
    @Select("""
        SELECT COUNT(*) AS total_cnt,
               MAX(alert_level) AS max_level,
               MAX(alert_date)  AS last_date
        FROM psy_risk_alert WHERE user_id = #{userId}
        """)
    Map<String, Object> overview(Long userId);
}
