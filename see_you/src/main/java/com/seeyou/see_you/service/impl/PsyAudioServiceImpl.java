package com.seeyou.see_you.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.seeyou.see_you.entity.PsyAudio;
import com.seeyou.see_you.mapper.PsyAudioMapper;
import com.seeyou.see_you.service.PsyAudioService;
import org.springframework.stereotype.Service;

@Service
public class PsyAudioServiceImpl extends ServiceImpl<PsyAudioMapper, PsyAudio> implements PsyAudioService {

    @Override
    public boolean incrementPlayCount(Long id) {
        return baseMapper.incrementPlayCount(id) > 0;
    }
}