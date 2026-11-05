package com.seeyou.see_you.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.seeyou.see_you.entity.UserCollect;

public interface UserCollectService extends IService<UserCollect> {
    /**
     * 按 userId + resourceType + resourceId 取消收藏
     */
    boolean removeByResource(Long userId, Integer resourceType, Long resourceId);

    /**
     * 统计某资源的收藏数
     */
    long countByResource(Integer resourceType, Long resourceId);

    /**
     * 检查用户是否已收藏某资源
     */
    boolean checkCollect(Long userId, Integer resourceType, Long resourceId);
}