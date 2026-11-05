package com.seeyou.see_you.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.seeyou.see_you.entity.PsyHistory;
import com.seeyou.see_you.mapper.PsyHistoryMapper;
import com.seeyou.see_you.service.PsyHistoryService;
import org.springframework.stereotype.Service;

@Service
public class PsyHistoryServiceImpl extends ServiceImpl<PsyHistoryMapper, PsyHistory> implements PsyHistoryService {
}