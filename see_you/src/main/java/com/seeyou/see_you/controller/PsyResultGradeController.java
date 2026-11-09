package com.seeyou.see_you.controller;

import com.seeyou.see_you.entity.PsyResultGrade;
import com.seeyou.see_you.service.PsyResultGradeService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.seeyou.see_you.util.Result;
import org.springframework.web.bind.annotation.*;
import jakarta.annotation.Resource;
import java.util.List;
import com.seeyou.see_you.config.RequireAdmin;

@RequireAdmin
@RestController
@RequestMapping("/admin/resultGrade")
public class PsyResultGradeController {
    @Resource
    private PsyResultGradeService psyResultGradeService;

    /**
     * 查询某个量表的所有结果分级
     */
    @GetMapping("/listByTest")
    public Result<List<PsyResultGrade>> listByTest(@RequestParam Long testId) {
        List<PsyResultGrade> list = psyResultGradeService.list(
            new LambdaQueryWrapper<PsyResultGrade>()
                .eq(PsyResultGrade::getTestId, testId)
                .orderByAsc(PsyResultGrade::getMinScore)
        );
        return Result.success(list);
    }

    /**
     * 新增分级
     */
    @PostMapping("/save")
    public Result<Long> save(@RequestBody PsyResultGrade grade) {
        boolean b = psyResultGradeService.save(grade);
        return b ? Result.success(grade.getGradeId()) : Result.error(500, "保存失败");
    }

    /**
     * 更新分级
     */
    @PutMapping("/update")
    public Result<Boolean> update(@RequestBody PsyResultGrade grade) {
        boolean b = psyResultGradeService.updateById(grade);
        return b ? Result.success() : Result.error(500, "更新失败");
    }

    /**
     * 删除分级
     */
    @DeleteMapping("/{id:\\d+}")
    public Result<Boolean> remove(@PathVariable Long id) {
        boolean b = psyResultGradeService.removeById(id);
        return b ? Result.success() : Result.error(500, "删除失败");
    }

    /**
     * 批量保存某个量表的所有分级（先删后插，用于编辑弹窗整体保存）
     */
    @PostMapping("/batchSave")
    @org.springframework.transaction.annotation.Transactional(rollbackFor = Exception.class)
    public Result<Boolean> batchSave(@RequestParam Long testId, @RequestBody List<PsyResultGrade> grades) {
        // 事务保证：先删后插必须原子，否则中途失败会把该量表的全部分级删光且没有新数据补上
        psyResultGradeService.remove(
            new LambdaQueryWrapper<PsyResultGrade>().eq(PsyResultGrade::getTestId, testId)
        );
        // 批量插入新分级
        if (grades != null && !grades.isEmpty()) {
            for (PsyResultGrade g : grades) {
                g.setGradeId(null);
                g.setTestId(testId);
            }
            boolean b = psyResultGradeService.saveBatch(grades);
            return b ? Result.success() : Result.error(500, "保存失败");
        }
        return Result.success();
    }
}
