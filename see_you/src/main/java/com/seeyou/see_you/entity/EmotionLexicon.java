package com.seeyou.see_you.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 无感记录 - 九类情绪中文词典。
 *
 * word_type：0=情感词 1=否定词 2=程度副词
 *  · 否定词：命中后对后续窗口内的情感词做符号翻转（"不开心"→负向）
 *  · 程度副词：命中后对紧随的情感词做倍数缩放（"非常累"×1.6）
 * 词典存在库里而非硬编码，是为了让词表可变可调（论文里可写"构建了面向九类情绪的中文情感词典"）。
 */
@Data
@TableName("psy_emotion_lexicon")
public class EmotionLexicon {
    @TableId(type = IdType.AUTO)
    private Long lexiconId;
    private String word;
    /** 映射到 9 类情绪：1开心2平静3疲惫4难过5生气6焦虑7兴奋8满足9其他 */
    private Integer emotion;
    private BigDecimal weight;
    /** 0=情感词 1=否定词 2=程度副词 */
    private Integer wordType;
    private String sourceTag;
    private LocalDateTime createTime;
}
