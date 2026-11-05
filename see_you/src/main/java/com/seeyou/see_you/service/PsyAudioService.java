package com.seeyou.see_you.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.seeyou.see_you.entity.PsyAudio;

public interface PsyAudioService extends IService<PsyAudio> {
    /**
     * 原子增加播放量
     */
    boolean incrementPlayCount(Long id);
}