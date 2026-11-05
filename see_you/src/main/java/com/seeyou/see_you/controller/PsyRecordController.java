package com.seeyou.see_you.controller;

import com.seeyou.see_you.entity.PsyRecord;
import com.seeyou.see_you.entity.PsyResultGrade;
import com.seeyou.see_you.entity.PsyTestPaper;
import com.seeyou.see_you.service.PsyRecordService;
import com.seeyou.see_you.service.PsyResultGradeService;
import com.seeyou.see_you.service.PsyTestPaperService;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.seeyou.see_you.util.Result;
import org.springframework.web.bind.annotation.*;
import jakarta.annotation.Resource;
import java.util.Map;
import java.util.HashMap;
import java.util.List;
import java.util.stream.Collectors;
import com.seeyou.see_you.util.PageLimit;

@RestController
@RequestMapping("/record")
public class PsyRecordController {
    @Resource
    private PsyRecordService psyRecordService;
    @Resource
    private PsyTestPaperService psyTestPaperService;
    @Resource
    private PsyResultGradeService psyResultGradeService;

    /**
     * 按「量表 + 得分」匹配后台配置的结果分级（用户端结果页展示用，游客亦可访问）
     * 未配置分级时返回 null，前端回退到本地兜底文案
     */
    @GetMapping("/grade")
    public Result<Map<String, Object>> matchGrade(@RequestParam Long testId, @RequestParam Integer score) {
        List<PsyResultGrade> list = psyResultGradeService.list(
            new LambdaQueryWrapper<PsyResultGrade>()
                .eq(PsyResultGrade::getTestId, testId)
                .orderByAsc(PsyResultGrade::getMinScore)
        );
        PsyResultGrade hit = null;
        for (PsyResultGrade g : list) {
            int min = g.getMinScore() == null ? 0 : g.getMinScore();
            int max = g.getMaxScore() == null ? Integer.MAX_VALUE : g.getMaxScore();
            if (score >= min && score <= max) { hit = g; break; }
        }
        if (hit == null) return Result.success(null);
        Map<String, Object> m = new HashMap<>();
        m.put("gradeName", hit.getGradeName());
        m.put("description", hit.getDescription());
        m.put("suggestion", hit.getSuggestion());
        m.put("minScore", hit.getMinScore());
        m.put("maxScore", hit.getMaxScore());
        return Result.success(m);
    }

    @GetMapping("/{id:\\d+}")
    public Result<PsyRecord> getById(@PathVariable Long id, jakarta.servlet.http.HttpServletRequest request){
        PsyRecord r = psyRecordService.getById(id);
        if (r == null) return Result.error(404, "记录不存在");
        // 安全：非管理员只能查看自己的测评记录
        Long uid = (Long) request.getAttribute("userId");
        Integer role = (Integer) request.getAttribute("role");
        boolean admin = Integer.valueOf(1).equals(role);
        if (!admin && !r.getUserId().equals(uid)) return Result.error(403, "无权查看他人记录");
        return Result.success(r);
    }
    @GetMapping("/list")
    public Result<Page<Map<String, Object>>> list(@RequestParam(defaultValue = "1") Integer page,
                                                  @RequestParam(defaultValue = "10") Integer size,
                                                  @RequestParam(required = false) Long userId,
                                                  jakarta.servlet.http.HttpServletRequest request) {
        LambdaQueryWrapper<PsyRecord> wrapper = new LambdaQueryWrapper<>();
        // 安全：userId 以登录态为准；仅管理员可显式按 userId 代查
        Long uid = (Long) request.getAttribute("userId");
        Integer role = (Integer) request.getAttribute("role");
        boolean admin = Integer.valueOf(1).equals(role);
        if (admin && userId != null) wrapper.eq(PsyRecord::getUserId, userId);
        else wrapper.eq(PsyRecord::getUserId, uid);
        wrapper.orderByDesc(PsyRecord::getCreateTime);
        Page<PsyRecord> recordPage = psyRecordService.page(new Page<>(PageLimit.page(page), PageLimit.of(size, 10)), wrapper);

        // 关联查询试卷名称
        List<Long> testIds = recordPage.getRecords().stream()
            .map(PsyRecord::getTestId).filter(id -> id != null).distinct().collect(Collectors.toList());
        Map<Long, String> testNameMap = new HashMap<>();
        if (!testIds.isEmpty()) {
            List<PsyTestPaper> papers = psyTestPaperService.listByIds(testIds);
            for (PsyTestPaper p : papers) testNameMap.put(p.getTestId(), p.getTestName());
        }

        // 组装返回结果
        Page<Map<String, Object>> resultPage = new Page<>(recordPage.getCurrent(), recordPage.getSize(), recordPage.getTotal());
        List<Map<String, Object>> records = recordPage.getRecords().stream().map(r -> {
            Map<String, Object> m = new HashMap<>();
            m.put("recordId", r.getRecordId());
            m.put("userId", r.getUserId());
            m.put("testId", r.getTestId());
            m.put("totalScore", r.getTotalScore());
            m.put("score", r.getScore() != null ? r.getScore() : r.getTotalScore());
            m.put("level", r.getLevel());
            m.put("tip", r.getTip());
            m.put("resultEmotion", r.getResultEmotion());
            m.put("createTime", r.getCreateTime());
            m.put("testName", testNameMap.getOrDefault(r.getTestId(), "综合测试"));
            m.put("testTitle", testNameMap.getOrDefault(r.getTestId(), "综合测试"));
            return m;
        }).collect(Collectors.toList());
        resultPage.setRecords(records);
        return Result.success(resultPage);
    }

    @PostMapping("/save")
    public Result<Boolean> save(@RequestBody PsyRecord record, jakarta.servlet.http.HttpServletRequest request){
        // 安全：归属强制为当前登录用户，防止代他人写入
        record.setUserId((Long) request.getAttribute("userId"));
        record.setRecordId(null);
        boolean b = psyRecordService.save(record);
        return b ? Result.success() : Result.error(500, "保存失败");
    }
    @PutMapping("/update")
    public Result<Boolean> update(@RequestBody PsyRecord record, jakarta.servlet.http.HttpServletRequest request){
        PsyRecord old = record.getRecordId() == null ? null : psyRecordService.getById(record.getRecordId());
        if (old == null) return Result.error(404, "记录不存在");
        Long uid = (Long) request.getAttribute("userId");
        Integer role = (Integer) request.getAttribute("role");
        if (!Integer.valueOf(1).equals(role) && !old.getUserId().equals(uid)) return Result.error(403, "无权修改他人记录");
        boolean b = psyRecordService.updateById(record);
        return b ? Result.success() : Result.error(500, "更新失败");
    }
    @DeleteMapping("/{id:\\d+}")
    public Result<Boolean> remove(@PathVariable Long id, jakarta.servlet.http.HttpServletRequest request){
        PsyRecord old = psyRecordService.getById(id);
        if (old == null) return Result.error(404, "记录不存在");
        Long uid = (Long) request.getAttribute("userId");
        Integer role = (Integer) request.getAttribute("role");
        if (!Integer.valueOf(1).equals(role) && !old.getUserId().equals(uid)) return Result.error(403, "无权删除他人记录");
        boolean b = psyRecordService.removeById(id);
        return b ? Result.success() : Result.error(500, "删除失败");
    }
}