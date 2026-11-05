package com.seeyou.see_you.controller;

import com.seeyou.see_you.entity.PsyAiWords;
import com.seeyou.see_you.service.PsyAiWordsService;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.seeyou.see_you.util.Result;
import jakarta.annotation.Resource;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.stream.Collectors;
import com.seeyou.see_you.util.PageLimit;

@RestController
@RequestMapping("/admin/aiWords")
public class PsyAiWordsController {
    @Resource
    private PsyAiWordsService psyAiWordsService;

    @GetMapping("/{id:\\d+}")
    public Result<PsyAiWords> getById(@PathVariable Long id) {
        return Result.success(psyAiWordsService.getById(id));
    }

    // 获取所有启用的关键词（前端匹配用）
    @GetMapping("/all")
    public Result<java.util.List<PsyAiWords>> getAll() {
        LambdaQueryWrapper<PsyAiWords> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(PsyAiWords::getStatus, 1)
               .orderByAsc(PsyAiWords::getSort);
        return Result.success(psyAiWordsService.list(wrapper));
    }

    // 获取所有分类
    @GetMapping("/categories")
    public Result<List<String>> getCategories() {
        LambdaQueryWrapper<PsyAiWords> wrapper = new LambdaQueryWrapper<>();
        wrapper.select(PsyAiWords::getCategory)
               .isNotNull(PsyAiWords::getCategory)
               .ne(PsyAiWords::getCategory, "")
               .groupBy(PsyAiWords::getCategory)
               .orderByAsc(PsyAiWords::getCategory);
        List<String> categories = psyAiWordsService.list(wrapper).stream()
                .map(PsyAiWords::getCategory)
                .filter(c -> c != null && !c.isBlank())
                .distinct()
                .collect(Collectors.toList());
        return Result.success(categories);
    }

    @GetMapping("/list")
    public Result<Page<PsyAiWords>> list(@RequestParam(defaultValue = "1") Integer page,
                                          @RequestParam(defaultValue = "10") Integer size,
                                          @RequestParam(required = false) String keyword,
                                          @RequestParam(required = false) String category,
                                          @RequestParam(required = false) Integer status) {
        LambdaQueryWrapper<PsyAiWords> wrapper = new LambdaQueryWrapper<>();
        if (keyword != null && !keyword.isBlank()) {
            wrapper.and(w -> w.like(PsyAiWords::getKeyword, keyword)
                    .or().like(PsyAiWords::getMatchExample, keyword)
                    .or().like(PsyAiWords::getReplyText, keyword));
        }
        if (category != null && !category.isBlank()) {
            wrapper.like(PsyAiWords::getCategory, category);
        }
        if (status != null) {
            wrapper.eq(PsyAiWords::getStatus, status);
        }
        wrapper.orderByAsc(PsyAiWords::getSort).orderByDesc(PsyAiWords::getWordId);
        return Result.success(psyAiWordsService.page(new Page<>(PageLimit.page(page), PageLimit.of(size, 10)), wrapper));
    }

    @PostMapping("/save")
    public Result<Boolean> save(@RequestBody PsyAiWords aiWords) {
        if (aiWords.getStatus() == null) aiWords.setStatus(1);
        if (aiWords.getSort() == null) aiWords.setSort(0);
        boolean b = psyAiWordsService.save(aiWords);
        return b ? Result.success() : Result.error(500, "保存失败");
    }

    @PutMapping("/update")
    public Result<Boolean> update(@RequestBody PsyAiWords aiWords) {
        boolean b = psyAiWordsService.updateById(aiWords);
        return b ? Result.success() : Result.error(500, "更新失败");
    }

    @DeleteMapping("/{id:\\d+}")
    public Result<Boolean> remove(@PathVariable Long id) {
        boolean b = psyAiWordsService.removeById(id);
        return b ? Result.success() : Result.error(500, "删除失败");
    }
}
