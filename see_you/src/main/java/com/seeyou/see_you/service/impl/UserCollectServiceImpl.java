package com.seeyou.see_you.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.seeyou.see_you.entity.UserCollect;
import com.seeyou.see_you.mapper.UserCollectMapper;
import com.seeyou.see_you.service.UserCollectService;
import org.springframework.stereotype.Service;

@Service
public class UserCollectServiceImpl extends ServiceImpl<UserCollectMapper, UserCollect> implements UserCollectService {

    @Override
    public boolean removeByResource(Long userId, Integer resourceType, Long resourceId) {
        LambdaQueryWrapper<UserCollect> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(UserCollect::getUserId, userId)
                .eq(UserCollect::getResourceType, resourceType)
                .eq(UserCollect::getResourceId, resourceId);
        return remove(wrapper);
    }

    @Override
    public long countByResource(Integer resourceType, Long resourceId) {
        LambdaQueryWrapper<UserCollect> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(UserCollect::getResourceType, resourceType)
                .eq(UserCollect::getResourceId, resourceId);
        return count(wrapper);
    }

    @Override
    public boolean checkCollect(Long userId, Integer resourceType, Long resourceId) {
        LambdaQueryWrapper<UserCollect> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(UserCollect::getUserId, userId)
                .eq(UserCollect::getResourceType, resourceType)
                .eq(UserCollect::getResourceId, resourceId);
        return count(wrapper) > 0;
    }
}
