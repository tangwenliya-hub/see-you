package com.seeyou.see_you.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.seeyou.see_you.entity.PsyTestPaper;

public interface PsyTestPaperService extends IService<PsyTestPaper> {

    /**
     * 删除试卷并级联清理其下的题目、选项与答题记录。
     * 涉及多张表，必须整体事务，否则中途失败会留下「有题目没试卷」的孤儿数据。
     */
    boolean removeCascade(Long testId);
}
