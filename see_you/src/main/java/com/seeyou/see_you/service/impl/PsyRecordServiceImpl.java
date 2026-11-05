package com.seeyou.see_you.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.seeyou.see_you.entity.PsyRecord;
import com.seeyou.see_you.mapper.PsyRecordMapper;
import com.seeyou.see_you.service.PsyRecordService;
import org.springframework.stereotype.Service;
@Service
public class PsyRecordServiceImpl extends ServiceImpl<PsyRecordMapper, PsyRecord> implements PsyRecordService {
}
