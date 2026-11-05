package com.seeyou.see_you.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.seeyou.see_you.entity.UserLike;
import com.seeyou.see_you.mapper.UserLikeMapper;
import com.seeyou.see_you.service.UserLikeService;
import org.springframework.stereotype.Service;

@Service
public class UserLikeServiceImpl extends ServiceImpl<UserLikeMapper, UserLike> implements UserLikeService {

    @Override
    public boolean removeByResource(Long userId, Integer resourceType, Long resourceId) {
        LambdaQueryWrapper<UserLike> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(UserLike::getUserId, userId)
                .eq(UserLike::getResourceType, resourceType)
                .eq(UserLike::getResourceId, resourceId);
        return remove(wrapper);
    }

    @Override
    public long countByResource(Integer resourceType, Long resourceId) {
        LambdaQueryWrapper<UserLike> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(UserLike::getResourceType, resourceType)
                .eq(UserLike::getResourceId, resourceId);
        return count(wrapper);
    }

    @Override
    public boolean checkLike(Long userId, Integer resourceType, Long resourceId) {
        LambdaQueryWrapper<UserLike> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(UserLike::getUserId, userId)
                .eq(UserLike::getResourceType, resourceType)
                .eq(UserLike::getResourceId, resourceId);
        return count(wrapper) > 0;
    }
}
