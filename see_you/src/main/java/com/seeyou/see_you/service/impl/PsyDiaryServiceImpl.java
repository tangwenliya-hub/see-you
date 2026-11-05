package com.seeyou.see_you.service.impl;

import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.seeyou.see_you.entity.EmotionPrediction;
import com.seeyou.see_you.entity.PsyDiary;
import com.seeyou.see_you.mapper.EmotionPredictionMapper;
import com.seeyou.see_you.mapper.PsyDiaryMapper;
import com.seeyou.see_you.service.PsyDiaryService;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PsyDiaryServiceImpl extends ServiceImpl<PsyDiaryMapper, PsyDiary> implements PsyDiaryService {

    @Resource
    private EmotionPredictionMapper predictionMapper;

    /**
     * 删除日记 + 回退推测状态，两步必须同事务。
     *
     * 说明：prediction 侧的三个字段需要显式 set(null)，因为 updateById 默认忽略 null 字段，
     * 用它无法把 diary_id / confirm_emotion 清空，只能走 LambdaUpdateWrapper。
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean removeWithPrediction(Long diaryId) {
        PsyDiary old = getById(diaryId);
        if (old == null) return false;
        boolean removed = removeById(diaryId);
        if (!removed || old.getPredictionId() == null) return removed;

        predictionMapper.update(null, new LambdaUpdateWrapper<EmotionPrediction>()
                .eq(EmotionPrediction::getPredictionId, old.getPredictionId())
                .eq(EmotionPrediction::getDiaryId, diaryId)
                .set(EmotionPrediction::getConfirmStatus, 0)
                .set(EmotionPrediction::getDiaryId, null)
                .set(EmotionPrediction::getConfirmEmotion, null));
        return true;
    }
}
