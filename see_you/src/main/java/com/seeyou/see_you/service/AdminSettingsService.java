package com.seeyou.see_you.service;

import com.seeyou.see_you.config.RiskProperties;
import com.seeyou.see_you.config.SenseProperties;
import com.seeyou.see_you.entity.SysConfig;
import com.seeyou.see_you.mapper.SysConfigMapper;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.*;

/**
 * 系统运行参数管理（后台「系统参数」页的支撑服务）。
 *
 * <h3>为什么要有这张表</h3>
 * 原来的阈值写在 application.yml 里，改一次要重启后端 —— 演示时非常尴尬。
 * 这里把高频调整项（预警窗口、连续天数、最低置信度等）落到 sys_config 表：
 * <ul>
 *   <li>启动时：DB 里有的键覆盖 yml 默认值；</li>
 *   <li>运行时：后台改完直接写库并同步到内存中的 {@link RiskProperties} / {@link SenseProperties}
 *       单例，下一次判定立即用新阈值，无需重启；</li>
 *   <li>恢复默认：删掉 DB 记录即回到 yml 的值。</li>
 * </ul>
 *
 * ⚠️ 非医疗边界：本页调的是「情绪状态提示」的统计阈值，
 *    无论怎么调，系统输出的都只是关心式提示，不构成任何医疗诊断。
 */
@Slf4j
@Service
public class AdminSettingsService {

    @Resource private SysConfigMapper configMapper;
    @Resource private RiskProperties riskProps;
    @Resource private SenseProperties senseProps;
    @Resource private com.seeyou.see_you.config.CommentProperties commentProps;

    /** 参数元数据类型 */
    public enum Type { BOOL, INT, DOUBLE, TEXT }

    /** 一个可调参数的元数据（key 与 yml 的 app.xxx 一一对应） */
    public static class Item {
        public final String key;
        public final String label;
        public final String group;
        public final Type type;
        public final String desc;
        public final String unit;

        Item(String key, String label, String group, Type type, String desc, String unit) {
            this.key = key; this.label = label; this.group = group;
            this.type = type; this.desc = desc; this.unit = unit;
        }
    }

    private static final List<Item> SCHEMA = new ArrayList<>();

    static {
        // ===== 情绪预警（非医疗提示）=====
        SCHEMA.add(new Item("alert.enabled", "预警总开关", "alert", Type.BOOL, "关闭后不再生成任何情绪风险提示", ""));
        SCHEMA.add(new Item("alert.windowDays", "判定窗口天数", "alert", Type.INT, "滑动窗口大小，统计最近 N 天的情绪", "天"));
        SCHEMA.add(new Item("alert.minRecordDays", "窗口内最少记录天数", "alert", Type.INT, "数据太少时不做判定，避免妄下结论", "天"));
        SCHEMA.add(new Item("alert.requireMultiDay", "要求低落天数≥2", "alert", Type.BOOL, "开启时一级预警要求窗口内低落天数 ≥ 2，避免单日坏心情打扰", ""));
        SCHEMA.add(new Item("alert.level2DowncastDays", "二级触发低落天数", "alert", Type.INT, "窗口内低落天数达到该值 → 中度提醒", "天"));
        SCHEMA.add(new Item("alert.level3DowncastDays", "三级触发低落天数", "alert", Type.INT, "窗口内低落天数达到该值 → 高度关注", "天"));
        SCHEMA.add(new Item("alert.level3Ratio", "三级负面占比阈值", "alert", Type.DOUBLE, "窗口内负面天数占比超过该值 → 高度关注", ""));
        SCHEMA.add(new Item("alert.level3AvgIndex", "三级平均指数上限", "alert", Type.DOUBLE, "窗口内平均情绪指数低于该值 → 高度关注", ""));
        SCHEMA.add(new Item("alert.level1Ratio", "一级负面占比阈值", "alert", Type.DOUBLE, "窗口内负面天数占比超过该值 → 轻度关注", ""));
        SCHEMA.add(new Item("alert.level1Slope", "一级指数斜率上限", "alert", Type.DOUBLE, "情绪指数趋势斜率低于该负值 → 轻度关注", ""));
        SCHEMA.add(new Item("alert.cooldownDays", "冷静期天数", "alert", Type.INT, "同等级提示发出后 N 天内不再重复打扰用户", "天"));
        SCHEMA.add(new Item("alert.crisisEnabled", "危机词即时提醒", "alert", Type.BOOL,
                "日记正文命中危机词库时立即提示，不等 7 天统计窗口（词库在「预警管理」页的词库管理区维护，改完立即生效）", ""));
        SCHEMA.add(new Item("alert.crisisLevel", "危机词提示等级", "alert", Type.INT,
                "危机词命中时给出的关怀等级（1~3，默认 3 = 高度关注）", "级"));
        // ===== 预警词库（TEXT 型，不在「系统参数」页渲染 —— 有专门的词库管理交互，见预警管理页）=====
        SCHEMA.add(new Item("alert.crisisWords", "危机词库", "alert", Type.TEXT,
                "命中任意一条即触发三级红色关怀（逗号分隔，改完立即生效，无需重启）", ""));
        SCHEMA.add(new Item("alert.warningWords", "预警词库", "alert", Type.TEXT,
                "命中触发二级中度提醒，语气更温和（逗号分隔，改完立即生效）", ""));
        SCHEMA.add(new Item("alert.crisisExcludes", "例外短语", "alert", Type.TEXT,
                "命中词邻域内出现这些表达判为亲昵/夸张用法，不触发，如「想死你了」（逗号分隔）", ""));

        // ===== 轻量无感记录 =====
        SCHEMA.add(new Item("sense.enabled", "无感记录总开关", "sense", Type.BOOL, "关闭后所有 /sense 接口返回「功能未开启」", ""));
        SCHEMA.add(new Item("sense.minConfidence", "最低置信度", "sense", Type.DOUBLE, "低于该值宁可不推测，也不误导用户", ""));
        SCHEMA.add(new Item("sense.knnK", "KNN 邻居数", "sense", Type.INT, "行为相似度比对时取的历史样本数", "个"));
        SCHEMA.add(new Item("sense.expireDays", "候选过期天数", "sense", Type.INT, "超时未确认的推测候选自动置为已过期", "天"));
        SCHEMA.add(new Item("sense.generateHour", "每日生成时刻", "sense", Type.INT, "定时任务的触发小时（cron 见 SenseScheduler，改此值需重启才换 cron）", "点"));

        // ===== 评论自动审核（TEXT 型词库不在「系统参数」页渲染 —— 在「评论管理」页维护）=====
        SCHEMA.add(new Item("comment.autoReview", "评论自动审核", "comment", Type.BOOL,
                "开启后系统先审：正常评论直接公开，仅命中可疑判据的转人工（词库在「评论管理」页维护）", ""));
        SCHEMA.add(new Item("comment.reviewWords", "评论可疑词库", "comment", Type.TEXT,
                "命中任意一条即转人工审核（逗号分隔，改完立即生效，无需重启）", ""));
    }

    /** yml 原始默认值快照（启动时抓取，用于「恢复默认」） */
    private final Map<String, String> ymlDefaults = new LinkedHashMap<>();

    /** 启动：先用 yml 值做快照，再用库里的值覆盖 */
    @PostConstruct
    public void init() {
        try {
            for (Item it : SCHEMA) ymlDefaults.put(it.key, read(it.key));
            for (SysConfig c : configMapper.selectList(null)) {
                if (c == null || c.getConfigKey() == null || c.getConfigValue() == null) continue;
                try {
                    write(c.getConfigKey(), c.getConfigValue());
                } catch (Exception ignored) { /* 脏值跳过，保留 yml 默认 */ }
            }
            log.info("[系统参数] 已加载 {} 项配置，其中库内覆盖 {} 项", SCHEMA.size(), configMapper.selectCount(null));
        } catch (Exception e) {
            log.warn("[系统参数] sys_config 加载失败（表不存在时不影响启动，将全部使用 yml 默认值）", e);
        }
    }

    /** 后台参数页：返回元数据 + 当前生效值 + 是否被库覆盖。
     *  TEXT 型（预警词库）不在本页渲染 —— 它有专门的词库管理交互（预警管理页），这里展示成只读数字输入框反而误导。 */
    public List<Map<String, Object>> list() {
        Map<String, String> db = new HashMap<>();
        try {
            configMapper.selectList(null).forEach(c -> {
                if (c != null && c.getConfigKey() != null) db.put(c.getConfigKey(), c.getConfigValue());
            });
        } catch (Exception ignored) { /* 表不存在时全部按默认值展示 */ }

        List<Map<String, Object>> out = new ArrayList<>();
        for (Item it : SCHEMA) {
            if (it.type == Type.TEXT) continue;
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("key", it.key);
            m.put("label", it.label);
            m.put("group", it.group);
            m.put("type", it.type.name().toLowerCase());
            m.put("desc", it.desc);
            m.put("unit", it.unit);
            m.put("value", read(it.key));
            m.put("defaultValue", ymlDefaults.get(it.key));
            m.put("overridden", db.containsKey(it.key));
            out.add(m);
        }
        return out;
    }

    /** 保存：写库 + 立即同步到内存单例 */
    public void save(Map<String, Object> body, String operator) {
        if (body == null || body.isEmpty()) return;
        for (Item it : SCHEMA) {
            if (!body.containsKey(it.key)) continue;
            Object raw = body.get(it.key);
            if (raw == null) continue;
            String v = String.valueOf(raw).trim();
            write(it.key, v);          // 先写内存，保证即使写库失败也只是丢失持久化，不会阻断演示
            upsert(it, v, operator);
        }
    }

    /** 恢复默认：删库记录 + 内存回写 yml 快照 */
    public void reset(String group, String operator) {
        for (Item it : SCHEMA) {
            if (group != null && !group.isBlank() && !group.equals(it.group)) continue;
            try {
                configMapper.deleteById(it.key);
            } catch (Exception ignored) { /* 表不存在时仅恢复内存值 */ }
            String def = ymlDefaults.get(it.key);
            if (def != null) write(it.key, def);
        }
        log.info("[系统参数] 恢复默认 group={} 操作人={}", group == null ? "ALL" : group, operator);
    }

    private void upsert(Item it, String value, String operator) {
        try {
            SysConfig c = configMapper.selectById(it.key);
            if (c == null) {
                c = new SysConfig();
                c.setConfigKey(it.key);
            }
            c.setConfigValue(value);
            c.setConfigGroup(it.group);
            c.setConfigRemark(it.label);
            c.setUpdateTime(LocalDateTime.now());
            c.setUpdateBy(operator);
            if (configMapper.selectById(it.key) == null) configMapper.insert(c);
            else configMapper.updateById(c);
        } catch (Exception e) {
            log.warn("[系统参数] 持久化失败 key={}（内存已生效）", it.key, e);
        }
    }

    /** 读：从当前生效的 Properties 单例取值 */
    private String read(String key) {
        switch (key) {
            case "alert.enabled" -> { return String.valueOf(riskProps.isEnabled()); }
            case "alert.windowDays" -> { return String.valueOf(riskProps.getWindowDays()); }
            case "alert.minRecordDays" -> { return String.valueOf(riskProps.getMinRecordDays()); }
            case "alert.requireMultiDay" -> { return String.valueOf(riskProps.isRequireMultiDay()); }
            case "alert.level2DowncastDays" -> { return String.valueOf(riskProps.getLevel2DowncastDays()); }
            case "alert.level3DowncastDays" -> { return String.valueOf(riskProps.getLevel3DowncastDays()); }
            case "alert.level3Ratio" -> { return String.valueOf(riskProps.getLevel3Ratio()); }
            case "alert.level3AvgIndex" -> { return String.valueOf(riskProps.getLevel3AvgIndex()); }
            case "alert.level1Ratio" -> { return String.valueOf(riskProps.getLevel1Ratio()); }
            case "alert.level1Slope" -> { return String.valueOf(riskProps.getLevel1Slope()); }
            case "alert.cooldownDays" -> { return String.valueOf(riskProps.getCooldownDays()); }
            case "alert.crisisEnabled" -> { return String.valueOf(riskProps.isCrisisEnabled()); }
            case "alert.crisisLevel" -> { return String.valueOf(riskProps.getCrisisLevel()); }
            case "alert.crisisWords" -> { return joinWords(riskProps.getCrisisWords()); }
            case "alert.warningWords" -> { return joinWords(riskProps.getWarningWords()); }
            case "alert.crisisExcludes" -> { return joinWords(riskProps.getCrisisExcludes()); }
            case "sense.enabled" -> { return String.valueOf(senseProps.isEnabled()); }
            case "sense.minConfidence" -> { return String.valueOf(senseProps.getMinConfidence()); }
            case "sense.knnK" -> { return String.valueOf(senseProps.getKnnK()); }
            case "sense.expireDays" -> { return String.valueOf(senseProps.getExpireDays()); }
            case "sense.generateHour" -> { return String.valueOf(senseProps.getGenerateHour()); }
            case "comment.autoReview" -> { return String.valueOf(commentProps.isAutoReview()); }
            case "comment.reviewWords" -> { return joinWords(commentProps.getReviewWords()); }
            default -> { return ""; }
        }
    }

    /** 写：把值落到 Properties 单例（下一次判定立即生效） */
    private void write(String key, String v) {
        switch (key) {
            case "alert.enabled" -> riskProps.setEnabled(Boolean.parseBoolean(v));
            case "alert.windowDays" -> riskProps.setWindowDays(toInt(v, riskProps.getWindowDays()));
            case "alert.minRecordDays" -> riskProps.setMinRecordDays(toInt(v, riskProps.getMinRecordDays()));
            case "alert.requireMultiDay" -> riskProps.setRequireMultiDay(Boolean.parseBoolean(v));
            case "alert.level2DowncastDays" -> riskProps.setLevel2DowncastDays(toInt(v, riskProps.getLevel2DowncastDays()));
            case "alert.level3DowncastDays" -> riskProps.setLevel3DowncastDays(toInt(v, riskProps.getLevel3DowncastDays()));
            case "alert.level3Ratio" -> riskProps.setLevel3Ratio(toDouble(v, riskProps.getLevel3Ratio()));
            case "alert.level3AvgIndex" -> riskProps.setLevel3AvgIndex(toDouble(v, riskProps.getLevel3AvgIndex()));
            case "alert.level1Ratio" -> riskProps.setLevel1Ratio(toDouble(v, riskProps.getLevel1Ratio()));
            case "alert.level1Slope" -> riskProps.setLevel1Slope(toDouble(v, riskProps.getLevel1Slope()));
            case "alert.cooldownDays" -> riskProps.setCooldownDays(toInt(v, riskProps.getCooldownDays()));
            case "alert.crisisEnabled" -> riskProps.setCrisisEnabled(Boolean.parseBoolean(v));
            case "alert.crisisLevel" -> riskProps.setCrisisLevel(toInt(v, riskProps.getCrisisLevel()));
            case "alert.crisisWords" -> riskProps.setCrisisWords(splitWords(v));
            case "alert.warningWords" -> riskProps.setWarningWords(splitWords(v));
            case "alert.crisisExcludes" -> riskProps.setCrisisExcludes(splitWords(v));
            case "sense.enabled" -> senseProps.setEnabled(Boolean.parseBoolean(v));
            case "sense.minConfidence" -> senseProps.setMinConfidence(toDouble(v, senseProps.getMinConfidence()));
            case "sense.knnK" -> senseProps.setKnnK(toInt(v, senseProps.getKnnK()));
            case "sense.expireDays" -> senseProps.setExpireDays(toInt(v, senseProps.getExpireDays()));
            case "sense.generateHour" -> senseProps.setGenerateHour(toInt(v, senseProps.getGenerateHour()));
            case "comment.autoReview" -> commentProps.setAutoReview(Boolean.parseBoolean(v));
            case "comment.reviewWords" -> commentProps.setReviewWords(splitWords(v));
            default -> { /* 未知键忽略 */ }
        }
    }

    private static int toInt(String v, int fallback) {
        try { return Integer.parseInt(v.trim()); } catch (Exception e) { return fallback; }
    }

    /** 词库序列化：词与词之间用逗号连接（词条本身不允许含逗号，见管理接口的校验） */
    private static String joinWords(List<String> l) {
        return (l == null || l.isEmpty()) ? "" : String.join(",", l);
    }

    /** 词库反序列化：中英文逗号都支持，去空白、去空项；保证返回可变的 ArrayList（管理接口要往里加词） */
    private static List<String> splitWords(String v) {
        if (v == null || v.isBlank()) return new ArrayList<>();
        return new ArrayList<>(java.util.Arrays.stream(v.split("[,，]"))
                .map(String::trim).filter(s -> !s.isEmpty()).toList());
    }

    private static double toDouble(String v, double fallback) {
        try { return Double.parseDouble(v.trim()); } catch (Exception e) { return fallback; }
    }

    /** 给前端的免责声明（预警参数页必须展示） */
    public String disclaimer() {
        return riskProps.getDisclaimer();
    }
}
