package com.seeyou.see_you.controller;

import com.seeyou.see_you.entity.PsyQuestion;
import com.seeyou.see_you.entity.PsyRecord;
import com.seeyou.see_you.entity.PsyTestPaper;
import com.seeyou.see_you.service.PsyQuestionService;
import com.seeyou.see_you.service.PsyRecordService;
import com.seeyou.see_you.service.PsyTestPaperService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.seeyou.see_you.util.Result;
import org.springframework.web.bind.annotation.*;
import jakarta.annotation.Resource;
import java.util.List;
import com.seeyou.see_you.util.PageLimit;
import com.seeyou.see_you.config.RequireAdmin;

@RestController
@RequestMapping("/paper")
public class PsyTestPaperController {
    @Resource
    private PsyTestPaperService psyTestPaperService;
    @Resource
    private PsyQuestionService psyQuestionService;
    @Resource
    private PsyRecordService psyRecordService;

    @GetMapping("/{id:\\d+}")
    public Result<PsyTestPaper> getById(@PathVariable Long id){
        return Result.success(psyTestPaperService.getById(id));
    }
    @GetMapping("/list")
    public Result<?> list(@RequestParam(defaultValue = "1") Integer page,
                           @RequestParam(defaultValue = "1000") Integer size) {
        if (size >= 1000) {
            List<PsyTestPaper> papers = psyTestPaperService.list();
            for (PsyTestPaper p : papers) {
                long qCount = psyQuestionService.count(
                    new LambdaQueryWrapper<PsyQuestion>().eq(PsyQuestion::getTestId, p.getTestId()));
                p.setQuestionCount((int) qCount);
                long rCount = psyRecordService.count(
                    new LambdaQueryWrapper<PsyRecord>().eq(PsyRecord::getTestId, p.getTestId()));
                p.setTakenCount((int) rCount);
            }
            return Result.success(papers);
        }
        Page<PsyTestPaper> pg = psyTestPaperService.page(new Page<>(PageLimit.page(page), PageLimit.of(size, 1000)));
        for (PsyTestPaper p : pg.getRecords()) {
            long qCount = psyQuestionService.count(
                new LambdaQueryWrapper<PsyQuestion>().eq(PsyQuestion::getTestId, p.getTestId()));
            p.setQuestionCount((int) qCount);
            long rCount = psyRecordService.count(
                new LambdaQueryWrapper<PsyRecord>().eq(PsyRecord::getTestId, p.getTestId()));
            p.setTakenCount((int) rCount);
        }
        return Result.success(pg);
    }

    @RequireAdmin
    @PostMapping("/save")
    public Result<Long> save(@RequestBody PsyTestPaper paper){
        boolean b = psyTestPaperService.save(paper);
        return b ? Result.success(paper.getTestId()) : Result.error(500, "保存失败");
    }
    @RequireAdmin
    @PutMapping("/update")
    public Result<Boolean> update(@RequestBody PsyTestPaper paper){
        boolean b = psyTestPaperService.updateById(paper);
        return b ? Result.success() : Result.error(500, "更新失败");
    }
    @RequireAdmin
    @DeleteMapping("/{id:\\d+}")
    public Result<Boolean> remove(@PathVariable Long id){
        // 级联删除该试卷下的题目、选项与答题记录（事务保证）
        boolean b = psyTestPaperService.removeCascade(id);
        return b ? Result.success() : Result.error(500, "删除失败");
    }

    /**
     * 更新量表状态（启用/禁用）
     */
    @RequireAdmin
    @PutMapping("/updateStatus")
    public Result<Boolean> updateStatus(@RequestParam Long testId, @RequestParam Integer status) {
        PsyTestPaper paper = new PsyTestPaper();
        paper.setTestId(testId);
        paper.setStatus(status);
        boolean b = psyTestPaperService.updateById(paper);
        return b ? Result.success() : Result.error(500, "更新状态失败");
    }
}