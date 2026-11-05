package com.seeyou.see_you.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.seeyou.see_you.dto.QuestionWithOptionsDTO;
import com.seeyou.see_you.entity.PsyQuestion;

public interface PsyQuestionService extends IService<PsyQuestion> {

    /**
     * 保存题目及其选项（新增/编辑）。
     * 题目与选项必须同生同灭，因此整体加事务：任一步失败全部回滚，避免留下半截数据。
     *
     * @return 题目 ID
     */
    Long saveWithOptions(QuestionWithOptionsDTO dto);

    /**
     * 删除题目并级联删除其下所有选项，避免留下孤儿选项。
     */
    boolean removeWithOptions(Long questionId);
}
