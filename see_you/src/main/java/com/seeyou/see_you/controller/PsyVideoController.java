package com.seeyou.see_you.controller;

import com.seeyou.see_you.entity.PsyVideo;
import com.seeyou.see_you.service.PsyVideoService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.seeyou.see_you.util.Result;
import jakarta.annotation.Resource;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;
import com.seeyou.see_you.util.PageLimit;
import com.seeyou.see_you.config.RequireAdmin;

@RestController
@RequestMapping("/video")
public class PsyVideoController {
    @Resource
    private PsyVideoService psyVideoService;

    @GetMapping("/{id:\\d+}")
    public Result<PsyVideo> getById(@PathVariable Long id) {
        return Result.success(psyVideoService.getById(id));
    }

    @GetMapping("/list")
    public Result<Page<PsyVideo>> list(@RequestParam(defaultValue = "1") Integer page,
                                        @RequestParam(defaultValue = "10") Integer size,
                                        @RequestParam(required = false) String category,
                                        @RequestParam(required = false) String keyword) {
        LambdaQueryWrapper<PsyVideo> wrapper = new LambdaQueryWrapper<>();
        if (StringUtils.hasText(category)) {
            wrapper.eq(PsyVideo::getCategory, category);
        }
        if (StringUtils.hasText(keyword)) {
            wrapper.like(PsyVideo::getVideoName, keyword);
        }
        wrapper.orderByDesc(PsyVideo::getCreateTime);
        return Result.success(psyVideoService.page(new Page<>(PageLimit.page(page), PageLimit.of(size, 10)), wrapper));
    }

    @RequireAdmin
    @PostMapping("/save")
    public Result<Boolean> save(@RequestBody PsyVideo video) {
        boolean b = psyVideoService.save(video);
        return b ? Result.success() : Result.error(500, "保存失败");
    }

    @RequireAdmin
    @PutMapping("/update")
    public Result<Boolean> update(@RequestBody PsyVideo video) {
        boolean b = psyVideoService.updateById(video);
        return b ? Result.success() : Result.error(500, "更新失败");
    }

    @RequireAdmin
    @DeleteMapping("/{id:\\d+}")
    public Result<Boolean> remove(@PathVariable Long id) {
        boolean b = psyVideoService.removeById(id);
        return b ? Result.success() : Result.error(500, "删除失败");
    }

    @PostMapping("/play/{id}")
    public Result<Boolean> play(@PathVariable Long id) {
        boolean b = psyVideoService.incrementPlayCount(id);
        return b ? Result.success() : Result.error(500, "播放量更新失败");
    }
}