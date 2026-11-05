package com.seeyou.see_you.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.seeyou.see_you.entity.UserLike;

public interface UserLikeService extends IService<UserLike> {
    /**
     * 按 userId + resourceType + resourceId 取消点赞
     */
    boolean removeByResource(Long userId, Integer resourceType, Long resourceId);

    /**
     * 统计某资源的点赞数
     */
    long countByResource(Integer resourceType, Long resourceId);

    /**
     * 检查用户是否已点赞某资源
     */
    boolean checkLike(Long userId, Integer resourceType, Long resourceId);
}
