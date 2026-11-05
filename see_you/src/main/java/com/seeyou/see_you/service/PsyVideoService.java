package com.seeyou.see_you.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.seeyou.see_you.entity.PsyVideo;

public interface PsyVideoService extends IService<PsyVideo> {
    /**
     * 原子增加播放量
     */
    boolean incrementPlayCount(Long id);
}