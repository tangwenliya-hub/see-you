package com.seeyou.see_you.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.seeyou.see_you.algorithm.EmotionConst;
import com.seeyou.see_you.entity.PsyDiary;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;
import java.util.Map;

/**
 * 统计专用 Mapper：把原先「全表拉到 Java 内存里用循环聚合」的统计逻辑下沉到 MySQL 侧。
 *
 * <p><b>为什么要单独抽这一层？</b>
 * 旧写法是 {@code diaryService.list()} 把整张日记表（含 diary_content 大文本、图片地址）
 * 全部查出来，再在内存里 for 循环计数/求平均。数据量一大就会：
 *   1. 单次请求传输几十 MB 数据；
 *   2. 每条统计都要全表扫描 + O(n) 内存聚合，接口响应时间随数据量线性增长；
 *   3. 这几个接口游客可访问，等于把「最慢的接口」对外开放，构成 DoS 放大面。
 *
 * <p>改造后：聚合全部交给 MySQL 的 {@code GROUP BY / COUNT / AVG(CASE WHEN)}，
 * 一次请求只返回个位数汇总行，响应时间与数据量基本解耦。
 *
 * <p>情绪指数表达式与积极/负面集合不再写死在本文件里，而是引用
 * {@link EmotionConst#SQL_INDEX} / {@link EmotionConst#SQL_POSITIVE} /
 * {@link EmotionConst#SQL_NEGATIVE}——字典改动只需动 EmotionConst 一处，
 * 并用 EmotionDictConsistencyTest 守住「字面量与标准答案一致」。
 *
 * <p><b>为什么情绪类统计一律带 {@code source = 1}：</b>
 * {@code psy_diary.source} 区分「1=用户手动记录 / 2=无感推测经用户确认」。
 * KNN 训练侧刻意只用 source=1（防止模型学习自己的输出），如果统计侧不跟着排除，
 * 用户越用「无感推荐」，看板上的情绪分布就越像系统自己的猜测——统计反而成了
 * 自证循环的展示面，与设计意图直接矛盾。因此：<b>个人维度的全部统计（分布/趋势/
 * 均值/占比/散点/词云/打卡天数）一律只取 source=1</b>，保证页面上的每个数字口径一致；
 * 只有全站的"写日记最多的人"排行（{@link #topDiaryUsers}）保留全量，
 * 因为它衡量的是活跃度而非情绪画像。
 */
@Mapper
public interface StatisticsMapper extends BaseMapper<PsyDiary> {

    // =========================================================
    // A. 全站统计
    // =========================================================

    /** 全站情绪分布：每种情绪各有几条日记（只统计手动记录，排除系统推测） */
    @Select("SELECT emotion AS e, COUNT(*) AS cnt " +
            "FROM psy_diary WHERE emotion BETWEEN 1 AND 9 AND source = 1 " +
            "GROUP BY emotion")
    List<Map<String, Object>> emotionCount();

    /** 全站月度情绪趋势：每月平均情绪指数 + 每月记录数（按月份升序，只统计手动记录） */
    @Select("SELECT DATE_FORMAT(diary_date, '%Y-%m') AS ym, " +
            "       ROUND(AVG(" + EmotionConst.SQL_INDEX + ")) AS avg_index, " +
            "       COUNT(*) AS cnt " +
            "FROM psy_diary " +
            "WHERE source = 1 " +
            "GROUP BY ym ORDER BY ym")
    List<Map<String, Object>> monthlyEmotionTrend();

    /** 音频分类统计（空分类归到「未分类」） */
    @Select("SELECT COALESCE(NULLIF(TRIM(category), ''), '未分类') AS name, COUNT(*) AS cnt " +
            "FROM psy_audio GROUP BY name ORDER BY cnt DESC")
    List<Map<String, Object>> audioCategoryCount();

    /** 视频分类统计（空分类归到「未分类」） */
    @Select("SELECT COALESCE(NULLIF(TRIM(category), ''), '未分类') AS name, COUNT(*) AS cnt " +
            "FROM psy_video GROUP BY name ORDER BY cnt DESC")
    List<Map<String, Object>> videoCategoryCount();

    /** 测评结果情绪分布 */
    @Select("SELECT result_emotion AS e, COUNT(*) AS cnt " +
            "FROM psy_test_record WHERE result_emotion BETWEEN 1 AND 9 " +
            "GROUP BY result_emotion")
    List<Map<String, Object>> testResultEmotionCount();

    /** 视频播放排行 TopN（播放量降序） */
    @Select("SELECT video_name AS name, category, COALESCE(play_count, 0) AS value " +
            "FROM psy_video ORDER BY play_count DESC LIMIT #{n}")
    List<Map<String, Object>> videoPlayRank(@Param("n") int n);

    /** 写日记最多的前 N 个用户（只返回 userId + 篇数，用户信息用第二次小查询补齐，避免大表 JOIN） */
    @Select("SELECT user_id AS uid, COUNT(*) AS cnt " +
            "FROM psy_diary GROUP BY user_id ORDER BY cnt DESC LIMIT #{n}")
    List<Map<String, Object>> topDiaryUsers(@Param("n") int n);

    /** 按 userId 批量取用户展示信息（昵称优先，没有则用账号名） */
    @Select({"<script>",
            "SELECT user_id AS uid, username, nickname, create_time ",
            "FROM sys_user WHERE user_id IN ",
            "<foreach collection='ids' item='id' open='(' separator=',' close=')'>#{id}</foreach>",
            "</script>"})
    List<Map<String, Object>> usersByIds(@Param("ids") List<Long> ids);

    // =========================================================
    // B. 个人统计（userId 只从 token 取，SQL 里一律带 user_id 过滤）
    // =========================================================

    /** 我的所有打卡日期（去重、升序）—— 打卡口径：手动/确认/系统代记都算（否则「不打开就没打卡」） */
    @Select("SELECT DISTINCT diary_date AS d FROM psy_diary WHERE user_id = #{uid} ORDER BY d")
    List<Map<String, Object>> myDiaryDates(@Param("uid") Long uid);

    /**
     * 我的日记聚合：打卡天数 / 本月天数 / 平均情绪指数 / 积极天数 / 消极天数。
     *
     * <p><b>口径说明（三处统一，不要再改回去）：</b>
     * <ol>
     *   <li><b>按天去重</b>：先取每天 diary_id 最大的一条（当天最后写的那篇），
     *       与 {@link #myDiaryBetween} 完全一致。旧实现用 {@code SUM(CASE...)} 数的是
     *       「条数」，字段却叫 xxxDays——同一天写两篇就会被算成两天，名不副实。</li>
     *   <li><b>打卡天数算全部、情绪指标只算 source=1</b>：这两个口径必须分开。
     *       打卡天数（total_days / month_days）如果只算手写，用户开了无感记录、
     *       当天没打开 App 就永远打不上卡，与「免打卡」的设计初衷冲突；
     *       而平均指数 / 积极 / 消极若把推测也算进去，模型就会用自己的输出喂自己，
     *       越猜越偏。故：manual_days 以下的情绪字段统一只取 source=1。</li>
     *   <li><b>消极 = {3,4,5,6}</b>：与 {@code EmotionConst.NEGATIVE}、预警模块的负面占比同集合。
     *       旧实现这里写的是 {4,5}，导致看板「消极 2 天」与预警「负面 5 天」自相矛盾。
     *       统一后 积极(4 类) + 消极(4 类) + 其他(9) 覆盖全部九类，
     *       「积极占比 + 消极占比」合计接近 100%，不会出现三类情绪无处归属。</li>
     * </ol>
     */
    @Select("SELECT COUNT(*) AS total_rows, " +
            "       COUNT(*) AS total_days, " +
            "       SUM(CASE WHEN YEAR(d) = #{y} AND MONTH(d) = #{m} THEN 1 ELSE 0 END) AS month_days, " +
            "       SUM(CASE WHEN is_manual = 1 THEN 1 ELSE 0 END) AS manual_days, " +
            "       ROUND(AVG(CASE WHEN is_manual = 1 THEN " + EmotionConst.SQL_INDEX + " END)) AS avg_index, " +
            "       SUM(CASE WHEN is_manual = 1 AND " + EmotionConst.SQL_POSITIVE + " THEN 1 ELSE 0 END) AS pos, " +
            "       SUM(CASE WHEN is_manual = 1 AND " + EmotionConst.SQL_NEGATIVE + " THEN 1 ELSE 0 END) AS neg " +
            "FROM (SELECT d.diary_date AS d, d.emotion AS emotion, (d.source = 1) AS is_manual " +
            "      FROM psy_diary d " +
            "      INNER JOIN (SELECT MAX(diary_id) AS mid FROM psy_diary " +
            "                  WHERE user_id = #{uid} GROUP BY diary_date) t " +
            "              ON d.diary_id = t.mid) x")
    Map<String, Object> myDiaryAgg(@Param("uid") Long uid, @Param("y") int y, @Param("m") int m);

    /** 我的日记按来源计数：手动(=经验值口径) / 确认推测 / 系统代记（别名避开 MySQL 保留字 manual） */
    @Select("SELECT COALESCE(SUM(source = 1), 0) AS manual_cnt, " +
            "       COALESCE(SUM(source = 2), 0) AS confirmed_cnt, " +
            "       COALESCE(SUM(source = 3), 0) AS auto_cnt " +
            "FROM psy_diary WHERE user_id = #{uid}")
    Map<String, Object> myDiarySourceCount(@Param("uid") Long uid);

    /** 我最新的一条日记（「最近一次情绪」——含系统代记，否则靠代记的用户会看到「暂无」） */
    @Select("SELECT emotion AS e, diary_date AS d FROM psy_diary " +
            "WHERE user_id = #{uid} ORDER BY diary_date DESC, diary_id DESC LIMIT 1")
    Map<String, Object> myLatestDiary(@Param("uid") Long uid);

    /** 我的情绪分布（只算手动记录） */
    @Select("SELECT emotion AS e, COUNT(*) AS cnt FROM psy_diary " +
            "WHERE user_id = #{uid} AND emotion BETWEEN 1 AND 9 AND source = 1 GROUP BY emotion")
    List<Map<String, Object>> myEmotionCount(@Param("uid") Long uid);

    /**
     * 我在某段日期内的日记：每天只取「当天最新一条」（日期 + 情绪），用于趋势图 / 热力图按天补全。
     *
     * <p>打卡口径：不再限定 source=1。热力图本质是「打卡日历」，
     * 用户靠无感代记保住连续记录时，格子里必须亮起来，否则页面会自相矛盾
     * （首页说连续 7 天，看板一片空白）。情绪画像的 source=1 限制在 Agg / 分布 / 词云里单独控制。
     *
     * <p>为什么用 MAX(diary_id) 子查询而不是直接 SELECT 明细：同一天可能有好几篇日记，
     * 直接查明细会一次捞回全部行（一天写 3 篇、一年就是上千行），而图表按天只要一个值。
     * 这里取当天 diary_id 最大的一条 = 当天最后写的那篇，结果确定且行数等于「有记录的天数」。
     */
    @Select("SELECT d.diary_date AS d, d.emotion AS e " +
            "FROM psy_diary d " +
            "INNER JOIN (SELECT MAX(diary_id) AS mid FROM psy_diary " +
            "            WHERE user_id = #{uid} AND diary_date BETWEEN #{from} AND #{to} " +
            "            GROUP BY diary_date) t ON d.diary_id = t.mid")
    List<Map<String, Object>> myDiaryBetween(@Param("uid") Long uid,
                                             @Param("from") String from,
                                             @Param("to") String to);

    /** 我的月度情绪趋势（按月份升序，只算手动记录） */
    @Select("SELECT DATE_FORMAT(diary_date, '%Y-%m') AS ym, " +
            "       ROUND(AVG(" + EmotionConst.SQL_INDEX + ")) AS avg_index, " +
            "       COUNT(*) AS cnt " +
            "FROM psy_diary WHERE user_id = #{uid} AND source = 1 " +
            "GROUP BY ym ORDER BY ym")
    List<Map<String, Object>> myMonthlyEmotionTrend(@Param("uid") Long uid);

    // =========================================================
    // C. 进阶图表（Step 3：散点回归 / 桑基流向 / 词云关键词）
    // =========================================================

    /** 我的「睡眠时长 x 情绪指数」散点样本（近 N 天，最多 500 条，供回归分析用） */
    @Select("SELECT sleep_hours AS s, emotion AS e FROM psy_diary " +
            "WHERE user_id = #{uid} AND sleep_hours IS NOT NULL AND emotion BETWEEN 1 AND 9 " +
            "  AND source = 1 AND diary_date >= #{from} " +
            "ORDER BY diary_date DESC LIMIT 500")
    List<Map<String, Object>> myScatter(@Param("uid") Long uid, @Param("from") String from);

    /** 我的「星期 x 情绪」流向矩阵：DAYOFWEEK 1=周日 ... 7=周六 */
    @Select("SELECT DAYOFWEEK(diary_date) AS wd, emotion AS e, COUNT(*) AS cnt " +
            "FROM psy_diary WHERE user_id = #{uid} AND emotion BETWEEN 1 AND 9 " +
            "  AND source = 1 AND diary_date >= #{from} " +
            "GROUP BY wd, e")
    List<Map<String, Object>> myWeekdayEmotion(@Param("uid") Long uid, @Param("from") String from);

    /** 我的日记正文（近 N 天、最多 limit 条，只取正文列，供词云做本地词频统计） */
    @Select("SELECT diary_content FROM psy_diary " +
            "WHERE user_id = #{uid} AND diary_content IS NOT NULL AND diary_content != '' " +
            "  AND source = 1 AND diary_date >= #{from} " +
            "ORDER BY diary_date DESC LIMIT #{limit}")
    List<String> myDiaryContents(@Param("uid") Long uid, @Param("from") String from, @Param("limit") int limit);
}
