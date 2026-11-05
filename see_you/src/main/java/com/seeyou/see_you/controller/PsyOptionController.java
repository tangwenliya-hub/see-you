package com.seeyou.see_you.controller;

import com.seeyou.see_you.entity.PsyOption;
import com.seeyou.see_you.service.PsyOptionService;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.seeyou.see_you.util.Result;
import org.springframework.web.bind.annotation.*;
import jakarta.annotation.Resource;
import com.seeyou.see_you.util.PageLimit;
@RestController
@RequestMapping("/option")
public class PsyOptionController {
    @Resource
    private PsyOptionService psyOptionService;

    @GetMapping("/{id:\\d+}")
    public Result<PsyOption> getById(@PathVariable Long id){
        return Result.success(psyOptionService.getById(id));
    }
    @GetMapping("/list")
    public Result<Page<PsyOption>> list(@RequestParam(defaultValue = "1") Integer page,
                                        @RequestParam(defaultValue = "10") Integer size,
                                        @RequestParam(required = false) Long questionId) {
        LambdaQueryWrapper<PsyOption> wrapper = new LambdaQueryWrapper<>();
        if (questionId != null) wrapper.eq(PsyOption::getQuestionId, questionId).orderByAsc(PsyOption::getOptionId);
        else wrapper.orderByDesc(PsyOption::getOptionId);
        return Result.success(psyOptionService.page(new Page<>(PageLimit.page(page), PageLimit.of(size, 10)), wrapper));
    }

    @PostMapping("/save")
    public Result<Long> save(@RequestBody PsyOption option){
        boolean b = psyOptionService.save(option);
        return b ? Result.success(option.getOptionId()) : Result.error(500, "保存失败");
    }
    @PutMapping("/update")
    public Result<Boolean> update(@RequestBody PsyOption option){
        boolean b = psyOptionService.updateById(option);
        return b ? Result.success() : Result.error(500, "更新失败");
    }
    @DeleteMapping("/{id:\\d+}")
    public Result<Boolean> remove(@PathVariable Long id){
        boolean b = psyOptionService.removeById(id);
        return b ? Result.success() : Result.error(500, "删除失败");
    }
}
