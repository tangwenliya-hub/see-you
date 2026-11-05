package com.seeyou.see_you.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.seeyou.see_you.entity.PsyBanner;
import com.seeyou.see_you.mapper.PsyBannerMapper;
import com.seeyou.see_you.util.Result;
import jakarta.annotation.Resource;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 首页轮播 / 公告的公开读取接口（游客可见，无需登录）。
 * 后台维护走 /admin/banner/*。
 */
@RestController
@RequestMapping("/banner")
public class BannerController {

    @Resource private PsyBannerMapper bannerMapper;

    /**
     * 取当前生效的轮播：启用状态 + 在生效时间窗内（时间为空表示不限制）+ 按 sort 升序。
     */
    @GetMapping("/list")
    public Result<List<PsyBanner>> list(@RequestParam(defaultValue = "home") String position) {
        LocalDateTime now = LocalDateTime.now();
        LambdaQueryWrapper<PsyBanner> w = new LambdaQueryWrapper<>();
        w.eq(PsyBanner::getPosition, position)
                .eq(PsyBanner::getStatus, 1)
                .and(x -> x.isNull(PsyBanner::getStartTime).or().le(PsyBanner::getStartTime, now))
                .and(x -> x.isNull(PsyBanner::getEndTime).or().ge(PsyBanner::getEndTime, now))
                .orderByAsc(PsyBanner::getSort)
                .orderByDesc(PsyBanner::getBannerId);
        return Result.success(bannerMapper.selectList(w));
    }
}
