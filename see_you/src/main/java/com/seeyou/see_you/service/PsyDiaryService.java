package com.seeyou.see_you.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.seeyou.see_you.entity.PsyDiary;

public interface PsyDiaryService extends IService<PsyDiary> {

    /**
     * 删除日记，并同步回退由它确认而来的推测记录。
     *
     * <p>推测确认后会写一条 source=2 的日记，并把 prediction 置为「已采用」并记录 diaryId。
     * 如果只删日记不处理 prediction，会留下「已采用但日记不存在」的悬空记录：
     * 用户重新生成时被判重跳过，历史里又显示已采用。
     * 因此删除动作必须在同一事务内把该推测回退为待处理状态。
     */
    boolean removeWithPrediction(Long diaryId);
}
