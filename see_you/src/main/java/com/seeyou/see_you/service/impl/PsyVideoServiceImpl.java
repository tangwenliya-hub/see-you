package com.seeyou.see_you.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.seeyou.see_you.entity.PsyVideo;
import com.seeyou.see_you.mapper.PsyVideoMapper;
import com.seeyou.see_you.service.PsyVideoService;
import org.springframework.stereotype.Service;

@Service
public class PsyVideoServiceImpl extends ServiceImpl<PsyVideoMapper, PsyVideo> implements PsyVideoService {

    @Override
    public boolean incrementPlayCount(Long id) {
        return baseMapper.incrementPlayCount(id) > 0;
    }
}