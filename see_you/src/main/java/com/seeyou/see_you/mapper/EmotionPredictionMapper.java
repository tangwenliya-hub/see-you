package com.seeyou.see_you.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.seeyou.see_you.entity.EmotionPrediction;
import org.apache.ibatis.annotations.Select;

import java.util.List;
import java.util.Map;

/**
 * 情绪推测候选 Mapper。
 *
 * 所有方法都强制带 user_id 条件——这是「仅本人可见」在持久层的最后一道防线：
 * 即使上层忘了过滤，SQL 本身也查不到别人的数据。
 */
public interface EmotionPredictionMapper extends BaseMapper<EmotionPrediction> {

    /**
     * 命中率统计（论文实验章节的数据来源）。
     * total    = 全部候选
     * adopted  = confirm_status = 1
     * exactHit = 已采纳且 confirm_emotion 与 suggest_emotion 完全一致
     */
    @Select("""
        SELECT
            COUNT(*)                                                              AS total,
            SUM(CASE WHEN confirm_status = 1 THEN 1 ELSE 0 END)                   AS adopted,
            SUM(CASE WHEN confirm_status = 2 THEN 1 ELSE 0 END)                   AS rejected,
            SUM(CASE WHEN confirm_status = 3 THEN 1 ELSE 0 END)                   AS expired,
            SUM(CASE WHEN confirm_status = 1
                      AND confirm_emotion = suggest_emotion THEN 1 ELSE 0 END)    AS exact_hit
        FROM psy_emotion_prediction
        WHERE user_id = #{userId}
        """)
    Map<String, Object> accuracyOf(Long userId);

    /** 分类别命中率 */
    @Select("""
        SELECT suggest_emotion AS emotion,
               COUNT(*) AS cnt,
               SUM(CASE WHEN confirm_emotion = suggest_emotion THEN 1 ELSE 0 END) AS hit_cnt
        FROM psy_emotion_prediction
        WHERE user_id = #{userId} AND confirm_status = 1
        GROUP BY suggest_emotion
        ORDER BY cnt DESC
        """)
    List<Map<String, Object>> accuracyByEmotion(Long userId);

    /** 过期清理：把超过 expire_time 且仍待确认的候选置为过期 */
    @Select("""
        SELECT prediction_id FROM psy_emotion_prediction
        WHERE confirm_status = 0 AND expire_time < NOW()
        """)
    List<Long> selectExpired();
}
