package com.seeyou.see_you.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.seeyou.see_you.entity.PsyOption;
import com.seeyou.see_you.entity.PsyQuestion;
import com.seeyou.see_you.entity.PsyRecord;
import com.seeyou.see_you.entity.PsyTestPaper;
import com.seeyou.see_you.mapper.PsyTestPaperMapper;
import com.seeyou.see_you.service.PsyOptionService;
import com.seeyou.see_you.service.PsyQuestionService;
import com.seeyou.see_you.service.PsyRecordService;
import com.seeyou.see_you.service.PsyTestPaperService;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
public class PsyTestPaperServiceImpl extends ServiceImpl<PsyTestPaperMapper, PsyTestPaper> implements PsyTestPaperService {

    @Resource
    private PsyQuestionService psyQuestionService;
    @Resource
    private PsyOptionService psyOptionService;
    @Resource
    private PsyRecordService psyRecordService;

    /**
     * 级联删除：答题记录 -> 选项 -> 题目 -> 试卷（顺序不能反，先删子表）。
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean removeCascade(Long testId) {
        // 1) 该试卷的答题记录
        psyRecordService.remove(new LambdaQueryWrapper<PsyRecord>().eq(PsyRecord::getTestId, testId));

        // 2) 该试卷下的题目，及其选项
        List<Long> questionIds = psyQuestionService.list(
                new LambdaQueryWrapper<PsyQuestion>().eq(PsyQuestion::getTestId, testId)
        ).stream().map(PsyQuestion::getQuestionId).collect(Collectors.toList());

        if (!questionIds.isEmpty()) {
            psyOptionService.remove(new LambdaQueryWrapper<PsyOption>().in(PsyOption::getQuestionId, questionIds));
            psyQuestionService.remove(new LambdaQueryWrapper<PsyQuestion>().eq(PsyQuestion::getTestId, testId));
        }

        // 3) 试卷本身
        return removeById(testId);
    }
}
