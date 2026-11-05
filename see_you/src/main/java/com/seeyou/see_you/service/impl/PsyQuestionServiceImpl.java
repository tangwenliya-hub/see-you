package com.seeyou.see_you.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.seeyou.see_you.dto.QuestionWithOptionsDTO;
import com.seeyou.see_you.entity.PsyOption;
import com.seeyou.see_you.entity.PsyQuestion;
import com.seeyou.see_you.mapper.PsyQuestionMapper;
import com.seeyou.see_you.service.PsyOptionService;
import com.seeyou.see_you.service.PsyQuestionService;
import com.seeyou.see_you.util.BizException;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class PsyQuestionServiceImpl extends ServiceImpl<PsyQuestionMapper, PsyQuestion> implements PsyQuestionService {

    @Resource
    private PsyOptionService psyOptionService;

    /**
     * 保存题目 + 选项。编辑时会删除前端未再传回的旧选项。
     * 多张表多次写入，必须整体事务，否则中途异常会留下「有题目没选项」的脏数据。
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long saveWithOptions(QuestionWithOptionsDTO dto) {
        if (dto.getQuestionContent() == null || dto.getQuestionContent().trim().isEmpty()) {
            throw new BizException("题目内容不能为空");
        }
        if (dto.getOptions() == null || dto.getOptions().size() < 2) {
            throw new BizException("至少需要2个选项");
        }

        boolean isEdit = dto.getQuestionId() != null;
        PsyQuestion question = new PsyQuestion();
        question.setQuestionId(dto.getQuestionId());
        question.setTestId(dto.getTestId());
        question.setQuestionContent(dto.getQuestionContent());

        boolean qSuccess = isEdit ? updateById(question) : save(question);
        if (!qSuccess) {
            throw new BizException("保存题目失败");
        }
        Long questionId = question.getQuestionId();

        List<PsyOption> options = dto.getOptions();
        if (isEdit) {
            // 编辑模式：删除数据库中有、但前端没传回来的选项
            List<PsyOption> existingOptions = psyOptionService.list(
                    new LambdaQueryWrapper<PsyOption>().eq(PsyOption::getQuestionId, questionId)
            );
            Set<Long> incomingIds = options.stream()
                    .filter(o -> o.getOptionId() != null)
                    .map(PsyOption::getOptionId)
                    .collect(Collectors.toSet());

            for (PsyOption existing : existingOptions) {
                if (!incomingIds.contains(existing.getOptionId())) {
                    psyOptionService.removeById(existing.getOptionId());
                }
            }
            for (PsyOption opt : options) {
                opt.setQuestionId(questionId);
                if (opt.getOptionId() != null) {
                    psyOptionService.updateById(opt);
                } else {
                    psyOptionService.save(opt);
                }
            }
        } else {
            // 新增模式：直接保存所有选项
            for (PsyOption opt : options) {
                opt.setOptionId(null);
                opt.setQuestionId(questionId);
                psyOptionService.save(opt);
            }
        }
        return questionId;
    }

    /** 删除题目时级联删除其选项，避免孤儿数据 */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean removeWithOptions(Long questionId) {
        psyOptionService.remove(new LambdaQueryWrapper<PsyOption>().eq(PsyOption::getQuestionId, questionId));
        return removeById(questionId);
    }
}
