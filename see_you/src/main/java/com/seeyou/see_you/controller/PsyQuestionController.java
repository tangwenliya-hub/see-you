package com.seeyou.see_you.controller;

import com.seeyou.see_you.dto.QuestionWithOptionsDTO;
import com.seeyou.see_you.entity.PsyQuestion;
import com.seeyou.see_you.service.PsyQuestionService;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.seeyou.see_you.util.Result;
import org.springframework.web.bind.annotation.*;
import jakarta.annotation.Resource;
import com.seeyou.see_you.util.PageLimit;

@RestController
@RequestMapping("/question")
public class PsyQuestionController {
    @Resource
    private PsyQuestionService psyQuestionService;

    @GetMapping("/{id:\\d+}")
    public Result<PsyQuestion> getById(@PathVariable Long id){
        return Result.success(psyQuestionService.getById(id));
    }
    @GetMapping("/list")
    public Result<Page<PsyQuestion>> list(@RequestParam(defaultValue = "1") Integer page,
                                           @RequestParam(defaultValue = "10") Integer size,
                                           @RequestParam(required = false) Long testId) {
        LambdaQueryWrapper<PsyQuestion> wrapper = new LambdaQueryWrapper<>();
        if (testId != null) wrapper.eq(PsyQuestion::getTestId, testId).orderByAsc(PsyQuestion::getQuestionId);
        else wrapper.orderByDesc(PsyQuestion::getQuestionId);
        return Result.success(psyQuestionService.page(new Page<>(PageLimit.page(page), PageLimit.of(size, 10)), wrapper));
    }

    @PostMapping("/save")
    public Result<Long> save(@RequestBody PsyQuestion question){
        boolean b = psyQuestionService.save(question);
        return b ? Result.success(question.getQuestionId()) : Result.error(500, "保存失败");
    }
    @PutMapping("/update")
    public Result<Boolean> update(@RequestBody PsyQuestion question){
        boolean b = psyQuestionService.updateById(question);
        return b ? Result.success() : Result.error(500, "更新失败");
    }
    @DeleteMapping("/{id:\\d+}")
    public Result<Boolean> remove(@PathVariable Long id){
        // 级联删除该题目下的所有选项（事务保证）
        boolean b = psyQuestionService.removeWithOptions(id);
        return b ? Result.success() : Result.error(500, "删除失败");
    }

    /**
     * 保存题目及其选项（支持新增和编辑，编辑时会自动删除不再传过来的选项）
     */
    @PostMapping("/saveWithOptions")
    public Result<Long> saveWithOptions(@RequestBody QuestionWithOptionsDTO dto) {
        // 参数校验与级联写入统一在 Service 层（事务保证题目与选项同生同灭）
        return Result.success(psyQuestionService.saveWithOptions(dto));
    }
}
