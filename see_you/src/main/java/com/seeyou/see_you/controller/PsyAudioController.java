package com.seeyou.see_you.controller;

import com.seeyou.see_you.entity.PsyAudio;
import com.seeyou.see_you.service.PsyAudioService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.seeyou.see_you.util.Result;
import jakarta.annotation.Resource;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;
import com.seeyou.see_you.util.PageLimit;
import com.seeyou.see_you.config.RequireAdmin;

@RestController
@RequestMapping("/audio")
public class PsyAudioController {
    @Resource
    private PsyAudioService psyAudioService;

    @GetMapping("/{id:\\d+}")
    public Result<PsyAudio> getById(@PathVariable Long id) {
        return Result.success(psyAudioService.getById(id));
    }

    @GetMapping("/list")
    public Result<Page<PsyAudio>> list(@RequestParam(defaultValue = "1") Integer page,
                                        @RequestParam(defaultValue = "10") Integer size,
                                        @RequestParam(required = false) String category,
                                        @RequestParam(required = false) String keyword) {
        LambdaQueryWrapper<PsyAudio> wrapper = new LambdaQueryWrapper<>();
        if (StringUtils.hasText(category)) {
            wrapper.eq(PsyAudio::getCategory, category);
        }
        if (StringUtils.hasText(keyword)) {
            wrapper.like(PsyAudio::getAudioName, keyword);
        }
        wrapper.orderByDesc(PsyAudio::getCreateTime);
        return Result.success(psyAudioService.page(new Page<>(PageLimit.page(page), PageLimit.of(size, 10)), wrapper));
    }

    @RequireAdmin
    @PostMapping("/save")
    public Result<Boolean> save(@RequestBody PsyAudio audio) {
        boolean b = psyAudioService.save(audio);
        return b ? Result.success() : Result.error(500, "保存失败");
    }

    @RequireAdmin
    @PutMapping("/update")
    public Result<Boolean> update(@RequestBody PsyAudio audio) {
        boolean b = psyAudioService.updateById(audio);
        return b ? Result.success() : Result.error(500, "更新失败");
    }

    @RequireAdmin
    @DeleteMapping("/{id:\\d+}")
    public Result<Boolean> remove(@PathVariable Long id) {
        boolean b = psyAudioService.removeById(id);
        return b ? Result.success() : Result.error(500, "删除失败");
    }

    @PostMapping("/play/{id}")
    public Result<Boolean> play(@PathVariable Long id) {
        boolean b = psyAudioService.incrementPlayCount(id);
        return b ? Result.success() : Result.error(500, "播放量更新失败");
    }
}
