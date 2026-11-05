package com.seeyou.see_you.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.seeyou.see_you.entity.BehaviorFeature;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 脱敏行为特征 Mapper。
 *
 * 核心是 upsert：同一用户同一天同一场景只保留一行，重复上报做「累加」而非新增，
 * 既避免行爆炸（一年每用户最多 365 × 8 场景行），也天然满足「多次刷新页面」的幂等。
 */
public interface BehaviorFeatureMapper extends BaseMapper<BehaviorFeature> {

    /**
     * 幂等累加上报。
     * hour_mask 用按位或合并（活跃的小时一旦为真就保持），其余数值字段累加。
     * 注意：MySQL 8.0.19+ 推荐用 AS new 的写法，VALUES() 函数在 8.4 已标记废弃。
     */
    @Insert("""
        INSERT INTO psy_behavior_feature
            (user_id, stat_date, scene_code, stay_ms, visit_cnt, click_cnt, hour_mask)
        VALUES
            (#{userId}, #{statDate}, #{sceneCode}, #{stayMs}, #{visitCnt}, #{clickCnt}, #{hourMask})
        AS new
        ON DUPLICATE KEY UPDATE
            stay_ms    = psy_behavior_feature.stay_ms   + new.stay_ms,
            visit_cnt  = psy_behavior_feature.visit_cnt + new.visit_cnt,
            click_cnt  = psy_behavior_feature.click_cnt + new.click_cnt,
            hour_mask  = psy_behavior_feature.hour_mask | new.hour_mask,
            update_time = NOW()
        """)
    int upsert(BehaviorFeature f);

    /** 查询某用户一段时间内的全部行为特征（预测算法构造特征向量用） */
    @Select("""
        SELECT feature_id, user_id, stat_date, scene_code, stay_ms, visit_cnt, click_cnt, hour_mask
        FROM psy_behavior_feature
        WHERE user_id = #{userId} AND stat_date BETWEEN #{from} AND #{to}
        ORDER BY stat_date ASC
        """)
    List<BehaviorFeature> selectRange(@Param("userId") Long userId,
                                      @Param("from") String from,
                                      @Param("to") String to);

    /** 数据概览：该用户行为特征的日期跨度与总行数（"我的数据"页面用） */
    @Select("""
        SELECT COUNT(*) AS rows_cnt, MIN(stat_date) AS first_date, MAX(stat_date) AS last_date
        FROM psy_behavior_feature WHERE user_id = #{userId}
        """)
    java.util.Map<String, Object> overview(@Param("userId") Long userId);

    /** 保留期清理：删除某日期之前的全部行为数据（RETENTION_CLEAN 定时任务调用） */
    @Select("""
        SELECT COUNT(*) FROM psy_behavior_feature WHERE user_id = #{userId}
        """)
    Long countByUser(@Param("userId") Long userId);
}
